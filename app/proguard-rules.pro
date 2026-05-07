# Keep Retrofit/Gson model metadata used by GitHub API responses.
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.abk.kernel.data.model.** { *; }

# libsu uses reflection internally.
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**
