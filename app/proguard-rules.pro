# Keep readable stack traces in release builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Pay by Square encoder builds the payment payload; keep it intact.
-keep class io.github.janhalasa.paybysquare.** { *; }
-dontwarn io.github.janhalasa.paybysquare.**

# ZXing core (QR + Code128 rendering on the invoice PDF).
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# XZ compression used by Pay by Square.
-keep class org.tukaani.xz.** { *; }
-dontwarn org.tukaani.xz.**
