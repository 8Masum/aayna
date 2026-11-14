package com.themasum.aayna.core.storage

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class AaynaSoundEngine(context: Context) {
    private val appContext = context.applicationContext
    private val loadedSamples = ConcurrentHashMap.newKeySet<Int>()
    private val soundIds = ConcurrentHashMap<String, Int>()
    private val isReleased = AtomicBoolean(false)

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
                    if (status == 0 && !isReleased.get()) {
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

    fun isSampleReady(key: String): Boolean {
        val sampleId = soundIds[key] ?: return false
        return loadedSamples.contains(sampleId)
    }

    fun playShutter(soundAssetName: String, sfxEnabled: Boolean) {
        if (!sfxEnabled || isReleased.get()) return
        val key = if (soundIds.containsKey(soundAssetName)) soundAssetName else SOUND_SHUTTER_CCD
        playSample(key, volume = 1.0f, rate = 1.0f)
    }

    fun playFilmWind(sfxEnabled: Boolean) {
        if (!sfxEnabled || isReleased.get()) return
        playSample(SOUND_FILM_WIND, volume = 0.9f, rate = 1.0f)
    }

    fun playDialTick(sfxEnabled: Boolean) {
        if (!sfxEnabled || isReleased.get()) return
        playSample(SOUND_DIAL_TICK, volume = 0.65f, rate = 1.0f)
    }

    fun playVideoStartStop(sfxEnabled: Boolean, isStarting: Boolean = true) {
        if (!sfxEnabled || isReleased.get()) return
        val rate = if (isStarting) 1.18f else 0.85f
        playSample(SOUND_DIAL_TICK, volume = 0.8f, rate = rate)
    }

    fun release() {
        if (!isReleased.compareAndSet(false, true)) return
        loadedSamples.clear()
        soundIds.clear()
        runCatching { soundPool.release() }
    }

    private fun loadSample(key: String, rawResId: Int) {
        if (isReleased.get()) return
        val sampleId = runCatching { soundPool.load(appContext, rawResId, 1) }.getOrDefault(0)
        if (sampleId > 0) {
            soundIds[key] = sampleId
        }
    }

    private fun playSample(key: String, volume: Float, rate: Float) {
        if (isReleased.get()) return
        val sampleId = soundIds[key] ?: return
        if (loadedSamples.contains(sampleId)) {
            runCatching { soundPool.play(sampleId, volume, volume, 1, 0, rate) }
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
