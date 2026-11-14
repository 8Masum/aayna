package com.themasum.aayna.core.gl

import com.themasum.aayna.core.model.CameraProfile
import com.themasum.aayna.core.model.OpticalSettings
import com.themasum.aayna.core.model.ShaderExposureFallback
import com.themasum.aayna.core.model.StampConfig

data class GlRenderParams(
    val profile: CameraProfile,
    val opticalSettings: OpticalSettings = OpticalSettings(),
    val stampConfig: StampConfig = StampConfig(),
    val stampText: String = "",
    val fallbackGains: ShaderExposureFallback = ShaderExposureFallback(0f, 0f, 0f),
    val isPreviewHoldOriginal: Boolean = false,
    val splitCompareRatio: Float = 0f,
    val randomSeed: Long = 1L,
    val inputAspectRatio: Float = 4f / 3f,
    val skipStamp: Boolean = false,
    val isPreComposited: Boolean = false
)
