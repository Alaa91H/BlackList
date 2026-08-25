plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.blacklist.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.blacklist.app"
        minSdk = 26
        targetSdk = 36
        // Version is linked to git tag via -PversionName / -PversionCode (see release.yml)
        // Falls back to 1.0.0 / 1 for local builds
        versionCode = (findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            // CI releases must use the permanent key restored from GitHub Actions secrets.
            // A debug key is permitted only for local developer builds and can never sign a CI release.
            fun signingValue(name: String): String? =
                System.getenv(name)?.takeIf { it.isNotBlank() }
                    ?: (findProperty(name) as String?)?.takeIf { it.isNotBlank() }

            val isCi = System.getenv("CI") == "true" || System.getenv("GITHUB_ACTIONS") == "true"
            val keyStorePath = signingValue("BLACKLIST_KEYSTORE_PATH")
            val secretKeyStore = keyStorePath?.let { file(it) }?.takeIf { it.exists() }
            val localKeyStore = file(rootProject.file("release.keystore")).takeIf { it.exists() }
            val selectedKeyStore = secretKeyStore ?: localKeyStore

            if (selectedKeyStore != null) {
                storeFile = selectedKeyStore
                storePassword = signingValue("BLACKLIST_KEYSTORE_PASSWORD")
                    ?: error("Missing BLACKLIST_KEYSTORE_PASSWORD for release signing")
                keyAlias = signingValue("BLACKLIST_KEY_ALIAS")
                    ?: error("Missing BLACKLIST_KEY_ALIAS for release signing")
                keyPassword = signingValue("BLACKLIST_KEY_PASSWORD")
                    ?: error("Missing BLACKLIST_KEY_PASSWORD for release signing")
            } else {
                check(!isCi) {
                    "CI release signing requires BLACKLIST_KEYSTORE_PATH and the GitHub Actions signing secrets."
                }
                // Local-only fallback. This branch is prohibited in GitHub Actions by the check above.
                val debugKs = file(System.getProperty("user.home") + "/.android/debug.keystore")
                storeFile = debugKs
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            // Debug uses default debug keystore automatically
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    androidResources { generateLocaleConfig = false }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splash)
    implementation(libs.androidx.datastore.preferences)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.libphonenumber)

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("androidx.room:room-testing:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling:1.9.3")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.3")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
