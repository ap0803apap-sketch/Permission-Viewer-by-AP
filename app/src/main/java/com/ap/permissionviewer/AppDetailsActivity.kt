package com.ap.permissionviewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class AppDetailsActivity : AppCompatActivity() {

    private lateinit var sectionsContainer: LinearLayout
    private var packageName: String? = null
    private lateinit var appInfo: AppInfo

    private var lastTheme: Int = -1
    private var lastAmoled: Boolean = false
    private var lastDynamic: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        lastTheme = prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        lastAmoled = prefs.getBoolean("amoled", false)
        lastDynamic = prefs.getBoolean("dynamic_colors", true)

        applyThemeSettings()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_details)

        packageName = intent.getStringExtra("PACKAGE_NAME")
        if (packageName == null) {
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        sectionsContainer = findViewById(R.id.sectionsContainer)
        
        loadAppInfo()
    }

    override fun onRestart() {
        super.onRestart()
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val theme = prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val amoled = prefs.getBoolean("amoled", false)
        val dynamic = prefs.getBoolean("dynamic_colors", true)

        if (theme != lastTheme || amoled != lastAmoled || dynamic != lastDynamic) {
            recreate()
        }
    }

    private fun applyThemeSettings() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        val themeMode = prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)

        if (prefs.getBoolean("dynamic_colors", true)) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        // Apply AMOLED if enabled and in dark mode
        val isNightMode = when (themeMode) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> {
                val currentMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                currentMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
        }
        if (isNightMode && prefs.getBoolean("amoled", false)) {
            theme.applyStyle(R.style.ThemeOverlay_PermissionViewerByAP_Amoled, true)
        }
    }

    private fun loadAppInfo() {
        try {
            val pm = packageManager
            val pInfo = pm.getPackageInfo(packageName!!, 0)
            val applicationInfo = pInfo.applicationInfo ?: throw Exception("App info not found")
            val appLabel = pm.getApplicationLabel(applicationInfo).toString()
            val appIcon = pm.getApplicationIcon(applicationInfo)
            
            appInfo = AppInfo(appLabel, packageName!!, appIcon)
            
            findViewById<ImageView>(R.id.detailAppIcon).setImageDrawable(appIcon)
            findViewById<TextView>(R.id.detailAppName).text = appLabel
            findViewById<TextView>(R.id.detailPackageName).text = packageName

            loadFullAppInfo(appInfo)
            populateSections(sectionsContainer, appInfo)
            
            findViewById<MaterialButton>(R.id.btnCopyAll).setOnClickListener {
                copyAppInfoToClipboard(this, appInfo)
            }

        } catch (e: Exception) {
            Toast.makeText(this, "Error loading app details: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadFullAppInfo(app: AppInfo) {
        try {
            val flags = PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_CONFIGURATIONS or
                    PackageManager.GET_SIGNATURES

            val info = packageManager.getPackageInfo(app.packageName, flags)
            val applicationInfo = info.applicationInfo

            app.apply {
                permissions = info.requestedPermissions?.toList() ?: emptyList()
                services = info.services?.mapNotNull { it.name } ?: emptyList()
                activities = info.activities?.mapNotNull { it.name } ?: emptyList()
                receivers = info.receivers?.mapNotNull { it.name } ?: emptyList()
                providers = info.providers?.mapNotNull { it.name } ?: emptyList()
                versionName = info.versionName
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
                minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) applicationInfo?.minSdkVersion else 0
                targetSdk = applicationInfo?.targetSdkVersion
                firstInstallTime = info.firstInstallTime
                lastUpdateTime = info.lastUpdateTime
                sourceDir = applicationInfo?.sourceDir
                dataDir = applicationInfo?.dataDir
                uid = applicationInfo?.uid
                features = info.reqFeatures?.mapNotNull { it.name } ?: emptyList()
                signatures = info.signatures?.map { getSignatureThumbprint(it) } ?: emptyList()
            }
        } catch (e: Exception) {
            app.permissions = listOf("Error loading details: ${e.message}")
        }
    }

    private fun populateSections(container: LinearLayout, app: AppInfo) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        addSection(container, "Version", listOf("Name: ${app.versionName}", "Code: ${app.versionCode}"))
        addSection(container, "SDK Info", listOf("Min SDK: ${app.minSdk}", "Target SDK: ${app.targetSdk}"))
        addSection(container, "Install Times", listOf(
            "First Installed: ${app.firstInstallTime?.let { dateFormat.format(Date(it)) }}",
            "Last Updated: ${app.lastUpdateTime?.let { dateFormat.format(Date(it)) }}"
        ))
        addSection(container, "Storage & UID", listOf("UID: ${app.uid}", "Source Dir: ${app.sourceDir}", "Data Dir: ${app.dataDir}"))
        addSection(container, "Signatures (SHA-256)", app.signatures)
        addSection(container, "Permissions", app.permissions)
        addSection(container, "Features", app.features)
        addSection(container, "Services", app.services)
        addSection(container, "Activities", app.activities)
        addSection(container, "Broadcast Receivers", app.receivers)
        addSection(container, "Content Providers", app.providers)
    }

    private fun addSection(container: LinearLayout, title: String, items: List<String>) {
        val filteredItems = items.filter { it.isNotBlank() && it != "null" }
        if (filteredItems.isEmpty()) return

        val titleColor = ContextCompat.getColor(this, android.R.color.holo_blue_dark)

        val titleView = TextView(this).apply {
            text = title
            setTextColor(titleColor)
            setPadding(0, 16, 0, 4)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        container.addView(titleView)

        val contentView = TextView(this).apply {
            text = filteredItems.joinToString("\n") { "• $it" }
            textSize = 13f
            val isDarkMode = resources.configuration.uiMode and
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
            setTextColor(if (isDarkMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
        }
        container.addView(contentView)
    }

    private fun getSignatureThumbprint(signature: Signature): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(signature.toByteArray())
            val digest = md.digest()
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun copyAppInfoToClipboard(context: Context, app: AppInfo) {
        val fullText = buildString {
            append("App Name: ${app.name}\n")
            append("Package: ${app.packageName}\n")
            append("Version: ${app.versionName} (${app.versionCode})\n")
            append("Min/Target SDK: ${app.minSdk}/${app.targetSdk}\n\n")
            append("Permissions:\n${app.permissions.joinToString("\n")}\n\n")
            append("Signatures:\n${app.signatures.joinToString("\n")}\n\n")
            append("Activities:\n${app.activities.joinToString("\n")}\n\n")
            append("Services:\n${app.services.joinToString("\n")}\n\n")
            append("Receivers:\n${app.receivers.joinToString("\n")}\n\n")
            append("Providers:\n${app.providers.joinToString("\n")}")
        }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("App Details", fullText))
        Toast.makeText(context, "Copied all details", Toast.LENGTH_SHORT).show()
    }
}
