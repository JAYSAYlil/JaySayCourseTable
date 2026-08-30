# === Kotlin ===
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
-keepclassmembers class kotlin.Metadata { public <methods>; }

# === Coroutines ===
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# === Release 脱敏 ===
# 不保留源码文件名和行号，异常栈只显示统一名称；mapping 文件留在本地用于排障。
-renamesourcefileattribute SourceFile
