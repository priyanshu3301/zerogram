# TDLib specific rules
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }

# Preserve data classes for serialization if any
-keep class com.zerogram.data.local.entity.** { *; }

# Ensure Hilt / Dagger generated code is preserved (usually automatic, but good to be safe)
-keep class dagger.** { *; }
-keep class hilt_aggregated_deps.** { *; }
-keep class dagger.hilt.** { *; }

# Compose rules (usually handled by Compose plugin, but safeguard for ViewModels)
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
