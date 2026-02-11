import org.gradle.api.Project

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

fun Project.stringProperty(name: String, defaultValue: String = ""): String {
    val value = findProperty(name)?.toString()?.trim()
    return if (value.isNullOrEmpty()) defaultValue else value
}

fun Project.booleanProperty(name: String, defaultValue: Boolean): Boolean {
    val rawValue = findProperty(name)?.toString()?.trim()?.lowercase()
    return when (rawValue) {
        "true", "1", "yes", "y", "on" -> true
        "false", "0", "no", "n", "off" -> false
        null, "" -> defaultValue
        else -> defaultValue
    }
}

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.echo.echocalendar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.echo.echocalendar"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("int", "AI_API_TIMEOUT_MS", project.stringProperty("AI_API_TIMEOUT_MS", "12000"))
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "AI_API_BASE_URL",
                project.stringProperty("AI_API_BASE_URL_DEBUG", project.stringProperty("AI_API_BASE_URL")).asBuildConfigString()
            )
            buildConfigField(
                "String",
                "AI_API_KEY",
                project.stringProperty("AI_API_KEY_DEBUG", project.stringProperty("AI_API_KEY")).asBuildConfigString()
            )
            buildConfigField(
                "boolean",
                "AI_SEND_CLIENT_API_KEY",
                project.booleanProperty("AI_SEND_CLIENT_API_KEY_DEBUG", true).toString()
            )
            buildConfigField(
                "boolean",
                "AI_REQUIRE_HTTPS",
                project.booleanProperty("AI_REQUIRE_HTTPS_DEBUG", false).toString()
            )
        }
        release {
            isMinifyEnabled = false
            buildConfigField(
                "String",
                "AI_API_BASE_URL",
                project.stringProperty("AI_API_BASE_URL_RELEASE", project.stringProperty("AI_API_BASE_URL")).asBuildConfigString()
            )
            buildConfigField(
                "String",
                "AI_API_KEY",
                project.stringProperty("AI_API_KEY_RELEASE", project.stringProperty("AI_API_KEY")).asBuildConfigString()
            )
            buildConfigField(
                "boolean",
                "AI_SEND_CLIENT_API_KEY",
                project.booleanProperty("AI_SEND_CLIENT_API_KEY_RELEASE", false).toString()
            )
            buildConfigField(
                "boolean",
                "AI_REQUIRE_HTTPS",
                project.booleanProperty("AI_REQUIRE_HTTPS_RELEASE", true).toString()
            )
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
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
