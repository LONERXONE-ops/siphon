# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# youtubedl-android
-keep class com.yausername.youtubedl_android.** { *; }
-keepclassmembers class * {
    native <methods>;
}

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Media3 ExoPlayer
-keep class androidx.media3.exoplayer.** { *; }
