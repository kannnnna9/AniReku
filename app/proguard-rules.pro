# kotlinx.serialization: keep generated serializers for our @Serializable models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.anireku.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.anireku.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.anireku.app.**$$serializer { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Media3 ships its own rules; nothing to add.
