import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
// Optional local signing config (never committed): signing.properties
val signingProps = Properties().apply {
    val f = rootProject.file("signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// -PwithX86=true adds an x86_64 build of the same app so the Android emulator (which has no arm64
// CPU) can run it. Review/release APKs are arm64-v8a only.
val withX86 = project.hasProperty("withX86")
android {
    namespace = "app.slatehome.dizien.terminal"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.slatehome.dizien.terminal"
        minSdk = 24
        // Intentionally 28: the Debian userland runs programs from the app's private storage, which
        // Android blocks for apps that target 29 or newer (same approach as Termux's sideloaded build).
        targetSdk = 28
        versionCode = 4
        versionName = "0.1.3"
        ndk { abiFilters += if (withX86) listOf("arm64-v8a", "x86_64") else listOf("arm64-v8a") }
        buildConfigField("boolean", "X86_TEST", withX86.toString())
    }
    signingConfigs {
        create("review") {
            if (signingProps.containsKey("storeFile")) {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug { if (signingProps.containsKey("storeFile")) signingConfig = signingConfigs.getByName("review") }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signingProps.containsKey("storeFile")) signingConfig = signingConfigs.getByName("review")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
    }
    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    sourceSets["main"].java.srcDir("src/main/kotlin")
    if (withX86) sourceSets["main"].jniLibs.srcDir("src/x86test/jniLibs")
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // PRoot and its loaders are executables shipped as lib*.so; they must be extracted to disk.
        jniLibs { useLegacyPackaging = true }
    }
    lint { abortOnError = false; disable += "ExpiredTargetSdkVersion" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("com.github.termux.termux-app:terminal-emulator:v0.118.0")
    implementation("com.github.termux.termux-app:terminal-view:v0.118.0")
    testImplementation("junit:junit:4.13.2")
}
