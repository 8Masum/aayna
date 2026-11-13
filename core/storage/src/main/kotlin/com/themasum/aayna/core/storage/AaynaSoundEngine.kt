package com.themasum.aayna.core.storage

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.util.concurrent.ConcurrentHashMap

class AaynaSoundEngine(context: Context) {
    private val appContext = context.applicationContext
    private val loadedSamples = ConcurrentHashMap.newKeySet<Int>()
    private val soundIds = ConcurrentHashMap<String, Int>()

    private val soundPool: SoundPool =
        SoundPool
            .Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            ).build()
            .apply {
                setOnLoadCompleteListener { _, sampleId, status ->
                    if (status == 0) {
                        loadedSamples.add(sampleId)
                    }
                }
            }

    init {
        loadSample(SOUND_SHUTTER_CCD, R.raw.shutter_ccd)
        loadSample(SOUND_SHUTTER_DISPOSABLE, R.raw.shutter_disposable)
        loadSample(SOUND_SHUTTER_RANGEFINDER, R.raw.shutter_rangefinder)
        loadSample(SOUND_SHUTTER_INSTANT, R.raw.shutter_instant)
        loadSample(SOUND_FILM_WIND, R.raw.film_wind)
        loadSample(SOUND_DIAL_TICK, R.raw.dial_tick)
    }

    fun playShutter(soundAssetName: String, sfxEnabled: Boolean) {
        if (!sfxEnabled) return
        val key = if (soundIds.containsKey(soundAssetName)) soundAssetName else SOUND_SHUTTER_CCD
        playSample(key, volume = 1.0f)
    }

    fun playFilmWind(sfxEnabled: Boolean) {
        if (!sfxEnabled) return
        playSample(SOUND_FILM_WIND, volume = 0.9f)
    }

    fun playDialTick(sfxEnabled: Boolean) {
        if (!sfxEnabled) return
        playSample(SOUND_DIAL_TICK, volume = 0.65f)
    }

    fun release() {
        loadedSamples.clear()
        soundIds.clear()
        soundPool.release()
    }

    private fun loadSample(key: String, rawResId: Int) {
        val sampleId = soundPool.load(appContext, rawResId, 1)
        if (sampleId > 0) {
            soundIds[key] = sampleId
        }
    }

    private fun playSample(key: String, volume: Float) {
        val sampleId = soundIds[key] ?: return
        if (loadedSamples.contains(sampleId)) {
            soundPool.play(sampleId, volume, volume, 1, 0, 1.0f)
        }
    }

    companion object {
        private const val MAX_STREAMS = 4
        const val SOUND_SHUTTER_CCD = "shutter_ccd"
        const val SOUND_SHUTTER_DISPOSABLE = "shutter_disposable"
        const val SOUND_SHUTTER_RANGEFINDER = "shutter_rangefinder"
        const val SOUND_SHUTTER_INSTANT = "shutter_instant"
        const val SOUND_FILM_WIND = "film_wind"
        const val SOUND_DIAL_TICK = "dial_tick"
    }
}
