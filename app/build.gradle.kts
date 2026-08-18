import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Release signing, read from `keystore.properties` at the repository root.
 *
 * Absent on a fresh clone, and that is deliberate rather than an oversight: the keystore is
 * the app's identity, so it is gitignored along with the passwords that open it. A clone
 * without them still builds and still runs — `assembleDebug` is unaffected, and
 * `assembleRelease` falls back to producing an unsigned APK, exactly as it did before this
 * block existed. Only the person holding the keystore can produce the artifact that installs
 * over an existing Swipey.
 */
val signingProps = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}

android {
    namespace = "com.swipey.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.swipey.app"
        minSdk = 33
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (signingProps != null) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Only when the keystore is actually here; see [signingProps]. Left unsigned
            // otherwise, which is what a release build did before and is honest about what
            // it is — an APK nobody can install rather than one pretending to be signed.
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // R8 stays off. The app is 26MB of ExoPlayer, Coil and Compose, and shrinking
            // it would be worth doing — but it is a change to what runs, not to how it is
            // packaged, and the release build is what people download. That deserves its
            // own pass with its own testing rather than riding along with a README.
            isMinifyEnabled = false
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    // The whole app is built on foundation + animation. Material3 used to arrive here and
    // carry these transitively; the last three widgets that needed it (SwipeyApp.kt's
    // Button, CircularProgressIndicator and Text) are now Swipey primitives, so the
    // dependency is gone — which makes "no Material in ui/" a fact about the classpath
    // rather than a convention someone has to remember.
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    // Renders the @Preview catalogue in ui/design/Previews.kt. Debug only — it is a
    // developer tool and has no business in a release build.
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    testImplementation(libs.junit)
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}

// Two unit tests read project sources off disk rather than exercising compiled classes:
// DomainPurityTest scans src/main/java for stray Android imports, and MarkGeometryTest
// parses the icon vectors in src/main/res against Mark.kt's geometry. Gradle has no way to
// know that, so it treats the test task as up-to-date when only a resource changes — which
// means an icon edited into a shape the mask would clip would silently skip its own guard.
// Declaring the directories as inputs makes the staleness check honest.
tasks.withType<Test>().configureEach {
    inputs.dir("src/main/res").withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir("src/main/java").withPathSensitivity(PathSensitivity.RELATIVE)
}
