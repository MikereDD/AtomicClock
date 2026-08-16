plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val releaseKeystore = providers.environmentVariable("TYPEZERO_ANDROID_KEYSTORE").orNull
val releaseStorePassword = providers.environmentVariable("TYPEZERO_ANDROID_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("TYPEZERO_ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("TYPEZERO_ANDROID_KEY_PASSWORD").orNull
val hasReleaseSigning = listOf(
    releaseKeystore,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.typezero.atomicclock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.typezero.atomicclock"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.6.1-dev.1"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("typezeroRelease") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
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
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("typezeroRelease")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

// Release artifacts must never be produced unsigned. Debug builds remain unaffected.
gradle.taskGraph.whenReady {
    val releaseRequested = allTasks.any {
        it.path.endsWith(":assembleRelease") ||
            it.path.endsWith(":bundleRelease") ||
            it.path.endsWith(":packageRelease")
    }
    if (releaseRequested && !hasReleaseSigning) {
        throw GradleException(
            "Release signing is not configured. Set TYPEZERO_ANDROID_KEYSTORE, " +
                "TYPEZERO_ANDROID_STORE_PASSWORD, TYPEZERO_ANDROID_KEY_ALIAS, and " +
                "TYPEZERO_ANDROID_KEY_PASSWORD before building a release."
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    debugImplementation(libs.androidx.ui.tooling)
}
