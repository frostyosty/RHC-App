package com.rockhard.blocker.figure

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A blocky stand-in person built in code, 1.75 m tall and facing +Z, with
 * three moves: "Idle", "Walk" and "Wave". It lets the AR test screen and the
 * tests run before any real figure has been imported.
 */
object Mannequin {
    private val names = listOf("Root", "Hips", "Spine", "Head", "UpperArm.L", "UpperArm.R", "UpperLeg.L", "UpperLeg.R")
    private val parents = intArrayOf(-1, 0, 1, 2, 2, 2, 1, 1)
    private val offsets = floatArrayOf(
        0f, 0f, 0f,
        0f, 0.95f, 0f,
        0f, 0.1f, 0f,
        0f, 0.47f, 0f,
        0.22f, 0.45f, 0f,
        -0.22f, 0.45f, 0f,
        0.1f, 0f, 0f,
        -0.1f, 0f, 0f,
    )

    fun skeleton() = Skeleton(
        names, parents, offsets.copyOf(),
        FloatArray(names.size * 4) { if (it % 4 == 3) 1f else 0f },
        FloatArray(names.size * 3) { 1f },
    )

    fun figure(): Figure {
        val skeleton = skeleton()
        val pose = Pose(skeleton).also { it.update() }
        // joints are nodes 1..7; at rest they only translate, so the inverse bind is the opposite translation
        val joints = IntArray(7) { it + 1 }
        val ibm = FloatArray(joints.size * 16)
        for (j in joints.indices) {
            val m = Mat4.identity()
            for (k in 0 until 3) m[12 + k] = -pose.global[joints[j] * 16 + 12 + k]
            m.copyInto(ibm, j * 16)
        }
        val clothes = Boxes()
        clothes.box(0, -0.15f, 0.85f, -0.1f, 0.15f, 1.05f, 0.1f) // pelvis, on Hips
        clothes.box(1, -0.18f, 1.05f, -0.11f, 0.18f, 1.5f, 0.11f) // torso, on Spine
        clothes.box(3, 0.19f, 0.95f, -0.05f, 0.26f, 1.48f, 0.05f) // left arm
        clothes.box(4, -0.26f, 0.95f, -0.05f, -0.19f, 1.48f, 0.05f) // right arm
        clothes.box(5, 0.03f, 0f, -0.06f, 0.16f, 0.88f, 0.08f) // left leg
        clothes.box(6, -0.16f, 0f, -0.06f, -0.03f, 0.88f, 0.08f) // right leg
        val skin = Boxes()
        skin.box(2, -0.1f, 1.52f, -0.1f, 0.1f, 1.75f, 0.12f) // head
        skin.box(2, -0.03f, 1.6f, 0.12f, 0.03f, 1.65f, 0.15f) // nose, so you can see which way it faces
        return Figure(
            skeleton, listOf(Skin(joints, ibm)),
            listOf(clothes.part("clothes", floatArrayOf(0.35f, 0.45f, 0.7f, 1f)), skin.part("skin", floatArrayOf(0.93f, 0.76f, 0.62f, 1f))),
            null,
        )
    }

    /** The mannequin's moves. They play on any skeleton with its bone names. */
    fun clips(): List<Clip> {
        val skel = skeleton()
        fun rot(bone: String, axis: Int, times: FloatArray, degrees: FloatArray): Clip.Track {
            val v = FloatArray(times.size * 4)
            for (k in times.indices) {
                val a = degrees[k] * PI.toFloat() / 360f
                v[k * 4 + axis] = sin(a)
                v[k * 4 + 3] = cos(a)
            }
            val i = skel.indexOf(bone)
            return Clip.Track(bone, Clip.Path.ROTATION, times, v, false, skel.restT.copyOfRange(i * 3, i * 3 + 3), skel.depth(i))
        }
        val t3 = floatArrayOf(0f, 1f, 2f)
        val idle = Clip("Idle", 2f, listOf(rot("Spine", 2, t3, floatArrayOf(-2f, 2f, -2f)), rot("Head", 1, t3, floatArrayOf(-8f, 8f, -8f))))
        val w = floatArrayOf(0f, 0.5f, 1f)
        val walk = Clip(
            "Walk", 1f,
            listOf(
                rot("UpperLeg.L", 0, w, floatArrayOf(25f, -25f, 25f)),
                rot("UpperLeg.R", 0, w, floatArrayOf(-25f, 25f, -25f)),
                rot("UpperArm.L", 0, w, floatArrayOf(-20f, 20f, -20f)),
                rot("UpperArm.R", 0, w, floatArrayOf(20f, -20f, 20f)),
            ),
        )
        val wave = Clip("Wave", 1.2f, listOf(rot("UpperArm.R", 2, floatArrayOf(0f, 0.3f, 0.6f, 0.9f, 1.2f), floatArrayOf(-140f, -165f, -140f, -165f, -140f))))
        return listOf(idle, walk, wave)
    }

    /** Boxes weighted wholly to one joint each, gathered into one part. */
    private class Boxes {
        val pos = ArrayList<Float>()
        val nrm = ArrayList<Float>()
        val jnt = ArrayList<Int>()
        val idx = ArrayList<Int>()

        fun box(joint: Int, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) {
            // each face: normal, then its four corners counter-clockwise seen from outside
            val faces = arrayOf(
                floatArrayOf(1f, 0f, 0f, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1),
                floatArrayOf(-1f, 0f, 0f, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0),
                floatArrayOf(0f, 1f, 0f, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0),
                floatArrayOf(0f, -1f, 0f, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1),
                floatArrayOf(0f, 0f, 1f, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1),
                floatArrayOf(0f, 0f, -1f, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0),
            )
            for (f in faces) {
                val base = pos.size / 3
                for (c in 0 until 4) {
                    for (k in 0 until 3) { pos.add(f[3 + c * 3 + k]); nrm.add(f[k]) }
                    jnt.add(joint); jnt.add(0); jnt.add(0); jnt.add(0)
                }
                idx.addAll(listOf(base, base + 1, base + 2, base, base + 2, base + 3))
            }
        }

        fun part(name: String, color: FloatArray): Part {
            val n = pos.size / 3
            return Part(
                name, pos.toFloatArray(), nrm.toFloatArray(), null,
                jnt.toIntArray(), FloatArray(n * 4) { if (it % 4 == 0) 1f else 0f },
                idx.toIntArray(), skin = 0, node = 0, color = color, image = -1, material = name,
            )
        }
    }
}
