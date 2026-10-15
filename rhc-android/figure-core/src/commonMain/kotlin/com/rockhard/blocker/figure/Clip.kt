package com.rockhard.blocker.figure

/**
 * A move (walk, wave, sit) as tracks keyed by bone name, so a move from one
 * file plays on a figure from another when they share a rig (Quaternius'
 * Universal Animation Library and Universal Base Characters do).
 */
class Clip(val name: String, val duration: Float, val tracks: List<Track>) {

    enum class Path { TRANSLATION, ROTATION, SCALE }

    /**
     * One bone's keyframes. `values` has 3 (translation, scale) or 4 (rotation)
     * floats per key. `restT` and `depth` are the bone's in the file the move
     * came from, for fitting it to a figure of other proportions.
     */
    class Track(
        val bone: String,
        val path: Path,
        val times: FloatArray,
        val values: FloatArray,
        val step: Boolean,
        val restT: FloatArray,
        val depth: Int,
    )

    /**
     * Fits this move to a figure's skeleton. Bones match by name; tracks for
     * bones the figure lacks are dropped. Bones only rotate, except the root
     * bone (the hips), whose movement is scaled to the figure's height. With
     * `inPlace`, the root's drift across the move is taken out, so a walk
     * walks on the spot and the game moves the figure. Use it for loops only:
     * sitting down is meant to lower the hips.
     */
    fun bind(skeleton: Skeleton, inPlace: Boolean = false): Bound {
        val rootDepth = tracks.filter { it.path == Path.TRANSLATION && skeleton.indexOf(it.bone) >= 0 }.minOfOrNull { it.depth }
        val bound = ArrayList<BoundTrack>()
        for (tr in tracks) {
            val node = skeleton.indexOf(tr.bone)
            if (node < 0) continue
            when (tr.path) {
                Path.ROTATION -> bound.add(BoundTrack(node, tr, tr.values))
                Path.SCALE -> {}
                Path.TRANSLATION -> {
                    if (tr.depth != rootDepth) continue
                    val restY = skeleton.restT[node * 3 + 1]
                    val ratio = if (restY > 0f && tr.restT[1] > 0f) restY / tr.restT[1] else 1f
                    val v = FloatArray(tr.values.size) { tr.values[it] * ratio }
                    if (inPlace && tr.times.size > 1) {
                        val n = tr.times.size
                        val t0 = tr.times[0]
                        val span = tr.times[n - 1] - t0
                        for (k in 0 until n) {
                            val f = if (span > 0f) (tr.times[k] - t0) / span else 0f
                            for (c in 0 until 3) v[k * 3 + c] -= (v[(n - 1) * 3 + c] - v[c]) * f
                        }
                    }
                    bound.add(BoundTrack(node, tr, v))
                }
            }
        }
        return Bound(this, bound)
    }

    internal class BoundTrack(val node: Int, val track: Track, val values: FloatArray)

    /** A move fitted to one skeleton, ready to play. */
    class Bound internal constructor(val clip: Clip, private val tracks: List<BoundTrack>) {
        private val q = FloatArray(4)
        private val v = FloatArray(4)

        /**
         * Puts the move at `time` seconds (clamped to the move) into the pose.
         * `weight` below 1 blends it over what the pose already holds, for
         * easing from one move into the next. Call [Pose.update] after.
         */
        fun apply(pose: Pose, time: Float, weight: Float = 1f) {
            for (bt in tracks) {
                val times = bt.track.times
                val n = times.size
                if (n == 0) continue
                val size = if (bt.track.path == Path.ROTATION) 4 else 3
                // the key at or before `time`, and how far towards the next
                var k = 0
                var f = 0f
                if (time >= times[n - 1]) {
                    k = n - 1
                } else if (time > times[0]) {
                    var lo = 0
                    var hi = n - 1
                    while (hi - lo > 1) {
                        val mid = (lo + hi) / 2
                        if (times[mid] <= time) lo = mid else hi = mid
                    }
                    k = lo
                    if (!bt.track.step) f = (time - times[lo]) / (times[hi] - times[lo])
                }
                val vals = bt.values
                val k2 = minOf(k + 1, n - 1)
                if (size == 4) {
                    Mat4.nlerp(vals, k * 4, vals, k2 * 4, f, v)
                    if (weight < 1f) {
                        pose.r.copyInto(q, 0, bt.node * 4, bt.node * 4 + 4)
                        Mat4.nlerp(q, 0, v, 0, weight, q)
                        q.copyInto(pose.r, bt.node * 4)
                    } else {
                        v.copyInto(pose.r, bt.node * 4)
                    }
                } else {
                    for (c in 0 until 3) {
                        val x = vals[k * 3 + c] + (vals[k2 * 3 + c] - vals[k * 3 + c]) * f
                        val o = bt.node * 3 + c
                        pose.t[o] = pose.t[o] + (x - pose.t[o]) * weight
                    }
                }
            }
        }
    }

    companion object {
        /** Every move in a .glb file. */
        fun from(gltf: Gltf): List<Clip> {
            val skeleton = Skeleton.from(gltf)
            return gltf.animations.mapIndexed { ai, anim ->
                val samplers = anim["samplers"].list().map { it.obj() }
                var duration = 0f
                val tracks = anim["channels"].list().mapNotNull { chAny ->
                    val ch = chAny.obj()
                    val target = ch["target"].obj()
                    val node = target["node"].int()
                    if (node < 0) return@mapNotNull null
                    val path = when (target["path"]) {
                        "translation" -> Path.TRANSLATION
                        "rotation" -> Path.ROTATION
                        "scale" -> Path.SCALE
                        else -> return@mapNotNull null // morph weights
                    }
                    val sampler = samplers[ch["sampler"].int()]
                    val times = gltf.floats(sampler["input"].int())
                    var values = gltf.floats(sampler["output"].int())
                    val interp = sampler["interpolation"] as? String ?: "LINEAR"
                    val size = if (path == Path.ROTATION) 4 else 3
                    if (interp == "CUBICSPLINE") {
                        // in-tangent, value, out-tangent per key: keep the values, play them linearly
                        values = FloatArray(times.size * size) { values[(it / size) * size * 3 + size + it % size] }
                    }
                    if (times.isNotEmpty()) duration = maxOf(duration, times.last())
                    Track(
                        bone = skeleton.names[node], path = path, times = times, values = values,
                        step = interp == "STEP",
                        restT = skeleton.restT.copyOfRange(node * 3, node * 3 + 3),
                        depth = skeleton.depth(node),
                    )
                }
                Clip(anim["name"] as? String ?: "move$ai", duration, tracks)
            }
        }
    }
}
