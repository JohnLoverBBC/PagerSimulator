package com.example.pager

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Msg(
    val id: Long, val from: String, val body: String,
    val time: Long, val urgent: Boolean, val read: Boolean,
    /** optional 128x256 1-bit image, base64 (see PagerImg) */
    val img: String? = null
)

object PagerCore {
    val messages = mutableStateListOf<Msg>()
    val log = mutableStateListOf<String>()
    var index by mutableIntStateOf(0)
    var beep by mutableStateOf(true)
    var vibrate by mutableStateOf(true)
    var systemNotify by mutableStateOf(true)

    // --- shop / models / settings ---
    var gems by mutableIntStateOf(0)
    val owned = mutableStateListOf<String>()
    var modelId by mutableStateOf(DEFAULT_MODEL)
    /** 0 = follow the phone, 1 = portrait, 2 = landscape (manual switch) */
    var orientMode by mutableIntStateOf(0)
    /** CM Paper Pager: is the stylus out of its compartment? */
    var stylusOut by mutableStateOf(false)
    var ledIdx by mutableIntStateOf(0)
    var kbLight by mutableIntStateOf(1)
    var lcdLight by mutableStateOf(false)
    var callsign by mutableStateOf("PAGER")
    var uid = ""
    var flashMsg by mutableStateOf<String?>(null)
    private var lastDay = ""
    val model: Model get() = MODELS.firstOrNull { it.id == modelId } ?: MODELS[0]
    private val prefs get() = app.getSharedPreferences("pager", Context.MODE_PRIVATE)
    private val clearFlash = Runnable { flashMsg = null }

    fun flash(s: String) {
        flashMsg = s
        main.removeCallbacks(clearFlash); main.postDelayed(clearFlash, 3000)
    }

    fun saveMeta() {
        prefs.edit().putInt("gems", gems).putString("owned", owned.joinToString(","))
            .putString("model", modelId).putInt("led", ledIdx).putInt("kb", kbLight)
            .putBoolean("lcd", lcdLight).putString("call", callsign).putString("uid", uid)
            .putString("day", lastDay).putInt("orient", orientMode).apply()
    }

    private fun loadMeta() {
        val p = prefs
        gems = p.getInt("gems", 0)
        owned.clear()
        owned.addAll((p.getString("owned", DEFAULT_MODEL) ?: DEFAULT_MODEL).split(",").filter { it.isNotBlank() })
        if (DEFAULT_MODEL !in owned) owned.add(DEFAULT_MODEL)
        modelId = p.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL
        orientMode = p.getInt("orient", 0)
        ledIdx = p.getInt("led", 0); kbLight = p.getInt("kb", 1); lcdLight = p.getBoolean("lcd", false)
        uid = p.getString("uid", null) ?: UUID.randomUUID().toString().take(6)
        callsign = p.getString("call", null) ?: ("PAGER-" + uid.take(3).uppercase())
        lastDay = p.getString("day", "") ?: ""
    }

    fun checkDaily() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (lastDay != today) {
            lastDay = today; gems++; saveMeta()
            log("DAILY GEM +1 (total $gems)"); flash("+1 GEM  DAILY BONUS")
        }
    }

    fun resetDaily() { lastDay = ""; checkDaily() }

    fun buy(m: Model): Boolean {
        if (m.id in owned || gems < m.price) return false
        gems -= m.price; owned.add(m.id); equip(m.id); log("BOUGHT ${m.name}"); return true
    }

    fun equip(id: String) {
        if (id !in owned) return
        val old = model
        modelId = id
        // sideways devices turn the screen landscape; leaving one turns it back
        if (model.landscapeNative) orientMode = 2
        else if (old.landscapeNative && orientMode == 2) orientMode = 0
        saveMeta()
    }

    /** 1 gem for every message sent or received. */
    fun earnGem(why: String) {
        gems++; saveMeta(); log("GEM +1 ($why) total $gems")
    }

    lateinit var app: Context
        private set
    private val main = Handler(Looper.getMainLooper())
    private const val CH_NORMAL = "pager_normal"
    private const val CH_URGENT = "pager_urgent"

    fun init(ctx: Context) {
        if (::app.isInitialized) return
        app = ctx.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_NORMAL, "Pages", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_URGENT, "Urgent pages", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 100, 300, 100, 600)
            }
        )
        loadMeta()
        load()
        log("PAGER ONLINE")
        checkDaily()
    }

    fun log(s: String) {
        val t = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        log.add(0, "$t $s")
        while (log.size > 100) log.removeAt(log.size - 1)
    }

    fun hasNotifPermission(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun receive(from: String, body: String, urgent: Boolean, img: String? = null) {
        val text = if (img != null && body.isBlank()) "[IMAGE 128x256]" else body
        val m = Msg(System.currentTimeMillis(), from.ifBlank { "UNKNOWN" }, text, System.currentTimeMillis(), urgent, false, img)
        messages.add(0, m)
        index = 0
        save()
        log("RX from ${m.from}${if (urgent) " [URGENT]" else ""}${if (img != null) " [IMAGE]" else ""}")
        earnGem("RX"); flash("+1 GEM")
        alert(m)
    }

    fun receiveDelayed(ms: Long, from: String, body: String, urgent: Boolean) {
        log("QUEUED page in ${ms / 1000}s")
        main.postDelayed({ receive(from, body, urgent) }, ms)
    }

    fun testNotification(urgent: Boolean) {
        val m = Msg(0, "TEST", "Notification test ${if (urgent) "(urgent)" else ""}", System.currentTimeMillis(), urgent, false)
        log("TEST notification fired, permission=${hasNotifPermission()}")
        postNotification(m, 9999)
        if (beep) playBeep(urgent)
        if (vibrate) doVibrate(urgent)
    }

    fun markRead(i: Int) {
        if (i !in messages.indices) return
        messages[i] = messages[i].copy(read = true); save()
    }

    fun markAllRead() {
        for (i in messages.indices) messages[i] = messages[i].copy(read = true)
        save(); log("ALL MARKED READ")
    }

    fun delete(i: Int) {
        if (i !in messages.indices) return
        messages.removeAt(i)
        index = index.coerceIn(0, maxOf(0, messages.size - 1)); save()
    }

    fun clear() { messages.clear(); index = 0; save(); log("MEMORY CLEARED") }

    private fun alert(m: Msg) {
        if (beep) playBeep(m.urgent)
        if (vibrate) doVibrate(m.urgent)
        if (systemNotify) postNotification(m, (m.id % 100000).toInt())
    }

    private fun playBeep(urgent: Boolean) {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            tg.startTone(if (urgent) ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD else ToneGenerator.TONE_PROP_BEEP2, if (urgent) 900 else 400)
            main.postDelayed({ tg.release() }, 1200)
        } catch (e: Exception) { log("BEEP ERR ${e.message}") }
    }

    private fun doVibrate(urgent: Boolean) {
        try {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= 31)
                app.getSystemService(VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") (app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
            val pattern = if (urgent) longArrayOf(0, 300, 100, 300, 100, 600) else longArrayOf(0, 200, 100, 200)
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } catch (e: Exception) { log("VIB ERR ${e.message}") }
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(m: Msg, id: Int) {
        if (!hasNotifPermission()) { log("NOTIF BLOCKED (no permission)"); return }
        val pi = PendingIntent.getActivity(
            app, 0, Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(app, if (m.urgent) CH_URGENT else CH_NORMAL)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("PAGE: ${m.from}")
            .setContentText(m.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(m.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(app).notify(id, n)
    }

    private fun save() {
        val arr = JSONArray()
        messages.forEach {
            arr.put(JSONObject().put("id", it.id).put("from", it.from).put("body", it.body)
                .put("time", it.time).put("urgent", it.urgent).put("read", it.read).put("img", it.img ?: ""))
        }
        app.getSharedPreferences("pager", Context.MODE_PRIVATE).edit().putString("msgs", arr.toString()).apply()
    }

    private fun load() {
        try {
            val s = app.getSharedPreferences("pager", Context.MODE_PRIVATE).getString("msgs", null) ?: return
            val arr = JSONArray(s)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                messages.add(Msg(o.getLong("id"), o.getString("from"), o.getString("body"),
                    o.getLong("time"), o.getBoolean("urgent"), o.getBoolean("read"),
                    o.optString("img", "").takeIf { s2 -> s2.isNotEmpty() }))
            }
        } catch (_: Exception) {}
    }
}
