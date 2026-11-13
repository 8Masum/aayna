package com.themasum.aayna.core.gl

import android.content.res.AssetManager
import android.opengl.GLES30
import com.themasum.aayna.core.model.FlashMode
import com.themasum.aayna.core.model.FrameBorderStyle
import com.themasum.aayna.core.model.FrameWindowMeta
import com.themasum.aayna.core.model.GlDiagnosticsInfo
import com.themasum.aayna.core.model.GrainType
import com.themasum.aayna.core.model.GrungeAtlasMeta
import com.themasum.aayna.core.model.LensAttachment
import com.themasum.aayna.core.model.PassShaderStatus
import com.themasum.aayna.core.model.StampAtlasMeta
import com.themasum.aayna.core.model.StampStyle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class AaynaFourPassEngine(private val assetManager: AssetManager, private val useOesInput: Boolean) {
    private data class FboAttachment(val fboId: Int, val texId: Int, val width: Int, val height: Int)

    private var pass1Program = 0
    private var pass2ThresholdProgram = 0
    private var pass2BlurProgram = 0
    private var pass2StarProgram = 0
    private var pass3Program = 0
    private var pass4Program = 0

    private var fbo1: FboAttachment? = null
    private var fboHi: FboAttachment? = null
    private var fboHiTemp: FboAttachment? = null
    private var fboStar: FboAttachment? = null
    private var fbo2: FboAttachment? = null

    private val lutCache = mutableMapOf<String, Pair<Int, Int>>()
    private val tex2DCache = mutableMapOf<String, Int>()
    private var fallbackBlackTex = 0

    private var stampMeta: StampAtlasMeta? = null
    private var grungeMeta: GrungeAtlasMeta? = null

    private var maxTextureSize = 4096
    private var glVendor = ""
    private var glRenderer = ""
    private var glVersion = ""
    private var lastTargetFboSize = "0x0"

    private val shaderStatusesInternal = mutableListOf<PassShaderStatus>()

    private val quadPosBuffer: FloatBuffer =
        ByteBuffer
            .allocateDirect(QUAD_POSITIONS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(QUAD_POSITIONS)
                position(0)
            }

    private val quadUvBuffer: FloatBuffer =
        ByteBuffer
            .allocateDirect(QUAD_UVS.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(QUAD_UVS)
                position(0)
            }

    val shaderStatuses: List<PassShaderStatus>
        get() = shaderStatusesInternal.toList()

    val diagnosticsInfo: GlDiagnosticsInfo
        get() =
            GlDiagnosticsInfo(
                glVendor = glVendor,
                glRenderer = glRenderer,
                glVersion = glVersion,
                maxTextureSize = maxTextureSize,
                targetFboSize = lastTargetFboSize
            )

    fun getMaxTextureSize(): Int = maxTextureSize

    fun init() {
        glVendor = GLES30.glGetString(GLES30.GL_VENDOR).orEmpty()
        glRenderer = GLES30.glGetString(GLES30.GL_RENDERER).orEmpty()
        glVersion = GLES30.glGetString(GLES30.GL_VERSION).orEmpty()
        val maxTex = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTex, 0)
        maxTextureSize = maxTex[0].coerceAtLeast(256)

        stampMeta = AssetMetadataLoader.loadStampAtlasMeta(assetManager)
        grungeMeta = AssetMetadataLoader.loadGrungeAtlasMeta(assetManager)
        fallbackBlackTex = createSolidBlackTexture()

        shaderStatusesInternal.clear()
        pass1Program =
            compileAndRecord(
                "Pass 1 - Geometry & Pre-Conditioning",
                GlShaderSources.PASS1_VERTEX_SHADER,
                GlShaderSources.buildPass1FragmentShader(useOesInput)
            )
        pass2ThresholdProgram =
            compileAndRecord(
                "Pass 2A - Highlight Threshold",
                GlShaderSources.QUAD_VERTEX_SHADER,
                GlShaderSources.PASS2_THRESHOLD_FRAGMENT_SHADER
            )
        pass2BlurProgram =
            compileAndRecord(
                "Pass 2B - Gaussian Bloom Blur",
                GlShaderSources.QUAD_VERTEX_SHADER,
                GlShaderSources.PASS2_BLUR_FRAGMENT_SHADER
            )
        pass2StarProgram =
            compileAndRecord(
                "Pass 2C - Star Diffraction",
                GlShaderSources.QUAD_VERTEX_SHADER,
                GlShaderSources.PASS2_STAR_FRAGMENT_SHADER
            )
        pass3Program =
            compileAndRecord(
                "Pass 3 - 3D LUT & Composite",
                GlShaderSources.QUAD_VERTEX_SHADER,
                GlShaderSources.PASS3_LUT_COMPOSITE_FRAGMENT_SHADER
            )
        pass4Program =
            compileAndRecord(
                "Pass 4 - Sensor Texture, Frame & Stamp",
                GlShaderSources.QUAD_VERTEX_SHADER,
                GlShaderSources.PASS4_FINAL_FRAGMENT_SHADER
            )
    }

    fun render(
        inputTextureId: Int,
        texMatrix: FloatArray,
        targetWidth: Int,
        targetHeight: Int,
        outputFboId: Int,
        viewportX: Int = 0,
        viewportY: Int = 0,
        viewportWidth: Int = targetWidth,
        viewportHeight: Int = targetHeight,
        params: GlRenderParams
    ) {
        val safeW = targetWidth.coerceIn(2, maxTextureSize)
        val safeH = targetHeight.coerceIn(2, maxTextureSize)
        lastTargetFboSize = "${safeW}x$safeH"
        ensureFbos(safeW, safeH)

        val fbo1Att = fbo1 ?: return
        val fboHiAtt = fboHi ?: return
        val fboHiTempAtt = fboHiTemp ?: return
        val fboStarAtt = fboStar ?: return
        val fbo2Att = fbo2 ?: return

        val (lutTexId, lutSize) = getOrLoadLut(params.profile.lutAsset)
        val targetAspect = safeW.toFloat() / safeH.toFloat()
        val cropScale = GlMath.computeAspectCropScale(params.inputAspectRatio, targetAspect)

        // PASS 1 -> FBO_1
        executePass1(fbo1Att, inputTextureId, texMatrix, cropScale, targetAspect, params)

        // PASS 2 -> FBO_Hi + FBO_Star
        executePass2(fbo1Att, fboHiAtt, fboHiTempAtt, fboStarAtt, params)

        // PASS 3 -> FBO_2
        executePass3(fbo1Att, fboHiAtt, fboStarAtt, fbo2Att, lutTexId, lutSize, params)

        // PASS 4 -> outputFboId (Screen or Offscreen FBO)
        executePass4(
            fbo1Att = fbo1Att,
            fbo2Att = fbo2Att,
            outputFboId = outputFboId,
            viewportX = viewportX,
            viewportY = viewportY,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            targetWidth = safeW,
            targetHeight = safeH,
            targetAspect = targetAspect,
            params = params
        )
    }

    private fun executePass1(
        fbo1Att: FboAttachment,
        inputTextureId: Int,
        texMatrix: FloatArray,
        cropScale: Pair<Float, Float>,
        targetAspect: Float,
        params: GlRenderParams
    ) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo1Att.fboId)
        GLES30.glViewport(0, 0, fbo1Att.width, fbo1Att.height)
        GLES30.glUseProgram(pass1Program)

        val texTarget = if (useOesInput) GL_TEXTURE_EXTERNAL_OES else GLES30.GL_TEXTURE_2D
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(texTarget, inputTextureId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pass1Program, "uInputTex"), 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(pass1Program, "uTexMatrix"), 1, false, texMatrix, 0)
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(pass1Program, "uCropScale"),
            cropScale.first,
            cropScale.second
        )
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pass1Program, "uTargetAspect"), targetAspect)

        val fisheyeStrength =
            when (params.opticalSettings.lensAttachment) {
                LensAttachment.FISHEYE -> maxOf(params.profile.optics.fisheyeDefault, 0.85f)
                else -> params.profile.optics.fisheyeDefault
            }
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pass1Program, "uFisheyeStrength"), fisheyeStrength)
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(pass1Program, "uCaStrength"),
            params.profile.optics.caStrength
        )
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(pass1Program, "uWbTemp"),
            params.opticalSettings.whiteBalance.temperatureShift
        )
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(pass1Program, "uWbTint"),
            params.opticalSettings.whiteBalance.tintShift
        )
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(pass1Program, "uWarmthShift"),
            params.opticalSettings.warmthShift
        )

        val totalStops =
            params.fallbackGains.evGainStops + params.fallbackGains.digitalBoostStops
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pass1Program, "uExposureStops"), totalStops)

        val flashStrength =
            if (params.opticalSettings.flashMode == FlashMode.OPTICAL_FLASH_SIM) 1.0f else 0.0f
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pass1Program, "uFlashSimStrength"), flashStrength)

        drawQuad()
    }

    private fun executePass2(
        fbo1Att: FboAttachment,
        fboHiAtt: FboAttachment,
        fboHiTempAtt: FboAttachment,
        fboStarAtt: FboAttachment,
        params: GlRenderParams
    ) {
        // 2A: Threshold -> FBO_Hi
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboHiAtt.fboId)
        GLES30.glViewport(0, 0, fboHiAtt.width, fboHiAtt.height)
        GLES30.glUseProgram(pass2ThresholdProgram)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fbo1Att.texId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pass2ThresholdProgram, "uFbo1Tex"), 0)
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(pass2ThresholdProgram, "uBloomThreshold"),
            params.profile.bloom.threshold
        )
        val (tintR, tintG, tintB) = GlMath.parseHexRgb(params.profile.bloom.tintHex)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(pass2ThresholdProgram, "uBloomTint"), tintR, tintG, tintB)
        drawQuad()

        // 2B: Horizontal Blur FBO_Hi -> FBO_Hi_Temp
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboHiTempAtt.fboId)
        GLES30.glViewport(0, 0, fboHiTempAtt.width, fboHiTempAtt.height)
        GLES30.glUseProgram(pass2BlurProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboHiAtt.texId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pass2BlurProgram, "uSourceTex"), 0)
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(pass2BlurProgram, "uTexelStep"),
            1.5f / fboHiAtt.width.toFloat(),
            0f
        )
        drawQuad()

        // 2B: Vertical Blur FBO_Hi_Temp -> FBO_Hi
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboHiAtt.fboId)
        GLES30.glViewport(0, 0, fboHiAtt.width, fboHiAtt.height)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboHiTempAtt.texId)
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(pass2BlurProgram, "uTexelStep"),
            0f,
            1.5f / fboHiAtt.height.toFloat()
        )
        drawQuad()

        // 2C: Star Diffraction -> FBO_Star
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboStarAtt.fboId)
        GLES30.glViewport(0, 0, fboStarAtt.width, fboStarAtt.height)
        GLES30.glUseProgram(pass2StarProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboHiAtt.texId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pass2StarProgram, "uFboHiTex"), 0)
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(pass2StarProgram, "uTexelSize"),
            1f / fboHiAtt.width.toFloat(),
            1f / fboHiAtt.height.toFloat()
        )
        val starPoints = resolveStarPoints(params)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pass2StarProgram, "uStarPoints"), starPoints)
        drawQuad()
    }

    private fun executePass3(
        fbo1Att: FboAttachment,
        fboHiAtt: FboAttachment,
        fboStarAtt: FboAttachment,
        fbo2Att: FboAttachment,
        lutTexId: Int,
        lutSize: Int,
        params: GlRenderParams
    ) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo2Att.fboId)
        GLES30.glViewport(0, 0, fbo2Att.width, fbo2Att.height)
        GLES30.glUseProgram(pass3Program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fbo1Att.texId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pass3Program, "uFbo1Tex"), 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTexId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pass3Program, "uLutTex"), 1)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboHiAtt.texId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pass3Program, "uFboHiTex"), 2)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboStarAtt.texId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pass3Program, "uFboStarTex"), 3)

        GLES30.glUniform1f(GLES30.glGetUniformLocation(pass3Program, "uLutSize"), lutSize.toFloat())
        val bloomIntensity =
            (params.profile.bloom.intensity * 0.5f + params.opticalSettings.bloomIntensity * 0.5f)
                .coerceIn(0f, 1.5f)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pass3Program, "uBloomIntensity"), bloomIntensity)
        val starIntensity = if (resolveStarPoints(params) > 0) 0.9f else 0f
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pass3Program, "uStarIntensity"), starIntensity)

        drawQuad()
    }

    private fun executePass4(
        fbo1Att: FboAttachment,
        fbo2Att: FboAttachment,
        outputFboId: Int,
        viewportX: Int,
        viewportY: Int,
        viewportWidth: Int,
        viewportHeight: Int,
        targetWidth: Int,
        targetHeight: Int,
        targetAspect: Float,
        params: GlRenderParams
    ) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, outputFboId)
        GLES30.glViewport(viewportX, viewportY, viewportWidth, viewportHeight)
        GLES30.glUseProgram(pass4Program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fbo2Att.texId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pass4Program, "uFbo2Tex"), 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fbo1Att.texId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pass4Program, "uFbo1Tex"), 1)

        val leakKey = if ((params.randomSeed and 1L) == 0L) "light_leak_warm" else "light_leak_edge"
        val leakTex = getOrLoadGrungeTex(leakKey)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, leakTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pass4Program, "uLightLeakTex"), 2)

        val dustTex = getOrLoadGrungeTex("dust_scratches")
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dustTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pass4Program, "uDustTex"), 3)

        val (frameTex, frameWindow) = resolveFrame(params.opticalSettings.frameStyle)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, frameTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pass4Program, "uFrameTex"), 4)

        val stampAtlasTex = getOrLoadStampAtlasTex(params.stampConfig.style)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE5)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, stampAtlasTex)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pass4Program, "uStampAtlasTex"), 5)

        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(pass4Program, "uOutputSize"),
            targetWidth.toFloat(),
            targetHeight.toFloat()
        )
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pass4Program, "uTargetAspect"), targetAspect)
        val grainTypeInt = if (params.profile.grain.type == GrainType.CCD_CHROMA_LUMA) 0 else 1
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pass4Program, "uGrainType"), grainTypeInt)
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(pass4Program, "uLumaGrainStrength"),
            params.profile.grain.lumaStrength
        )
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(pass4Program, "uChromaGrainStrength"),
            params.profile.grain.chromaStrength
        )
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(pass4Program, "uShaderNoiseBoost"),
            params.fallbackGains.shaderNoiseBoost
        )

        val vigStrength =
            maxOf(params.profile.optics.vignetteStrength, params.opticalSettings.vignetteIntensity)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pass4Program, "uVignetteStrength"), vigStrength)

        val leakIntensity =
            maxOf(params.profile.grunge.lightLeakStrength, params.opticalSettings.lightLeakIntensity)
        val dustIntensity =
            maxOf(params.profile.grunge.dustStrength, params.opticalSettings.dustIntensity)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pass4Program, "uLightLeakIntensity"), leakIntensity)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(pass4Program, "uDustIntensity"), dustIntensity)
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(pass4Program, "uRandomSeed"),
            (params.randomSeed and 0xFFFFL).toFloat()
        )

        if (frameWindow != null) {
            GLES30.glUniform1f(GLES30.glGetUniformLocation(pass4Program, "uHasFrame"), 1.0f)
            GLES30.glUniform4f(
                GLES30.glGetUniformLocation(pass4Program, "uFrameWindow"),
                frameWindow.uMin,
                frameWindow.vMin,
                frameWindow.uMax,
                frameWindow.vMax
            )
        } else {
            GLES30.glUniform1f(GLES30.glGetUniformLocation(pass4Program, "uHasFrame"), 0.0f)
            GLES30.glUniform4f(GLES30.glGetUniformLocation(pass4Program, "uFrameWindow"), 0f, 0f, 1f, 1f)
        }

        bindStampUniforms(params)

        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(pass4Program, "uSplitCompareRatio"),
            params.splitCompareRatio.coerceIn(0f, 1f)
        )
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(pass4Program, "uHoldOriginal"),
            if (params.isPreviewHoldOriginal) 1.0f else 0.0f
        )

        drawQuad()
    }

    private fun bindStampUniforms(params: GlRenderParams) {
        val meta = stampMeta
        val enabled = params.stampConfig.enabled && params.stampText.isNotEmpty() && meta != null
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(pass4Program, "uStampEnabled"),
            if (enabled) 1.0f else 0.0f
        )
        if (!enabled) {
            GLES30.glUniform1i(GLES30.glGetUniformLocation(pass4Program, "uStampLength"), 0)
            return
        }
        val activeMeta = checkNotNull(meta)

        val indices = GlMath.encodeStampIndices(meta, params.stampText, GlMath.MAX_STAMP_CHARS)
        val padded = IntArray(GlMath.MAX_STAMP_CHARS)
        indices.copyInto(padded)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(pass4Program, "uStampLength"), indices.size)
        GLES30.glUniform1iv(
            GLES30.glGetUniformLocation(pass4Program, "uStampIndices"),
            GlMath.MAX_STAMP_CHARS,
            padded,
            0
        )

        val (sr, sg, sb) = GlMath.parseHexRgb(params.stampConfig.style.hexColor)
        GLES30.glUniform3f(GLES30.glGetUniformLocation(pass4Program, "uStampColor"), sr, sg, sb)
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(pass4Program, "uStampGlowStrength"),
            params.stampConfig.glowStrength
        )
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(pass4Program, "uAtlasGrid"),
            meta.columns.toFloat(),
            meta.rows.toFloat()
        )
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(pass4Program, "uAtlasSize"),
            meta.atlasWidth.toFloat(),
            meta.atlasHeight.toFloat()
        )
        GLES30.glUniform2f(
            GLES30.glGetUniformLocation(pass4Program, "uCellSize"),
            meta.cellWidth.toFloat(),
            meta.cellHeight.toFloat()
        )
        GLES30.glUniform1f(
            GLES30.glGetUniformLocation(pass4Program, "uGlyphPadding"),
            meta.glyphPadding.toFloat()
        )
    }

    private fun resolveStarPoints(params: GlRenderParams): Int = when (params.opticalSettings.lensAttachment) {
        LensAttachment.STAR_4PT -> 4
        LensAttachment.STAR_6PT -> 6
        else -> params.profile.optics.starPoints
    }

    private fun resolveFrame(style: FrameBorderStyle): Pair<Int, FrameWindowMeta?> {
        val meta = grungeMeta ?: return fallbackBlackTex to null
        val key =
            when (style) {
                FrameBorderStyle.NONE -> return fallbackBlackTex to null
                FrameBorderStyle.INSTANT_PRINT -> "instant_frame"
                FrameBorderStyle.FILM_35MM -> "film_border_35mm"
            }
        val window = meta.frames[key] ?: return fallbackBlackTex to null
        val texId = getOrLoadGrungeTex(key)
        return texId to window
    }

    private fun getOrLoadLut(assetPath: String): Pair<Int, Int> = lutCache.getOrPut(assetPath) {
        val lut = CubeLutParser.loadFromAssets(assetManager, assetPath)
        val texId = CubeLutParser.uploadToTexture3D(lut)
        texId to lut.size
    }

    private fun getOrLoadGrungeTex(key: String): Int {
        val assetPath = grungeMeta?.textures?.get(key) ?: return fallbackBlackTex
        return tex2DCache.getOrPut(assetPath) {
            val bmp = AssetMetadataLoader.decodeAssetBitmap(assetManager, assetPath)
            AssetMetadataLoader.uploadTexture2D(bmp, recycleBitmap = true)
        }
    }

    private fun getOrLoadStampAtlasTex(style: StampStyle): Int {
        val atlasKey =
            when (style) {
                StampStyle.SEVEN_SEGMENT_EMERALD -> "seven_segment"
                else -> "dot_matrix"
            }
        val assetPath = stampMeta?.atlases?.get(atlasKey) ?: return fallbackBlackTex
        return tex2DCache.getOrPut(assetPath) {
            val bmp = AssetMetadataLoader.decodeAssetBitmap(assetManager, assetPath)
            AssetMetadataLoader.uploadTexture2D(bmp, recycleBitmap = true)
        }
    }

    private fun ensureFbos(width: Int, height: Int) {
        if (fbo1?.width == width && fbo1?.height == height) {
            return
        }
        releaseFbos()
        fbo1 = createFbo(width, height)
        fbo2 = createFbo(width, height)

        val hiW = (width / 4).coerceAtLeast(64)
        val hiH = (height / 4).coerceAtLeast(64)
        fboHi = createFbo(hiW, hiH)
        fboHiTemp = createFbo(hiW, hiH)
        fboStar = createFbo(hiW, hiH)
    }

    private fun createFbo(width: Int, height: Int): FboAttachment {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        val texId = texIds[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        // CRITICAL ES 3.0 PORTABILITY: always use GL_RGBA8, never GL_RGBA16F
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA8,
            width,
            height,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            null
        )

        val fboIds = IntArray(1)
        GLES30.glGenFramebuffers(1, fboIds, 0)
        val fboId = fboIds[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER,
            GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D,
            texId,
            0
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        check(status == GLES30.GL_FRAMEBUFFER_COMPLETE) {
            "FBO incomplete (${width}x$height): status=0x${status.toString(16)}"
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return FboAttachment(fboId = fboId, texId = texId, width = width, height = height)
    }

    private fun drawQuad() {
        quadPosBuffer.position(0)
        quadUvBuffer.position(0)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, quadPosBuffer)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 0, quadUvBuffer)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    private fun compileAndRecord(passName: String, vertexSrc: String, fragmentSrc: String): Int {
        val (vs, vsErr) = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val (fs, fsErr) = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        if (vs == 0 || fs == 0) {
            if (vs != 0) GLES30.glDeleteShader(vs)
            if (fs != 0) GLES30.glDeleteShader(fs)
            shaderStatusesInternal +=
                PassShaderStatus(
                    passName = passName,
                    compiled = false,
                    errorLog = listOfNotNull(vsErr, fsErr).joinToString(" | ")
                )
            return 0
        }

        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        return if (linkStatus[0] == GLES30.GL_TRUE) {
            shaderStatusesInternal += PassShaderStatus(passName = passName, compiled = true)
            program
        } else {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            shaderStatusesInternal += PassShaderStatus(passName = passName, compiled = false, errorLog = log)
            0
        }
    }

    private fun compileShader(type: Int, source: String): Pair<Int, String?> {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        return if (status[0] == GLES30.GL_TRUE) {
            shader to null
        } else {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            0 to log
        }
    }

    private fun createSolidBlackTexture(): Int {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        val texId = texIds[0]
        val pixel =
            ByteBuffer
                .allocateDirect(4)
                .order(ByteOrder.nativeOrder())
                .put(byteArrayOf(0, 0, 0, 0))
                .apply { position(0) }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D,
            0,
            GLES30.GL_RGBA8,
            1,
            1,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            pixel
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return texId
    }

    private fun releaseFbos() {
        listOfNotNull(fbo1, fboHi, fboHiTemp, fboStar, fbo2).forEach { att ->
            GLES30.glDeleteFramebuffers(1, intArrayOf(att.fboId), 0)
            GLES30.glDeleteTextures(1, intArrayOf(att.texId), 0)
        }
        fbo1 = null
        fboHi = null
        fboHiTemp = null
        fboStar = null
        fbo2 = null
    }

    fun release() {
        releaseFbos()
        intArrayOf(
            pass1Program,
            pass2ThresholdProgram,
            pass2BlurProgram,
            pass2StarProgram,
            pass3Program,
            pass4Program
        ).filter { it != 0 }.forEach { GLES30.glDeleteProgram(it) }
        pass1Program = 0
        pass2ThresholdProgram = 0
        pass2BlurProgram = 0
        pass2StarProgram = 0
        pass3Program = 0
        pass4Program = 0

        lutCache.values.forEach { (texId, _) -> GLES30.glDeleteTextures(1, intArrayOf(texId), 0) }
        lutCache.clear()
        tex2DCache.values.forEach { texId -> GLES30.glDeleteTextures(1, intArrayOf(texId), 0) }
        tex2DCache.clear()
        if (fallbackBlackTex != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(fallbackBlackTex), 0)
            fallbackBlackTex = 0
        }
    }

    companion object {
        private const val GL_TEXTURE_EXTERNAL_OES = 0x8D65

        private val QUAD_POSITIONS =
            floatArrayOf(
                -1f,
                -1f,
                1f,
                -1f,
                -1f,
                1f,
                1f,
                1f
            )

        private val QUAD_UVS =
            floatArrayOf(
                0f,
                0f,
                1f,
                0f,
                0f,
                1f,
                1f,
                1f
            )
    }
}
