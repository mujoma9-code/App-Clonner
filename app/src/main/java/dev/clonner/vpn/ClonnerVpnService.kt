package dev.clonner.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.clonner.MainActivity
import dev.clonner.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A local, on-device DNS filter.
 *
 * The tunnel is deliberately narrow: only the fake DNS server address is routed into it,
 * so every other byte of traffic takes its normal path and never passes through Clonner.
 * For each DNS question we either answer NXDOMAIN ourselves (blocked) or forward the query
 * to the real upstream resolver over a [protect]ed socket and relay the reply back.
 *
 * Nothing is logged off-device and there is no remote endpoint — "VPN" here is only the
 * Android API that lets an app see its own device's DNS traffic.
 */
class ClonnerVpnService : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private lateinit var forwarders: ExecutorService

    @Volatile
    private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                teardown()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (!running) start()
        return START_STICKY
    }

    private fun start() {
        val descriptor = try {
            Builder()
                .setSession(getString(R.string.app_name))
                .addAddress(TUN_ADDRESS, 32)
                .addDnsServer(TUN_DNS)
                .addRoute(TUN_DNS, 32)          // only DNS enters the tunnel
                .setBlocking(true)
                .setMtu(MTU)
                .also { builder ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        builder.setMetered(false)
                    }
                    // Never filter our own lookups — that would deadlock blocklist updates.
                    runCatching { builder.addDisallowedApplication(packageName) }
                }
                .establish()
        } catch (t: Throwable) {
            Log.e(TAG, "Could not establish the tunnel", t)
            null
        }

        if (descriptor == null) {
            _state.value = ShieldState.Error
            stopSelf()
            return
        }

        tunnel = descriptor
        running = true
        forwarders = Executors.newFixedThreadPool(FORWARDER_THREADS)
        startForeground(NOTIFICATION_ID, buildNotification())
        _state.value = ShieldState.Running

        worker = Thread({ pump(descriptor) }, "clonner-dns").apply {
            isDaemon = true
            start()
        }
    }

    /** Reads DNS queries off the tunnel until the service is torn down. */
    private fun pump(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(MTU)
        val writeLock = Any()

        try {
            while (running) {
                val read = input.read(buffer)
                if (read <= 0) continue

                val datagram = Packets.parseUdp(buffer, read) ?: continue
                if (datagram.destPort != DNS_PORT) continue

                val query = datagram.payload
                val domain = DnsMessage.readQuestionName(query)

                if (domain != null && BlocklistEngine.shouldBlock(domain)) {
                    BlocklistEngine.record(domain, blocked = true)
                    val reply = Packets.buildUdp(
                        sourceIp = datagram.destIp,
                        destIp = datagram.sourceIp,
                        sourcePort = datagram.destPort,
                        destPort = datagram.sourcePort,
                        payload = DnsMessage.buildNxDomain(query),
                    )
                    synchronized(writeLock) { output.write(reply) }
                } else {
                    if (domain != null) BlocklistEngine.record(domain, blocked = false)
                    forwarders.execute { forward(datagram, output, writeLock) }
                }
            }
        } catch (t: Throwable) {
            if (running) Log.w(TAG, "DNS pump stopped", t)
        }
    }

    /** Sends an allowed query to the real resolver and relays the answer back into the tunnel. */
    private fun forward(datagram: Packets.UdpDatagram, output: FileOutputStream, writeLock: Any) {
        val socket = DatagramSocket()
        try {
            // Keeps this socket outside the tunnel, otherwise the query would loop back to us.
            if (!protect(socket)) return
            socket.soTimeout = UPSTREAM_TIMEOUT_MS

            val upstream = InetAddress.getByName(UPSTREAM_DNS)
            socket.send(DatagramPacket(datagram.payload, datagram.payload.size, upstream, DNS_PORT))

            val response = ByteArray(MAX_DNS_RESPONSE)
            val packet = DatagramPacket(response, response.size)
            socket.receive(packet)

            val reply = Packets.buildUdp(
                sourceIp = datagram.destIp,
                destIp = datagram.sourceIp,
                sourcePort = datagram.destPort,
                destPort = datagram.sourcePort,
                payload = response.copyOf(packet.length),
            )
            synchronized(writeLock) { output.write(reply) }
        } catch (t: Throwable) {
            // A timeout is normal on a flaky network; the caller's resolver will retry.
        } finally {
            socket.close()
        }
    }

    private fun teardown() {
        running = false
        _state.value = ShieldState.Stopped
        worker?.interrupt()
        worker = null
        runCatching { tunnel?.close() }
        tunnel = null
        if (this::forwarders.isInitialized) {
            forwarders.shutdownNow()
            runCatching { forwarders.awaitTermination(1, TimeUnit.SECONDS) }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onRevoke() {
        teardown()
        stopSelf()
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.vpn_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { setShowBadge(false) }
            )
        }

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, ClonnerVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_notification)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_body))
            .setContentIntent(open)
            .addAction(0, getString(R.string.stop), stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "ClonnerVpn"
        const val ACTION_STOP = "dev.clonner.STOP_SHIELD"

        private const val CHANNEL_ID = "clonner_shield"
        private const val NOTIFICATION_ID = 42

        private const val TUN_ADDRESS = "10.215.173.1"
        private const val TUN_DNS = "10.215.173.2"
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val DNS_PORT = 53
        private const val MTU = 1500
        private const val MAX_DNS_RESPONSE = 4096
        private const val UPSTREAM_TIMEOUT_MS = 5_000
        private const val FORWARDER_THREADS = 8

        private val _state = MutableStateFlow<ShieldState>(ShieldState.Stopped)
        val state: StateFlow<ShieldState> = _state.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, ClonnerVpnService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, ClonnerVpnService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}

sealed interface ShieldState {
    data object Stopped : ShieldState
    data object Running : ShieldState
    data object Error : ShieldState
}
