package com.rockhard.blocker

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

object AudioEngine {
    private var soundPool: SoundPool? = null
    private val loadedSounds = mutableMapOf<Int, Int>()

    private fun init() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(5).setAudioAttributes(attrs).build()
    }

    fun playSfx(context: Context, filename: String) {
        if (soundPool == null) init()
        val cleanName = filename.lowercase().replace(" ", "_").replace("-", "_").replace(".gif", "")
        val resId = context.resources.getIdentifier(cleanName, "raw", context.packageName)
        if (resId != 0) {
            if (loadedSounds.containsKey(resId)) {
                soundPool?.play(loadedSounds[resId]!!, 1f, 1f, 1, 0, 1f)
            } else {
                val soundId = soundPool?.load(context, resId, 1) ?: return
                loadedSounds[resId] = soundId
                soundPool?.setOnLoadCompleteListener { sp, id, status ->
                    if (status == 0 && id == soundId) sp.play(id, 1f, 1f, 1, 0, 1f)
                }
            }
        }
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        loadedSounds.clear()
    }
}