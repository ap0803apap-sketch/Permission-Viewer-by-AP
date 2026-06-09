package com.ap.permissionviewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppAdapter
    private lateinit var searchEditText: TextInputEditText
    private lateinit var shimmerLayout: View
    private lateinit var btnLoadApk: Button
    private lateinit var btnRefresh: Button
    private lateinit var btnSettings: ImageButton

    private val appList = ArrayList<AppInfo>()
    private val displayedList = ArrayList<AppInfo>()

    private val apkPickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                showShimmer()
                recyclerView.postDelayed({ loadPermissionsFromApk(it) }, 100)
            }
        }

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
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerViewApps)
        searchEditText = findViewById(R.id.searchEditText)
        shimmerLayout = findViewById(R.id.shimmerLayout)
        btnLoadApk = findViewById(R.id.btnLoadApk)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnSettings = findViewById(R.id.btnSettings)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = AppAdapter(displayedList, packageManager)
        recyclerView.adapter = adapter

        btnLoadApk.setOnClickListener {
            apkPickerLauncher.launch("*/*")
        }

        btnRefresh.setOnClickListener {
            showShimmer()
            recyclerView.postDelayed({ loadInstalledApps() }, 200)
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterList(s.toString())
            }
        })

        showShimmer()
        recyclerView.postDelayed({ loadInstalledApps() }, 200)
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

    private fun filterList(query: String) {
        val filtered = if (query.isEmpty()) {
            appList
        } else {
            appList.filter { it.name.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
        }
        displayedList.clear()
        displayedList.addAll(filtered)
        adapter.notifyDataSetChanged()
    }

    private fun showShimmer() {
        shimmerLayout.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun hideShimmer() {
        shimmerLayout.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    private fun loadInstalledApps() {
        try {
            val pm = packageManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            appList.clear()
            for (app in installedApps) {
                val name = pm.getApplicationLabel(app).toString()
                val pkg = app.packageName
                val icon: Drawable = pm.getApplicationIcon(app)
                appList.add(AppInfo(name, pkg, icon))
            }

            appList.sortBy { it.name.lowercase() }
            displayedList.clear()
            displayedList.addAll(appList)

            adapter.notifyDataSetChanged()
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading apps: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            hideShimmer()
        }
    }

    private fun loadPermissionsFromApk(uri: Uri) {
        try {
            val tempFile = File(cacheDir, "temp_apk.apk")
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            val flags = PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_CONFIGURATIONS or
                    PackageManager.GET_SIGNATURES

            val info: PackageInfo? = packageManager.getPackageArchiveInfo(tempFile.path, flags)

            val appInfo = info?.applicationInfo ?: run {
                Toast.makeText(this, "Invalid APK file", Toast.LENGTH_SHORT).show()
                hideShimmer()
                return
            }

            appInfo.sourceDir = tempFile.path
            appInfo.publicSourceDir = tempFile.path

            val label = packageManager.getApplicationLabel(appInfo).toString()
            val icon = packageManager.getApplicationIcon(appInfo)
            val pkgName = appInfo.packageName

            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 32, 32, 32)
            }

            val iconView = ImageView(this).apply {
                setImageDrawable(icon)
                layoutParams = LinearLayout.LayoutParams(120, 120).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setMargins(0, 0, 0, 24)
                }
            }
            layout.addView(iconView)

            fun addSection(title: String, content: String) {
                if (content.isBlank()) return
                val titleView = TextView(this).apply {
                    text = title
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.holo_blue_dark))
                    setPadding(0, 16, 0, 4)
                }
                val contentView = TextView(this).apply {
                    text = content
                    textSize = 13f
                }
                layout.addView(titleView)
                layout.addView(contentView)
            }

            addSection("App Name", label)
            addSection("Package Name", pkgName)
            addSection("Version", "${info?.versionName} (${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info?.longVersionCode else info?.versionCode})")
            addSection("SDK Info", "Min: ${appInfo.minSdkVersion} | Target: ${appInfo.targetSdkVersion}")
            addSection("UID", appInfo.uid.toString())
            addSection("Permissions", info?.requestedPermissions?.joinToString("\n") { "• $it" } ?: "None")
            addSection("Signatures (SHA-256)", info?.signatures?.joinToString("\n") { "• ${getSignatureThumbprint(it)}" } ?: "None")
            addSection("Services", info?.services?.joinToString("\n") { "• ${it.name}" } ?: "None")
            addSection("Activities", info?.activities?.joinToString("\n") { "• ${it.name}" } ?: "None")
            addSection("Receivers", info?.receivers?.joinToString("\n") { "• ${it.name}" } ?: "None")
            addSection("Providers", info?.providers?.joinToString("\n") { "• ${it.name}" } ?: "None")

            val scrollView = ScrollView(this).apply {
                addView(layout)
            }

            MaterialAlertDialogBuilder(this)
                .setTitle("Extracted APK Info")
                .setView(scrollView)
                .setPositiveButton("Close", null)
                .setNegativeButton("Copy All") { _, _ ->
                    val fullText = buildString {
                        append("App Name: $label\nPackage: $pkgName\n\n")
                        append("Permissions:\n${info?.requestedPermissions?.joinToString("\n")}\n\n")
                        append("Activities:\n${info?.activities?.joinToString("\n") { it.name }}")
                    }
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("APK Info", fullText))
                    Toast.makeText(this@MainActivity, "Copied all APK info", Toast.LENGTH_SHORT).show()
                }
                .show()

        } catch (e: Exception) {
            Toast.makeText(this, "Failed to load APK: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            hideShimmer()
        }
    }

    private fun getSignatureThumbprint(signature: android.content.pm.Signature): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(signature.toByteArray())
            val digest = md.digest()
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            "N/A"
        }
    }
}
