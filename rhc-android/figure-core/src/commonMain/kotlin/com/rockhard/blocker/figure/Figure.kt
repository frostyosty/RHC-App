package com.rockhard.blocker.figure

/**
 * Every node of a glTF scene with its rest transform. Bones are nodes, so this
 * is the rig; `order` lists parents before their children.
 */
class Skeleton(
    val names: List<String>,
    val parent: IntArray,
    val restT: FloatArray,
    val restR: FloatArray,
    val restS: FloatArray,
) {
    val size get() = names.size
    val order: IntArray = run {
        val children = Array(size) { ArrayList<Int>() }
        for (i in 0 until size) if (parent[i] >= 0) children[parent[i]].add(i)
        val out = ArrayList<Int>(size)
        fun visit(i: Int) { out.add(i); children[i].forEach(::visit) }
        for (i in 0 until size) if (parent[i] < 0) visit(i)
        out.toIntArray()
    }

    fun indexOf(name: String) = names.indexOf(name)

    /** How many parents a node has. */
    fun depth(i: Int): Int {
        var d = 0
        var p = parent[i]
        while (p >= 0) { d++; p = parent[p] }
        return d
    }

    companion object {
        internal fun from(gltf: Gltf): Skeleton {
            val n = gltf.nodes.size
            val parent = IntArray(n) { -1 }
            gltf.nodes.forEachIndexed { i, node -> node["children"].list().forEach { parent[it.int()] = i } }
            val t = FloatArray(n * 3)
            val r = FloatArray(n * 4)
            val s = FloatArray(n * 3)
            val tq = FloatArray(4)
            gltf.nodes.forEachIndexed { i, node ->
                val m = node["matrix"].floats()
                if (m != null) {
                    val tt = FloatArray(3); val ss = FloatArray(3)
                    Mat4.decompose(m, tt, tq, ss)
                    tt.copyInto(t, i * 3); tq.copyInto(r, i * 4); ss.copyInto(s, i * 3)
                } else {
                    (node["translation"].floats() ?: floatArrayOf(0f, 0f, 0f)).copyInto(t, i * 3)
                    (node["rotation"].floats() ?: floatArrayOf(0f, 0f, 0f, 1f)).copyInto(r, i * 4)
                    (node["scale"].floats() ?: floatArrayOf(1f, 1f, 1f)).copyInto(s, i * 3)
                }
            }
            val names = gltf.nodes.mapIndexed { i, node -> node["name"] as? String ?: "node$i" }
            return Skeleton(names, parent, t, r, s)
        }
    }
}

/** A skin: the nodes that are its joints and their inverse bind matrices (16 floats each). */
class Skin(val joints: IntArray, val inverseBind: FloatArray)

/**
 * One drawable piece of a figure (a body, a hair mesh, a shirt) with one
 * material. Joints are indices into `skin`'s joint list, four per vertex.
 * An unskinned piece (skin -1) moves with its `node`.
 */
class Part(
    val name: String,
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray?,
    val joints: IntArray?,
    val weights: FloatArray?,
    val indices: IntArray,
    val skin: Int,
    val node: Int,
    /** Base colour, RGBA 0..1, multiplied with the texture when there is one. */
    val color: FloatArray,
    /** The glTF image the base colour texture comes from, or -1. */
    val image: Int,
    val material: String,
) {
    val vertexCount get() = positions.size / 3
}

/** A rigged person (or anything else rigged), from one .glb file or built in code ([Mannequin]). */
class Figure(val skeleton: Skeleton, val skins: List<Skin>, val parts: List<Part>, val gltf: Gltf?) {

    /** The lowest and highest point in the rest pose: [minX, minY, minZ, maxX, maxY, maxZ]. */
    val restBounds: FloatArray by lazy {
        val pose = Pose(skeleton)
        pose.update()
        bounds(pose)
    }

    /** The bounds of the figure in a pose, by skinning every vertex. */
    fun bounds(pose: Pose): FloatArray {
        val b = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        val p = FloatArray(3)
        for (part in parts) {
            val skinned = Skinner(this, pose, part)
            for (v in 0 until part.vertexCount) {
                skinned.position(v, p)
                for (k in 0 until 3) {
                    if (p[k] < b[k]) b[k] = p[k]
                    if (p[k] > b[k + 3]) b[k + 3] = p[k]
                }
            }
        }
        return b
    }

    companion object {
        /** Reads a figure from a .glb file's bytes. Its moves, if any, come from [Clip.from]. */
        fun load(bytes: ByteArray): Figure = from(Gltf.parse(bytes))

        fun from(gltf: Gltf): Figure {
            val skeleton = Skeleton.from(gltf)
            val skins = gltf.skins.map { s ->
                val joints = s["joints"].list().map { it.int() }.toIntArray()
                val ibmAcc = s["inverseBindMatrices"].int()
                val ibm = if (ibmAcc >= 0) gltf.floats(ibmAcc) else FloatArray(joints.size * 16).also {
                    for (j in joints.indices) Mat4.identity().copyInto(it, j * 16)
                }
                Skin(joints, ibm)
            }
            val parts = ArrayList<Part>()
            gltf.nodes.forEachIndexed { nodeIndex, node ->
                val mesh = gltf.meshes.getOrNull(node["mesh"].int()) ?: return@forEachIndexed
                val skin = node["skin"].int()
                mesh["primitives"].list().forEachIndexed { pi, primAny ->
                    val prim = primAny.obj()
                    if (prim["mode"].int(4) != 4) return@forEachIndexed // triangles only
                    val attrs = prim["attributes"].obj()
                    val pos = gltf.floats(attrs["POSITION"].int())
                    val count = pos.size / 3
                    val normals = attrs["NORMAL"].int().let { if (it >= 0) gltf.floats(it) else FloatArray(count * 3) }
                    val uvs = attrs["TEXCOORD_0"].int().let { if (it >= 0) gltf.floats(it) else null }
                    val joints = if (skin >= 0) attrs["JOINTS_0"].int().let { if (it >= 0) gltf.ints(it) else null } else null
                    val weights = if (joints != null) attrs["WEIGHTS_0"].int().let { if (it >= 0) gltf.floats(it) else null } else null
                    val indices = prim["indices"].int().let { if (it >= 0) gltf.ints(it) else IntArray(count) { i -> i } }
                    val mat = gltf.materials.getOrNull(prim["material"].int()).obj()
                    val pbr = mat["pbrMetallicRoughness"].obj()
                    val color = pbr["baseColorFactor"].floats() ?: floatArrayOf(1f, 1f, 1f, 1f)
                    val tex = pbr["baseColorTexture"].obj()["index"].int()
                    val image = if (tex >= 0) gltf.textureImage(tex) else -1
                    parts.add(
                        Part(
                            name = (mesh["name"] as? String ?: "mesh") + if (pi > 0) ".$pi" else "",
                            positions = pos, normals = normals, uvs = uvs,
                            joints = joints, weights = if (joints != null) weights else null,
                            indices = indices,
                            skin = if (joints != null && weights != null) skin else -1,
                            node = nodeIndex, color = color, image = image,
                            material = mat["name"] as? String ?: "",
                        )
                    )
                }
            }
            return Figure(skeleton, skins, parts, gltf)
        }
    }
}

/** Where a part's vertices are in a pose, worked out on the CPU (for bounds and the preview tool). */
class Skinner(private val figure: Figure, private val pose: Pose, private val part: Part) {
    private val jointMats: FloatArray? = if (part.skin >= 0) pose.jointMatrices(figure.skins[part.skin]) else null
    private val tmp = FloatArray(3)

    private fun blend(v: Int, x: Float, y: Float, z: Float, w: Float, out: FloatArray) {
        val mats = jointMats
        if (mats == null) {
            val g = pose.global
            val o = part.node * 16
            val sx = g[o] * x + g[o + 4] * y + g[o + 8] * z + g[o + 12] * w
            val sy = g[o + 1] * x + g[o + 5] * y + g[o + 9] * z + g[o + 13] * w
            val sz = g[o + 2] * x + g[o + 6] * y + g[o + 10] * z + g[o + 14] * w
            out[0] = sx; out[1] = sy; out[2] = sz
            return
        }
        var sx = 0f; var sy = 0f; var sz = 0f
        val joints = part.joints!!
        val weights = part.weights!!
        for (k in 0 until 4) {
            val wt = weights[v * 4 + k]
            if (wt == 0f) continue
            val o = joints[v * 4 + k] * 16
            sx += wt * (mats[o] * x + mats[o + 4] * y + mats[o + 8] * z + mats[o + 12] * w)
            sy += wt * (mats[o + 1] * x + mats[o + 5] * y + mats[o + 9] * z + mats[o + 13] * w)
            sz += wt * (mats[o + 2] * x + mats[o + 6] * y + mats[o + 10] * z + mats[o + 14] * w)
        }
        out[0] = sx; out[1] = sy; out[2] = sz
    }

    fun position(v: Int, out: FloatArray) =
        blend(v, part.positions[v * 3], part.positions[v * 3 + 1], part.positions[v * 3 + 2], 1f, out)

    /** The normal, not renormalised. */
    fun normal(v: Int, out: FloatArray) {
        blend(v, part.normals[v * 3], part.normals[v * 3 + 1], part.normals[v * 3 + 2], 0f, tmp)
        tmp.copyInto(out)
    }
}
