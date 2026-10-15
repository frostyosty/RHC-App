package com.rockhard.blocker.figure

/**
 * A binary glTF 2.0 file (.glb): the JSON header and the one binary chunk, and
 * a reader for its accessors. Only what rigged people need: no sparse
 * accessors, no Draco or meshopt compression, no external files.
 */
class Gltf private constructor(internal val json: Map<String, Any?>, internal val bin: ByteArray) {

    internal val nodes = json["nodes"].list().map { it.obj() }
    internal val meshes = json["meshes"].list().map { it.obj() }
    internal val skins = json["skins"].list().map { it.obj() }
    internal val animations = json["animations"].list().map { it.obj() }
    internal val materials = json["materials"].list().map { it.obj() }
    private val accessors = json["accessors"].list().map { it.obj() }
    private val bufferViews = json["bufferViews"].list().map { it.obj() }

    /** The bytes of an embedded image (PNG or JPEG), for the platform to decode, with its MIME type. */
    fun image(index: Int): Pair<ByteArray, String>? {
        val img = json["images"].list().getOrNull(index).obj()
        val view = bufferViews.getOrNull(img["bufferView"].int()) ?: return null
        val off = view["byteOffset"].int(0)
        return bin.copyOfRange(off, off + view["byteLength"].int()) to (img["mimeType"] as? String ?: "image/png")
    }

    /** Which image a texture uses. */
    internal fun textureImage(texture: Int): Int =
        json["textures"].list().getOrNull(texture).obj()["source"].int()

    internal fun count(accessor: Int) = accessors[accessor]["count"].int()

    /** Every element of an accessor as floats, `size` per element, normalized integers scaled to 0..1. */
    internal fun floats(accessor: Int): FloatArray {
        val a = accessors[accessor]
        if (a["sparse"] != null) throw IllegalArgumentException("glTF: sparse accessors aren't supported")
        val n = a["count"].int()
        val size = sizeOf(a["type"] as String)
        val type = a["componentType"].int()
        val normalized = a["normalized"] == true
        val out = FloatArray(n * size)
        val view = bufferViews.getOrNull(a["bufferView"].int()) ?: return out
        val elem = bytesOf(type)
        val stride = view["byteStride"].int(0).takeIf { it > 0 } ?: (elem * size)
        val base = view["byteOffset"].int(0) + a["byteOffset"].int(0)
        for (e in 0 until n) {
            for (c in 0 until size) {
                val at = base + e * stride + c * elem
                out[e * size + c] = when (type) {
                    FLOAT -> Float.fromBits(i32(at))
                    UBYTE -> (bin[at].toInt() and 0xFF).let { if (normalized) it / 255f else it.toFloat() }
                    BYTE -> bin[at].toInt().let { if (normalized) maxOf(it / 127f, -1f) else it.toFloat() }
                    USHORT -> u16(at).let { if (normalized) it / 65535f else it.toFloat() }
                    SHORT -> u16(at).toShort().toInt().let { if (normalized) maxOf(it / 32767f, -1f) else it.toFloat() }
                    UINT -> i32(at).toLong().and(0xFFFFFFFFL).toFloat()
                    else -> throw IllegalArgumentException("glTF: component type $type")
                }
            }
        }
        return out
    }

    /** Every element of an integer accessor (indices, joints) as ints. */
    internal fun ints(accessor: Int): IntArray {
        val a = accessors[accessor]
        val n = a["count"].int()
        val size = sizeOf(a["type"] as String)
        val type = a["componentType"].int()
        val out = IntArray(n * size)
        val view = bufferViews.getOrNull(a["bufferView"].int()) ?: return out
        val elem = bytesOf(type)
        val stride = view["byteStride"].int(0).takeIf { it > 0 } ?: (elem * size)
        val base = view["byteOffset"].int(0) + a["byteOffset"].int(0)
        for (e in 0 until n) {
            for (c in 0 until size) {
                val at = base + e * stride + c * elem
                out[e * size + c] = when (type) {
                    UBYTE -> bin[at].toInt() and 0xFF
                    USHORT -> u16(at)
                    UINT -> i32(at)
                    else -> throw IllegalArgumentException("glTF: component type $type for integers")
                }
            }
        }
        return out
    }

    private fun u16(at: Int) = (bin[at].toInt() and 0xFF) or ((bin[at + 1].toInt() and 0xFF) shl 8)

    private fun i32(at: Int) = u16(at) or (u16(at + 2) shl 16)

    companion object {
        private const val BYTE = 5120
        private const val UBYTE = 5121
        private const val SHORT = 5122
        private const val USHORT = 5123
        private const val UINT = 5125
        private const val FLOAT = 5126

        private fun bytesOf(type: Int) = when (type) {
            BYTE, UBYTE -> 1
            SHORT, USHORT -> 2
            else -> 4
        }

        private fun sizeOf(type: String) = when (type) {
            "SCALAR" -> 1
            "VEC2" -> 2
            "VEC3" -> 3
            "VEC4" -> 4
            "MAT4" -> 16
            else -> throw IllegalArgumentException("glTF: accessor type $type")
        }

        /** Reads a .glb file. */
        fun parse(bytes: ByteArray): Gltf {
            fun le(at: Int) = (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8) or
                ((bytes[at + 2].toInt() and 0xFF) shl 16) or ((bytes[at + 3].toInt() and 0xFF) shl 24)
            if (bytes.size < 20 || le(0) != 0x46546C67) throw IllegalArgumentException("glTF: not a .glb file")
            if (le(4) != 2) throw IllegalArgumentException("glTF: version ${le(4)}, only 2 is supported")
            var at = 12
            var json: Map<String, Any?>? = null
            var bin = ByteArray(0)
            while (at + 8 <= bytes.size) {
                val len = le(at)
                val type = le(at + 4)
                val start = at + 8
                when (type) {
                    0x4E4F534A -> json = Json.parse(bytes.decodeToString(start, start + len)).obj()
                    0x004E4942 -> bin = bytes.copyOfRange(start, start + len)
                }
                at = start + len
            }
            return Gltf(json ?: throw IllegalArgumentException("glTF: no JSON chunk"), bin)
        }
    }
}
