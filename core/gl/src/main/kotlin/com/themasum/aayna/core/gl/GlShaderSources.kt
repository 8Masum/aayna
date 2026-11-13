package com.themasum.aayna.core.gl

object GlShaderSources {
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
uniform float uTargetAspect;
uniform float uFisheyeStrength;
uniform float uCaStrength;
uniform float uWbTemp;
uniform float uWbTint;
uniform float uWarmthShift;
uniform float uExposureStops;
uniform float uFlashSimStrength;

vec2 toSourceUv(vec2 fboUv) {
    return (uTexMatrix * vec4(fboUv, 0.0, 1.0)).xy;
}

void main() {
    vec2 uv = vFboUv;
    float edgeMask = 1.0;

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
            edgeMask = 1.0 - smoothstep(0.92, 1.04, rNorm * (1.0 + uFisheyeStrength * 0.15));
        }
    }

    vec2 croppedUv = (uv - 0.5) * uCropScale + 0.5;

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

    // Pre-LUT White Balance & Warmth
    float temp = uWbTemp + uWarmthShift * 0.25;
    float tint = uWbTint;
    color.r *= (1.0 + temp * 0.35 - tint * 0.10);
    color.g *= (1.0 + tint * 0.24);
    color.b *= (1.0 - temp * 0.35 - tint * 0.10);

    // Pre-LUT EV & Fallback Digital Exposure
    color *= exp2(uExposureStops);

    // Direct-Flash Simulation: radial spotlight punch + dark background falloff
    if (uFlashSimStrength > 0.001) {
        vec2 flashPos = (vFboUv - 0.5) * vec2(uTargetAspect, 1.0);
        float d = length(flashPos) * 1.45;
        float centerPunch = exp(-d * d * 2.4) * 0.52 * uFlashSimStrength;
        float bgFalloff = mix(1.0, smoothstep(1.45, 0.25, d) * 0.62 + 0.38, uFlashSimStrength);
        color *= (bgFalloff + centerPunch);
        float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
        color = mix(color, color * vec3(0.98, 1.0, 1.04), smoothstep(0.6, 1.0, luma) * 0.35 * uFlashSimStrength);
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

    const val PASS2_BLUR_FRAGMENT_SHADER = """#version 300 es
precision highp float;

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uSourceTex;
uniform vec2 uTexelStep;

void main() {
    vec3 sum = texture(uSourceTex, vTexCoord).rgb * 0.227027;
    sum += texture(uSourceTex, vTexCoord + uTexelStep * 1.384615).rgb * 0.316216;
    sum += texture(uSourceTex, vTexCoord - uTexelStep * 1.384615).rgb * 0.316216;
    sum += texture(uSourceTex, vTexCoord + uTexelStep * 3.230769).rgb * 0.070270;
    sum += texture(uSourceTex, vTexCoord - uTexelStep * 3.230769).rgb * 0.070270;
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
uniform sampler3D uLutTex;
uniform sampler2D uFboHiTex;
uniform sampler2D uFboStarTex;
uniform float uLutSize;
uniform float uBloomIntensity;
uniform float uStarIntensity;

void main() {
    vec3 baseColor = texture(uFbo1Tex, vTexCoord).rgb;
    float n = max(uLutSize, 2.0);
    vec3 lutCoord = clamp(baseColor, 0.0, 1.0) * ((n - 1.0) / n) + (0.5 / n);
    vec3 graded = texture(uLutTex, lutCoord).rgb;

    // Gentle CCD highlight roll-off
    vec3 excess = max(graded - vec3(0.84), vec3(0.0));
    graded -= excess * 0.22;

    vec3 bloom = texture(uFboHiTex, vTexCoord).rgb * uBloomIntensity;
    vec3 star = texture(uFboStarTex, vTexCoord).rgb * uStarIntensity;
    vec3 glow = clamp(bloom + star, 0.0, 1.0);
    vec3 composited = 1.0 - (1.0 - graded) * (1.0 - glow);

    fragColor = vec4(clamp(composited, 0.0, 1.0), 1.0);
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

uniform float uHasFrame;
uniform vec4 uFrameWindow;

uniform float uStampEnabled;
uniform int uStampLength;
uniform int uStampIndices[24];
uniform vec3 uStampColor;
uniform float uStampGlowStrength;
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

vec3 applyEffects(vec2 photoUv) {
    vec3 color = texture(uFbo2Tex, photoUv).rgb;

    // 1. Sensor Noise / Film Grain (post-LUT)
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
        // FILM_ORGANIC (slightly softer 2x2 clump blend)
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

    // 4. Date Stamp (dot_matrix / seven_segment atlas)
    if (uStampEnabled > 0.5 && uStampLength > 0) {
        float charH = 0.044;
        float charW = charH / max(uTargetAspect, 0.5) * 0.78;
        float stepX = charW * 0.92;
        float endX = 0.945;
        float startX = endX - float(uStampLength) * stepX;
        float startY = 0.055;
        float haloPad = 0.012;

        if (photoUv.x >= startX - haloPad && photoUv.x <= endX + haloPad &&
            photoUv.y >= startY - haloPad && photoUv.y <= startY + charH + haloPad) {
            float relX = (photoUv.x - startX) / stepX;
            int slot = int(floor(relX));
            float localX = fract(relX);
            float localY = (photoUv.y - startY) / charH;

            if (slot >= 0 && slot < uStampLength && localX >= 0.0 && localX <= 1.0 && localY >= 0.0 && localY <= 1.0) {
                int charIdx = uStampIndices[slot];
                int cols = int(uAtlasGrid.x);
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
                float coreAlpha = texture(uStampAtlasTex, glyphUv).a;

                float haloAlpha = 0.0;
                if (uStampGlowStrength > 0.001) {
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
    }

    return clamp(color, 0.0, 1.0);
}

void main() {
    if (uHoldOriginal > 0.5) {
        fragColor = vec4(texture(uFbo1Tex, vTexCoord).rgb, 1.0);
        return;
    }

    vec3 finalColor;
    if (uHasFrame > 0.5) {
        vec2 frameTexUv = vec2(vTexCoord.x, 1.0 - vTexCoord.y);
        vec4 frameSample = texture(uFrameTex, frameTexUv);
        vec2 winSize = max(uFrameWindow.zw - uFrameWindow.xy, vec2(0.001));
        vec2 photoUv = vec2(
            (frameTexUv.x - uFrameWindow.x) / winSize.x,
            1.0 - (frameTexUv.y - uFrameWindow.y) / winSize.y
        );
        vec3 photoColor = vec3(0.0);
        if (photoUv.x >= 0.0 && photoUv.x <= 1.0 && photoUv.y >= 0.0 && photoUv.y <= 1.0) {
            photoColor = applyEffects(photoUv);
        }
        finalColor = mix(photoColor, frameSample.rgb, frameSample.a);
    } else {
        finalColor = applyEffects(vTexCoord);
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
}
