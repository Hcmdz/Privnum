# Strip debug/verbose logging in release builds (may carry phone numbers).
-assumenosideeffects class android.util.Log {
    public static int d(...);
    public static int v(...);
}

# Tink (via androidx security-crypto) references error-prone annotations
# absent from the runtime classpath; safe to ignore for shrinking.
-dontwarn com.google.errorprone.annotations.**

# kotlinx-serialization: keep serializable route classes used by Navigation 3.
-keep @kotlinx.serialization.Serializable class * { *; }
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
