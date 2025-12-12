# Keep the annotation definition itself
-keep @interface com.dg.fendai.implement.Prompt
-keepattributes *Annotation*

# Keep ANY class annotated with @Prompt (and all its methods)
-keep @com.dg.fendai.implement.Prompt class * { *; }

# (Optional) If @Prompt is ever allowed on methods in future
-keepclassmembers class * {
    @com.dg.fendai.implement.Prompt <methods>;
}

# Core library must always be kept (reflection, router, dynamic lookup)
-keep class com.dg.fendai.implementgguf.define.** { *; }
-keep class com.dg.fendai.implementgguf.manager.** { *; }
-keep class com.dg.fendai.implementgguf.** { *; }
-keep class com.dg.fendai.core.** { *; }
-keep class com.dg.fendai.runner.** { *; }
-keep class com.dg.fendai.implement.MainFendAI { *; }


# Keep interface for registration
-keep interface com.dg.fendai.implement.RegisterFendAI
-keep class com.dg.fendai.implement.RegisterFendAI { *; }

# Fix for missing Google AutoValue classes (MediaPipe)
-dontwarn com.google.auto.value.**
-dontwarn com.google.mediapipe.**