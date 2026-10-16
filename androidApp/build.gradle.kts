import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val phoneOptimizedApk =
    providers.gradleProperty("yumerin.phoneOptimizedApk").orNull == "true"
val releaseStoreFile = providers.environmentVariable("YUMERIN_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("YUMERIN_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("YUMERIN_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("YUMERIN_RELEASE_KEY_PASSWORD").orNull
val releaseSigningConfigured =
    listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
        .all { !it.isNullOrBlank() } && releaseStoreFile?.let { file(it).isFile } == true

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.lhy.yumerin"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.lhy.yumerin"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 4
        versionName = "1.3.0"
        if (phoneOptimizedApk) {
            ndk {
                abiFilters.add("arm64-v8a")
            }
        }
    }
    if (releaseSigningConfigured) {
        signingConfigs {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        debug {
            buildConfigField(
                "boolean",
                "SKIP_ADULT_CONFIRMATION",
                (!phoneOptimizedApk).toString()
            )
            if (phoneOptimizedApk) {
                // Produce a release-like, arm64-only sideload APK while keeping normal Debug intact.
                isDebuggable = false
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "SKIP_ADULT_CONFIRMATION", "false")
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}
