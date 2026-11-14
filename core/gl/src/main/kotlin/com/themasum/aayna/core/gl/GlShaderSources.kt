package com.themasum.aayna.core.gl

object GlShaderSources {
    val PASS1_UNIFORM_NAMES: List<String> =
        listOf(
            "uInputTex",
            "uTexMatrix",
            "uCropScale",
            "uInputAspect",
            "uTargetAspect",
            "uStraightenRad",
            "uStraightenScale",
            "uLensMode",
            "uFisheyeStrength",
            "uCaStrength",
            "uBradfordMatrix",
            "uFlashSimStrength",
            "uHighlights",
            "uShadows"
        )

    val PASS2_THRESHOLD_UNIFORM_NAMES: List<String> =
        listOf(
            "uFbo1Tex",
            "uBloomThreshold",
            "uBloomTint"
        )

    val PASS2_BLUR_UNIFORM_NAMES: List<String> =
        listOf(
            "uSourceTex",
            "uTexelStep"
        )

    val PASS2_STAR_UNIFORM_NAMES: List<String> =
        listOf(
            "uFboHiTex",
            "uTexelSize",
            "uStarPoints"
        )

    val PASS3_UNIFORM_NAMES: List<String> =
        listOf(
            "uFbo1Tex",
            "uLut2dTex",
            "uLut3dTex",
            "uUse2dLut",
            "uLutSize",
            "uLutTiles",
            "uLutTexSize",
            "uEvStops",
            "uFboHiTex",
            "uFboStarTex",
            "uBloomIntensity",
            "uStarIntensity",
            "uBeautySoftness",
            "uSharpenStrength",
            "uTexelSize"
        )

    val PASS4_UNIFORM_NAMES: List<String> =
        listOf(
            "uFbo2Tex",
            "uFbo1Tex",
            "uLightLeakTex",
            "uDustTex",
            "uDvScanlineTex",
            "uFrameTex",
            "uStampAtlasTex",
            "uOutputSize",
            "uTargetAspect",
            "uGrainType",
            "uLumaGrainStrength",
            "uChromaGrainStrength",
            "uShaderNoiseBoost",
            "uVignetteStrength",
            "uLightLeakIntensity",
            "uDustIntensity",
            "uRandomSeed",
            "uHasFrame",
            "uFrameWindow",
            "uPhotoPanels",
            "uStampEnabled",
            "uStampLength",
            "uStampIndices",
            "uStampColor",
            "uStampGlowStrength",
            "uStampPosition",
            "uStampCleanSans",
            "uAtlasGrid",
            "uAtlasSize",
            "uCellSize",
            "uGlyphPadding",
            "uSplitCompareRatio",
            "uHoldOriginal"
        )

    val PASS_OVERLAY_UNIFORM_NAMES: List<String> =
        listOf(
            "uBaseTex",
            "uFrameTex",
            "uStampAtlasTex",
            "uTargetAspect",
            "uStampEnabled",
            "uStampLength",
            "uStampIndices",
            "uStampColor",
            "uStampGlowStrength",
            "uStampPosition",
            "uStampCleanSans",
            "uAtlasGrid",
            "uAtlasSize",
            "uCellSize",
            "uGlyphPadding"
        )

    const val PASS1_VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;
out vec2 vFboUv;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vFboUv = aTexCoord;
}
"""

    const val QUAD_VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec2 aPosition;
layout(location = 1) in vec2 aTexCoord;
out vec2 vTexCoord;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTexCoord = aTexCoord;
}
"""

    fun buildPass1FragmentShader(useOesInput: Boolean): String {
        val oesDefine = if (useOesInput) 1 else 0
        return """#version 300 es
#define USE_OES_INPUT $oesDefine
#if USE_OES_INPUT
#extension GL_OES_EGL_image_external_essl3 : require
#endif
precision highp float;

in vec2 vFboUv;
out vec4 fragColor;

#if USE_OES_INPUT
uniform samplerExternalOES uInputTex;
#else
uniform sampler2D uInputTex;
#endif

uniform mat4 uTexMatrix;
uniform vec2 uCropScale;
uniform float uInputAspect;
uniform float uTargetAspect;
uniform float uStraightenRad;
uniform float uStraightenScale;
uniform int uLensMode;
uniform float uFisheyeStrength;
uniform float uCaStrength;
uniform mat3 uBradfordMatrix;
uniform float uFlashSimStrength;
uniform float uHighlights;
uniform float uShadows;

vec2 toSourceUv(vec2 fboUv) {
    return (uTexMatrix * vec4(fboUv, 0.0, 1.0)).xy;
}

void main() {
    vec2 uv = vFboUv;
    float edgeMask = 1.0;

    // Multi-lens contact grid modes (7 = GRID_2X, 8 = GRID_4X)
    if (uLensMode == 7) {
        uv = fract(uv * 2.0);
        float border = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
        edgeMask *= smoothstep(0.0, 0.012, border);
    } else if (uLensMode == 8) {
        uv = fract(uv * 4.0);
        float border = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
        edgeMask *= smoothstep(0.0, 0.018, border);
    }

    // 6-fold kaleidoscope symmetry (6 = KALEIDOSCOPE)
    if (uLensMode == 6) {
        vec2 p = (uv - 0.5) * vec2(uTargetAspect, 1.0);
        float r = length(p);
        float theta = atan(p.y, p.x);
        float sector = 1.04719755; // PI / 3.0
        theta = mod(theta + 6.2831853, sector);
        if (theta > sector * 0.5) {
            theta = sector - theta;
        }
        vec2 kp = vec2(cos(theta), sin(theta)) * r;
        uv = clamp(kp / vec2(uTargetAspect, 1.0) + 0.5, 0.0, 1.0);
    }

    // Fisheye barrel distortion
    if (uFisheyeStrength > 0.001) {
        vec2 centered = (uv - 0.5) * 2.0;
        centered.x *= uTargetAspect;
        float r = length(centered);
        float rNorm = r / max(uTargetAspect, 1.0);
        float k = uFisheyeStrength * 0.42;
        float rWarped = r * (1.0 + k * r * r);
        vec2 warped = (r > 1e-5) ? (centered * (rWarped / r)) : centered;
        warped.x /= uTargetAspect;
        uv = warped * 0.5 + 0.5;
        if (uFisheyeStrength > 0.4) {
            edgeMask *= 1.0 - smoothstep(0.92, 1.04, rNorm * (1.0 + uFisheyeStrength * 0.15));
        }
    }

    vec2 croppedUv = (uv - 0.5) * uCropScale + 0.5;

    // Straighten rotation with inscribed auto-zoom to avoid black corner triangles
    if (abs(uStraightenRad) > 0.0001) {
        float safeInputAspect = max(uInputAspect, 0.2);
        vec2 sp = (croppedUv - 0.5) * vec2(safeInputAspect, 1.0) * uStraightenScale;
        float cR = cos(uStraightenRad);
        float sR = sin(uStraightenRad);
        vec2 rotP = vec2(sp.x * cR - sp.y * sR, sp.x * sR + sp.y * cR);
        croppedUv = rotP / vec2(safeInputAspect, 1.0) + 0.5;
    }

    vec2 delta = croppedUv - 0.5;
    float r2 = dot(delta, delta);
    vec2 caOffset = delta * r2 * (uCaStrength * 0.045);

    vec2 uvR = clamp(croppedUv + caOffset, 0.0, 1.0);
    vec2 uvG = clamp(croppedUv, 0.0, 1.0);
    vec2 uvB = clamp(croppedUv - caOffset, 0.0, 1.0);

    float rChan = texture(uInputTex, toSourceUv(uvR)).r;
    float gChan = texture(uInputTex, toSourceUv(uvG)).g;
    float bChan = texture(uInputTex, toSourceUv(uvB)).b;
    vec3 color = vec3(rChan, gChan, bChan);

    // Prism spectral dispersion refraction (5 = PRISM)
    if (uLensMode == 5) {
        float pEdge = smoothstep(0.14, 0.72, length(delta));
        vec2 pShift = vec2(0.065, -0.045) * (0.35 + pEdge);
        float prR = texture(uInputTex, toSourceUv(clamp(croppedUv + pShift * 1.18, 0.0, 1.0))).r;
        float prG = texture(uInputTex, toSourceUv(clamp(croppedUv + pShift, 0.0, 1.0))).g;
        float prB = texture(uInputTex, toSourceUv(clamp(croppedUv + pShift * 0.82, 0.0, 1.0))).b;
        vec3 prismColor = vec3(prR, prG, prB);
        color = mix(color, max(color, prismColor), 0.35 + 0.45 * pEdge);
    }

    // Bradford XYZ Chromatic Adaptation White Balance (linear sRGB space)
    vec3 lin = pow(max(color, vec3(0.0)), vec3(2.2));
    lin = max(uBradfordMatrix * lin, vec3(0.0));
    color = pow(lin, vec3(1.0 / 2.2));

    // Tonal Highlights & Shadows curve
    if (abs(uShadows) > 0.001 || abs(uHighlights) > 0.001) {
        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        float shadowMask = pow(1.0 - clamp(luma, 0.0, 1.0), 2.2);
        float highlightMask = pow(clamp(luma, 0.0, 1.0), 2.2);
        float tonalStops = uShadows * shadowMask * 1.10 + uHighlights * highlightMask * 0.90;
        color *= exp2(tonalStops);
    }

    // Direct-Flash Simulation: radial spotlight punch + dark background falloff
    if (uFlashSimStrength > 0.001) {
        vec2 flashPos = (vFboUv - 0.5) * vec2(uTargetAspect, 1.0);
        float d = length(flashPos) * 1.45;
        float centerPunch = exp(-d * d * 2.4) * 0.52 * uFlashSimStrength;
        float bgFalloff = mix(1.0, smoothstep(1.45, 0.25, d) * 0.62 + 0.38, uFlashSimStrength);
        color *= (bgFalloff + centerPunch);
        float flashLuma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        color = mix(color, color * vec3(0.98, 1.0, 1.04), smoothstep(0.6, 1.0, flashLuma) * 0.35 * uFlashSimStrength);
    }

    color *= edgeMask;
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
"""
    }

    const val PASS2_THRESHOLD_FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uFbo1Tex;
uniform float uBloomThreshold;
uniform vec3 uBloomTint;

void main() {
    vec3 c = texture(uFbo1Tex, vTexCoord).rgb;
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float w = smoothstep(uBloomThreshold - 0.08, uBloomThreshold + 0.12, luma);
    fragColor = vec4(c * w * uBloomTint, 1.0);
}
"""

    // 11-tap separable Gaussian halation blur (sigma ~ 2.5, normalized sum = 1.0)
    const val PASS2_BLUR_FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uSourceTex;
uniform vec2 uTexelStep;

void main() {
    vec3 sum = texture(uSourceTex, vTexCoord).rgb * 0.164466;
    sum += (texture(uSourceTex, vTexCoord + uTexelStep * 1.0).rgb +
            texture(uSourceTex, vTexCoord - uTexelStep * 1.0).rgb) * 0.151655;
    sum += (texture(uSourceTex, vTexCoord + uTexelStep * 2.0).rgb +
            texture(uSourceTex, vTexCoord - uTexelStep * 2.0).rgb) * 0.119115;
    sum += (texture(uSourceTex, vTexCoord + uTexelStep * 3.0).rgb +
            texture(uSourceTex, vTexCoord - uTexelStep * 3.0).rgb) * 0.079655;
    sum += (texture(uSourceTex, vTexCoord + uTexelStep * 4.0).rgb +
            texture(uSourceTex, vTexCoord - uTexelStep * 4.0).rgb) * 0.045350;
    sum += (texture(uSourceTex, vTexCoord + uTexelStep * 5.0).rgb +
            texture(uSourceTex, vTexCoord - uTexelStep * 5.0).rgb) * 0.021992;
    fragColor = vec4(sum, 1.0);
}
"""

    const val PASS2_STAR_FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uFboHiTex;
uniform vec2 uTexelSize;
uniform int uStarPoints;

vec3 sampleStreak(vec2 dir) {
    vec3 acc = vec3(0.0);
    for (int i = 1; i <= 6; i++) {
        float fi = float(i);
        float w = exp(-fi * 0.35) * 0.22;
        vec2 offset = dir * (fi * 3.0) * uTexelSize;
        acc += texture(uFboHiTex, clamp(vTexCoord + offset, 0.0, 1.0)).rgb * w;
        acc += texture(uFboHiTex, clamp(vTexCoord - offset, 0.0, 1.0)).rgb * w;
    }
    return acc;
}

void main() {
    if (uStarPoints <= 0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    vec3 star = vec3(0.0);
    if (uStarPoints == 4) {
        star += sampleStreak(vec2(0.70710678, 0.70710678));
        star += sampleStreak(vec2(-0.70710678, 0.70710678));
    } else {
        star += sampleStreak(vec2(0.0, 1.0));
        star += sampleStreak(vec2(0.8660254, 0.5));
        star += sampleStreak(vec2(-0.8660254, 0.5));
    }
    fragColor = vec4(clamp(star, 0.0, 1.0), 1.0);
}
"""

    const val PASS3_LUT_COMPOSITE_FRAGMENT_SHADER = """#version 300 es
precision highp float;
precision highp sampler3D;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uFbo1Tex;
uniform sampler2D uLut2dTex;
uniform sampler3D uLut3dTex;
uniform int uUse2dLut;
uniform float uLutSize;
uniform vec2 uLutTiles;
uniform vec2 uLutTexSize;
uniform float uEvStops;
uniform sampler2D uFboHiTex;
uniform sampler2D uFboStarTex;
uniform float uBloomIntensity;
uniform float uStarIntensity;
uniform float uBeautySoftness;
uniform float uSharpenStrength;
uniform vec2 uTexelSize;

vec3 applyBilateralBeauty(vec2 uv, vec3 center) {
    vec2 stepUv = uTexelSize * (1.2 + uBeautySoftness * 1.4);
    float sigmaR2 = 0.012 + uBeautySoftness * 0.028;
    vec3 sum = center * 1.0;
    float weightSum = 1.0;

    vec2 offsets[8];
    offsets[0] = vec2(-1.0, -1.0);
    offsets[1] = vec2(0.0, -1.0);
    offsets[2] = vec2(1.0, -1.0);
    offsets[3] = vec2(-1.0, 0.0);
    offsets[4] = vec2(1.0, 0.0);
    offsets[5] = vec2(-1.0, 1.0);
    offsets[6] = vec2(0.0, 1.0);
    offsets[7] = vec2(1.0, 1.0);

    for (int i = 0; i < 8; i++) {
        vec3 s = texture(uFbo1Tex, clamp(uv + offsets[i] * stepUv, 0.0, 1.0)).rgb;
        vec3 diff = s - center;
        float spatialW = (i == 1 || i == 3 || i == 4 || i == 6) ? 0.85 : 0.60;
        float rangeW = exp(-dot(diff, diff) / sigmaR2);
        float w = spatialW * rangeW;
        sum += s * w;
        weightSum += w;
    }

    return mix(center, sum / max(weightSum, 1e-4), uBeautySoftness * 0.85);
}

vec3 sampleLut2d(vec3 rgb) {
    float n = max(uLutSize, 2.0);
    float maxIdx = n - 1.0;
    float blueScaled = clamp(rgb.b, 0.0, 1.0) * maxIdx;
    float b0 = floor(blueScaled);
    float b1 = min(b0 + 1.0, maxIdx);
    float fracB = blueScaled - b0;

    float tilesX = max(uLutTiles.x, 1.0);
    vec2 tile0 = vec2(mod(b0, tilesX), floor(b0 / tilesX));
    vec2 tile1 = vec2(mod(b1, tilesX), floor(b1 / tilesX));

    vec2 inTilePx = clamp(rgb.rg, 0.0, 1.0) * maxIdx + 0.5;
    vec2 uv0 = (tile0 * n + inTilePx) / max(uLutTexSize, vec2(1.0));
    vec2 uv1 = (tile1 * n + inTilePx) / max(uLutTexSize, vec2(1.0));

    vec3 c0 = texture(uLut2dTex, uv0).rgb;
    vec3 c1 = texture(uLut2dTex, uv1).rgb;
    return mix(c0, c1, fracB);
}

vec3 sampleLut3d(vec3 rgb) {
    float n = max(uLutSize, 2.0);
    vec3 lutCoord = clamp(rgb, 0.0, 1.0) * ((n - 1.0) / n) + (0.5 / n);
    return texture(uLut3dTex, lutCoord).rgb;
}

void main() {
    vec3 centerColor = texture(uFbo1Tex, vTexCoord).rgb;
    vec3 baseColor = (uBeautySoftness > 0.001)
        ? applyBilateralBeauty(vTexCoord, centerColor)
        : centerColor;

    // Inline EV scaling before LUT grading
    vec3 evScaled = clamp(baseColor * pow(2.0, uEvStops), 0.0, 1.0);

    vec3 graded = (uUse2dLut == 1)
        ? sampleLut2d(evScaled)
        : sampleLut3d(evScaled);

    // Gentle CCD highlight roll-off
    vec3 excess = max(graded - vec3(0.84), vec3(0.0));
    graded -= excess * 0.22;

    // Unsharp mask sharpen
    if (uSharpenStrength > 0.001) {
        vec3 nUp = texture(uFbo1Tex, clamp(vTexCoord + vec2(0.0, uTexelSize.y), 0.0, 1.0)).rgb;
        vec3 nDown = texture(uFbo1Tex, clamp(vTexCoord - vec2(0.0, uTexelSize.y), 0.0, 1.0)).rgb;
        vec3 nLeft = texture(uFbo1Tex, clamp(vTexCoord - vec2(uTexelSize.x, 0.0), 0.0, 1.0)).rgb;
        vec3 nRight = texture(uFbo1Tex, clamp(vTexCoord + vec2(uTexelSize.x, 0.0), 0.0, 1.0)).rgb;
        vec3 avg4 = 0.25 * (nUp + nDown + nLeft + nRight);
        float lumaDetail = clamp(dot(centerColor - avg4, vec3(0.2126, 0.7152, 0.0722)), -0.25, 0.25);
        graded = clamp(graded + vec3(lumaDetail * (uSharpenStrength * 1.15)), 0.0, 1.0);
    }

    // Stage 4 halation highlight compose
    vec3 bloom = texture(uFboHiTex, vTexCoord).rgb * uBloomIntensity;
    vec3 star = texture(uFboStarTex, vTexCoord).rgb * uStarIntensity;
    vec3 glow = clamp(bloom + star, 0.0, 1.0);
    vec3 composited = 1.0 - (1.0 - graded) * (1.0 - glow);

    fragColor = vec4(clamp(composited, 0.0, 1.0), 1.0);
}
"""

    private const val STAMP_HELPER_GLSL = """
vec3 renderStampOverlay(vec3 color, vec2 photoUv, float targetAspect) {
    if (uStampEnabled <= 0.5 || uStampLength <= 0) {
        return color;
    }
    float charH = 0.044;
    float charW = charH / max(targetAspect, 0.5) * 0.78;
    float stepX = charW * 0.92;
    float totalW = float(uStampLength) * stepX;

    // 0 = BOTTOM_RIGHT, 1 = BOTTOM_LEFT, 2 = TOP_RIGHT, 3 = TOP_LEFT
    float startX = (uStampPosition == 1 || uStampPosition == 3)
        ? 0.055
        : (0.945 - totalW);
    float endX = startX + totalW;
    float startY = (uStampPosition == 2 || uStampPosition == 3)
        ? (0.945 - charH)
        : 0.055;
    float haloPad = 0.012;

    if (photoUv.x >= startX - haloPad && photoUv.x <= endX + haloPad &&
        photoUv.y >= startY - haloPad && photoUv.y <= startY + charH + haloPad) {
        float relX = (photoUv.x - startX) / stepX;
        int slot = int(floor(relX));
        float localX = fract(relX);
        float localY = (photoUv.y - startY) / charH;

        if (slot >= 0 && slot < uStampLength && localX >= 0.0 && localX <= 1.0 && localY >= 0.0 && localY <= 1.0) {
            int charIdx = uStampIndices[slot];
            int cols = max(int(uAtlasGrid.x), 1);
            int col = charIdx % cols;
            int row = charIdx / cols;

            float padU = uGlyphPadding / uAtlasSize.x;
            float padV = uGlyphPadding / uAtlasSize.y;
            float cellU = uCellSize.x / uAtlasSize.x;
            float cellV = uCellSize.y / uAtlasSize.y;

            float uMin = float(col) * cellU + padU;
            float uMax = float(col + 1) * cellU - padU;
            float vMin = float(row) * cellV + padV;
            float vMax = float(row + 1) * cellV - padV;

            // Flip localY for top-down Bitmap atlas
            vec2 glyphUv = vec2(mix(uMin, uMax, localX), mix(vMax, vMin, localY));
            float rawAlpha = texture(uStampAtlasTex, glyphUv).a;
            float coreAlpha = (uStampCleanSans > 0.5)
                ? smoothstep(0.30, 0.70, rawAlpha)
                : rawAlpha;

            float haloAlpha = 0.0;
            if (uStampGlowStrength > 0.001 && uStampCleanSans <= 0.5) {
                vec2 stepUv = vec2(2.0 / uAtlasSize.x, 2.0 / uAtlasSize.y);
                haloAlpha += texture(uStampAtlasTex, clamp(glyphUv + vec2(stepUv.x, 0.0), vec2(uMin, vMin), vec2(uMax, vMax))).a;
                haloAlpha += texture(uStampAtlasTex, clamp(glyphUv - vec2(stepUv.x, 0.0), vec2(uMin, vMin), vec2(uMax, vMax))).a;
                haloAlpha += texture(uStampAtlasTex, clamp(glyphUv + vec2(0.0, stepUv.y), vec2(uMin, vMin), vec2(uMax, vMax))).a;
                haloAlpha += texture(uStampAtlasTex, clamp(glyphUv - vec2(0.0, stepUv.y), vec2(uMin, vMin), vec2(uMax, vMax))).a;
                haloAlpha *= 0.25 * uStampGlowStrength * 0.65;
            }

            float totalAlpha = clamp(coreAlpha + haloAlpha, 0.0, 1.0);
            color = mix(color, uStampColor, totalAlpha * 0.92) + uStampColor * haloAlpha * 0.35;
        }
    }
    return color;
}
"""

    const val PASS4_FINAL_FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uFbo2Tex;
uniform sampler2D uFbo1Tex;
uniform sampler2D uLightLeakTex;
uniform sampler2D uDustTex;
uniform sampler2D uDvScanlineTex;
uniform sampler2D uFrameTex;
uniform sampler2D uStampAtlasTex;

uniform vec2 uOutputSize;
uniform float uTargetAspect;
uniform int uGrainType;
uniform float uLumaGrainStrength;
uniform float uChromaGrainStrength;
uniform float uShaderNoiseBoost;
uniform float uVignetteStrength;
uniform float uLightLeakIntensity;
uniform float uDustIntensity;
uniform float uRandomSeed;

// 0 = NONE, 1 = SINGLE_WINDOW_FRAME, 2 = DV_TIMECODE, 3 = PHOTO_BOOTH_4STRIP
uniform int uHasFrame;
uniform vec4 uFrameWindow;
uniform vec4 uPhotoPanels[4];

uniform float uStampEnabled;
uniform int uStampLength;
uniform int uStampIndices[24];
uniform vec3 uStampColor;
uniform float uStampGlowStrength;
uniform int uStampPosition;
uniform float uStampCleanSans;
uniform vec2 uAtlasGrid;
uniform vec2 uAtlasSize;
uniform vec2 uCellSize;
uniform float uGlyphPadding;

uniform float uSplitCompareRatio;
uniform float uHoldOriginal;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 transformGrungeUv(vec2 uv, float seedOffset) {
    float s = fract(sin((uRandomSeed + seedOffset) * 12.9898) * 43758.5453);
    float flipX = step(0.5, fract(s * 7.13));
    float flipY = step(0.5, fract(s * 13.71));
    vec2 outUv = vec2(
        mix(uv.x, 1.0 - uv.x, flipX),
        mix(uv.y, 1.0 - uv.y, flipY)
    );
    vec2 shift = vec2(fract(s * 3.7) - 0.5, fract(s * 5.9) - 0.5) * 0.12;
    return clamp(outUv + shift, 0.0, 1.0);
}
""" + STAMP_HELPER_GLSL + """
vec3 applyEffects(vec2 photoUv, bool drawStamp) {
    vec3 color = texture(uFbo2Tex, photoUv).rgb;

    // 1. Sensor Noise / Film Grain (post-LUT, fast hash12 temporal grain)
    float noiseAmp = 1.0 + uShaderNoiseBoost;
    vec2 pixelCoord = floor(photoUv * uOutputSize);
    vec2 seedVec = vec2(fract(uRandomSeed * 0.0137), fract(uRandomSeed * 0.0293)) * 256.0;

    if (uGrainType == 0) {
        // CCD_CHROMA_LUMA
        float lumaN = (hash12(pixelCoord + seedVec) - 0.5) * 2.0;
        float rN = (hash12(pixelCoord + seedVec + vec2(17.0, 31.0)) - 0.5) * 2.0;
        float bN = (hash12(pixelCoord + seedVec + vec2(53.0, 11.0)) - 0.5) * 2.0;
        color += vec3(lumaN * uLumaGrainStrength * 0.18 * noiseAmp);
        color.r += rN * uChromaGrainStrength * 0.16 * noiseAmp;
        color.b += bN * uChromaGrainStrength * 0.16 * noiseAmp;
    } else {
        // FILM_ORGANIC
        vec2 clumpCoord = floor(pixelCoord * 0.65);
        float n1 = hash12(pixelCoord + seedVec) - 0.5;
        float n2 = hash12(clumpCoord + seedVec + vec2(23.0, 41.0)) - 0.5;
        float organic = (n1 * 0.55 + n2 * 0.45) * 2.0;
        float chroma = (hash12(clumpCoord + seedVec + vec2(71.0, 19.0)) - 0.5) * 2.0;
        color += vec3(organic * uLumaGrainStrength * 0.18 * noiseAmp);
        color.r += chroma * uChromaGrainStrength * 0.09 * noiseAmp;
        color.b -= chroma * uChromaGrainStrength * 0.09 * noiseAmp;
    }

    // 2. Elliptical Corner Vignette
    vec2 vigVec = (photoUv - 0.5) * vec2(1.0, 1.0 / max(uTargetAspect, 0.5));
    float vigDist = length(vigVec) * 1.45;
    float vignette = 1.0 - smoothstep(0.45, 1.20, vigDist) * uVignetteStrength;
    color *= vignette;

    // 3. Deterministic Light Leaks & Dust/Scratches
    if (uLightLeakIntensity > 0.001) {
        vec2 leakUv = transformGrungeUv(photoUv, 1.0);
        vec4 leakSample = texture(uLightLeakTex, leakUv);
        vec3 leak = leakSample.rgb * leakSample.a * uLightLeakIntensity;
        color = 1.0 - (1.0 - clamp(color, 0.0, 1.0)) * (1.0 - clamp(leak, 0.0, 1.0));
    }
    if (uDustIntensity > 0.001) {
        vec2 dustUv = transformGrungeUv(photoUv, 2.0);
        vec4 dustSample = texture(uDustTex, dustUv);
        vec3 dust = dustSample.rgb * dustSample.a * uDustIntensity;
        color = 1.0 - (1.0 - clamp(color, 0.0, 1.0)) * (1.0 - clamp(dust, 0.0, 1.0));
    }

    // 4. Date Stamp
    if (drawStamp) {
        color = renderStampOverlay(color, photoUv, uTargetAspect);
    }

    return clamp(color, 0.0, 1.0);
}

vec3 applyDvTimecodeBars(vec3 color, vec2 uv) {
    vec2 scanUv = vec2(uv.x, 1.0 - uv.y);
    vec4 scanSample = texture(uDvScanlineTex, scanUv);
    color = mix(color, color * (1.0 - scanSample.a * 0.25) + scanSample.rgb * scanSample.a * 0.22, 0.85);

    // Top and bottom dark camcorder HUD bars
    float inTopBar = step(0.915, uv.y);
    float inBottomBar = step(uv.y, 0.085);
    float barMask = clamp(inTopBar + inBottomBar, 0.0, 1.0);
    color = mix(color, color * 0.35 + vec3(0.03, 0.04, 0.05), barMask * 0.82);

    // Subtle red REC dot in top-left bar
    vec2 recPos = (uv - vec2(0.065, 0.957)) * vec2(uTargetAspect, 1.0);
    float recDot = 1.0 - smoothstep(0.011, 0.015, length(recPos));
    color = mix(color, vec3(0.92, 0.16, 0.14), recDot);
    return color;
}

void main() {
    if (uHoldOriginal > 0.5) {
        fragColor = vec4(texture(uFbo1Tex, vTexCoord).rgb, 1.0);
        return;
    }

    vec3 finalColor;
    if (uHasFrame == 1) {
        vec2 frameTexUv = vec2(vTexCoord.x, 1.0 - vTexCoord.y);
        vec4 frameSample = texture(uFrameTex, frameTexUv);
        vec2 winSize = max(uFrameWindow.zw - uFrameWindow.xy, vec2(0.001));
        vec2 photoUv = vec2(
            (frameTexUv.x - uFrameWindow.x) / winSize.x,
            1.0 - (frameTexUv.y - uFrameWindow.y) / winSize.y
        );
        vec3 photoColor = vec3(0.0);
        if (photoUv.x >= 0.0 && photoUv.x <= 1.0 && photoUv.y >= 0.0 && photoUv.y <= 1.0) {
            photoColor = applyEffects(photoUv, true);
        }
        finalColor = mix(photoColor, frameSample.rgb, frameSample.a);
    } else if (uHasFrame == 2) {
        finalColor = applyEffects(vTexCoord, true);
        finalColor = applyDvTimecodeBars(finalColor, vTexCoord);
    } else if (uHasFrame == 3) {
        vec2 frameTexUv = vec2(vTexCoord.x, 1.0 - vTexCoord.y);
        vec4 frameSample = texture(uFrameTex, frameTexUv);
        vec3 photoColor = vec3(0.0);
        for (int i = 0; i < 4; i++) {
            vec4 panel = uPhotoPanels[i];
            if (frameTexUv.x >= panel.x && frameTexUv.x <= panel.z &&
                frameTexUv.y >= panel.y && frameTexUv.y <= panel.w) {
                vec2 pSize = max(panel.zw - panel.xy, vec2(0.001));
                vec2 panelUv = vec2(
                    (frameTexUv.x - panel.x) / pSize.x,
                    1.0 - (frameTexUv.y - panel.y) / pSize.y
                );
                photoColor = applyEffects(panelUv, false);
            }
        }
        finalColor = mix(photoColor, frameSample.rgb, frameSample.a);
        finalColor = renderStampOverlay(finalColor, vTexCoord, uTargetAspect);
    } else {
        finalColor = applyEffects(vTexCoord, true);
    }

    if (uSplitCompareRatio > 0.001 && uSplitCompareRatio < 0.999) {
        if (vTexCoord.x < uSplitCompareRatio) {
            finalColor = texture(uFbo1Tex, vTexCoord).rgb;
        }
        if (abs(vTexCoord.x - uSplitCompareRatio) < 0.002) {
            finalColor = vec3(1.0);
        }
    }

    fragColor = vec4(clamp(finalColor, 0.0, 1.0), 1.0);
}
"""

    const val PASS_OVERLAY_FRAME_FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uBaseTex;
uniform sampler2D uFrameTex;
uniform sampler2D uStampAtlasTex;
uniform float uTargetAspect;

uniform float uStampEnabled;
uniform int uStampLength;
uniform int uStampIndices[24];
uniform vec3 uStampColor;
uniform float uStampGlowStrength;
uniform int uStampPosition;
uniform float uStampCleanSans;
uniform vec2 uAtlasGrid;
uniform vec2 uAtlasSize;
uniform vec2 uCellSize;
uniform float uGlyphPadding;
""" + STAMP_HELPER_GLSL + """
void main() {
    vec3 baseColor = texture(uBaseTex, vTexCoord).rgb;
    vec2 frameTexUv = vec2(vTexCoord.x, 1.0 - vTexCoord.y);
    vec4 frameSample = texture(uFrameTex, frameTexUv);
    vec3 composited = mix(baseColor, frameSample.rgb, frameSample.a);
    composited = renderStampOverlay(composited, vTexCoord, uTargetAspect);
    fragColor = vec4(clamp(composited, 0.0, 1.0), 1.0);
}
"""
}
