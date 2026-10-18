package com.rockhard.blocker

import android.app.AlertDialog
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.text.format.Formatter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/** [exact] is code + data + cache; without Usage Access Android only lets us see the app's own files. */
private class AppSize(val bytes: Long, val exact: Boolean)

private fun MainActivity.phoneStorage(): Pair<Long, Long> = try {
    val stats = getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
    stats.getTotalBytes(StorageManager.UUID_DEFAULT) to stats.getFreeBytes(StorageManager.UUID_DEFAULT)
} catch (e: Exception) {
    val fs = StatFs(Environment.getDataDirectory().path)
    fs.totalBytes to fs.availableBytes
}

private fun MainActivity.appSize(info: ApplicationInfo): AppSize {
    try {
        val s = (getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager)
            .queryStatsForPackage(info.storageUuid, info.packageName, Process.myUserHandle())
        return AppSize(s.appBytes + s.dataBytes + s.cacheBytes, true)
    } catch (e: Exception) { /* SecurityException without Usage Access: fall through to file sizes */ }
    val files = listOf(info.sourceDir) + (info.splitSourceDirs?.toList() ?: emptyList())
    return AppSize(files.sumOf { File(it).length() }, false)
}

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
    
    val storageText = TextView(this).apply {
        textSize = 14f
        setTextColor(Color.WHITE)
    }
    val storageBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000
        progressTintList = ColorStateList.valueOf(Color.parseColor("#D32F2F"))
        progressBackgroundTintList = ColorStateList.valueOf(Color.parseColor("#2A2A2A"))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 24).apply { setMargins(0, 16, 0, 0) }
    }
    val storageCard = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(30, 30, 30, 30)
        setBackgroundColor(Color.parseColor("#1C1C1C"))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 30) }
        addView(storageText)
        addView(storageBar)
    }
    fun refreshStorage() {
        val (total, free) = phoneStorage()
        val used = total - free
        storageText.text = "Phone storage: ${Formatter.formatFileSize(this, used)} used of ${Formatter.formatFileSize(this, total)}\n${Formatter.formatFileSize(this, free)} free"
        storageBar.progress = if (total > 0) (used * 1000 / total).toInt() else 0
    }
    refreshStorage()

    layout.addView(title)
    layout.addView(subtitle)
    layout.addView(storageCard)

    val scrollView = ScrollView(this)
    val listLayout = LinearLayout(this).apply { 
        orientation = LinearLayout.VERTICAL 
    }

    val items = mutableMapOf<String, android.view.View>()
    val sizeViews = mutableMapOf<String, TextView>()

    userApps.forEach { appInfo ->
        val appName = pm.getApplicationLabel(appInfo).toString()
        val pkgName = appInfo.packageName
        val item = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items[pkgName] = item

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
        }
        val sizeView = TextView(this).apply {
            text = "Measuring size..."
            setTextColor(Color.parseColor("#888888"))
            textSize = 12f
        }
        sizeViews[pkgName] = sizeView
        val nameColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(nameView)
            addView(sizeView)
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

        row.addView(nameColumn)
        row.addView(btnUninstall)
        item.addView(row)

        // Injecting the faint divider line here
        val divider = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2).apply {
                setMargins(0, 0, 0, 0)
            }
            setBackgroundColor(Color.parseColor("#2A2A2A"))
        }
        item.addView(divider)
        listLayout.addView(item)
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

    // Sizes are measured off the main thread, since each one is a call into the system
    Thread {
        userApps.forEach { appInfo ->
            val size = appSize(appInfo)
            runOnUiThread {
                if (!dialog.isShowing) return@runOnUiThread
                val label = Formatter.formatFileSize(this, size.bytes)
                sizeViews[appInfo.packageName]?.text = if (size.exact) label else "$label (app file only)"
            }
        }
    }.start()

    // Coming back from the system's uninstall prompt: show the freed space and drop apps that are gone
    layout.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
        if (!hasFocus) return@addOnWindowFocusChangeListener
        refreshStorage()
        items.forEach { (pkg, view) ->
            val installed = try { pm.getApplicationInfo(pkg, 0); true } catch (e: PackageManager.NameNotFoundException) { false }
            if (!installed) view.visibility = android.view.View.GONE
        }
    }
}
