-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class dev.clonner.**$$serializer { *; }
-keepclassmembers class dev.clonner.** {
    *** Companion;
    *** INSTANCE;
}

-keep class dev.clonner.vpn.ClonnerVpnService { *; }
-keep class dev.clonner.shortcut.CloneLaunchActivity { *; }
