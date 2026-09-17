import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Room schema 导出目录：供迁移测试与增量迁移生成使用
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Release 签名配置从 keystore.properties 读取（该文件不入库）。
// 文件缺失时不报错，仅跳过签名配置 —— 这样没有密钥的协作者仍能构建 debug。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties()
val hasReleaseSigning = keystorePropsFile.exists()
if (hasReleaseSigning) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}

android {
    namespace = "com.example.homehealth"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.adajunk.homehealth"
        minSdk = 26
        // targetSdk 36（Android 16）：Google Play 自 2026-08-31 起要求新应用与更新
        // 必须 target API 36+；国内商店的下限要求也在持续跟进。
        // 与 build-tools 36.0.0 对齐。升级前请按下方行为变更逐项回归。
        targetSdk = 36
        versionCode = 2
        versionName = "1.2.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // 开启 V1+V2+V3 签名：国内各商店与低版本 Android 的兼容性最好
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // 发布包必须开启混淆与资源压缩，否则体积与逆向风险都不可接受
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        // 把导出的 Room schema 作为 androidTest 的 assets，MigrationTestHelper 需要它来校验迁移结果
        // 用 directories 而非已废弃的 srcDir。
        //
        // ⚠️ 陷阱：mergeDebugAndroidTestAssets 并不依赖 ksp 的 schema 生成任务，
        // 所以**刚升 DB 版本后的第一次构建，新版本的 <N>.json 可能来不及被打进 assets**，
        // 此时迁移测试会在运行时因找不到 schema 而失败。再构建一次即可。
        // （schemas/ 已入库，因此这个现象只影响本地"改完版本号后的首次构建"。）
        getByName("androidTest").assets.directories.add("schemas")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    // AAB 打包：国内部分商店（如 Google Play）要求，这里统一配置
    bundle {
        language {
            enableSplit = false
        }
    }
}

dependencies {

    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2024.02.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Core / Activity / Lifecycle
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.4.0")
    implementation("androidx.hilt:hilt-work:1.4.0")
    ksp("androidx.hilt:hilt-compiler:1.4.0")

    // OkHttp / Gson
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Coil（图片加载）
    implementation("io.coil-kt:coil-compose:2.5.0")

    // 单元测试
    testImplementation("junit:junit:4.13.2")
    // JVM 单测解析 Room schema 快照用（android.jar 里的 org.json 在本地单测中是 stub）
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
