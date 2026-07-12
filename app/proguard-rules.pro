# CampbellXGM ProGuard Rules

# Keep Kotlin metadata
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Keep Compose
-keep class androidx.compose.** { *; }

# Keep AccessibilityService
-keep class com.campbell.xgm.domain.services.SafetyInterceptor { *; }
-keep class com.campbell.xgm.domain.services.CampbellAdminReceiver { *; }
-keep class com.campbell.xgm.domain.services.PingStabilizerVpnService { *; }

# Keep data classes
-keep class com.campbell.xgm.data.local.** { *; }
-keep class com.campbell.xgm.ui.screens.AppInfo { *; }

# Keep services and receivers (referenced in AndroidManifest.xml)
-keep class com.campbell.xgm.domain.services.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# Kotlinx serialization (if used in future)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep enum entries
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Prevent R8 from stripping interface information needed by Compose
-keep class * extends java.lang.Exception
