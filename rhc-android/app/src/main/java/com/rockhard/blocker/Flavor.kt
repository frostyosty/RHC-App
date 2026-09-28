package com.rockhard.blocker

object Flavor {
    private val f = BuildConfig.FLAVOR
    val isGamers = f.startsWith("gamers")
    val isFemale = f.contains("Female")
    val isHomevisitsApk = f == "gamersFemaleHomevisits"
    const val HOMEVISITS_ACTIVITY = "com.rockhard.blocker.homevisits.HomevisitsActivity"

    fun homevisits() = isHomevisitsApk
    fun usesMomentum() = !isGamers || homevisits()
}
