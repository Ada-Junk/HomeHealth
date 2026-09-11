// 根构建脚本：声明插件版本（AGP 9.4 起内置 Kotlin 支持，无需 kotlin-android 插件）
plugins {
    id("com.android.application") version "9.4.0" apply false
    id("com.google.devtools.ksp") version "2.3.6" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
