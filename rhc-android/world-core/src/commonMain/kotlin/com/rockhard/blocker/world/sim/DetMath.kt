package com.rockhard.blocker.world.sim

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Trig for the sim, built only from + - * /, floor and sqrt, which IEEE 754
 * makes bit-identical on the JVM and wasm. kotlin.math's sin/cos/atan2/hypot
 * are not: the JVM uses CPU intrinsics and wasm its own library, so they can
 * differ in the last bit, and a circling fight would drift apart between the
 * Android and web clients (DeterminismTest pins this). Accurate to ~1e-12,
 * far more than the game needs. The renderer can keep kotlin.math.
 */
object DetMath {
    private const val TAU = 2 * PI
    private const val HALF_PI = PI / 2

    fun sin(x: Double): Double {
        var r = x - TAU * floor((x + PI) / TAU) // [-pi, pi)
        if (r > HALF_PI) r = PI - r else if (r < -HALF_PI) r = -PI - r // [-pi/2, pi/2]
        val r2 = r * r
        // Taylor series to r^17: the error is under 1e-12 on [-pi/2, pi/2]
        return r * (1 + r2 * (-1.0 / 6 + r2 * (1.0 / 120 + r2 * (-1.0 / 5040 + r2 * (1.0 / 362880 + r2 * (-1.0 / 39916800 +
            r2 * (1.0 / 6227020800 + r2 * (-1.0 / 1307674368000 + r2 * (1.0 / 355687428096000)))))))))
    }

    fun cos(x: Double) = sin(x + HALF_PI)

    fun atan(x: Double): Double {
        if (x.isNaN()) return x
        val neg = x < 0
        var a = abs(x)
        val inv = a > 1
        if (inv) a = 1 / a
        // halve the angle twice (atan x = 2 atan(x / (1 + sqrt(1 + x^2)))), leaving |a| <= tan(pi/16)
        a /= 1 + sqrt(1 + a * a)
        a /= 1 + sqrt(1 + a * a)
        val a2 = a * a
        var r = a * (1 + a2 * (-1.0 / 3 + a2 * (1.0 / 5 + a2 * (-1.0 / 7 + a2 * (1.0 / 9 + a2 * (-1.0 / 11 +
            a2 * (1.0 / 13 + a2 * (-1.0 / 15 + a2 * (1.0 / 17)))))))))
        r *= 4
        if (inv) r = HALF_PI - r
        return if (neg) -r else r
    }

    fun atan2(y: Double, x: Double): Double = when {
        x > 0 -> atan(y / x)
        x < 0 && y >= 0 -> atan(y / x) + PI
        x < 0 -> atan(y / x) - PI
        y > 0 -> HALF_PI
        y < 0 -> -HALF_PI
        else -> 0.0
    }

    fun hypot(x: Double, y: Double) = sqrt(x * x + y * y)
}
