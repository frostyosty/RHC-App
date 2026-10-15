// Renders rigged figures (figure-core) to PNG sheets without a phone: for each
// move, 8 views (every 45°, front first) across, 4 moments of the move down.
// Same lighting as the AR kit's FigureRenderer, flat-shaded per pixel.
// Usage (through run.sh): FigurePreviewKt <out_dir> [figure.glb [moves.glb]]
import com.rockhard.blocker.figure.Clip
import com.rockhard.blocker.figure.Figure
import com.rockhard.blocker.figure.Gltf
import com.rockhard.blocker.figure.Mannequin
import com.rockhard.blocker.figure.Pose
import com.rockhard.blocker.figure.Skinner
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

const val CELL_W = 160
const val CELL_H = 220
const val VIEWS = 8
const val MOMENTS = 4

fun main(args: Array<String>) {
    val out = File(args.getOrElse(0) { "out" }).apply { mkdirs() }
    val (figure, clips, label) = if (args.size > 1) {
        val gltf = Gltf.parse(File(args[1]).readBytes())
        val moves = if (args.size > 2) Clip.from(Gltf.parse(File(args[2]).readBytes())) else emptyList()
        Triple(Figure.from(gltf), Clip.from(gltf) + moves, File(args[1]).nameWithoutExtension)
    } else {
        Triple(Mannequin.figure(), Mannequin.clips(), "mannequin")
    }
    val b = figure.restBounds
    println("🧍 $label: ${figure.parts.size} parts, ${figure.parts.sumOf { it.indices.size / 3 }} triangles, " +
        "${figure.skins.firstOrNull()?.joints?.size ?: 0} joints, ${"%.2f".format(b[4] - b[1])} tall at rest")
    if (figure.skins.any { it.joints.size > 80 }) println("⚠️  more than 80 joints in a skin: the AR kit's shader won't draw them all")
    val textures = HashMap<Int, BufferedImage?>()
    fun texture(image: Int) = textures.getOrPut(image) {
        figure.gltf?.image(image)?.first?.let { ImageIO.read(ByteArrayInputStream(it)) }
    }

    val rest = Clip("rest", 0f, emptyList())
    for (clip in listOf(rest) + clips) {
        val bound = clip.bind(figure.skeleton, inPlace = true)
        val img = BufferedImage(CELL_W * VIEWS, CELL_H * MOMENTS, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = java.awt.Color(0x2B2B33)
        g.fillRect(0, 0, img.width, img.height)
        g.dispose()
        val pose = Pose(figure.skeleton)
        for (m in 0 until MOMENTS) {
            pose.reset()
            bound.apply(pose, clip.duration * m / MOMENTS)
            pose.update()
            for (v in 0 until VIEWS) draw(figure, pose, v * 45.0, img, v * CELL_W, m * CELL_H, b, ::texture)
        }
        val file = File(out, "${label}_${clip.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}.png")
        ImageIO.write(img, "png", file)
        println("🖼️  ${file.name} (${"%.2f".format(clip.duration)} s)")
    }
}

/** Draws the posed figure turned by `degrees` (0 = facing us) into one cell, scaled so the rest height fills 90% of it. */
fun draw(fig: Figure, pose: Pose, degrees: Double, img: BufferedImage, ox: Int, oy: Int, rest: FloatArray, texture: (Int) -> BufferedImage?) {
    val zbuf = FloatArray(CELL_W * CELL_H) { -Float.MAX_VALUE }
    val a = Math.toRadians(degrees)
    val ca = cos(a).toFloat()
    val sa = sin(a).toFloat()
    val scale = CELL_H * 0.9f / (rest[4] - rest[1])
    val cx = (rest[0] + rest[3]) / 2
    val cz = (rest[2] + rest[5]) / 2
    val p = FloatArray(3)
    val n = FloatArray(3)
    // light from above and in front of the viewer, as in the AR kit
    val l = floatArrayOf(0.3f, 1f, 0.5f).let { v -> val k = 1 / sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); FloatArray(3) { v[it] * k } }
    for (part in fig.parts) {
        val sk = Skinner(fig, pose, part)
        val vc = part.vertexCount
        val sx = FloatArray(vc)
        val sy = FloatArray(vc)
        val sz = FloatArray(vc)
        val shade = FloatArray(vc)
        for (v in 0 until vc) {
            sk.position(v, p)
            // turn about the vertical through the figure's middle; +Z (its front) towards the viewer at 0°
            val x = p[0] - cx
            val z = p[2] - cz
            val rx = x * ca + z * sa
            val rz = -x * sa + z * ca
            sx[v] = CELL_W / 2f + rx * scale
            sy[v] = CELL_H * 0.95f - (p[1] - rest[1]) * scale
            sz[v] = rz
            sk.normal(v, n)
            val nx = n[0] * ca + n[2] * sa
            val nz = -n[0] * sa + n[2] * ca
            val len = sqrt(nx * nx + n[1] * n[1] + nz * nz).coerceAtLeast(1e-6f)
            val d = (nx * l[0] + n[1] * l[1] + nz * l[2]) / len * 0.5f + 0.5f
            shade[v] = 0.35f + 0.75f * d * d
        }
        val tex = if (part.image >= 0) texture(part.image) else null
        val uvs = part.uvs
        val idx = part.indices
        for (t in 0 until idx.size / 3) {
            val i0 = idx[t * 3]; val i1 = idx[t * 3 + 1]; val i2 = idx[t * 3 + 2]
            val minX = maxOf(0, minOf(sx[i0], sx[i1], sx[i2]).toInt())
            val maxX = minOf(CELL_W - 1, maxOf(sx[i0], sx[i1], sx[i2]).toInt() + 1)
            val minY = maxOf(0, minOf(sy[i0], sy[i1], sy[i2]).toInt())
            val maxY = minOf(CELL_H - 1, maxOf(sy[i0], sy[i1], sy[i2]).toInt() + 1)
            val area = (sx[i1] - sx[i0]) * (sy[i2] - sy[i0]) - (sx[i2] - sx[i0]) * (sy[i1] - sy[i0])
            if (area == 0f) continue
            for (py in minY..maxY) for (px in minX..maxX) {
                val fx = px + 0.5f
                val fy = py + 0.5f
                val w0 = ((sx[i1] - fx) * (sy[i2] - fy) - (sx[i2] - fx) * (sy[i1] - fy)) / area
                val w1 = ((sx[i2] - fx) * (sy[i0] - fy) - (sx[i0] - fx) * (sy[i2] - fy)) / area
                val w2 = 1 - w0 - w1
                if (w0 < 0 || w1 < 0 || w2 < 0) continue
                val z = w0 * sz[i0] + w1 * sz[i1] + w2 * sz[i2]
                val zi = py * CELL_W + px
                if (z <= zbuf[zi]) continue
                var r = part.color[0]; var gg = part.color[1]; var bb = part.color[2]
                if (tex != null && uvs != null) {
                    val u = w0 * uvs[i0 * 2] + w1 * uvs[i1 * 2] + w2 * uvs[i2 * 2]
                    val vv = w0 * uvs[i0 * 2 + 1] + w1 * uvs[i1 * 2 + 1] + w2 * uvs[i2 * 2 + 1]
                    val tx = Math.floorMod((u * tex.width).toInt(), tex.width)
                    val ty = Math.floorMod((vv * tex.height).toInt(), tex.height)
                    val argb = tex.getRGB(tx, ty)
                    if ((argb ushr 24) < 128) continue
                    r *= ((argb shr 16) and 0xFF) / 255f; gg *= ((argb shr 8) and 0xFF) / 255f; bb *= (argb and 0xFF) / 255f
                }
                zbuf[zi] = z
                val s = w0 * shade[i0] + w1 * shade[i1] + w2 * shade[i2]
                fun c(x: Float) = (x * s * 255f).toInt().coerceIn(0, 255)
                img.setRGB(ox + px, oy + py, (c(r) shl 16) or (c(gg) shl 8) or c(bb))
            }
        }
    }
}
