package com.ap.permissionviewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import com.google.android.material.materialswitch.MaterialSwitch

class SettingsActivity : AppCompatActivity() {

    private lateinit var radioGroupTheme: RadioGroup
    private lateinit var switchAmoled: MaterialSwitch
    private lateinit var switchDynamic: MaterialSwitch
    private lateinit var licenseText: TextView
    private lateinit var licenseExpandIcon: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeSettings()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        radioGroupTheme = findViewById(R.id.radioGroupTheme)
        switchAmoled = findViewById(R.id.switchAmoled)
        switchDynamic = findViewById(R.id.switchDynamic)
        licenseText = findViewById(R.id.licenseText)
        licenseExpandIcon = findViewById(R.id.licenseExpandIcon)

        setupThemeSettings()
        setupDeveloperInfo()
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

    private fun setupThemeSettings() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        
        // Theme
        val theme = prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        when (theme) {
            AppCompatDelegate.MODE_NIGHT_NO -> radioGroupTheme.check(R.id.radioLight)
            AppCompatDelegate.MODE_NIGHT_YES -> radioGroupTheme.check(R.id.radioDark)
            else -> radioGroupTheme.check(R.id.radioSystem)
        }

        radioGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                R.id.radioLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.radioDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            prefs.edit().putInt("theme", newTheme).apply()
            AppCompatDelegate.setDefaultNightMode(newTheme)
        }

        // AMOLED
        switchAmoled.isChecked = prefs.getBoolean("amoled", false)
        switchAmoled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("amoled", isChecked).apply()
            recreate()
        }

        // Dynamic Colors
        switchDynamic.isChecked = prefs.getBoolean("dynamic_colors", true)
        switchDynamic.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dynamic_colors", isChecked).apply()
            recreate()
        }
    }

    private fun setupDeveloperInfo() {
        findViewById<View>(R.id.btnEmail).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${getString(R.string.dev_email)}")
            }
            startActivity(intent)
        }

        findViewById<View>(R.id.btnSourceCode).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.source_code_url)))
            startActivity(intent)
        }

        val licenseHeader = findViewById<View>(R.id.licenseHeader)
        licenseHeader.setOnClickListener {
            val isVisible = licenseText.visibility == View.VISIBLE
            TransitionManager.beginDelayedTransition(findViewById<ViewGroup>(R.id.cardDeveloper))
            licenseText.visibility = if (isVisible) View.GONE else View.VISIBLE
            licenseExpandIcon.animate().rotation(if (isVisible) 0f else 180f).start()
        }
    }
}
