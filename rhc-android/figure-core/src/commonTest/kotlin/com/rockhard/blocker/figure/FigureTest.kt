package com.rockhard.blocker.figure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FigureTest {
    private fun near(expected: Float, actual: Float, what: String) =
        assertTrue(kotlin.math.abs(expected - actual) < 1e-3f, "$what: expected $expected, got $actual")

    @Test
    fun jsonReadsWhatGltfWrites() {
        val j = Json.parse("""{"a":[1,-2.5e1,true,null],"b":{"c":"x\"yA"}}""").obj()
        assertEquals(listOf(1.0, -25.0, true, null), j["a"])
        assertEquals("x\"yA", j["b"].obj()["c"])
    }

    @Test
    fun mannequinStandsOnTheFloorAtItsHeight() {
        val b = Mannequin.figure().restBounds
        near(0f, b[1], "feet")
        near(1.75f, b[4], "top of the head")
    }

    @Test
    fun aWaveLiftsTheRightArmAboveTheShoulder() {
        val fig = Mannequin.figure()
        val pose = Pose(fig.skeleton)
        val wave = Mannequin.clips().first { it.name == "Wave" }.bind(fig.skeleton)
        wave.apply(pose, 0f)
        pose.update()
        // the right arm's lowest corner, which hangs at 0.95 m at rest
        val arm = fig.parts[0]
        val sk = Skinner(fig, pose, arm)
        val p = FloatArray(3)
        var lowest = Float.MAX_VALUE
        for (v in 0 until arm.vertexCount) if (arm.joints!![v * 4] == 4) { sk.position(v, p); lowest = minOf(lowest, p[1]) }
        assertTrue(lowest > 1.3f, "the waving arm should be up, its lowest point is at $lowest")
    }

    @Test
    fun aWalkInPlaceEndsWhereItStarted() {
        val skel = Mannequin.skeleton()
        val hips = skel.indexOf("Hips")
        val track = Clip.Track(
            "Hips", Clip.Path.TRANSLATION, floatArrayOf(0f, 0.5f, 1f),
            floatArrayOf(0f, 0.95f, 0f, 0f, 0.9f, 0.7f, 0f, 0.95f, 1.4f), false,
            floatArrayOf(0f, 0.95f, 0f), skel.depth(hips),
        )
        val walk = Clip("Walk", 1f, listOf(track)).bind(skel, inPlace = true)
        val pose = Pose(skel)
        walk.apply(pose, 0f)
        val start = pose.t[hips * 3 + 2]
        walk.apply(pose, 1f)
        near(start, pose.t[hips * 3 + 2], "forward drift")
        walk.apply(pose, 0.5f)
        near(0.9f, pose.t[hips * 3 + 1], "the dip in the middle of the step")
    }

    @Test
    fun aMoveScalesTheHipsToAShorterFigure() {
        val skel = Mannequin.skeleton()
        val hips = skel.indexOf("Hips")
        // made on a rig whose hips are 1.9 m up: twice the mannequin's 0.95
        val track = Clip.Track(
            "Hips", Clip.Path.TRANSLATION, floatArrayOf(0f), floatArrayOf(0f, 1.6f, 0f), false,
            floatArrayOf(0f, 1.9f, 0f), skel.depth(hips),
        )
        val pose = Pose(skel)
        Clip("Crouch", 0f, listOf(track)).bind(skel).apply(pose, 0f)
        near(0.8f, pose.t[hips * 3 + 1], "hips")
    }

    @Test
    fun aGlbRoundTrips() {
        val glb = TestGlb.twoBoneStrip()
        val gltf = Gltf.parse(glb)
        val fig = Figure.from(gltf)
        assertEquals(1, fig.parts.size)
        assertEquals(4, fig.parts[0].vertexCount)
        assertEquals(1, fig.skins.size)
        val clips = Clip.from(gltf)
        assertEquals("Bend", clips.single().name)
        near(1f, clips.single().duration, "duration")

        // bent 90° at the second bone: the vertex at (0, 2) swings out to (-1, 1)
        val pose = Pose(fig.skeleton)
        clips.single().bind(fig.skeleton).apply(pose, 1f)
        pose.update()
        val sk = Skinner(fig, pose, fig.parts[0])
        val p = FloatArray(3)
        sk.position(2, p)
        near(-1f, p[0], "bent tip x")
        near(1f, p[1], "bent tip y")
        sk.position(0, p)
        near(0f, p[1], "the root stays put")
    }
}

/** Writes small .glb files for the tests. */
private object TestGlb {
    /**
     * Two bones stacked 1 m apart and a strip of four vertices, two at y = 0
     * on the first bone and two at y = 2 on the second, plus a move "Bend"
     * that turns the second bone 90° about Z over one second.
     */
    fun twoBoneStrip(): ByteArray {
        val bin = Bin()
        val pos = bin.floats(0f, 0f, 0f, 0.1f, 0f, 0f, 0f, 2f, 0f, 0.1f, 2f, 0f)
        val nrm = bin.floats(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f)
        val jnt = bin.bytes(0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0)
        val wgt = bin.floats(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f)
        val idx = bin.shorts(0, 1, 2, 1, 3, 2)
        val ibm = bin.floats(
            1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f,
            1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, -1f, 0f, 1f,
        )
        val times = bin.floats(0f, 1f)
        val s = 0.70710677f
        val rots = bin.floats(0f, 0f, 0f, 1f, 0f, 0f, s, s)
        val views = bin.views.joinToString(",") { (o, l) -> """{"buffer":0,"byteOffset":$o,"byteLength":$l}""" }
        val json = """{"asset":{"version":"2.0"},
            "nodes":[{"name":"Strip","mesh":0,"skin":0},{"name":"Bone1","children":[2]},{"name":"Bone2","translation":[0,1,0]}],
            "meshes":[{"name":"Strip","primitives":[{"attributes":{"POSITION":0,"NORMAL":1,"JOINTS_0":2,"WEIGHTS_0":3},"indices":4}]}],
            "skins":[{"joints":[1,2],"inverseBindMatrices":5}],
            "animations":[{"name":"Bend","channels":[{"sampler":0,"target":{"node":2,"path":"rotation"}}],
                "samplers":[{"input":6,"output":7,"interpolation":"LINEAR"}]}],
            "accessors":[
                {"bufferView":$pos,"componentType":5126,"count":4,"type":"VEC3"},
                {"bufferView":$nrm,"componentType":5126,"count":4,"type":"VEC3"},
                {"bufferView":$jnt,"componentType":5121,"count":4,"type":"VEC4"},
                {"bufferView":$wgt,"componentType":5126,"count":4,"type":"VEC4"},
                {"bufferView":$idx,"componentType":5123,"count":6,"type":"SCALAR"},
                {"bufferView":$ibm,"componentType":5126,"count":2,"type":"MAT4"},
                {"bufferView":$times,"componentType":5126,"count":2,"type":"SCALAR"},
                {"bufferView":$rots,"componentType":5126,"count":2,"type":"VEC4"}],
            "bufferViews":[$views],
            "buffers":[{"byteLength":${bin.size}}]}"""
        return glb(json, bin.toBytes())
    }

    private fun glb(json: String, bin: ByteArray): ByteArray {
        var j = json.encodeToByteArray()
        while (j.size % 4 != 0) j += ' '.code.toByte()
        val out = ArrayList<Byte>()
        fun le(v: Int) { for (k in 0 until 4) out.add((v shr (8 * k)).toByte()) }
        le(0x46546C67); le(2); le(12 + 8 + j.size + 8 + bin.size)
        le(j.size); le(0x4E4F534A); j.forEach { out.add(it) }
        le(bin.size); le(0x004E4942); bin.forEach { out.add(it) }
        return out.toByteArray()
    }

    private class Bin {
        private val data = ArrayList<Byte>()
        val views = ArrayList<Pair<Int, Int>>()
        val size get() = data.size

        private fun view(bytes: List<Byte>): Int {
            while (data.size % 4 != 0) data.add(0)
            views.add(data.size to bytes.size)
            data.addAll(bytes)
            return views.size - 1
        }

        fun floats(vararg v: Float) = view(v.flatMap { f -> f.toBits().let { b -> List(4) { (b shr (8 * it)).toByte() } } })
        fun shorts(vararg v: Int) = view(v.flatMap { s -> List(2) { (s shr (8 * it)).toByte() } })
        fun bytes(vararg v: Int) = view(v.map { it.toByte() })
        fun toBytes(): ByteArray {
            while (data.size % 4 != 0) data.add(0)
            return data.toByteArray()
        }
    }
}
