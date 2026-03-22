# ObjectBox
-keep class io.objectbox.** { *; }
-keep interface io.objectbox.** { *; }
-keep enum io.objectbox.** { *; }
-keep class com.ml.shubham0204.clipandroid.data.** { *; }

# Keep the generated MyObjectBox class
-keep class com.ml.shubham0204.clipandroid.data.MyObjectBox { *; }

# CLIP Android Native
-keep class android.clip.cpp.** { *; }
-keepclassmembers class android.clip.cpp.** {
    native <methods>;
}

# General native code
-keepclasseswithmembernames class * {
    native <methods>;
}