import java.util.Properties
import java.util.UUID

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.pr4y.app" 
    compileSdk = 35

    val keystoreProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { keystoreProperties.load(it) }
    }

    fun getSignProperty(key: String): String? {
        return keystoreProperties.getProperty(key) ?: System.getenv(key)
    }

    defaultConfig {
        applicationId = "com.pr4y.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Manifest: usado por AndroidManifest.xml meta-data (MIUI / estabilización dev)
        manifestPlaceholders["miui_optimization"] = "false"
    }

    signingConfigs {
        create("release") {
            val sFile = getSignProperty("storeFile")
            val sPass = getSignProperty("storePassword")
            val kAlias = getSignProperty("keyAlias")
            val kPass = getSignProperty("keyPassword")

            if (!sFile.isNullOrEmpty() && !sPass.isNullOrEmpty() && !kAlias.isNullOrEmpty() && !kPass.isNullOrEmpty()) {
                storeFile = file(sFile.replace("\\", "/"))
                storePassword = sPass
                keyAlias = kAlias
                keyPassword = kPass
            }
        }
    }

    val googleWebClientId = getSignProperty("GOOGLE_WEB_CLIENT_ID") ?: ""
    val googleAndroidClientId = getSignProperty("GOOGLE_ANDROID_CLIENT_ID") ?: ""

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"https://pr4yapi-production.up.railway.app/v1/\"")
            buildConfigField("String", "TEST_USER_EMAIL", "\"test_user_${UUID.randomUUID()}@pr4y.cl\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
            buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", "\"$googleAndroidClientId\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"https://pr4yapi-production.up.railway.app/v1/\"")
            buildConfigField("String", "TEST_USER_EMAIL", "\"\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
            buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", "\"$googleAndroidClientId\"")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
        }
        create("prod") { dimension = "environment" }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// APK para compartir: después de ./gradlew assembleDevDebug, ejecuta ./gradlew copyPr4yApk
// El APK quedará en apps/mobile-android/dist/pr4y-0.0.1.apk
val distDir = file("${rootProject.projectDir}/dist")
tasks.register<Copy>("copyPr4yApk") {
    description = "Copia el APK a dist/pr4y-<version>.apk para compartir"
    group = "distribution"
    from(file("build/outputs/apk/dev/debug/app-dev-debug.apk"))
    into(distDir)
    rename { _ -> "pr4y-${android.defaultConfig.versionName}.apk" }
    onlyIf { file("build/outputs/apk/dev/debug/app-dev-debug.apk").exists() }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
