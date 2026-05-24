package com.rockhard.blocker.sync

import android.app.Service
import android.content.Intent
import android.os.IBinder

class DummySyncService : Service() {
    companion object {
        private var syncAdapter: DummySyncAdapter? = null
        private val lock = Any()
    }

    override fun onCreate() {
        super.onCreate()
        synchronized(lock) {
            if (syncAdapter == null) {
                syncAdapter = DummySyncAdapter(applicationContext, true)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return syncAdapter?.syncAdapterBinder
    }
}
