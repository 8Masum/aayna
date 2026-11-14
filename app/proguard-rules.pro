# ProGuard rules for :app
-keepattributes *Annotation*, InnerClasses, Signature

# kotlinx.serialization (models, Companion, $$serializer)
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}
-keep,includedescriptorclasses class com.themasum.aayna.**$$serializer { *; }
-keep,includedescriptorclasses class *$$serializer { *; }
-keepclassmembers class com.themasum.aayna.** {
    *** Companion;
}
-keepclassmembers @kotlinx.serialization.Serializable class com.themasum.aayna.** { *; }
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room v2 database, generated _Impl classes, DAOs, and Entities
-keep class * extends androidx.room.RoomDatabase
-keep class com.themasum.aayna.core.storage.db.*_Impl { *; }
-keep class com.themasum.aayna.core.storage.db.*_Impl$* { *; }
-keep @androidx.room.Entity class com.themasum.aayna.core.storage.db.** { *; }
-keep @androidx.room.Dao interface com.themasum.aayna.core.storage.db.** { *; }

# CameraX SurfaceProcessor, CameraEffect, and VideoCapture Recorder
-keep class androidx.camera.core.SurfaceProcessor { *; }
-keep class androidx.camera.core.CameraEffect { *; }
-keep class androidx.camera.video.Recorder { *; }
-keep class androidx.camera.video.VideoCapture { *; }
-keep class com.themasum.aayna.core.camera.** { *; }
