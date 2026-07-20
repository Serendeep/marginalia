# Marginalia — release ProGuard rules (spike stage; minify disabled).
# pdfium native bindings are loaded via JNI; keep them if minify is enabled later.
-keep class io.legere.pdfiumandroid.** { *; }
