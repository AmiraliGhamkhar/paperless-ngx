# Vela Studio ProGuard rules.
# Kotlin serialization keeps generated serializers via annotations.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Room entities and DAOs are generated against these classes.
-keep class studio.vela.art.data.db.** { *; }
-keepclassmembers class studio.vela.art.data.db.** { *; }

# Serialized document manifests must survive minification.
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class studio.vela.art.data.document.**$$serializer { *; }
-keep class studio.vela.art.data.document.** { *; }

# Keep enum names stable: persisted project files store them as strings.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-dontwarn org.jetbrains.annotations.**
