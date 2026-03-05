import org.gradle.api.Project
import java.io.File

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

fun File.readKeyValueConfig(): Map<String, String> {
    if (!isFile) return emptyMap()
    return buildMap {
        useLines { lines ->
            lines.map { it.trim() }.forEach { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEach
                val delimiter = line.indexOf("=")
                if (delimiter <= 0) return@forEach
                val key = line.substring(0, delimiter).trim()
                val value = line.substring(delimiter + 1).trim()
                if (key.isNotBlank()) put(key, value)
            }
        }
    }
}

fun String.asBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

fun expandConfigPath(raw: String): String {
    var value = raw.trim().trim('"', '\'')
    val percentPattern = Regex("%([A-Za-z0-9_]+)%")
    value = percentPattern.replace(value) { match ->
        System.getenv(match.groupValues[1]) ?: match.value
    }
    val bracePattern = Regex("\\$\\{([A-Za-z0-9_]+)\\}")
    value = bracePattern.replace(value) { match ->
        System.getenv(match.groupValues[1]) ?: match.value
    }
    if (value.startsWith("~")) {
        value = System.getProperty("user.home") + value.removePrefix("~")
    }
    return value
}

fun Project.resolvePathConfigFile(configFileName: String): File? {
    val configFile = rootProject.file(configFileName)
    if (!configFile.isFile) return null

    val rawPath = configFile.readLines()
        .asSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
        ?: return null

    val pathValue = if (rawPath.contains("=")) {
        val idx = rawPath.indexOf('=')
        val key = rawPath.substring(0, idx).trim()
        val value = rawPath.substring(idx + 1).trim()
        if (key !in setOf("SIGNING_CONFIG_FILE_PATH", "RELEASE_SIGNING_FILE_PATH", "APP_SIGNING_FILE_PATH")) {
            return null
        }
        value
    } else {
        rawPath
    }

    if (pathValue.isBlank()) return null
    val expanded = expandConfigPath(pathValue)
    val candidate = File(expanded)
    return if (candidate.isAbsolute) candidate else rootProject.file(expanded)
}

// 앱 기본 서버/버전 설정은 app/APP_CLIENT_CONFIG.txt 에서 읽습니다.
val appClientConfigFile = rootProject.projectDir.resolve("APP_CLIENT_CONFIG.txt")
val appClientConfig = appClientConfigFile.readKeyValueConfig()

val appDefaultServerBaseUrl = appClientConfig["SERVER_BASE_URL"]
    ?.trimEnd('/')
    ?.takeIf { it.isNotBlank() }
    ?: error("APP_CLIENT_CONFIG.txt: SERVER_BASE_URL is required (e.g. http://10.0.2.2:8088)")
val appVersionCode = appClientConfig["APP_VERSION_CODE"]
    ?.toIntOrNull()
    ?.takeIf { it > 0 }
    ?: error("APP_CLIENT_CONFIG.txt: APP_VERSION_CODE must be a positive integer")
val appVersionName = appClientConfig["APP_VERSION_NAME"]
    ?.takeIf { it.isNotBlank() }
    ?: error("APP_CLIENT_CONFIG.txt: APP_VERSION_NAME is required (e.g. 1.1.0)")
val defaultAiApiBaseUrl = appDefaultServerBaseUrl
val defaultHolidaySyncUrl = "$appDefaultServerBaseUrl/holidays"
val signingConfigFile = project.resolvePathConfigFile("APP_SIGNING_PATH.txt")
val signingFileConfig = signingConfigFile?.readKeyValueConfig().orEmpty()
val releaseStoreFile = signingFileConfig["storeFile"]?.trim().orEmpty()
val releaseStorePassword = signingFileConfig["storePassword"]?.trim().orEmpty()
val releaseKeyAlias = signingFileConfig["keyAlias"]?.trim().orEmpty()
val releaseKeyPassword = signingFileConfig["keyPassword"]?.trim().orEmpty()
val hasReleaseSigningConfig = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.echo.echocalendar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.echo.echocalendar"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("int", "AI_API_TIMEOUT_MS", project.stringProperty("AI_API_TIMEOUT_MS", "12000"))
        buildConfigField("int", "HOLIDAY_SYNC_TIMEOUT_MS", project.stringProperty("HOLIDAY_SYNC_TIMEOUT_MS", "5000"))
        buildConfigField(
            "boolean",
            "ALLOW_SIGNUP",
            project.booleanProperty("ALLOW_SIGNUP", false).toString()
        )
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigningConfig) {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
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
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
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
