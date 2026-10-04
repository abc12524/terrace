# Shell Tool Android ProGuard Rules
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Gson
-keep class com.terrace.data.model.** { *; }
-keep class com.terrace.data.api.** { *; }
