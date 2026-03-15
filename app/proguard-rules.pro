# Vendel Gateway ProGuard Rules

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Moshi
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers @com.squareup.moshi.JsonClass class * {
    <init>(...);
    <fields>;
}
-keep class **JsonAdapter { *; }
-keep class com.jimscope.vendel.data.remote.dto.** { *; }
-keep class com.jimscope.vendel.ui.setup.QrPayload { *; }

# Retrofit
-keepattributes Signature
-keepattributes Exceptions
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.** { *; }

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# WorkManager
-keep class * extends androidx.work.Worker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keepclassmembers class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ML Kit Barcode
-keep class com.google.mlkit.vision.** { *; }
-dontwarn com.google.mlkit.vision.**

# Room DAO
-keep @androidx.room.Dao interface * { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
