package app.calstats

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import app.calstats.work.NoteFile
import app.calstats.work.WorkStore
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Запись совещаний в заметку. Работает как служба с уведомлением, поэтому не обрывается,
 * когда гаснет экран или вы переключаетесь на другое приложение.
 */
class RecorderService : Service() {
    companion object {
        /** Идёт запись: в какую заметку и с какого момента (для таймера в интерфейсе). */
        var noteId by mutableStateOf<String?>(null)
            private set
        var startedAt by mutableStateOf(0L)
            private set
        /** Пока приложение открыто — готовую запись добавляет оно (чтобы не было двух копий данных). */
        var onSaved: ((String, NoteFile) -> Unit)? = null

        private var recorder: MediaRecorder? = null
        private var file: File? = null

        fun start(ctx: Context, note: String) {
            if (noteId != null) return
            val i = Intent(ctx, RecorderService::class.java).putExtra("note", note)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, RecorderService::class.java).setAction("stop"))
        }

        fun dir(ctx: Context, note: String) = File(ctx.filesDir, "notes/$note").apply { mkdirs() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { finish(); return START_NOT_STICKY }
        val note = intent?.getStringExtra("note") ?: return START_NOT_STICKY
        if (recorder != null) return START_NOT_STICKY
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("rec", "Запись звука", NotificationManager.IMPORTANCE_LOW))
        val stopPi = PendingIntent.getService(this, 1, Intent(this, RecorderService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        val openPi = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_QUICK, "note:$note")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, "rec")
            .setSmallIcon(R.drawable.ic_mic_tile).setContentTitle("🎙 Идёт запись в заметку")
            .setContentText("Нажмите, чтобы открыть заметку").setUsesChronometer(true).setWhen(System.currentTimeMillis())
            .setOngoing(true).setContentIntent(openPi).addAction(0, "Остановить", stopPi).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE) else startForeground(77, n)
        try {
            val f = File(dir(this, note), "rec-${System.currentTimeMillis()}.m4a")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start()
            recorder = r; file = f
            noteId = note; startedAt = SystemClock.elapsedRealtime()
        } catch (e: Exception) {
            recorder = null; noteId = null
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun finish() {
        val r = recorder; val f = file; val note = noteId
        val dur = SystemClock.elapsedRealtime() - startedAt
        recorder = null; file = null; noteId = null
        runCatching { r?.stop() }; runCatching { r?.release() }
        if (f != null && note != null && f.length() > 0) {
            val name = "Запись " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("ru"))) + ".m4a"
            val nf = NoteFile(app.calstats.work.newId(), name, "audio/mp4", f.relativeTo(filesDir).path, f.length(), dur)
            val cb = onSaved
            if (cb != null) cb(note, nf) else {
                val store = WorkStore(this)
                val w = store.load()
                store.save(w.copy(notes = w.notes.map { if (it.id == note) it.copy(files = it.files + nf, updated = System.currentTimeMillis()) else it }))
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (recorder != null) finish()
        super.onDestroy()
    }
}
