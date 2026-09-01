plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.jaysay.coursetable"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jaysay.coursetable"
        minSdk = 26
        targetSdk = 35
        versionCode = 126
        versionName = "3.4.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    androidResources {
        // 仅保留中文语言资源（无英文资源，避免误导性裁剪配置）。
        localeFilters += listOf("zh")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt"
        )
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    // 源码编码在编译任务中声明，避免修改整个 Gradle JVM 的默认文件系统编码。
    options.encoding = "UTF-8"
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // `.xlsx` 使用轻量 MinimalXlsxReader；Apache POI 仅保留用于旧版 `.xls`（OLE2/BIFF8）。
    implementation(libs.apache.poi)
    testImplementation(libs.apache.poi.ooxml)
    androidTestImplementation(libs.apache.poi.ooxml)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
