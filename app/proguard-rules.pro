# ShareSafe R8 rules.
# ML Kit loads its bundled models and vision internals reflectively; keep them intact.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

-keep class com.google.android.gms.internal.mlkit_vision_** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode_** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_face_** { *; }
-dontwarn com.google.android.gms.internal.mlkit_**

# Play Services Task API used for the ML Kit callbacks.
-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.android.gms.dynamite.** { *; }
-dontwarn com.google.android.gms.dynamite.**

# Native entry points of the bundled ML Kit models.
-keepclasseswithmembernames class * {
    native <methods>;
}
