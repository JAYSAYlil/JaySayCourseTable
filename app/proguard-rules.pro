# === Kotlin ===
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
-keepclassmembers class kotlin.Metadata { public <methods>; }

# === Apache POI ===
# POI/XMLBeans 大量使用反射（Class.forName 加载 schema 类），
# 必须保持原名，否则 R8 混淆后 Excel 导入会因找不到类闪退。
# 课表只读取 XLS/XLSX：排除 PowerPoint(sl/xslf/hslf)、Word(hwpf/xwpf)、
# Visio/邮件(hdgf/hmef)与文档数字签名(dsig)模块，由 R8 安全裁剪以缩小体积。
-keep class !org.apache.poi.poifs.crypt.dsig.**,!org.apache.poi.sl.**,!org.apache.poi.hslf.**,!org.apache.poi.xslf.**,!org.apache.poi.hwpf.**,!org.apache.poi.xwpf.**,!org.apache.poi.hdgf.**,!org.apache.poi.hmef.**,org.apache.poi.** { *; }
-keep class org.apache.poi.schemas.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class com.microsoft.schemas.** { *; }
# log4j2 通过 ServiceLoader/反射初始化，R8 改名会破坏 META-INF/services，
# 导致 POI 静态初始化 Logger 时抛 ExceptionInInitializerError（导入闪退）
-keep class org.apache.logging.log4j.** { *; }
# commons-compress 的 ExtraFieldUtils 用反射按类名实例化 zip 扩展字段，
# R8 改名/删构造函数会导致 NoSuchMethodException（导入闪退）
-keep class org.apache.commons.compress.** { *; }
# commons-compress 的可选压缩后端未打包，消除缺失类报错
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn org.tukaani.xz.**
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn com.microsoft.schemas.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.osgi.**
-dontwarn aQute.bnd.**
-dontwarn java.awt.**
-dontwarn com.graphbuilder.**

# === Coroutines ===
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}

# === Release 脱敏 ===
# 不保留源码文件名和行号，异常栈只显示统一名称；mapping 文件留在本地用于排障。
-renamesourcefileattribute SourceFile
