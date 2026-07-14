package com.rockhard.blocker

import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

internal fun MainActivity.showSafeUninstaller() {
    val pm = packageManager
    val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
    
    // Filter out system apps and RHC itself
    val userApps = packages.filter { 
        (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && 
        (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 &&
        it.packageName != packageName 
    }.sortedBy { pm.getApplicationLabel(it).toString().lowercase() }

    val layout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(40, 40, 40, 40)
        setBackgroundColor(Color.parseColor("#121212"))
    }

    val title = TextView(this).apply {
        text = "Safe App Manager"
        textSize = 22f
        setTextColor(Color.WHITE)
        setPadding(0, 0, 0, 30)
    }
    
    val subtitle = TextView(this).apply {
        text = "System settings are locked by the shield. You may use this portal to safely uninstall your other applications."
        textSize = 14f
        setTextColor(Color.parseColor("#888888"))
        setPadding(0, 0, 0, 40)
    }
    
    layout.addView(title)
    layout.addView(subtitle)

    val scrollView = ScrollView(this)
    val listLayout = LinearLayout(this).apply { 
        orientation = LinearLayout.VERTICAL 
    }

    userApps.forEach { appInfo ->
        val appName = pm.getApplicationLabel(appInfo).toString()
        val pkgName = appInfo.packageName

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 24)
            weightSum = 1f
            gravity = android.view.Gravity.CENTER_VERTICAL // Perfect alignment
        }

        val nameView = TextView(this).apply {
            text = appName
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnUninstall = Button(this).apply {
            text = "Uninstall"
            setBackgroundResource(R.drawable.bg_btn_danger)
            setTextColor(Color.WHITE)
            setPadding(30, 0, 30, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkgName"))
                startActivity(intent)
            }
        }

        row.addView(nameView)
        row.addView(btnUninstall)
        listLayout.addView(row)

        // Injecting the faint divider line here
        val divider = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                setMargins(0, 0, 0, 0)
            }
            setBackgroundColor(Color.parseColor("#2A2A2A"))
        }
        listLayout.addView(divider)
    }

    scrollView.addView(listLayout)
    
    val btnClose = Button(this).apply {
        text = "CLOSE SAFE MANAGER"
        setBackgroundResource(R.drawable.bg_btn_standard)
        setTextColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 30, 0, 0)
        }
    }
    
    layout.addView(scrollView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    layout.addView(btnClose)

    val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
        .setView(layout)
        .setCancelable(false)
        .create()
    
    btnClose.setOnClickListener { dialog.dismiss() }
    dialog.show()
}
