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

fun Project.expandServerPath(raw: String): String {
    val userProfile = System.getenv("USERPROFILE").orEmpty()
    val homeDrive = System.getenv("HOMEDRIVE").orEmpty()
    val homePath = System.getenv("HOMEPATH").orEmpty()
    var value = raw.trim().removeSurrounding("\"").removeSurrounding("'")
    if (userProfile.isNotBlank()) value = value.replace("%USERPROFILE%", userProfile)
    if (homeDrive.isNotBlank()) value = value.replace("%HOMEDRIVE%", homeDrive)
    if (homePath.isNotBlank()) value = value.replace("%HOMEPATH%", homePath)
    return value
}

fun Project.toBuildHostPath(path: String): String {
    val isWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)
    if (isWindows) return path
    val windowsPath = Regex("""^([A-Za-z]):\\(.*)$""").find(path) ?: return path
    val drive = windowsPath.groupValues[1].lowercase()
    val rest = windowsPath.groupValues[2].replace("\\", "/")
    return "/mnt/$drive/$rest"
}

fun File.firstConfigValue(validKeys: Set<String>): String? {
    if (!isFile) return null
    return useLines { lines ->
        lines
            .map { it.trim() }
            .firstNotNullOfOrNull { line ->
                if (line.isBlank() || line.startsWith("#")) return@firstNotNullOfOrNull null
                if ("=" in line) {
                    val key = line.substringBefore("=").trim()
                    if (key in validKeys) line.substringAfter("=").trim() else null
                } else {
                    line
                }
            }
    }
}

fun Project.readServerHostingBaseUrl(): String? {
    val serverDir = rootProject.projectDir.parentFile.resolve("server")
    val pathConfig = serverDir.resolve("SERVER_ENV_PATH.txt")
    val envPathRaw = pathConfig.firstConfigValue(
        setOf("OPENAI_API_KEY_FILE_PATH", "API_KEY_FILE_PATH", "OPENAI_ENV_FILE")
    ) ?: return null
    val envPath = toBuildHostPath(expandServerPath(envPathRaw))
    val envFile = file(envPath)
    val hostingBaseUrl = envFile.firstConfigValue(setOf("HOSTING_BASE_URL")) ?: return null
    return hostingBaseUrl.trimEnd('/').takeIf { it.isNotBlank() }
}

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val serverHostingBaseUrl = project.readServerHostingBaseUrl()
val defaultAiApiBaseUrl = serverHostingBaseUrl ?: "http://10.0.2.2:8088"
val defaultHolidaySyncUrl = serverHostingBaseUrl?.let { "$it/holidays" } ?: "http://10.0.2.2:8088/holidays"

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
        buildConfigField("int", "HOLIDAY_SYNC_TIMEOUT_MS", project.stringProperty("HOLIDAY_SYNC_TIMEOUT_MS", "5000"))
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            buildConfigField(
                "String",
                "AI_API_BASE_URL",
                project.stringProperty(
                    "AI_API_BASE_URL_DEBUG",
                    project.stringProperty("AI_API_BASE_URL", defaultAiApiBaseUrl)
                ).asBuildConfigString()
            )
            buildConfigField(
                "String",
                "AI_API_KEY",
                project.stringProperty("AI_API_KEY_DEBUG", project.stringProperty("AI_API_KEY")).asBuildConfigString()
            )
            buildConfigField(
                "boolean",
                "AI_SEND_CLIENT_API_KEY",
                project.booleanProperty("AI_SEND_CLIENT_API_KEY_DEBUG", false).toString()
            )
            buildConfigField(
                "boolean",
                "AI_REQUIRE_HTTPS",
                project.booleanProperty("AI_REQUIRE_HTTPS_DEBUG", false).toString()
            )
            buildConfigField(
                "String",
                "HOLIDAY_SYNC_URL",
                project.stringProperty(
                    "HOLIDAY_SYNC_URL_DEBUG",
                    project.stringProperty("HOLIDAY_SYNC_URL", defaultHolidaySyncUrl)
                ).asBuildConfigString()
            )
        }
        release {
            isMinifyEnabled = false
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            buildConfigField(
                "String",
                "AI_API_BASE_URL",
                project.stringProperty(
                    "AI_API_BASE_URL_RELEASE",
                    project.stringProperty("AI_API_BASE_URL", defaultAiApiBaseUrl)
                ).asBuildConfigString()
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
            buildConfigField(
                "String",
                "HOLIDAY_SYNC_URL",
                project.stringProperty(
                    "HOLIDAY_SYNC_URL_RELEASE",
                    project.stringProperty("HOLIDAY_SYNC_URL", defaultHolidaySyncUrl)
                ).asBuildConfigString()
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
