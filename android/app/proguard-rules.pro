# Proguard rules for TALOS
-keep class com.sarvesh173.talos.data.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
