package com.rockhard.blocker.sync

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.SyncResult
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.rockhard.blocker.GuardianService

class DummySyncAdapter(context: Context, autoInitialize: Boolean) : AbstractThreadedSyncAdapter(context, autoInitialize) {
    override fun onPerformSync(
        account: Account?,
        extras: Bundle?,
        authority: String?,
        provider: ContentProviderClient?,
        syncResult: SyncResult?
    ) {
        // Heartbeat tick to verify process is alive. This runs on the sync thread, and the log is only
        // touched on the main thread
        Handler(Looper.getMainLooper()).post { GuardianService.addLog("[SYNC] Background System Heartbeat Executed.") }
    }
}
