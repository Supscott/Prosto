# Room Database keep rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# WebView & JavaScript interface rules
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Domain & Entity models serialization
-keep class com.jestk.browser.domain.model.** { *; }
-keep class com.jestk.browser.data.local.entity.** { *; }

# Coil image loader
-keep class coil.** { *; }
-dontwarn coil.**
