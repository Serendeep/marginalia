# pdfium binds to native code through JNI.
-keep class io.legere.pdfiumandroid.** { *; }
# Ink geometry, brushes and rendering are backed by native handles.
-keep class androidx.ink.** { *; }
# zstd-jni reads and writes Java fields from native code.
-keep class com.github.luben.zstd.** { *; }
