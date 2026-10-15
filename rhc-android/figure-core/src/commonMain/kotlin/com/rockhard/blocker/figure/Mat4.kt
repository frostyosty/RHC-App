package com.rockhard.blocker.figure

import kotlin.math.sqrt

/**
 * 4x4 matrices as FloatArray(16), column-major like glTF and OpenGL, and
 * quaternions as (x, y, z, w) like glTF.
 */
object Mat4 {
    fun identity() = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    /** out = a × b. `out` may be neither `a` nor `b`. */
    fun mul(a: FloatArray, b: FloatArray, out: FloatArray = FloatArray(16)): FloatArray {
        for (c in 0 until 4) for (r in 0 until 4) {
            var s = 0f
            for (k in 0 until 4) s += a[k * 4 + r] * b[c * 4 + k]
            out[c * 4 + r] = s
        }
        return out
    }

    /** The matrix of translation t, rotation q (x, y, z, w) and scale s, written into `out`. */
    fun compose(t: FloatArray, q: FloatArray, s: FloatArray, out: FloatArray = FloatArray(16)): FloatArray {
        val (x, y, z, w) = q
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        out[0] = (1 - 2 * (yy + zz)) * s[0]; out[1] = 2 * (xy + wz) * s[0]; out[2] = 2 * (xz - wy) * s[0]; out[3] = 0f
        out[4] = 2 * (xy - wz) * s[1]; out[5] = (1 - 2 * (xx + zz)) * s[1]; out[6] = 2 * (yz + wx) * s[1]; out[7] = 0f
        out[8] = 2 * (xz + wy) * s[2]; out[9] = 2 * (yz - wx) * s[2]; out[10] = (1 - 2 * (xx + yy)) * s[2]; out[11] = 0f
        out[12] = t[0]; out[13] = t[1]; out[14] = t[2]; out[15] = 1f
        return out
    }

    /** Splits a matrix with no shear into translation, rotation and scale. */
    fun decompose(m: FloatArray, t: FloatArray, q: FloatArray, s: FloatArray) {
        t[0] = m[12]; t[1] = m[13]; t[2] = m[14]
        s[0] = sqrt(m[0] * m[0] + m[1] * m[1] + m[2] * m[2])
        s[1] = sqrt(m[4] * m[4] + m[5] * m[5] + m[6] * m[6])
        s[2] = sqrt(m[8] * m[8] + m[9] * m[9] + m[10] * m[10])
        val r00 = m[0] / s[0]; val r10 = m[1] / s[0]; val r20 = m[2] / s[0]
        val r01 = m[4] / s[1]; val r11 = m[5] / s[1]; val r21 = m[6] / s[1]
        val r02 = m[8] / s[2]; val r12 = m[9] / s[2]; val r22 = m[10] / s[2]
        val tr = r00 + r11 + r22
        if (tr > 0) {
            val k = 0.5f / sqrt(tr + 1f)
            q[3] = 0.25f / k; q[0] = (r21 - r12) * k; q[1] = (r02 - r20) * k; q[2] = (r10 - r01) * k
        } else if (r00 > r11 && r00 > r22) {
            val k = 2f * sqrt(1f + r00 - r11 - r22)
            q[3] = (r21 - r12) / k; q[0] = 0.25f * k; q[1] = (r01 + r10) / k; q[2] = (r02 + r20) / k
        } else if (r11 > r22) {
            val k = 2f * sqrt(1f + r11 - r00 - r22)
            q[3] = (r02 - r20) / k; q[0] = (r01 + r10) / k; q[1] = 0.25f * k; q[2] = (r12 + r21) / k
        } else {
            val k = 2f * sqrt(1f + r22 - r00 - r11)
            q[3] = (r10 - r01) / k; q[0] = (r02 + r20) / k; q[1] = (r12 + r21) / k; q[2] = 0.25f * k
        }
    }

    /** Transforms the point (x, y, z) by m into out[o..o+2]. */
    fun point(m: FloatArray, x: Float, y: Float, z: Float, out: FloatArray, o: Int = 0) {
        out[o] = m[0] * x + m[4] * y + m[8] * z + m[12]
        out[o + 1] = m[1] * x + m[5] * y + m[9] * z + m[13]
        out[o + 2] = m[2] * x + m[6] * y + m[10] * z + m[14]
    }

    /** Normalised lerp between two quaternions, the short way round. Close keyframes make it as good as slerp. */
    fun nlerp(a: FloatArray, ao: Int, b: FloatArray, bo: Int, t: Float, out: FloatArray) {
        val dot = a[ao] * b[bo] + a[ao + 1] * b[bo + 1] + a[ao + 2] * b[bo + 2] + a[ao + 3] * b[bo + 3]
        val sb = if (dot < 0) -t else t
        val sa = 1 - t
        var len = 0f
        for (i in 0 until 4) {
            out[i] = a[ao + i] * sa + b[bo + i] * sb
            len += out[i] * out[i]
        }
        val inv = if (len > 0) 1f / sqrt(len) else 0f
        for (i in 0 until 4) out[i] *= inv
    }
}
