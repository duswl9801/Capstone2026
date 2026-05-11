package com.example.visa.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue

/**
 * TTSManager — singleton wrapper around Android TextToSpeech.
 *
 * Usage:
 *   TTSManager.init(context)
 *   TTSManager.speak("Hello")
 *   TTSManager.shutdown()  // call in onDestroy
 */
object TTSManager {

    private const val TAG = "TTSManager"

    private var tts: TextToSpeech? = null

    @Volatile private var isReady = false
    @Volatile private var initFailed = false


    // speak() is called from the UI thread; LinkedBlockingQueue handles both safely
    private val pendingQueue = LinkedBlockingQueue<String>()

    // ── Init ──────────────────────────────────────────────────────────────────

    /**
     * Initialize TTS. Safe to call from any thread.
     * If a previous init failed resets and retries cleanly.
     */
    fun init(context: Context) {
        if (isReady) return

        // Fix #5: broken instance cleanup — don't stay locked in a failed state
        if (initFailed) {
            tts?.shutdown()
            tts = null
            initFailed = false
        }

        if (tts != null) return // init already in progress

        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.e(TAG, "TTS initialization failed with status: $status")
                initFailed = true
                tts = null
                pendingQueue.clear()
                return@TextToSpeech
            }

            //  prefer system locale so non-English speakers hear their language
            val locale = resolveLocale()
            val langResult = tts?.setLanguage(locale)

            if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                langResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.w(TAG, "Locale $locale not supported, falling back to Locale.US")
                val fallbackResult = tts?.setLanguage(Locale.US)
                if (fallbackResult == TextToSpeech.LANG_MISSING_DATA ||
                    fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.e(TAG, "Fallback Locale.US also unsupported — TTS disabled")
                    initFailed = true
                    tts = null
                    pendingQueue.clear() // Fix #3: prevent indefinite memory growth
                    return@TextToSpeech
                }
            }

            tts?.setSpeechRate(0.85f)
            tts?.setPitch(1.0f)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {}
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS error for utterance: $utteranceId")
                }
            })

            isReady = true
            Log.d(TAG, "TTS ready with locale: ${tts?.voice?.locale}")


            // spoken in order — QUEUE_FLUSH would only play the last one
            flushPendingQueue()
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Speak a message, interrupting anything currently playing.
     * Safe to call from any thread.
     * If TTS is not ready yet, the message is queued.
     */
    fun speak(text: String) {
        if (text.isBlank()) return
        when {
            isReady     -> tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
            !initFailed -> pendingQueue.offer(text) // thread-safe offer
            // initFailed → silently drop; TTS unavailable on this device
        }
    }

    /**
     * Speak after the current utterance finishes (non-interrupting).
     * Safe to call from any thread.
     */
    fun speakQueued(text: String) {
        if (text.isBlank()) return
        when {
            isReady     -> tts?.speak(text, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
            !initFailed -> pendingQueue.offer(text)
        }
    }

    /**
     * Stop speaking immediately.
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * Release TTS resources. Call in onDestroy of your Service/Activity.
     * Fully resets state so init() works cleanly on next call.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
        initFailed = false
        pendingQueue.clear()
        Log.d(TAG, "TTS shut down")
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Fix #1: drain pending queue with QUEUE_ADD so all messages play in order.
     * LinkedBlockingQueue.poll() is thread-safe (Fix #2).
     */
    private fun flushPendingQueue() {
        var message = pendingQueue.poll()
        while (message != null) {
            tts?.speak(message, TextToSpeech.QUEUE_ADD, null, UUID.randomUUID().toString())
            message = pendingQueue.poll()
        }
    }

    /**
     * Fix #4: use device system locale so non-English speakers hear their
     * own language. Falls back to Locale.US if system locale is ROOT.
     */
    private fun resolveLocale(): Locale =
        Locale.getDefault().takeIf { it != Locale.ROOT } ?: Locale.US
}