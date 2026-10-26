# Proguard rules for Panda Touch Root

# Keep Native JNI Methods & Classes
-keepclassmembers class * {
    native <methods>;
}

-keep class com.panda.touch.core.NativeTouchEngine { *; }

# Keep AIDL Generated Classes & Stubs
-keep interface com.panda.touch.ITouchInjectorService { *; }
-keep class com.panda.touch.ITouchInjectorService$* { *; }
-keep class com.panda.touch.service.TouchRootService { *; }

# Keep libsu library
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# Keep Data Models for GSON serialization
-keep class com.panda.touch.model.** { *; }
