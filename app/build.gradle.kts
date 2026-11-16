import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Release signing is read from a gitignored keystore.properties so a fresh clone
// still builds: without it the release APK is simply left unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasSigningConfig = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "me.jhot.swiftcurrent"
    compileSdk = 35

    lint {
        // Translations are contributed, so they arrive behind the strings they translate, and
        // Android falls back to English for anything a locale is missing. A volunteer being a
        // release behind is not a defect and must not be what stops a build.
        warning += "MissingTranslation"
        // QUERY_ALL_PACKAGES is requested on purpose: an archived app has no launcher activity, so the
        // LAUNCHER query cannot see it, and without it archived apps vanish instead of being marked.
        disable += "QueryAllPackagesPermission"
    }

    defaultConfig {
        applicationId = "me.jhot.swiftcurrent"
        minSdk = 34
        targetSdk = 35
        // Published builds take both from the release tag (scripts/release-version.sh) through
        // -PVERSION_CODE and -PVERSION_NAME. A local build keeps these defaults.
        versionCode = (findProperty("VERSION_CODE") as String?)?.toInt() ?: 1
        versionName = (findProperty("VERSION_NAME") as String?) ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 does the heavy lifting on size and startup: the screens draw their own paths, but
            // the Compose UI library alone is large enough that shrinking is still worthwhile.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasSigningConfig) signingConfigs.getByName("release") else null
        }
        // What :benchmark measures: release code (R8 on) that installs with the debug key and is
        // profileable through the benchmark-only manifest overlay. Release itself stays as is.
        create("benchmark") {
            initWith(getByName("release"))
            isDebuggable = false
            matchingFallbacks += "release"
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Shared by unit tests and device tests (the emulator guard), and never shipped in the app.
    sourceSets {
        getByName("test").java.srcDir("src/testShared/java")
        getByName("androidTest").java.srcDir("src/testShared/java")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Strips the Google dependency-metadata blob from the APK, which otherwise
    // changes from build to build and makes releases non-reproducible.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    sourceSets {
        // MigrationTestHelper reads the exported schemas as assets. Robolectric only sees the debug
        // variant's merged assets, so they ride along in debug builds (a few KB); release is untouched.
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged manifest and resources to resolve intents.
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

// Compose ui-test needs its manifest activity, which only the debug variant carries; the release
// and benchmark unit tests would just repeat the debug ones.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
    beforeVariants(selector().withBuildType("benchmark")) { it.enableUnitTest = false }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
