plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Note on Google's `datatransport` telemetry stack: ML Kit drags it in through
// `com.google.mlkit:common` and the play-services-mlkit-* shims. It used to be the reason the
// INTERNET permission had to be stripped from the merged manifest. Google Mobile Ads now needs
// that permission legitimately, so the permission is declared instead of removed - but the
// distinction still matters and is enforced by PermissionPolicyInstrumentedTest: the app has no
// network *code path* of its own, and nothing derived from a screenshot is ever put into an ad
// request. See core/ads/AdsConfig.kt for the single place the SDK is configured.

android {
    namespace = "com.sharesafe.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sharesafe.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        // Ship English and Indonesian only; every other locale falls back to English.
        localeFilters += listOf("en", "in")
    }

    // Bundled ML Kit models are native libraries, so a universal APK is bulky. APK splits keep the
    // per-device download reasonable and the universal APK stays available for sideloading. Play
    // splits App Bundles itself, and AGP refuses splits + bundle in one run
    // (issuetracker.google.com/402800800), so splits are enabled only for APK builds.
    val buildingAppBundle = gradle.startParameter.taskNames.any {
        it.contains("bundle", ignoreCase = true)
    }
    splits {
        abi {
            isEnable = !buildingAppBundle
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Signed with the debug key until a real upload key is configured, so the
            // release APK stays installable straight from this repository.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
            )
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        disable += setOf(
            // Versions are pinned deliberately for reproducible, cache-friendly builds.
            "GradleDependency",
            "OldTargetApi",
            // The adaptive icon lives in mipmap-anydpi-v26 on purpose: dropping the qualifier makes
            // AAPT2 stop resolving @mipmap/ic_launcher.
            "ObsoleteSdkInt",
        )
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // On-device ML Kit: bundled models, no Play Services download.
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.face.detection)

    // Google Mobile Ads. Configured with Google's official test app id / test ad units (see
    // core/ads/AdsConfig.kt), and only ever asked for an ad - never given any image data.
    implementation(libs.play.services.ads)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
