# ProGuard rules for :app
-keepattributes *Annotation*
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}
