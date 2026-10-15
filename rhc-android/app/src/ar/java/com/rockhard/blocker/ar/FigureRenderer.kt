package com.rockhard.blocker.ar

import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.TrackingState
import com.rockhard.blocker.figure.Clip
import com.rockhard.blocker.figure.Figure
import com.rockhard.blocker.figure.Pose
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * A rigged figure standing in the room: where (an anchor), which way it faces,
 * how tall it is, and the move it's doing. Made with [ArRenderer.place]; change
 * it only from tasks queued with [ArRenderer.queue], which run on the GL thread.
 */
class PlacedFigure internal constructor(
    val figure: Figure,
    val anchor: Anchor,
    /** Turn about the vertical, radians; 0 faces the session's +Z. */
    var yaw: Float,
    /** Metres from the feet to the top of the head. */
    var height: Float,
) {
    internal val pose = Pose(figure.skeleton)
    private var move: Clip.Bound? = null
    private var loop = true
    private var startNs = 0L
    private var last: Clip.Bound? = null
    private var lastAt = 0f
    private var fadeEndNs = 0L

    val moveName: String? get() = move?.clip?.name

    /**
     * Starts a move, eased in over `fadeS` seconds from the one before. A
     * looping move plays on the spot (`Clip.bind`'s inPlace); one that doesn't
     * loop holds its last frame.
     */
    fun play(clip: Clip, loop: Boolean = true, fadeS: Float = 0.25f) {
        val now = System.nanoTime()
        last = move
        lastAt = move?.let { timeIn(it, now) } ?: 0f
        move = clip.bind(figure.skeleton, inPlace = loop)
        this.loop = loop
        startNs = now
        fadeEndNs = now + (fadeS * 1e9f).toLong()
    }

    private fun timeIn(m: Clip.Bound, now: Long): Float {
        val t = (now - startNs) / 1e9f
        val d = m.clip.duration
        return if (d <= 0f) 0f else if (loop) t % d else minOf(t, d)
    }

    /** Poses the figure for this frame. */
    internal fun animate(now: Long) {
        val m = move
        pose.reset()
        val lastMove = last
        if (lastMove != null && now < fadeEndNs) {
            lastMove.apply(pose, lastAt)
            val span = (fadeEndNs - startNs).coerceAtLeast(1L)
            m?.apply(pose, timeIn(m, now), (now - startNs).toFloat() / span)
        } else {
            last = null
            m?.apply(pose, timeIn(m, now))
        }
        pose.update()
    }
}

/**
 * Draws rigged figures (figure-core) over the camera: skinned on the GPU, lit
 * from above and tinted by ARCore's light estimate, with a soft round shadow
 * at their feet. GLES 3, which every ARCore phone has.
 */
internal class FigureRenderer {
    private var program = 0
    private var shadowProgram = 0
    private var white = 0
    private val gpu = HashMap<Figure, List<GpuPart>>()
    private val jointRows = FloatArray(MAX_JOINTS * 12)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)
    private val tmp = FloatArray(16)
    private val shadowQuad: FloatBuffer = floats(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)

    private class GpuPart(val vao: Int, val count: Int, val texture: Int, val color: FloatArray, val skinned: Boolean)

    fun createOnGlThread() {
        program = link(VERTEX, FRAGMENT)
        shadowProgram = link(SHADOW_VERTEX, SHADOW_FRAGMENT)
        white = texture(byteArrayOf(-1, -1, -1, -1), 1, 1)
        gpu.clear() // a new GL context has none of the old buffers
    }

    /** Draws every figure whose anchor is tracking. `light` is ARCore's colour correction (RGB scale, then intensity). */
    fun draw(figures: List<PlacedFigure>, view: FloatArray, proj: FloatArray, light: FloatArray) {
        lightUniform = light
        val now = System.nanoTime()
        val vp = FloatArray(16).also { Matrix.multiplyMM(it, 0, proj, 0, view, 0) }
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        for (f in figures) {
            if (f.anchor.trackingState != TrackingState.TRACKING) continue
            f.animate(now)
            modelMatrix(f)
            Matrix.multiplyMM(mvp, 0, vp, 0, model, 0)
            drawFigure(f)
        }
        // shadows last, blended over the floor, without writing depth
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glUseProgram(shadowProgram)
        for (f in figures) {
            if (f.anchor.trackingState != TrackingState.TRACKING) continue
            f.anchor.pose.toMatrix(tmp, 0)
            // a disc a little wider than the shoulders, 3 mm above the floor so it doesn't flicker into it
            Matrix.translateM(tmp, 0, 0f, 0.003f, 0f)
            val r = f.height * 0.17f
            Matrix.scaleM(tmp, 0, r, 1f, r)
            Matrix.multiplyMM(mvp, 0, vp, 0, tmp, 0)
            GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(shadowProgram, "u_Mvp"), 1, false, mvp, 0)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, shadowQuad)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glDisableVertexAttribArray(0)
        }
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private var lightUniform = floatArrayOf(1f, 1f, 1f, 0.466f)

    /** Anchor × turn × scale to the asked height × feet on the floor, centred. */
    private fun modelMatrix(f: PlacedFigure) {
        val b = f.figure.restBounds
        val scale = f.height / (b[4] - b[1]).coerceAtLeast(0.01f)
        f.anchor.pose.toMatrix(model, 0)
        Matrix.rotateM(model, 0, Math.toDegrees(f.yaw.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.scaleM(model, 0, scale, scale, scale)
        Matrix.translateM(model, 0, -(b[0] + b[3]) / 2f, -b[1], -(b[2] + b[5]) / 2f)
    }

    private fun drawFigure(f: PlacedFigure) {
        val parts = gpu.getOrPut(f.figure) { upload(f.figure) }
        GLES30.glUseProgram(program)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "u_Mvp"), 1, false, mvp, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(program, "u_Model"), 1, false, model, 0)
        GLES30.glUniform4fv(GLES30.glGetUniformLocation(program, "u_Light"), 1, lightUniform, 0)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "u_Texture"), 0)
        val jointsLoc = GLES30.glGetUniformLocation(program, "u_Joints")
        var lastSkin = -2
        f.figure.parts.forEachIndexed { i, part ->
            val g = parts[i]
            if (part.skin != lastSkin || !g.skinned) {
                val n = if (g.skinned) {
                    val skin = f.figure.skins[part.skin]
                    val m = f.pose.jointMatrices(skin)
                    for (j in 0 until minOf(skin.joints.size, MAX_JOINTS)) rows(m, j * 16, j * 12)
                    minOf(skin.joints.size, MAX_JOINTS)
                } else {
                    // an unskinned part is one "joint": the matrix of the node it hangs from
                    rows(f.pose.global, part.node * 16, 0)
                    1
                }
                GLES30.glUniform4fv(jointsLoc, n * 3, jointRows, 0)
                lastSkin = if (g.skinned) part.skin else -2
            }
            GLES30.glUniform4fv(GLES30.glGetUniformLocation(program, "u_Color"), 1, g.color, 0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, g.texture)
            GLES30.glBindVertexArray(g.vao)
            GLES30.glDrawElements(GLES30.GL_TRIANGLES, g.count, GLES30.GL_UNSIGNED_INT, 0)
        }
        GLES30.glBindVertexArray(0)
    }

    /** The top three rows of a column-major matrix, as the shader's three vec4s per joint. */
    private fun rows(m: FloatArray, mo: Int, out: Int) {
        for (r in 0 until 3) for (c in 0 until 4) jointRows[out + r * 4 + c] = m[mo + c * 4 + r]
    }

    private fun upload(fig: Figure): List<GpuPart> {
        val textures = HashMap<Int, Int>()
        return fig.parts.map { part ->
            val n = part.vertexCount
            val skinned = part.skin >= 0
            if (skinned && fig.skins[part.skin].joints.size > MAX_JOINTS) {
                Log.w(TAG, "${part.name}: ${fig.skins[part.skin].joints.size} joints, only $MAX_JOINTS are drawn")
            }
            val vao = IntArray(1).also { GLES30.glGenVertexArrays(1, it, 0) }[0]
            GLES30.glBindVertexArray(vao)
            attribute(0, 3, part.positions)
            attribute(1, 3, part.normals)
            attribute(2, 2, part.uvs ?: FloatArray(n * 2))
            attribute(3, 4, if (skinned) FloatArray(n * 4) { part.joints!![it].toFloat() } else FloatArray(n * 4))
            attribute(4, 4, if (skinned) part.weights!! else FloatArray(n * 4) { if (it % 4 == 0) 1f else 0f })
            val ibo = IntArray(1).also { GLES30.glGenBuffers(1, it, 0) }[0]
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibo)
            val ib = ByteBuffer.allocateDirect(part.indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().put(part.indices)
            ib.position(0)
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, part.indices.size * 4, ib, GLES30.GL_STATIC_DRAW)
            GLES30.glBindVertexArray(0)
            val tex = if (part.image < 0) white else textures.getOrPut(part.image) { imageTexture(fig, part.image) }
            GpuPart(vao, part.indices.size, tex, part.color, skinned)
        }
    }

    private fun attribute(loc: Int, size: Int, data: FloatArray) {
        val vbo = IntArray(1).also { GLES30.glGenBuffers(1, it, 0) }[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, floats(*data), GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(loc)
        GLES30.glVertexAttribPointer(loc, size, GLES30.GL_FLOAT, false, 0, 0)
    }

    private fun imageTexture(fig: Figure, image: Int): Int {
        val bytes = fig.gltf?.image(image)?.first ?: return white
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return white
        val id = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        bmp.recycle()
        return id
    }

    private fun texture(rgba: ByteArray, w: Int, h: Int): Int {
        val id = IntArray(1).also { GLES30.glGenTextures(1, it, 0) }[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        val buf = ByteBuffer.allocateDirect(rgba.size).put(rgba)
        buf.position(0)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        return id
    }

    private fun link(vs: String, fs: String): Int {
        fun shader(type: Int, src: String) = GLES30.glCreateShader(type).also {
            GLES30.glShaderSource(it, src)
            GLES30.glCompileShader(it)
            val ok = IntArray(1)
            GLES30.glGetShaderiv(it, GLES30.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) Log.e(TAG, "shader: " + GLES30.glGetShaderInfoLog(it))
        }
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, shader(GLES30.GL_VERTEX_SHADER, vs))
        GLES30.glAttachShader(p, shader(GLES30.GL_FRAGMENT_SHADER, fs))
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) Log.e(TAG, "link: " + GLES30.glGetProgramInfoLog(p))
        return p
    }

    companion object {
        private const val TAG = "FigureRenderer"

        /** Joints per skin the shader holds: 3 vec4 uniforms each, inside GLES 3's guaranteed 256. Quaternius' rig has 65. */
        const val MAX_JOINTS = 80

        private fun floats(vararg v: Float): FloatBuffer =
            ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(v).also { it.position(0) }

        private val VERTEX = """#version 300 es
layout(location = 0) in vec3 a_Pos;
layout(location = 1) in vec3 a_Normal;
layout(location = 2) in vec2 a_Uv;
layout(location = 3) in vec4 a_Joints;
layout(location = 4) in vec4 a_Weights;
uniform mat4 u_Mvp;
uniform mat4 u_Model;
uniform vec4 u_Joints[${MAX_JOINTS * 3}];
out vec3 v_Normal;
out vec2 v_Uv;
mat4 joint(float f) {
    int j = int(f + 0.5) * 3;
    vec4 a = u_Joints[j];
    vec4 b = u_Joints[j + 1];
    vec4 c = u_Joints[j + 2];
    return mat4(a.x, b.x, c.x, 0.0, a.y, b.y, c.y, 0.0, a.z, b.z, c.z, 0.0, a.w, b.w, c.w, 1.0);
}
void main() {
    mat4 skin = a_Weights.x * joint(a_Joints.x) + a_Weights.y * joint(a_Joints.y)
              + a_Weights.z * joint(a_Joints.z) + a_Weights.w * joint(a_Joints.w);
    gl_Position = u_Mvp * skin * vec4(a_Pos, 1.0);
    v_Normal = mat3(u_Model) * mat3(skin) * a_Normal;
    v_Uv = a_Uv;
}
"""

        // Half-Lambert from above and in front, so nothing goes black, scaled by the room's light
        private const val FRAGMENT = """#version 300 es
precision mediump float;
in vec3 v_Normal;
in vec2 v_Uv;
uniform sampler2D u_Texture;
uniform vec4 u_Color;
uniform vec4 u_Light;
out vec4 o_Color;
void main() {
    vec4 base = texture(u_Texture, v_Uv) * u_Color;
    if (base.a < 0.5) discard;
    vec3 n = normalize(v_Normal);
    float d = dot(n, normalize(vec3(0.3, 1.0, 0.5))) * 0.5 + 0.5;
    vec3 lit = base.rgb * (0.35 + 0.75 * d * d) * u_Light.rgb * (u_Light.a / 0.466);
    o_Color = vec4(lit, 1.0);
}
"""

        private const val SHADOW_VERTEX = """#version 300 es
layout(location = 0) in vec2 a_Xz;
uniform mat4 u_Mvp;
out vec2 v_Xz;
void main() {
    v_Xz = a_Xz;
    gl_Position = u_Mvp * vec4(a_Xz.x, 0.0, a_Xz.y, 1.0);
}
"""

        private const val SHADOW_FRAGMENT = """#version 300 es
precision mediump float;
in vec2 v_Xz;
out vec4 o_Color;
void main() {
    float a = 0.45 * (1.0 - smoothstep(0.3, 1.0, length(v_Xz)));
    o_Color = vec4(0.0, 0.0, 0.0, a);
}
"""
    }
}
