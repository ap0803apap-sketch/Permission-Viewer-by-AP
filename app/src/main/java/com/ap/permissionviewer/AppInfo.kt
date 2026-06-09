// File: AppInfo.kt
package com.ap.permissionviewer

import android.graphics.drawable.Drawable

data class AppInfo(
    val name: String,
    val packageName: String,
    val icon: Drawable,
    var isExpanded: Boolean = false,
    var permissions: List<String> = emptyList(),
    var services: List<String> = emptyList(),
    var activities: List<String> = emptyList(),
    var receivers: List<String> = emptyList(),
    var providers: List<String> = emptyList(),
    // Added fields as var to allow updates
    var versionName: String? = null,
    var versionCode: Long? = null,
    var minSdk: Int? = null,
    var targetSdk: Int? = null,
    var firstInstallTime: Long? = null,
    var lastUpdateTime: Long? = null,
    var sourceDir: String? = null,
    var dataDir: String? = null,
    var uid: Int? = null,
    var features: List<String> = emptyList(),
    var signatures: List<String> = emptyList()
)
