package com.rockhard.blocker.ar

import android.Manifest
import android.app.Activity
import android.content.Context
import com.rockhard.blocker.PermissionGate

/** The camera, asked for past the Guardian like every permission (PermissionGate). */
object CameraPermission {
    const val REQUEST_CODE = 9001
    private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    fun hasPermission(context: Context) = PermissionGate.has(context, PERMISSIONS)

    fun requestPermission(activity: Activity) = PermissionGate.request(activity, PERMISSIONS, REQUEST_CODE)

    /** The activity's onRequestPermissionsResult must call this; it says whether the camera is allowed now. */
    fun onPermissionResult(activity: Activity) = PermissionGate.onResult(activity, PERMISSIONS, "camera")
}
