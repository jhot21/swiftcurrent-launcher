import org.jetbrains.kotlin.gradle.dsl.JvmTarget
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "me.jhot.swiftcurrent.benchmark"
    compileSdk = 35

    defaultConfig {
        minSdk = 34
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"

    buildTypes {
        // Matches the app's benchmark build type; the debug key keeps it installable.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    // The test APK instruments the target through shell access instead of a separate process.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

androidComponents {
    // Only the benchmark variant is meaningful; debug would measure unoptimized code.
    beforeVariants(selector().all()) { it.enable = it.buildType == "benchmark" }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
