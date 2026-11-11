package com.ilink.vehicle.platform.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** VUI 안내 ID (기획서 ID Rule U01~U09) */
enum class VoiceCue { U01, U02, U03, U04, U05, U06, U07, U08, U09 }

/**
 * 발화와 함께 앱 창에 띄우는 안내 (assets/U01~U08).
 * @param query U08 음성 명령: 운전자가 한 말. 있으면 카드 위에 따로 보여 준다
 */
data class Banner(val cue: VoiceCue, val title: String, val sub: String? = null, val query: String? = null) {
    /** TTS로 읽을 문장 */
    val speech: String get() = listOfNotNull(title, sub).joinToString(". ")
}

interface Announcer {
    val banner: StateFlow<Banner?>
    fun start()
    /** 같은 사건(eventKey)은 한 번만. eventKey가 null이면 매번 (음성 명령 응답) */
    fun announce(banner: Banner, eventKey: String?)
    fun clearBanner()
    fun stop()
}

/**
 * U01~U09 발화. 오디오 포커스(GAIN_TRANSIENT_MAY_DUCK)를 얻은 경우에만 1회 말하고,
 * 못 얻으면 배너로만 보여준다 (SEQ-11). 같은 사건(eventKey)은 한 번만 발화한다.
 */
class VoiceAnnouncer(context: Context) : Announcer {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val tts = TextToSpeech(context.applicationContext, Mode.ON_DEVICE)
    private val spoken = LinkedHashSet<String>()
    private var ready = false
    private var focusRequest: AudioFocusRequest? = null

    private val _banner = MutableStateFlow<Banner?>(null)
    override val banner: StateFlow<Banner?> = _banner

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    override fun start() {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                ready = true
                tts.setAudioAttributes(attrs)
            }

            override fun onFailure(type: ErrorType) {
                ready = false
            }
        })
        tts.addEventListener(object : EventListener {
            override fun onStart() = Unit
            override fun onReady() = Unit
            override fun onDone() = abandonFocus()
            override fun onStop() = abandonFocus()
            override fun onError(type: ErrorType) = abandonFocus()
            override fun onUpdatedRms(rms: Double) = Unit
        })
    }

    override fun announce(banner: Banner, eventKey: String?) {
        if (eventKey != null) {
            if (!spoken.add(eventKey)) return
            if (spoken.size > 200) spoken.remove(spoken.first())
        }
        _banner.value = banner
        if (ready && requestFocus()) tts.speak(banner.speech, false)
    }

    override fun clearBanner() {
        _banner.value = null
    }

    private fun requestFocus(): Boolean {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attrs)
            .build()
        focusRequest = req
        return audio.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    override fun stop() {
        runCatching { tts.stop() }
        runCatching { tts.release() }
        abandonFocus()
    }
}
