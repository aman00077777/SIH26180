# Add project-specific ProGuard rules here.
# TFLite GPU/NNAPI delegate classes must not be stripped.
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
