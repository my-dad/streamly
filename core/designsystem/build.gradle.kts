plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.mabrur.streamly.core.designsystem"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        minSdk = 25
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // `api`, not `implementation`: ContentState and ErrorMessages expose AppError in
    // their public signatures, so consumers need :domain on their compile classpath.
    api(project(":domain"))

    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material3.window.size)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    constraints {
        // Coil 3.5.0 constrains kotlin-stdlib to 2.4.0, whose metadata AGP 9.3.1's
        // built-in Kotlin compiler (reads up to 2.3.0) rejects outright. Pin to the
        // version the rest of the project already resolves to. Drop this when the
        // toolchain's Kotlin catches up to Coil's floor.
        implementation(libs.kotlin.stdlib)
    }

    debugImplementation(libs.androidx.compose.ui.tooling)
}
