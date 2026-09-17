# ============================================================
# HomeHealth ProGuard / R8 规则
# 发布包开启了 minify + resource shrink，以下规则确保反射相关的
# 组件（Hilt / Room / Gson / OkHttp / WorkManager）不被误裁。
# ============================================================

# ---------- 通用属性保留 ----------
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ---------- 项目自身 ----------
# 网络 DTO：Gson 通过反射按字段名映射 JSON，字段名不可混淆
-keep class com.example.homehealth.data.remote.dto.** { *; }
-keep class com.example.homehealth.data.remote.AgentProtocol { *; }
-keep class com.example.homehealth.data.remote.AgentConfig { *; }
-keep class com.example.homehealth.data.remote.AgentRequestBody { *; }

# 枚举：Room TypeConverter 与 Gson 都按名称序列化
-keep class com.example.homehealth.data.local.entity.Enums { *; }
-keepclassmembers enum com.example.homehealth.data.local.entity.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Room 实体：保留构造器与字段（Room 生成的 DAO 实现依赖）
-keep class com.example.homehealth.data.local.entity.** { *; }

# Room 生成的实现类不能混淆，否则运行时报 AbstractMethodError
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ---------- Hilt / Dagger ----------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.AndroidEntryPoint <fields>;
}
-keep,allowobfuscation @interface dagger.hilt.android.AndroidEntryPoint
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }

# ---------- OkHttp / Okio ----------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------- Gson ----------
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.**

# ---------- Kotlin / 协程 ----------
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-keepclassmembers class **$WhenMappings { <fields>; }
-keep class kotlin.Metadata { *; }

# ---------- WorkManager ----------
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class * extends androidx.work.Worker { <init>(...); }
-keep class androidx.work.impl.WorkManagerInitializer { *; }

# ---------- Compose ----------
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }

# ---------- 应用入口 ----------
-keep class com.example.homehealth.HomeHealthApp { *; }
-keep class com.example.homehealth.MainActivity { *; }
-keep class com.example.homehealth.SplashActivity { *; }
