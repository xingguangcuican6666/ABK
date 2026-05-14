# Keep Retrofit/Gson model metadata used by GitHub API responses.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.abk.kernel.data.model.** { *; }

# libsu uses reflection internally.
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# Native KernelSU bridge resolves this class and profile fields by JNI name.
-keep class com.abk.kernel.utils.AbkKsuNative { *; }
-keep class com.abk.kernel.utils.AbkKsuNative$Profile { *; }

# Module WebUI JavaScript bridge methods are called by name from WebView.
-keepclassmembers class com.abk.kernel.ui.webui.ModuleWebUiActivity$ModuleWebBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# WorkManager initializes its Room database through reflection during app startup.
# Keep the generated database and DAO implementations intact in minified release builds.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.work.impl.model.** { *; }
