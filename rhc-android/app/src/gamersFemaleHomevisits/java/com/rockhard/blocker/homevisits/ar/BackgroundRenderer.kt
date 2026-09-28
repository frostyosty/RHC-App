package com.rockhard.blocker.homevisits.ar

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class BackgroundRenderer {
    private var quadCoords: FloatBuffer
    private var quadTexCoords: FloatBuffer
    private var program = 0
    private var positionAttrib = 0
    private var texCoordAttrib = 0
    private var textureUniform = 0
    var textureId = -1
        private set

    init {
        val coords = floatArrayOf(-1f, -1f, -1f, 1f, 1f, -1f, 1f, 1f)
        quadCoords = ByteBuffer.allocateDirect(coords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(coords)
        quadCoords.position(0)

        val texCoords = floatArrayOf(0f, 1f, 0f, 0f, 1f, 1f, 1f, 0f)
        quadTexCoords = ByteBuffer.allocateDirect(texCoords.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(texCoords)
        quadTexCoords.position(0)
    }

    fun createOnGlThread() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, "attribute vec4 a_Position; attribute vec2 a_TexCoord; varying vec2 v_TexCoord; void main() { gl_Position = a_Position; v_TexCoord = a_TexCoord; }")
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, "#extension GL_OES_EGL_image_external : require\nprecision mediump float; varying vec2 v_TexCoord; uniform samplerExternalOES sTexture; void main() { gl_FragColor = texture2D(sTexture, v_TexCoord); }")

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordAttrib = GLES20.glGetAttribLocation(program, "a_TexCoord")
        textureUniform = GLES20.glGetUniformLocation(program, "sTexture")
    }

    fun draw(frame: Frame) {
        if (!frame.hasDisplayGeometryChanged()) {
            // Need transformed coords based on display
            val transformedTexCoords = FloatArray(8)
            frame.transformDisplayUvCoords(quadTexCoords, FloatBuffer.wrap(transformedTexCoords))
            quadTexCoords.clear()
            quadTexCoords.put(transformedTexCoords)
            quadTexCoords.position(0)
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(textureUniform, 0)

        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glVertexAttribPointer(positionAttrib, 2, GLES20.GL_FLOAT, false, 0, quadCoords)

        GLES20.glEnableVertexAttribArray(texCoordAttrib)
        GLES20.glVertexAttribPointer(texCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(positionAttrib)
        GLES20.glDisableVertexAttribArray(texCoordAttrib)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        return shader
    }
}
