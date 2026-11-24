package app.calstats

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.calstats.work.CardPlace
import app.calstats.work.WorkStore
import app.calstats.work.Workspace
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Подсказка карты у магазина. Приложение запоминает место, когда вы открываете карту на кассе,
 * и потом, оказавшись рядом (≈150 м, больше минуты), присылает уведомление «Открыть карту».
 * Координаты хранятся только в телефоне.
 */
object CardPlaces {
    private const val RADIUS = 150f
    private const val MAX_PER_CARD = 5

    private fun sp(ctx: Context) = ctx.getSharedPreferences("card_places", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = sp(ctx).getBoolean("on", false)
    fun setEnabled(ctx: Context, on: Boolean, w: Workspace) {
        sp(ctx).edit().putBoolean("on", on).apply()
        sync(ctx, w)
    }

    fun hasFine(ctx: Context) = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun hasBackground(ctx: Context) = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun dist(a: CardPlace, lat: Double, lon: Double): Float {
        val r = FloatArray(1)
        android.location.Location.distanceBetween(a.lat, a.lon, lat, lon, r)
        return r[0]
    }

    /** Запомнить текущее место для карты. onDone(true) — добавлено новое место. */
    @SuppressLint("MissingPermission")
    fun remember(ctx: Context, vm: MainViewModel, cardId: String, silent: Boolean, onDone: (Boolean) -> Unit = {}) {
        if (!hasFine(ctx)) { onDone(false); return }
        LocationServices.getFusedLocationProviderClient(ctx)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc == null || loc.accuracy > 120) { if (!silent) vm.message = "Не удалось точно определить место — попробуйте на улице"; onDone(false); return@addOnSuccessListener }
                val card = vm.work.cards.firstOrNull { it.id == cardId } ?: return@addOnSuccessListener
                if (card.places.any { dist(it, loc.latitude, loc.longitude) < 200 }) { if (!silent) vm.message = "Это место уже запомнено"; onDone(false); return@addOnSuccessListener }
                val places = (card.places + CardPlace(loc.latitude, loc.longitude)).takeLast(MAX_PER_CARD)
                vm.updateWork { w -> w.copy(cards = w.cards.map { if (it.id == cardId) it.copy(places = places) else it }) }
                sync(ctx, vm.work)
                vm.message = "📍 Запомнил магазин «${card.store}» — подскажу карту, когда будете рядом"
                onDone(true)
            }
            .addOnFailureListener { if (!silent) vm.message = "Место недоступно: включите геолокацию"; onDone(false) }
    }

    private fun pending(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 77, Intent(ctx, CardPlaceReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0),
    )

    /** Перерегистрировать зоны магазинов (не больше 100 — ограничение Android). */
    @SuppressLint("MissingPermission")
    fun sync(ctx: Context, w: Workspace) {
        val client = LocationServices.getGeofencingClient(ctx)
        runCatching { client.removeGeofences(pending(ctx)) }
        if (!enabled(ctx) || !hasFine(ctx) || !hasBackground(ctx)) return
        val fences = w.cards.sortedByDescending { it.uses }.flatMap { c ->
            c.places.mapIndexed { i, p ->
                Geofence.Builder().setRequestId("${c.id}|$i").setCircularRegion(p.lat, p.lon, RADIUS)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL).setLoiteringDelay(60_000).build()
            }
        }.take(100)
        if (fences.isEmpty()) return
        val req = GeofencingRequest.Builder().setInitialTrigger(0).addGeofences(fences).build()
        runCatching { client.addGeofences(req, pending(ctx)) }
    }

    fun signature(w: Workspace) = w.cards.sumOf { it.places.size * 31 + it.id.hashCode() }
}

class CardPlaceReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val ev = GeofencingEvent.fromIntent(intent) ?: return
        if (ev.hasError()) return
        val ids = ev.triggeringGeofences.orEmpty().map { it.requestId.substringBefore('|') }.distinct()
        if (ids.isEmpty()) return
        val w = WorkStore(ctx).load()
        val p = ctx.getSharedPreferences("card_places", Context.MODE_PRIVATE)
        ids.mapNotNull { id -> w.cards.firstOrNull { it.id == id } }.forEach { c ->
            // Не чаще раза в 3 часа на магазин
            val key = "last_${c.id}"
            if (System.currentTimeMillis() - p.getLong(key, 0) < 3 * 3600_000L) return@forEach
            p.edit().putLong(key, System.currentTimeMillis()).apply()
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("cards", "Карты у магазина", NotificationManager.IMPORTANCE_HIGH))
            val open = Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_QUICK, "card:${c.id}")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(ctx, c.id.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val n = NotificationCompat.Builder(ctx, "cards").setSmallIcon(R.drawable.ic_card_tile)
                .setContentTitle("💳 Вы рядом: ${c.store}").setContentText("Нажмите, чтобы открыть карту").setContentIntent(pi).setAutoCancel(true).build()
            runCatching { nm.notify(9000 + (c.id.hashCode() and 0xfff), n) }
        }
    }
}
