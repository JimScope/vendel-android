# Vendel Gateway ProGuard Rules

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Moshi - only app model classes and generated adapters
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class **JsonAdapter {
    <init>(...);
    <fields>;
    <methods>;
}
-keep class com.jimscope.vendel.data.remote.dto.** { *; }
-keep class com.jimscope.vendel.ui.setup.QrPayload { *; }

# Retrofit - keep generic signatures for converters
-keepattributes Signature
-keepattributes Exceptions

# Room - keep DB, DAO, and entity classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface *
-keep @androidx.room.Entity class * { *; }

# WorkManager - keep worker constructors
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler
