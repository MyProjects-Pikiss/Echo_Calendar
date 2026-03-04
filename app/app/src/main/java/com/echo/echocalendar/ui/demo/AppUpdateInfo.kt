package com.echo.echocalendar.ui.demo

data class AppUpdateInfo(
    val hasUpdate: Boolean,
    val required: Boolean,
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minSupportedVersionCode: Int,
    val apkDownloadUrl: String?
)
