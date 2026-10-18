package com.rockhard.blocker.figure

/**
 * A skeleton in one pose: every node's local translation, rotation and scale
 * (starting at rest), and after [update] its matrix in the figure's space.
 */
class Pose(val skeleton: Skeleton) {
    val t = skeleton.restT.copyOf()
    val r = skeleton.restR.copyOf()
    val s = skeleton.restS.copyOf()
    val global = FloatArray(skeleton.size * 16)
    private val local = FloatArray(16)
    private val tv = FloatArray(3)
    private val rv = FloatArray(4)
    private val sv = FloatArray(3)

    fun reset() {
        skeleton.restT.copyInto(t)
        skeleton.restR.copyInto(r)
        skeleton.restS.copyInto(s)
    }

    /** Works out every node's matrix from the local values. */
    fun update() {
        for (i in skeleton.order) {
            t.copyInto(tv, 0, i * 3, i * 3 + 3)
            r.copyInto(rv, 0, i * 4, i * 4 + 4)
            s.copyInto(sv, 0, i * 3, i * 3 + 3)
            Mat4.compose(tv, rv, sv, local)
            val p = skeleton.parent[i]
            if (p < 0) local.copyInto(global, i * 16) else mul(global, p * 16, local, 0, global, i * 16)
        }
    }

    /** The skinning matrix of each of a skin's joints, 16 floats each: the joint's matrix × its inverse bind. */
    fun jointMatrices(skin: Skin, out: FloatArray = FloatArray(skin.joints.size * 16)): FloatArray {
        for (j in skin.joints.indices) mul(global, skin.joints[j] * 16, skin.inverseBind, j * 16, out, j * 16)
        return out
    }

    /** Computes out = a × b using offsets, without allocating. The output must not overlap either input. */
    private fun mul(a: FloatArray, ao: Int, b: FloatArray, bo: Int, out: FloatArray, oo: Int) {
        for (c in 0 until 4) for (r in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) sum += a[ao + k * 4 + r] * b[bo + c * 4 + k]
            out[oo + c * 4 + r] = sum
        }
    }
}
