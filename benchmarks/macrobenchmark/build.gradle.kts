plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
}

// Enabled by -PenableBenchmarks=true; uses an isolated benchmark application.
android {
    namespace = "com.jaysay.coursetable.benchmark.test"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("benchmark")
        }
    }

    targetProjectPath = ":app"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.4")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

androidComponents {
    beforeVariants(selector().all()) {
        it.enabled = it.buildType == "benchmark"
    }
}

// This configuration installs the target APK, not its library dependencies.
// Avoid resolving irrelevant desktop KMP artifacts through the app dependency graph.
configurations.matching { it.name.endsWith("TestedApks") }.configureEach {
    isTransitive = false
}
