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
        versionCode = (getSignProperty("VERSION_CODE")?.toIntOrNull() ?: 1)
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"https://pr4yapi-production.up.railway.app/v1/\"")
            buildConfigField("String", "TEST_USER_EMAIL", "\"test_user_${UUID.randomUUID()}@pr4y.cl\"")
            buildConfigField("Boolean", "DEBUG_RELEASE_GUARD", "false")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_BASE_URL", "\"https://pr4yapi-production.up.railway.app/v1/\"")
            buildConfigField("String", "TEST_USER_EMAIL", "\"\"")
            buildConfigField("Boolean", "DEBUG_RELEASE_GUARD", "false")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationId = "com.pr4y.app.dev"
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${getSignProperty("GOOGLE_WEB_CLIENT_ID_DEV") ?: getSignProperty("GOOGLE_WEB_CLIENT_ID") ?: ""}\"")
            buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", "\"${getSignProperty("GOOGLE_ANDROID_CLIENT_ID_DEV") ?: getSignProperty("GOOGLE_ANDROID_CLIENT_ID") ?: ""}\"")
        }
        create("prod") {
            dimension = "environment"
            applicationId = "com.pr4y.app"
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${getSignProperty("GOOGLE_WEB_CLIENT_ID") ?: ""}\"")
            buildConfigField("String", "GOOGLE_ANDROID_CLIENT_ID", "\"${getSignProperty("GOOGLE_ANDROID_CLIENT_ID") ?: ""}\"")
        }
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

// Bloqueo de hotfixes: impide APK release si versionCode no se ha incrementado respecto al último publicado
val releaseVersionCodeFile = rootProject.file(".last-release-versioncode")

tasks.register("validateRelease") {
    group = "verification"
    description = "Falla si se intenta release con versionCode no incrementado"
    doLast {
        if (project.findProperty("skipReleaseVersionCheck") == "true") return@doLast

        val vc = android.defaultConfig.versionCode
        val last = project.findProperty("lastReleasedVersionCode")?.toString()?.toIntOrNull()
            ?: (if (releaseVersionCodeFile.exists()) releaseVersionCodeFile.readText().trim().toIntOrNull() else null)
            ?: 0

        if (vc <= last) {
            throw GradleException(
                "Release bloqueado: versionCode ($vc) debe ser mayor que el último publicado ($last). " +
                    "Incrementa versionCode en app/build.gradle.kts. Tras publicar, actualiza .last-release-versioncode con $vc. " +
                    "Para saltar solo en local: -PskipReleaseVersionCheck=true"
            )
        }
    }
}

afterEvaluate {
    tasks.matching { it.name.startsWith("assemble") && it.name.contains("Release") }.configureEach {
        dependsOn("validateRelease")
    }
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
