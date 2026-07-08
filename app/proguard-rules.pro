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
