package com.rockhard.blocker.sync

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

class StubContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun query(uri: Uri, p: Array<out String>?, s: String?, sa: Array<out String>?, so: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
