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
import com.themasum.aayna.core.model.StampConfig
import com.themasum.aayna.core.model.StampFontStyle
import com.themasum.aayna.core.model.StampStyle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI

class AaynaFourPassEngine(
    private val assetManager: AssetManager,
    private val useOesInput: Boolean,
    private val compileBothPass1Variants: Boolean = false
) {
    private data class FboAttachment(val fboId: Int, val texId: Int, val width: Int, val height: Int)

    private data class CachedLut(
        val texId: Int,
        val is2d: Boolean,
        val cubeSize: Int,
        val tilesX: Int,
        val texWidth: Int,
        val texHeight: Int
    )

    private data class Pass1Uniforms(
        val uInputTex: Int,
        val uTexMatrix: Int,
        val uCropScale: Int,
        val uInputAspect: Int,
        val uTargetAspect: Int,
        val uStraightenRad: Int,
        val uStraightenScale: Int,
        val uLensMode: Int,
        val uFisheyeStrength: Int,
        val uCaStrength: Int,
        val uBradfordMatrix: Int,
        val uFlashSimStrength: Int,
        val uHighlights: Int,
        val uShadows: Int
    )

    private data class Pass2ThresholdUniforms(val uFbo1Tex: Int, val uBloomThreshold: Int, val uBloomTint: Int)

    private data class Pass2BlurUniforms(val uSourceTex: Int, val uTexelStep: Int)

    private data class Pass2StarUniforms(val uFboHiTex: Int, val uTexelSize: Int, val uStarPoints: Int)

    private data class Pass3Uniforms(
        val uFbo1Tex: Int,
        val uLut2dTex: Int,
        val uLut3dTex: Int,
        val uUse2dLut: Int,
        val uLutSize: Int,
        val uLutTiles: Int,
        val uLutTexSize: Int,
        val uEvStops: Int,
        val uFboHiTex: Int,
        val uFboStarTex: Int,
        val uBloomIntensity: Int,
        val uStarIntensity: Int,
        val uBeautySoftness: Int,
        val uSharpenStrength: Int,
        val uTexelSize: Int
    )

    private data class Pass4Uniforms(
        val uFbo2Tex: Int,
        val uFbo1Tex: Int,
        val uLightLeakTex: Int,
        val uDustTex: Int,
        val uDvScanlineTex: Int,
        val uFrameTex: Int,
        val uStampAtlasTex: Int,
        val uOutputSize: Int,
        val uTargetAspect: Int,
        val uGrainType: Int,
        val uLumaGrainStrength: Int,
        val uChromaGrainStrength: Int,
        val uShaderNoiseBoost: Int,
        val uVignetteStrength: Int,
        val uLightLeakIntensity: Int,
        val uDustIntensity: Int,
        val uRandomSeed: Int,
        val uHasFrame: Int,
        val uFrameWindow: Int,
        val uPhotoPanels: Int,
        val uStampEnabled: Int,
        val uStampLength: Int,
        val uStampIndices: Int,
        val uStampColor: Int,
        val uStampGlowStrength: Int,
        val uStampPosition: Int,
        val uStampCleanSans: Int,
        val uAtlasGrid: Int,
        val uAtlasSize: Int,
        val uCellSize: Int,
        val uGlyphPadding: Int,
        val uSplitCompareRatio: Int,
        val uHoldOriginal: Int
    )

    private data class OverlayUniforms(
        val uBaseTex: Int,
        val uFrameTex: Int,
        val uStampAtlasTex: Int,
        val uTargetAspect: Int,
        val uStampEnabled: Int,
        val uStampLength: Int,
        val uStampIndices: Int,
        val uStampColor: Int,
        val uStampGlowStrength: Int,
        val uStampPosition: Int,
        val uStampCleanSans: Int,
        val uAtlasGrid: Int,
        val uAtlasSize: Int,
        val uCellSize: Int,
        val uGlyphPadding: Int
    )

    private var pass1OesProgram = 0
    private var pass1Tex2dProgram = 0
    private var pass2ThresholdProgram = 0
    private var pass2BlurProgram = 0
    private var pass2StarProgram = 0
    private var pass3Program = 0
    private var pass4Program = 0
    private var passOverlayProgram = 0

    private var pass1OesUniforms: Pass1Uniforms? = null
    private var pass1Tex2dUniforms: Pass1Uniforms? = null
    private var pass2ThresholdUniforms: Pass2ThresholdUniforms? = null
    private var pass2BlurUniforms: Pass2BlurUniforms? = null
    private var pass2StarUniforms: Pass2StarUniforms? = null
    private var pass3Uniforms: Pass3Uniforms? = null
    private var pass4Uniforms: Pass4Uniforms? = null
    private var overlayUniforms: OverlayUniforms? = null

    private var fbo1: FboAttachment? = null
    private var fboHi: FboAttachment? = null
    private var fboHiTemp: FboAttachment? = null
    private var fboStar: FboAttachment? = null
    private var fbo2: FboAttachment? = null

    private val lutCache =
        object : LinkedHashMap<String, CachedLut>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedLut>?): Boolean {
                if (size > MAX_CACHED_LUTS && eldest != null) {
                    GLES30.glDeleteTextures(1, intArrayOf(eldest.value.texId), 0)
                    return true
                }
                return false
            }
        }
    private val tex2DCache = mutableMapOf<String, Int>()
    private var fallbackBlackTex = 0
    private var fallbackBlack3dTex = 0

    private var stampMeta: StampAtlasMeta? = null
    private var grungeMeta: GrungeAtlasMeta? = null
    private var lut2dMeta: Lut2dAtlasMeta? = null

    private var maxTextureSize = 4096
    private var glVendor = ""
    private var glRenderer = ""
    private var glVersion = ""
    private var lastTargetFboSize = "0x0"

    private val shaderStatusesInternal = mutableListOf<PassShaderStatus>()
    private val stampIndicesScratch = IntArray(GlMath.MAX_STAMP_CHARS)
    private val photoPanelsScratch = FloatArray(16)

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

    fun getGrungeMeta(): GrungeAtlasMeta? = grungeMeta

    fun init() {
        glVendor = GLES30.glGetString(GLES30.GL_VENDOR).orEmpty()
        glRenderer = GLES30.glGetString(GLES30.GL_RENDERER).orEmpty()
        glVersion = GLES30.glGetString(GLES30.GL_VERSION).orEmpty()
        val maxTex = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTex, 0)
        maxTextureSize = maxTex[0].coerceAtLeast(256)

        stampMeta = AssetMetadataLoader.loadStampAtlasMeta(assetManager)
        grungeMeta = AssetMetadataLoader.loadGrungeAtlasMeta(assetManager)
        lut2dMeta =
            runCatching { AssetMetadataLoader.loadLut2dMeta(assetManager) }
                .getOrElse { Lut2dAtlasMeta() }

        fallbackBlackTex = createSolidBlackTexture2D()
        fallbackBlack3dTex = createSolidBlackTexture3D()

        // Preload all grunge overlays/frames and all 4 stamp atlases at init rather than mid-draw
        preloadGrungeTextures()
        preloadStampAtlases()

        shaderStatusesInternal.clear()
        if (compileBothPass1Variants || useOesInput) {
            pass1OesProgram =
                compileAndRecord(
                    "Pass 1 (OES) - Geometry, Optics & Bradford WB",
                    GlShaderSources.PASS1_VERTEX_SHADER,
                    GlShaderSources.buildPass1FragmentShader(useOesInput = true)
                )
            if (pass1OesProgram != 0) {
                pass1OesUniforms = linkPass1Uniforms(pass1OesProgram)
            }
        }
        if (compileBothPass1Variants || !useOesInput) {
            pass1Tex2dProgram =
                compileAndRecord(
                    "Pass 1 (2D) - Geometry, Optics & Bradford WB",
                    GlShaderSources.PASS1_VERTEX_SHADER,
                    GlShaderSources.buildPass1FragmentShader(useOesInput = false)
                )
            if (pass1Tex2dProgram != 0) {
                pass1Tex2dUniforms = linkPass1Uniforms(pass1Tex2dProgram)
            }
        }

        pass2ThresholdProgram =
            compileAndRecord(
                "Pass 2A - Highlight Threshold",
                GlShaderSources.QUAD_VERTEX_SHADER,
                GlShaderSources.PASS2_THRESHOLD_FRAGMENT_SHADER
            )
        if (pass2ThresholdProgram != 0) {
            pass2ThresholdUniforms = linkPass2ThresholdUniforms(pass2ThresholdProgram)
        }

        pass2BlurProgram =
            compileAndRecord(
                "Pass 2B - 11-Tap Gaussian Halation Blur",
                GlShaderSources.QUAD_VERTEX_SHADER,
                GlShaderSources.PASS2_BLUR_FRAGMENT_SHADER
            )
        if (pass2BlurProgram != 0) {
            pass2BlurUniforms = linkPass2BlurUniforms(pass2BlurProgram)
        }

        pass2StarProgram =
            compileAndRecord(
                "Pass 2C - Star Diffraction",
                GlShaderSources.QUAD_VERTEX_SHADER,
                GlShaderSources.PASS2_STAR_FRAGMENT_SHADER
            )
        if (pass2StarProgram != 0) {
            pass2StarUniforms = linkPass2StarUniforms(pass2StarProgram)
        }

        pass3Program =
            compileAndRecord(
                "Pass 3 - 2D/3D LUT, EV & Bilateral Composite",
                GlShaderSources.QUAD_VERTEX_SHADER,
                GlShaderSources.PASS3_LUT_COMPOSITE_FRAGMENT_SHADER
            )
        if (pass3Program != 0) {
            pass3Uniforms = linkPass3Uniforms(pass3Program)
        }

        pass4Program =
            compileAndRecord(
                "Pass 4 - Sensor Grain, Frame & Stamp",
                GlShaderSources.QUAD_VERTEX_SHADER,
                GlShaderSources.PASS4_FINAL_FRAGMENT_SHADER
            )
        if (pass4Program != 0) {
            pass4Uniforms = linkPass4Uniforms(pass4Program)
        }

        passOverlayProgram =
            compileAndRecord(
                "Overlay - Multi-Panel Frame & Stamp",
                GlShaderSources.QUAD_VERTEX_SHADER,
                GlShaderSources.PASS_OVERLAY_FRAME_FRAGMENT_SHADER
            )
        if (passOverlayProgram != 0) {
            overlayUniforms = linkOverlayUniforms(passOverlayProgram)
        }
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
        params: GlRenderParams,
        useOes: Boolean = this.useOesInput
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

        val lut = getOrLoadLut(params.profile.lutAssetPath, params.profile.lutCubeAssetPath)
        val targetAspect = safeW.toFloat() / safeH.toFloat()
        val cropScale = GlMath.computeAspectCropScale(params.inputAspectRatio, targetAspect)

        // PASS 1 -> FBO_1
        executePass1(
            fbo1Att = fbo1Att,
            inputTextureId = inputTextureId,
            texMatrix = texMatrix,
            cropScale = cropScale,
            targetAspect = targetAspect,
            params = params,
            useOes = useOes
        )

        // PASS 2 -> FBO_Hi + FBO_Star
        executePass2(fbo1Att, fboHiAtt, fboHiTempAtt, fboStarAtt, params)

        // PASS 3 -> FBO_2
        executePass3(fbo1Att, fboHiAtt, fboStarAtt, fbo2Att, lut, params)

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

    /**
     * Composites a frame border (such as `photo_booth_4strip_frame`) and optional date stamp
     * on top of [baseTextureId] into [outputFboId] of size `[width, height]`.
     */
    fun renderFrameOverlay(
        baseTextureId: Int,
        frameKey: String,
        outputFboId: Int,
        width: Int,
        height: Int,
        params: GlRenderParams
    ) {
        val uniforms = overlayUniforms ?: return
        if (passOverlayProgram == 0) return

        val frameTex = getOrLoadGrungeTex(frameKey)
        val stampAtlasTex = getOrLoadStampAtlasTex(params.stampConfig)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, outputFboId)
        GLES30.glViewport(0, 0, width, height)
        GLES30.glUseProgram(passOverlayProgram)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, baseTextureId)
        GLES30.glUniform1i(uniforms.uBaseTex, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, frameTex)
        GLES30.glUniform1i(uniforms.uFrameTex, 1)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, stampAtlasTex)
        GLES30.glUniform1i(uniforms.uStampAtlasTex, 2)

        GLES30.glUniform1f(uniforms.uTargetAspect, width.toFloat() / height.coerceAtLeast(1).toFloat())

        bindStampUniforms(
            params = params,
            uStampEnabled = uniforms.uStampEnabled,
            uStampLength = uniforms.uStampLength,
            uStampIndices = uniforms.uStampIndices,
            uStampColor = uniforms.uStampColor,
            uStampGlowStrength = uniforms.uStampGlowStrength,
            uStampPosition = uniforms.uStampPosition,
            uStampCleanSans = uniforms.uStampCleanSans,
            uAtlasGrid = uniforms.uAtlasGrid,
            uAtlasSize = uniforms.uAtlasSize,
            uCellSize = uniforms.uCellSize,
            uGlyphPadding = uniforms.uGlyphPadding
        )

        drawQuad()
    }

    private fun executePass1(
        fbo1Att: FboAttachment,
        inputTextureId: Int,
        texMatrix: FloatArray,
        cropScale: Pair<Float, Float>,
        targetAspect: Float,
        params: GlRenderParams,
        useOes: Boolean
    ) {
        val program = if (useOes) pass1OesProgram else pass1Tex2dProgram
        val uniforms = if (useOes) pass1OesUniforms else pass1Tex2dUniforms
        if (program == 0 || uniforms == null) return

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo1Att.fboId)
        GLES30.glViewport(0, 0, fbo1Att.width, fbo1Att.height)
        GLES30.glUseProgram(program)

        val texTarget = if (useOes) GL_TEXTURE_EXTERNAL_OES else GLES30.GL_TEXTURE_2D
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(texTarget, inputTextureId)
        GLES30.glUniform1i(uniforms.uInputTex, 0)
        GLES30.glUniformMatrix4fv(uniforms.uTexMatrix, 1, false, texMatrix, 0)
        GLES30.glUniform2f(uniforms.uCropScale, cropScale.first, cropScale.second)

        val safeInputAspect = params.inputAspectRatio.coerceAtLeast(0.2f)
        GLES30.glUniform1f(uniforms.uInputAspect, safeInputAspect)
        GLES30.glUniform1f(uniforms.uTargetAspect, targetAspect)

        val straightenDeg = params.opticalSettings.straightenDegrees
            .coerceIn(-15f, 15f)
        val straightenRad = (straightenDeg.toDouble() * PI / 180.0).toFloat()
        val straightenScale = GlMath.computeStraightenScale(straightenDeg, safeInputAspect)
        GLES30.glUniform1f(uniforms.uStraightenRad, straightenRad)
        GLES30.glUniform1f(uniforms.uStraightenScale, straightenScale)

        GLES30.glUniform1i(uniforms.uLensMode, params.opticalSettings.lensAttachment.ordinal)

        val fisheyeStrength =
            when (params.opticalSettings.lensAttachment) {
                LensAttachment.FISHEYE -> maxOf(params.profile.optics.fisheyeDefault, 0.85f)
                else -> params.profile.optics.fisheyeDefault
            }
        GLES30.glUniform1f(uniforms.uFisheyeStrength, fisheyeStrength)
        GLES30.glUniform1f(uniforms.uCaStrength, params.profile.optics.caStrength)

        val bradfordMatrix =
            GlMath.computeBradfordWbMatrix(
                temperatureShift = params.opticalSettings.whiteBalance.temperatureShift,
                warmthShift = params.opticalSettings.warmthShift,
                tintShift = params.opticalSettings.whiteBalance.tintShift
            )
        GLES30.glUniformMatrix3fv(uniforms.uBradfordMatrix, 1, false, bradfordMatrix, 0)

        val flashStrength =
            if (params.opticalSettings.flashMode == FlashMode.OPTICAL_FLASH_SIM) 1.0f else 0.0f
        GLES30.glUniform1f(uniforms.uFlashSimStrength, flashStrength)
        GLES30.glUniform1f(uniforms.uHighlights, params.opticalSettings.highlights.coerceIn(-1f, 1f))
        GLES30.glUniform1f(uniforms.uShadows, params.opticalSettings.shadows.coerceIn(-1f, 1f))

        drawQuad()
    }

    private fun executePass2(
        fbo1Att: FboAttachment,
        fboHiAtt: FboAttachment,
        fboHiTempAtt: FboAttachment,
        fboStarAtt: FboAttachment,
        params: GlRenderParams
    ) {
        val thresholdUniforms = pass2ThresholdUniforms ?: return
        val blurUniforms = pass2BlurUniforms ?: return
        val starUniforms = pass2StarUniforms ?: return

        // 2A: Threshold -> FBO_Hi
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboHiAtt.fboId)
        GLES30.glViewport(0, 0, fboHiAtt.width, fboHiAtt.height)
        GLES30.glUseProgram(pass2ThresholdProgram)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fbo1Att.texId)
        GLES30.glUniform1i(thresholdUniforms.uFbo1Tex, 0)

        val effectiveThreshold =
            if (params.opticalSettings.lensAttachment == LensAttachment.SOFT_GLOW) {
                (params.profile.bloom.threshold - 0.16f).coerceAtLeast(0.25f)
            } else {
                params.profile.bloom.threshold
            }
        GLES30.glUniform1f(thresholdUniforms.uBloomThreshold, effectiveThreshold)
        val (tintR, tintG, tintB) = GlMath.parseHexRgb(params.profile.bloom.tintHex)
        GLES30.glUniform3f(thresholdUniforms.uBloomTint, tintR, tintG, tintB)
        drawQuad()

        // 2B: Horizontal 11-tap Gaussian Blur FBO_Hi -> FBO_Hi_Temp
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboHiTempAtt.fboId)
        GLES30.glViewport(0, 0, fboHiTempAtt.width, fboHiTempAtt.height)
        GLES30.glUseProgram(pass2BlurProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboHiAtt.texId)
        GLES30.glUniform1i(blurUniforms.uSourceTex, 0)
        GLES30.glUniform2f(blurUniforms.uTexelStep, 1.35f / fboHiAtt.width.toFloat(), 0f)
        drawQuad()

        // 2B: Vertical 11-tap Gaussian Blur FBO_Hi_Temp -> FBO_Hi
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboHiAtt.fboId)
        GLES30.glViewport(0, 0, fboHiAtt.width, fboHiAtt.height)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboHiTempAtt.texId)
        GLES30.glUniform2f(blurUniforms.uTexelStep, 0f, 1.35f / fboHiAtt.height.toFloat())
        drawQuad()

        // 2C: Star Diffraction -> FBO_Star
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboStarAtt.fboId)
        GLES30.glViewport(0, 0, fboStarAtt.width, fboStarAtt.height)
        GLES30.glUseProgram(pass2StarProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboHiAtt.texId)
        GLES30.glUniform1i(starUniforms.uFboHiTex, 0)
        GLES30.glUniform2f(
            starUniforms.uTexelSize,
            1f / fboHiAtt.width.toFloat(),
            1f / fboHiAtt.height.toFloat()
        )
        val starPoints = resolveStarPoints(params)
        GLES30.glUniform1i(starUniforms.uStarPoints, starPoints)
        drawQuad()
    }

    private fun executePass3(
        fbo1Att: FboAttachment,
        fboHiAtt: FboAttachment,
        fboStarAtt: FboAttachment,
        fbo2Att: FboAttachment,
        lut: CachedLut,
        params: GlRenderParams
    ) {
        val uniforms = pass3Uniforms ?: return
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo2Att.fboId)
        GLES30.glViewport(0, 0, fbo2Att.width, fbo2Att.height)
        GLES30.glUseProgram(pass3Program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fbo1Att.texId)
        GLES30.glUniform1i(uniforms.uFbo1Tex, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (lut.is2d) lut.texId else fallbackBlackTex)
        GLES30.glUniform1i(uniforms.uLut2dTex, 1)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, if (!lut.is2d) lut.texId else fallbackBlack3dTex)
        GLES30.glUniform1i(uniforms.uLut3dTex, 2)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboHiAtt.texId)
        GLES30.glUniform1i(uniforms.uFboHiTex, 3)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboStarAtt.texId)
        GLES30.glUniform1i(uniforms.uFboStarTex, 4)

        GLES30.glUniform1i(uniforms.uUse2dLut, if (lut.is2d) 1 else 0)
        GLES30.glUniform1f(uniforms.uLutSize, lut.cubeSize.toFloat())
        GLES30.glUniform2f(uniforms.uLutTiles, lut.tilesX.toFloat(), lut.tilesX.toFloat())
        GLES30.glUniform2f(uniforms.uLutTexSize, lut.texWidth.toFloat(), lut.texHeight.toFloat())

        val evStops = params.fallbackGains.evGainStops + params.fallbackGains.digitalBoostStops
        GLES30.glUniform1f(uniforms.uEvStops, evStops)

        val baseBloom =
            (params.profile.bloom.intensity * 0.5f + params.opticalSettings.bloomIntensity * 0.5f)
        val bloomIntensity =
            if (params.opticalSettings.lensAttachment == LensAttachment.SOFT_GLOW) {
                (baseBloom + 0.40f).coerceIn(0f, 1.5f)
            } else {
                baseBloom.coerceIn(0f, 1.5f)
            }
        GLES30.glUniform1f(uniforms.uBloomIntensity, bloomIntensity)
        val starIntensity = if (resolveStarPoints(params) > 0) 0.9f else 0f
        GLES30.glUniform1f(uniforms.uStarIntensity, starIntensity)
        GLES30.glUniform1f(uniforms.uBeautySoftness, params.opticalSettings.beautySoftness.coerceIn(0f, 1f))
        GLES30.glUniform1f(uniforms.uSharpenStrength, params.opticalSettings.sharpen.coerceIn(0f, 1f))
        GLES30.glUniform2f(uniforms.uTexelSize, 1f / fbo1Att.width.toFloat(), 1f / fbo1Att.height.toFloat())

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
        val uniforms = pass4Uniforms ?: return
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, outputFboId)
        GLES30.glViewport(viewportX, viewportY, viewportWidth, viewportHeight)
        GLES30.glUseProgram(pass4Program)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fbo2Att.texId)
        GLES30.glUniform1i(uniforms.uFbo2Tex, 0)

        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fbo1Att.texId)
        GLES30.glUniform1i(uniforms.uFbo1Tex, 1)

        val leakKey = if ((params.randomSeed and 1L) == 0L) "light_leak_warm" else "light_leak_edge"
        val leakTex = getOrLoadGrungeTex(leakKey)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, leakTex)
        GLES30.glUniform1i(uniforms.uLightLeakTex, 2)

        val dustTex = getOrLoadGrungeTex("dust_scratches")
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dustTex)
        GLES30.glUniform1i(uniforms.uDustTex, 3)

        val dvScanlineTex = getOrLoadGrungeTex("dv_scanlines")
        GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dvScanlineTex)
        GLES30.glUniform1i(uniforms.uDvScanlineTex, 4)

        val (frameMode, frameTex, frameWindow) = resolveFrame(params.opticalSettings.frameStyle)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE5)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, frameTex)
        GLES30.glUniform1i(uniforms.uFrameTex, 5)

        val stampAtlasTex = getOrLoadStampAtlasTex(params.stampConfig)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE6)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, stampAtlasTex)
        GLES30.glUniform1i(uniforms.uStampAtlasTex, 6)

        GLES30.glUniform2f(uniforms.uOutputSize, targetWidth.toFloat(), targetHeight.toFloat())
        GLES30.glUniform1f(uniforms.uTargetAspect, targetAspect)
        val grainTypeInt = if (params.profile.grain.type == GrainType.CCD_CHROMA_LUMA) 0 else 1
        GLES30.glUniform1i(uniforms.uGrainType, grainTypeInt)
        GLES30.glUniform1f(uniforms.uLumaGrainStrength, params.profile.grain.lumaStrength)
        GLES30.glUniform1f(uniforms.uChromaGrainStrength, params.profile.grain.chromaStrength)
        GLES30.glUniform1f(uniforms.uShaderNoiseBoost, params.fallbackGains.shaderNoiseBoost)

        val vigStrength =
            maxOf(params.profile.optics.vignetteStrength, params.opticalSettings.vignetteIntensity)
        GLES30.glUniform1f(uniforms.uVignetteStrength, vigStrength)

        val leakIntensity =
            maxOf(params.profile.grunge.lightLeakStrength, params.opticalSettings.lightLeakIntensity)
        val dustIntensity =
            maxOf(params.profile.grunge.dustStrength, params.opticalSettings.dustIntensity)
        GLES30.glUniform1f(uniforms.uLightLeakIntensity, leakIntensity)
        GLES30.glUniform1f(uniforms.uDustIntensity, dustIntensity)
        GLES30.glUniform1f(uniforms.uRandomSeed, (params.randomSeed and 0xFFFFL).toFloat())

        GLES30.glUniform1i(uniforms.uHasFrame, frameMode)
        if (frameWindow != null) {
            GLES30.glUniform4f(
                uniforms.uFrameWindow,
                frameWindow.uMin,
                frameWindow.vMin,
                frameWindow.uMax,
                frameWindow.vMax
            )
            if (frameMode == FRAME_MODE_PHOTO_BOOTH && uniforms.uPhotoPanels >= 0) {
                val panels = GlMath.computeFramePanelsNormalized(frameWindow)
                photoPanelsScratch.fill(0f)
                for (i in 0 until minOf(4, panels.size)) {
                    val p = panels[i]
                    photoPanelsScratch[i * 4] = p[0]
                    photoPanelsScratch[i * 4 + 1] = p[1]
                    photoPanelsScratch[i * 4 + 2] = p[2]
                    photoPanelsScratch[i * 4 + 3] = p[3]
                }
                GLES30.glUniform4fv(uniforms.uPhotoPanels, 4, photoPanelsScratch, 0)
            }
        } else {
            GLES30.glUniform4f(uniforms.uFrameWindow, 0f, 0f, 1f, 1f)
        }

        bindStampUniforms(
            params = params,
            uStampEnabled = uniforms.uStampEnabled,
            uStampLength = uniforms.uStampLength,
            uStampIndices = uniforms.uStampIndices,
            uStampColor = uniforms.uStampColor,
            uStampGlowStrength = uniforms.uStampGlowStrength,
            uStampPosition = uniforms.uStampPosition,
            uStampCleanSans = uniforms.uStampCleanSans,
            uAtlasGrid = uniforms.uAtlasGrid,
            uAtlasSize = uniforms.uAtlasSize,
            uCellSize = uniforms.uCellSize,
            uGlyphPadding = uniforms.uGlyphPadding
        )

        GLES30.glUniform1f(uniforms.uSplitCompareRatio, params.splitCompareRatio.coerceIn(0f, 1f))
        GLES30.glUniform1f(uniforms.uHoldOriginal, if (params.isPreviewHoldOriginal) 1.0f else 0.0f)

        drawQuad()
    }

    private fun bindStampUniforms(
        params: GlRenderParams,
        uStampEnabled: Int,
        uStampLength: Int,
        uStampIndices: Int,
        uStampColor: Int,
        uStampGlowStrength: Int,
        uStampPosition: Int,
        uStampCleanSans: Int,
        uAtlasGrid: Int,
        uAtlasSize: Int,
        uCellSize: Int,
        uGlyphPadding: Int
    ) {
        val meta = stampMeta
        val enabled =
            !params.skipStamp &&
                params.stampConfig.enabled &&
                params.stampText.isNotEmpty() &&
                meta != null
        GLES30.glUniform1f(uStampEnabled, if (enabled) 1.0f else 0.0f)
        if (!enabled) {
            GLES30.glUniform1i(uStampLength, 0)
            return
        }
        val activeMeta = checkNotNull(meta)

        val indices = GlMath.encodeStampIndices(activeMeta, params.stampText, GlMath.MAX_STAMP_CHARS)
        stampIndicesScratch.fill(0)
        indices.copyInto(stampIndicesScratch)
        GLES30.glUniform1i(uStampLength, indices.size)
        GLES30.glUniform1iv(uStampIndices, GlMath.MAX_STAMP_CHARS, stampIndicesScratch, 0)

        val (sr, sg, sb) = GlMath.parseHexRgb(params.stampConfig.style.hexColor)
        GLES30.glUniform3f(uStampColor, sr, sg, sb)
        GLES30.glUniform1f(uStampGlowStrength, params.stampConfig.glowStrength)
        GLES30.glUniform1i(uStampPosition, params.stampConfig.position.ordinal)
        GLES30.glUniform1f(
            uStampCleanSans,
            if (params.stampConfig.fontStyle == StampFontStyle.CLEAN_SANS) 1.0f else 0.0f
        )
        GLES30.glUniform2f(uAtlasGrid, activeMeta.columns.toFloat(), activeMeta.rows.toFloat())
        GLES30.glUniform2f(uAtlasSize, activeMeta.atlasWidth.toFloat(), activeMeta.atlasHeight.toFloat())
        GLES30.glUniform2f(uCellSize, activeMeta.cellWidth.toFloat(), activeMeta.cellHeight.toFloat())
        GLES30.glUniform1f(uGlyphPadding, activeMeta.glyphPadding.toFloat())
    }

    private fun resolveStarPoints(params: GlRenderParams): Int = when (params.opticalSettings.lensAttachment) {
        LensAttachment.STAR_4PT -> 4
        LensAttachment.STAR_6PT -> 6
        else -> params.profile.optics.starPoints
    }

    private fun resolveFrame(style: FrameBorderStyle): Triple<Int, Int, FrameWindowMeta?> {
        val meta = grungeMeta ?: return Triple(FRAME_MODE_NONE, fallbackBlackTex, null)
        val key =
            when (style) {
                FrameBorderStyle.NONE -> return Triple(FRAME_MODE_NONE, fallbackBlackTex, null)
                FrameBorderStyle.DV_TIMECODE -> return Triple(FRAME_MODE_DV_TIMECODE, fallbackBlackTex, null)
                FrameBorderStyle.INSTANT_PRINT -> "instant_frame"
                FrameBorderStyle.INSTANT_WIDE -> "instant_wide_frame"
                FrameBorderStyle.POLAROID_SQUARE -> "polaroid_square_frame"
                FrameBorderStyle.FILM_35MM -> "film_border_35mm"
                FrameBorderStyle.CINE_16MM -> "cine_16mm_frame"
                FrameBorderStyle.PHOTO_BOOTH_4STRIP -> "photo_booth_4strip_frame"
            }
        val window = meta.frames[key] ?: return Triple(FRAME_MODE_NONE, fallbackBlackTex, null)
        val texId = getOrLoadGrungeTex(key)
        val mode =
            if (style == FrameBorderStyle.PHOTO_BOOTH_4STRIP && window.panels.size >= 4) {
                FRAME_MODE_PHOTO_BOOTH
            } else {
                FRAME_MODE_SINGLE_WINDOW
            }
        return Triple(mode, texId, window)
    }

    private fun getOrLoadLut(primaryAssetPath: String, cubeFallbackPath: String?): CachedLut {
        val resolvedPath = primaryAssetPath.ifBlank { cubeFallbackPath.orEmpty() }
        lutCache[resolvedPath]?.let { return it }

        val loaded =
            if (resolvedPath.endsWith(".cube", ignoreCase = true)) {
                loadCubeLut(resolvedPath)
            } else {
                runCatching { load2dPngLut(resolvedPath) }
                    .getOrElse { err ->
                        val fallback = cubeFallbackPath?.takeIf { it.isNotBlank() }
                        if (fallback != null) {
                            loadCubeLut(fallback)
                        } else {
                            throw err
                        }
                    }
            }
        lutCache[resolvedPath] = loaded
        return loaded
    }

    private fun load2dPngLut(assetPath: String): CachedLut {
        val bmp = AssetMetadataLoader.decodeAssetBitmap(assetManager, assetPath)
        val w = bmp.width
        val h = bmp.height
        val texId = AssetMetadataLoader.uploadTexture2D(bmp, recycleBitmap = true, clampToEdge = true)
        val meta = lut2dMeta
        val cubeSize = meta?.cubeSize ?: DEFAULT_LUT_CUBE_SIZE
        val tilesX = meta?.tilesX ?: DEFAULT_LUT_TILES_X
        return CachedLut(
            texId = texId,
            is2d = true,
            cubeSize = cubeSize,
            tilesX = tilesX,
            texWidth = w,
            texHeight = h
        )
    }

    private fun loadCubeLut(assetPath: String): CachedLut {
        val lut = CubeLutParser.loadFromAssets(assetManager, assetPath)
        val texId = CubeLutParser.uploadToTexture3D(lut)
        return CachedLut(
            texId = texId,
            is2d = false,
            cubeSize = lut.size,
            tilesX = 1,
            texWidth = lut.size,
            texHeight = lut.size
        )
    }

    private fun preloadGrungeTextures() {
        val textures = grungeMeta?.textures ?: return
        for (key in textures.keys) {
            getOrLoadGrungeTex(key)
        }
    }

    private fun preloadStampAtlases() {
        val atlases = stampMeta?.atlases ?: return
        for (assetPath in atlases.values) {
            tex2DCache.getOrPut(assetPath) {
                val bmp = AssetMetadataLoader.decodeAssetBitmap(assetManager, assetPath)
                AssetMetadataLoader.uploadTexture2D(bmp, recycleBitmap = true)
            }
        }
    }

    private fun getOrLoadGrungeTex(key: String): Int {
        val assetPath = grungeMeta?.textures?.get(key) ?: return fallbackBlackTex
        return tex2DCache.getOrPut(assetPath) {
            val bmp = AssetMetadataLoader.decodeAssetBitmap(assetManager, assetPath)
            AssetMetadataLoader.uploadTexture2D(bmp, recycleBitmap = true)
        }
    }

    private fun getOrLoadStampAtlasTex(stampConfig: StampConfig): Int {
        val atlasKey =
            when (stampConfig.fontStyle) {
                StampFontStyle.DOT_MATRIX -> {
                    if (stampConfig.style == StampStyle.SEVEN_SEGMENT_EMERALD) "seven_segment" else "dot_matrix"
                }
                StampFontStyle.SEVEN_SEGMENT -> "seven_segment"
                StampFontStyle.VCR_OSD -> "vcr_osd"
                StampFontStyle.PIXEL -> "pixel"
                StampFontStyle.CLEAN_SANS -> "pixel"
            }
        val assetPath = stampMeta?.atlases?.get(atlasKey) ?: return fallbackBlackTex
        return tex2DCache.getOrPut(assetPath) {
            val bmp = AssetMetadataLoader.decodeAssetBitmap(assetManager, assetPath)
            AssetMetadataLoader.uploadTexture2D(bmp, recycleBitmap = true)
        }
    }

    private fun linkPass1Uniforms(program: Int): Pass1Uniforms = Pass1Uniforms(
        uInputTex = uniformLoc(program, "uInputTex"),
        uTexMatrix = uniformLoc(program, "uTexMatrix"),
        uCropScale = uniformLoc(program, "uCropScale"),
        uInputAspect = uniformLoc(program, "uInputAspect"),
        uTargetAspect = uniformLoc(program, "uTargetAspect"),
        uStraightenRad = uniformLoc(program, "uStraightenRad"),
        uStraightenScale = uniformLoc(program, "uStraightenScale"),
        uLensMode = uniformLoc(program, "uLensMode"),
        uFisheyeStrength = uniformLoc(program, "uFisheyeStrength"),
        uCaStrength = uniformLoc(program, "uCaStrength"),
        uBradfordMatrix = uniformLoc(program, "uBradfordMatrix"),
        uFlashSimStrength = uniformLoc(program, "uFlashSimStrength"),
        uHighlights = uniformLoc(program, "uHighlights"),
        uShadows = uniformLoc(program, "uShadows")
    )

    private fun linkPass2ThresholdUniforms(program: Int): Pass2ThresholdUniforms = Pass2ThresholdUniforms(
        uFbo1Tex = uniformLoc(program, "uFbo1Tex"),
        uBloomThreshold = uniformLoc(program, "uBloomThreshold"),
        uBloomTint = uniformLoc(program, "uBloomTint")
    )

    private fun linkPass2BlurUniforms(program: Int): Pass2BlurUniforms = Pass2BlurUniforms(
        uSourceTex = uniformLoc(program, "uSourceTex"),
        uTexelStep = uniformLoc(program, "uTexelStep")
    )

    private fun linkPass2StarUniforms(program: Int): Pass2StarUniforms = Pass2StarUniforms(
        uFboHiTex = uniformLoc(program, "uFboHiTex"),
        uTexelSize = uniformLoc(program, "uTexelSize"),
        uStarPoints = uniformLoc(program, "uStarPoints")
    )

    private fun linkPass3Uniforms(program: Int): Pass3Uniforms = Pass3Uniforms(
        uFbo1Tex = uniformLoc(program, "uFbo1Tex"),
        uLut2dTex = uniformLoc(program, "uLut2dTex"),
        uLut3dTex = uniformLoc(program, "uLut3dTex"),
        uUse2dLut = uniformLoc(program, "uUse2dLut"),
        uLutSize = uniformLoc(program, "uLutSize"),
        uLutTiles = uniformLoc(program, "uLutTiles"),
        uLutTexSize = uniformLoc(program, "uLutTexSize"),
        uEvStops = uniformLoc(program, "uEvStops"),
        uFboHiTex = uniformLoc(program, "uFboHiTex"),
        uFboStarTex = uniformLoc(program, "uFboStarTex"),
        uBloomIntensity = uniformLoc(program, "uBloomIntensity"),
        uStarIntensity = uniformLoc(program, "uStarIntensity"),
        uBeautySoftness = uniformLoc(program, "uBeautySoftness"),
        uSharpenStrength = uniformLoc(program, "uSharpenStrength"),
        uTexelSize = uniformLoc(program, "uTexelSize")
    )

    private fun linkPass4Uniforms(program: Int): Pass4Uniforms = Pass4Uniforms(
        uFbo2Tex = uniformLoc(program, "uFbo2Tex"),
        uFbo1Tex = uniformLoc(program, "uFbo1Tex"),
        uLightLeakTex = uniformLoc(program, "uLightLeakTex"),
        uDustTex = uniformLoc(program, "uDustTex"),
        uDvScanlineTex = uniformLoc(program, "uDvScanlineTex"),
        uFrameTex = uniformLoc(program, "uFrameTex"),
        uStampAtlasTex = uniformLoc(program, "uStampAtlasTex"),
        uOutputSize = uniformLoc(program, "uOutputSize"),
        uTargetAspect = uniformLoc(program, "uTargetAspect"),
        uGrainType = uniformLoc(program, "uGrainType"),
        uLumaGrainStrength = uniformLoc(program, "uLumaGrainStrength"),
        uChromaGrainStrength = uniformLoc(program, "uChromaGrainStrength"),
        uShaderNoiseBoost = uniformLoc(program, "uShaderNoiseBoost"),
        uVignetteStrength = uniformLoc(program, "uVignetteStrength"),
        uLightLeakIntensity = uniformLoc(program, "uLightLeakIntensity"),
        uDustIntensity = uniformLoc(program, "uDustIntensity"),
        uRandomSeed = uniformLoc(program, "uRandomSeed"),
        uHasFrame = uniformLoc(program, "uHasFrame"),
        uFrameWindow = uniformLoc(program, "uFrameWindow"),
        uPhotoPanels = uniformArrayLoc(program, "uPhotoPanels"),
        uStampEnabled = uniformLoc(program, "uStampEnabled"),
        uStampLength = uniformLoc(program, "uStampLength"),
        uStampIndices = uniformArrayLoc(program, "uStampIndices"),
        uStampColor = uniformLoc(program, "uStampColor"),
        uStampGlowStrength = uniformLoc(program, "uStampGlowStrength"),
        uStampPosition = uniformLoc(program, "uStampPosition"),
        uStampCleanSans = uniformLoc(program, "uStampCleanSans"),
        uAtlasGrid = uniformLoc(program, "uAtlasGrid"),
        uAtlasSize = uniformLoc(program, "uAtlasSize"),
        uCellSize = uniformLoc(program, "uCellSize"),
        uGlyphPadding = uniformLoc(program, "uGlyphPadding"),
        uSplitCompareRatio = uniformLoc(program, "uSplitCompareRatio"),
        uHoldOriginal = uniformLoc(program, "uHoldOriginal")
    )

    private fun linkOverlayUniforms(program: Int): OverlayUniforms = OverlayUniforms(
        uBaseTex = uniformLoc(program, "uBaseTex"),
        uFrameTex = uniformLoc(program, "uFrameTex"),
        uStampAtlasTex = uniformLoc(program, "uStampAtlasTex"),
        uTargetAspect = uniformLoc(program, "uTargetAspect"),
        uStampEnabled = uniformLoc(program, "uStampEnabled"),
        uStampLength = uniformLoc(program, "uStampLength"),
        uStampIndices = uniformArrayLoc(program, "uStampIndices"),
        uStampColor = uniformLoc(program, "uStampColor"),
        uStampGlowStrength = uniformLoc(program, "uStampGlowStrength"),
        uStampPosition = uniformLoc(program, "uStampPosition"),
        uStampCleanSans = uniformLoc(program, "uStampCleanSans"),
        uAtlasGrid = uniformLoc(program, "uAtlasGrid"),
        uAtlasSize = uniformLoc(program, "uAtlasSize"),
        uCellSize = uniformLoc(program, "uCellSize"),
        uGlyphPadding = uniformLoc(program, "uGlyphPadding")
    )

    private fun uniformLoc(program: Int, name: String): Int = GLES30.glGetUniformLocation(program, name)

    private fun uniformArrayLoc(program: Int, baseName: String): Int {
        val direct = GLES30.glGetUniformLocation(program, baseName)
        return if (direct >= 0) direct else GLES30.glGetUniformLocation(program, "$baseName[0]")
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

    private fun createSolidBlackTexture2D(): Int {
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

    private fun createSolidBlackTexture3D(): Int {
        val texIds = IntArray(1)
        GLES30.glGenTextures(1, texIds, 0)
        val texId = texIds[0]
        val pixel =
            ByteBuffer
                .allocateDirect(4)
                .order(ByteOrder.nativeOrder())
                .put(byteArrayOf(0, 0, 0, 255.toByte()))
                .apply { position(0) }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexImage3D(
            GLES30.GL_TEXTURE_3D,
            0,
            GLES30.GL_RGBA8,
            1,
            1,
            1,
            0,
            GLES30.GL_RGBA,
            GLES30.GL_UNSIGNED_BYTE,
            pixel
        )
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, 0)
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
            pass1OesProgram,
            pass1Tex2dProgram,
            pass2ThresholdProgram,
            pass2BlurProgram,
            pass2StarProgram,
            pass3Program,
            pass4Program,
            passOverlayProgram
        ).filter { it != 0 }.forEach { GLES30.glDeleteProgram(it) }
        pass1OesProgram = 0
        pass1Tex2dProgram = 0
        pass2ThresholdProgram = 0
        pass2BlurProgram = 0
        pass2StarProgram = 0
        pass3Program = 0
        pass4Program = 0
        passOverlayProgram = 0
        pass1OesUniforms = null
        pass1Tex2dUniforms = null
        pass2ThresholdUniforms = null
        pass2BlurUniforms = null
        pass2StarUniforms = null
        pass3Uniforms = null
        pass4Uniforms = null
        overlayUniforms = null

        lutCache.values.forEach { GLES30.glDeleteTextures(1, intArrayOf(it.texId), 0) }
        lutCache.clear()
        tex2DCache.values.forEach { texId -> GLES30.glDeleteTextures(1, intArrayOf(texId), 0) }
        tex2DCache.clear()
        if (fallbackBlackTex != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(fallbackBlackTex), 0)
            fallbackBlackTex = 0
        }
        if (fallbackBlack3dTex != 0) {
            GLES30.glDeleteTextures(1, intArrayOf(fallbackBlack3dTex), 0)
            fallbackBlack3dTex = 0
        }
    }

    companion object {
        private const val GL_TEXTURE_EXTERNAL_OES = 0x8D65
        private const val MAX_CACHED_LUTS = 96
        private const val DEFAULT_LUT_CUBE_SIZE = 64
        private const val DEFAULT_LUT_TILES_X = 8

        private const val FRAME_MODE_NONE = 0
        private const val FRAME_MODE_SINGLE_WINDOW = 1
        private const val FRAME_MODE_DV_TIMECODE = 2
        private const val FRAME_MODE_PHOTO_BOOTH = 3

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
