# Preserve stack trace line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep data classes used by Room and Firestore (fields must not be renamed)
-keep class com.skd.notebook.data.local.NoteEntity { *; }

# Room – keep generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface *

# Firebase Firestore – keep model classes for deserialization
-keepclassmembers class com.skd.notebook.** {
    public <init>();
    <fields>;
}

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Kotlin serialization / reflection metadata
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes Signature