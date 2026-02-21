package com.application.jomato.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.application.jomato.utils.FileLogger
import kotlinx.coroutines.*

/**
 * Plays the system alarm ringtone on STREAM_ALARM with vibration.
 * Volume is controlled entirely by the device's alarm volume setting.
 * Auto-stops after [MAX_DURATION_MS].
 */
object AlarmReminder {

    private const val TAG = "AlarmReminder"
    private const val MAX_DURATION_MS = 10_000L

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var autoStopJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val VIBRATION_PATTERN = longArrayOf(0, 800, 400, 800, 400, 800)

    @Synchronized
    fun play(context: Context) {
        stop() // clean up any previous playback

        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            val alarmAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(alarmAttributes)
                setDataSource(context, alarmUri!!)
                isLooping = true
                prepare()
                start()
            }

            vibrator = getVibrator(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(VIBRATION_PATTERN, 0)
            }

            autoStopJob = scope.launch {
                delay(MAX_DURATION_MS)
                FileLogger.log(context, TAG, "Auto-stopped after ${MAX_DURATION_MS / 1000}s")
                stop()
            }

            FileLogger.log(context, TAG, "Playing alarm (URI: $alarmUri)")

        } catch (e: Exception) {
            FileLogger.log(context, TAG, "Failed: ${e.message}", e)
            stop()
        }
    }

    @Synchronized
    fun stop() {
        autoStopJob?.cancel()
        autoStopJob = null

        try {
            mediaPlayer?.let { if (it.isPlaying) it.stop(); it.release() }
        } catch (_: Exception) {}
        mediaPlayer = null

        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null
    }

    private fun getVibrator(context: Context): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
}
