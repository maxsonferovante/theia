package app.theia.android.capture

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import app.theia.domain.catalog.BuiltInVideoStyles
import app.theia.domain.model.CaptureRecipe
import app.theia.domain.model.ResolvedVisualParameters
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

/** CameraX GPU effect shared by live preview and encoded video frames. */
class FilmPreviewEffect(
    recipe: CaptureRecipe,
    includeVideoTarget: Boolean,
    videoStyleId: String = BuiltInVideoStyles.EQUIPMENT_RECIPE_ID,
) : CameraEffect(
    PREVIEW or if (includeVideoTarget) VIDEO_CAPTURE else 0,
    TRANSFORMATION_CAMERA_AND_SURFACE_ROTATION,
    OUTPUT_OPTION_ONE_FOR_EACH_TARGET,
    Executor { it.run() },
    FilmSurfaceProcessor(requireNotNull(BuiltInVideoStyles.resolve(recipe, videoStyleId)) {
        "No video visual profile exists for this recipe and style"
    }),
    androidx.core.util.Consumer { },
) {
    private val processor = getSurfaceProcessor() as FilmSurfaceProcessor

    fun release() = processor.release()
}

/** Minimal EGL2 processor: camera OES texture in, graded texture to each CameraX output. */
private class FilmSurfaceProcessor(private val profile: ResolvedVisualParameters) : SurfaceProcessor {
    private val thread = HandlerThread("theia-film-surface").apply { start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { command -> handler.post(command) }
    private val released = AtomicBoolean(false)
    private val outputs = mutableListOf<OutputTarget>()
    private var eglDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: android.opengl.EGLConfig? = null
    private var pbuffer = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var textureId = 0
    private var cameraSurfaceTexture: SurfaceTexture? = null
    private var cameraInputSurface: Surface? = null
    private var size = Size(0, 0)
    private var errorListener: ((Throwable) -> Unit)? = null

    override fun onInputSurface(request: SurfaceRequest) {
        handler.post {
            if (released.get()) {
                request.willNotProvideSurface()
                return@post
            }
            try {
                ensureEgl()
                size = request.resolution
                val textureIds = IntArray(1)
                GLES20.glGenTextures(1, textureIds, 0)
                textureId = textureIds[0]
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                program = createProgram(VERTEX_SHADER, fragmentShader())
                cameraSurfaceTexture = SurfaceTexture(textureId).apply {
                    setDefaultBufferSize(size.width, size.height)
                    setOnFrameAvailableListener({ source -> renderFrame(source) }, handler)
                }
                cameraInputSurface = Surface(cameraSurfaceTexture)
                request.provideSurface(cameraInputSurface!!, executor) { result ->
                    handler.post {
                        cameraInputSurface?.release()
                        cameraInputSurface = null
                        cameraSurfaceTexture?.release()
                        cameraSurfaceTexture = null
                        if (result.resultCode == SurfaceRequest.Result.RESULT_SURFACE_USED_SUCCESSFULLY) {
                            destroyGlObjects()
                        }
                    }
                }
                request.addRequestCancellationListener(executor) {
                    cameraSurfaceTexture?.setOnFrameAvailableListener(null)
                }
            } catch (failure: Throwable) {
                request.willNotProvideSurface()
                errorListener?.invoke(failure)
            }
        }
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        handler.post {
            if (released.get()) {
                surfaceOutput.close()
                return@post
            }
            try {
                ensureEgl()
                val surface = surfaceOutput.getSurface(executor) { event ->
                    if (event.eventCode == SurfaceOutput.Event.EVENT_REQUEST_CLOSE) {
                        handler.post { closeOutput(surfaceOutput) }
                    }
                }
                val eglSurface = EGL14.eglCreateWindowSurface(
                    eglDisplay,
                    eglConfig,
                    surface,
                    intArrayOf(EGL14.EGL_NONE),
                    0,
                )
                check(eglSurface != EGL14.EGL_NO_SURFACE) { "Could not create CameraX output EGL surface" }
                outputs += OutputTarget(surfaceOutput, surface, eglSurface)
            } catch (failure: Throwable) {
                surfaceOutput.close()
                errorListener?.invoke(failure)
            }
        }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        handler.post {
            outputs.toList().forEach(::destroyOutput)
            cameraSurfaceTexture?.setOnFrameAvailableListener(null)
            cameraInputSurface?.release()
            cameraInputSurface = null
            cameraSurfaceTexture?.release()
            cameraSurfaceTexture = null
            destroyGlObjects()
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            thread.quitSafely()
        }
    }

    private fun renderFrame(source: SurfaceTexture) {
        if (released.get() || program == 0 || outputs.isEmpty()) return
        try {
            source.updateTexImage()
            val sourceMatrix = FloatArray(16)
            source.getTransformMatrix(sourceMatrix)
            outputs.toList().forEach { output ->
                if (!EGL14.eglMakeCurrent(eglDisplay, output.eglSurface, output.eglSurface, eglContext)) return@forEach
                val surfaceSize = output.output.size
                GLES20.glViewport(0, 0, surfaceSize.width, surfaceSize.height)
                GLES20.glUseProgram(program)
                val transformed = FloatArray(16)
                output.output.updateTransformMatrix(transformed, sourceMatrix)
                GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false, transformed, 0)
                bindProfileUniforms(surfaceSize)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
                GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uCamera"), 0)
                val vertices = vertexBuffer(FULLSCREEN_VERTICES)
                val coords = vertexBuffer(TEXTURE_COORDS)
                val position = GLES20.glGetAttribLocation(program, "aPosition")
                val texCoord = GLES20.glGetAttribLocation(program, "aTexCoord")
                GLES20.glEnableVertexAttribArray(position)
                GLES20.glEnableVertexAttribArray(texCoord)
                GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
                GLES20.glVertexAttribPointer(texCoord, 2, GLES20.GL_FLOAT, false, 0, coords)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                GLES20.glDisableVertexAttribArray(position)
                GLES20.glDisableVertexAttribArray(texCoord)
                check(EGL14.eglSwapBuffers(eglDisplay, output.eglSurface)) { "CameraX EGL frame swap failed" }
            }
        } catch (failure: Throwable) {
            errorListener?.invoke(failure)
        }
    }

    private fun bindProfileUniforms(outputSize: Size) {
        fun float(name: String, value: Float) = GLES20.glUniform1f(GLES20.glGetUniformLocation(program, name), value)
        float("uExposure", 2.0.pow(profile.exposureBiasEv).toFloat())
        float("uContrast", profile.contrast.toFloat())
        float("uSaturation", profile.saturation.toFloat())
        float("uWarmth", profile.warmth.toFloat())
        float("uGreenMagenta", profile.greenMagenta.toFloat())
        float("uHighlightRollOff", profile.highlightRollOff.toFloat())
        float("uShadowLift", profile.shadowLift.toFloat())
        float("uGrain", profile.grainIntensity.toFloat())
        float("uGrainScale", profile.grainScale.toFloat())
        float("uVignette", profile.vignette.toFloat())
        float("uBloom", profile.bloom.toFloat())
        float("uHalation", profile.halation.toFloat())
        float("uEdgeSoftness", profile.edgeSoftness.toFloat())
        float("uDistortion", profile.distortion.toFloat())
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "uResolution"), outputSize.width.toFloat(), outputSize.height.toFloat())
    }

    private fun ensureEgl() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) return
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "Could not get EGL display" }
        val versions = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, versions, 0, versions, 1)) { "Could not initialize EGL" }
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val count = IntArray(1)
        check(EGL14.eglChooseConfig(eglDisplay, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
            "No recordable EGL configuration is available"
        }
        eglConfig = configs[0]
        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            eglConfig,
            EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
            0,
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "Could not create EGL context" }
        pbuffer = EGL14.eglCreatePbufferSurface(eglDisplay, eglConfig, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        check(EGL14.eglMakeCurrent(eglDisplay, pbuffer, pbuffer, eglContext)) { "Could not activate EGL context" }
    }

    private fun closeOutput(output: SurfaceOutput) {
        outputs.firstOrNull { it.output === output }?.let(::destroyOutput)
    }

    private fun destroyOutput(target: OutputTarget) {
        outputs.remove(target)
        EGL14.eglDestroySurface(eglDisplay, target.eglSurface)
        target.surface.release()
        target.output.close()
    }

    private fun destroyGlObjects() {
        if (program != 0) GLES20.glDeleteProgram(program)
        program = 0
        if (textureId != 0) GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        textureId = 0
        if (eglDisplay != EGL14.EGL_NO_DISPLAY && pbuffer != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(eglDisplay, pbuffer)
        pbuffer = EGL14.EGL_NO_SURFACE
    }

    private fun createProgram(vertexShader: String, fragmentShader: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
        val result = GLES20.glCreateProgram()
        GLES20.glAttachShader(result, vertex)
        GLES20.glAttachShader(result, fragment)
        GLES20.glLinkProgram(result)
        val status = IntArray(1)
        GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetProgramInfoLog(result) }
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        return result
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    private fun vertexBuffer(values: FloatArray): FloatBuffer = ByteBuffer.allocateDirect(values.size * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().put(values).apply { position(0) }

    private data class OutputTarget(val output: SurfaceOutput, val surface: Surface, val eglSurface: android.opengl.EGLSurface)

    private fun fragmentShader() = """
        #extension GL_OES_EGL_image_external : require
        precision highp float;
        uniform samplerExternalOES uCamera;
        uniform mat4 uTexMatrix;
        uniform vec2 uResolution;
        uniform float uExposure, uContrast, uSaturation, uWarmth, uGreenMagenta;
        uniform float uHighlightRollOff, uShadowLift, uGrain, uGrainScale;
        uniform float uVignette, uBloom, uHalation, uEdgeSoftness, uDistortion;
        varying vec2 vTexCoord;
        float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
        vec3 sampleCamera(vec2 uv) { return texture2D(uCamera, (uTexMatrix * vec4(uv, 0.0, 1.0)).xy).rgb; }
        void main() {
            vec2 centered = vTexCoord - 0.5;
            float radius2 = dot(centered, centered) * 4.0;
            vec2 uv = vTexCoord + centered * radius2 * uDistortion;
            vec2 pixel = 1.0 / max(uResolution, vec2(1.0));
            vec3 color = sampleCamera(uv);
            vec3 soft = (sampleCamera(uv + vec2(pixel.x, 0.0)) + sampleCamera(uv - vec2(pixel.x, 0.0)) +
                         sampleCamera(uv + vec2(0.0, pixel.y)) + sampleCamera(uv - vec2(0.0, pixel.y))) * 0.25;
            color = mix(color, soft, clamp(uEdgeSoftness * 0.45, 0.0, 0.2));
            float lum = dot(color, vec3(0.2126, 0.7152, 0.0722));
            float glow = smoothstep(0.68, 1.0, lum) * (uBloom * 0.18 + uHalation * 0.12);
            color = mix(color, soft, clamp(uBloom * smoothstep(0.7, 1.0, lum) * 0.25, 0.0, 0.18));
            color *= uExposure;
            color.r += uWarmth * 0.09 + uGreenMagenta * 0.035;
            color.g -= uGreenMagenta * 0.07;
            color.b -= uWarmth * 0.09 - uGreenMagenta * 0.035;
            color += vec3(uShadowLift * (1.0 - lum));
            color = (color - 0.5) * uContrast + 0.5;
            lum = dot(color, vec3(0.2126, 0.7152, 0.0722));
            color = mix(vec3(lum), color, uSaturation);
            float h = max(max(color.r, color.g), color.b);
            float roll = smoothstep(0.55, 1.0, h) * uHighlightRollOff * 0.32;
            color *= 1.0 - roll;
            color += vec3(glow * 1.0, glow * 0.32, glow * 0.05);
            float vignette = 1.0 - uVignette * smoothstep(0.18, 0.75, radius2);
            color *= vignette;
            float grainCell = mix(1.0, 10.0, uGrainScale);
            float grain = (hash(floor(vTexCoord * uResolution / grainCell)) - 0.5) * uGrain * 0.12;
            color += grain;
            gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
        }
    """.trimIndent()

    private companion object {
        const val EGL_RECORDABLE_ANDROID = 0x3142
        const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() { gl_Position = aPosition; vTexCoord = aTexCoord; }
        """
        val FULLSCREEN_VERTICES = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val TEXTURE_COORDS = floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f)
    }
}
