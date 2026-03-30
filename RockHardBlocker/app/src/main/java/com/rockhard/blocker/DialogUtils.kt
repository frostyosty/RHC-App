// app/src/main/java/com/rockhard/blocker/DialogUtils.kt
package com.rockhard.blocker

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

object DialogUtils {
    fun showCustomDialog(
        activity: Activity, title: String, message: String?, 
        showNegative: Boolean, positiveText: String, 
        onPositive: (() -> Unit)?, viewSetup: ((LinearLayout, AlertDialog) -> Unit)? = null
    ) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_custom, null)
        val dialog = AlertDialog.Builder(activity).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = title
        if (message != null) { 
            val tvMsg = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
            tvMsg.text = message
            tvMsg.visibility = View.VISIBLE 
        }
        
        val contentLayout = dialogView.findViewById<LinearLayout>(R.id.llDialogContent)
        val btnPos = dialogView.findViewById<Button>(R.id.btnDialogPositive)
        
        if (positiveText.isNotEmpty()) { 
            btnPos.text = positiveText; btnPos.visibility = View.VISIBLE
            btnPos.setOnClickListener { onPositive?.invoke(); dialog.dismiss() } 
        }
        
        val btnNeg = dialogView.findViewById<Button>(R.id.btnDialogNegative)
        if (showNegative) { 
            btnNeg.visibility = View.VISIBLE; btnNeg.setOnClickListener { dialog.dismiss() } 
        }
        
        dialog.show()
        viewSetup?.invoke(contentLayout, dialog)
    }
}
