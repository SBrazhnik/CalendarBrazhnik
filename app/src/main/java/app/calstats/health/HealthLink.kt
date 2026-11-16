package app.calstats.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import app.calstats.work.HealthSnapshot
import app.calstats.work.Workout
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

/**
 * Health Connect — системное хранилище данных о здоровье Android.
 * Samsung Health пишет туда шаги, сон, пульс, вес и тренировки (Samsung Health → Настройки → Health Connect).
 */
object HealthLink {
    const val PROVIDER = "com.google.android.apps.healthdata"

    enum class Status { AVAILABLE, NEEDS_UPDATE, UNAVAILABLE }

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
    )

    fun status(ctx: Context): Status = when (HealthConnectClient.getSdkStatus(ctx, PROVIDER)) {
        HealthConnectClient.SDK_AVAILABLE -> Status.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.NEEDS_UPDATE
        else -> Status.UNAVAILABLE
    }

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    suspend fun granted(ctx: Context): Set<String> =
        if (status(ctx) != Status.AVAILABLE) emptySet()
        else runCatching { HealthConnectClient.getOrCreate(ctx).permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    /** Установить/обновить Health Connect (на Android 13 и ниже это отдельное приложение). */
    fun installIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun settingsIntent(): Intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Данные за последние [days] дней (включая сегодня). Каждый блок читается отдельно — нет разрешения, нет и блока. */
    suspend fun load(ctx: Context, days: Int = 14): HealthSnapshot {
        val client = HealthConnectClient.getOrCreate(ctx)
        val granted = granted(ctx)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val first = today.minusDays(days - 1L)
        val dayList = (0 until days).map { first.plusDays(it.toLong()) }
        val startLdt = first.atStartOfDay()
        val endLdt = today.plusDays(1).atStartOfDay()

        fun has(r: kotlin.reflect.KClass<out androidx.health.connect.client.records.Record>) =
            HealthPermission.getReadPermission(r) in granted

        val steps = LongArray(days)
        val active = DoubleArray(days)
        if (has(StepsRecord::class)) runCatching {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startLdt, endLdt),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            ).forEach { g ->
                val i = (g.startTime.toLocalDate().toEpochDay() - first.toEpochDay()).toInt()
                if (i in 0 until days) steps[i] = g.result[StepsRecord.COUNT_TOTAL] ?: 0
            }
        }
        if (has(ActiveCaloriesBurnedRecord::class)) runCatching {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startLdt, endLdt),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            ).forEach { g ->
                val i = (g.startTime.toLocalDate().toEpochDay() - first.toEpochDay()).toInt()
                if (i in 0 until days) active[i] = g.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
            }
        }

        // Сон относим к дню пробуждения.
        val sleep = LongArray(days)
        if (has(SleepSessionRecord::class)) runCatching {
            val from = first.minusDays(1).atStartOfDay(zone).toInstant()
            client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(from, Instant.now()),
                ),
            ).records.forEach { s ->
                val wake = s.endTime.atZone(zone).toLocalDate()
                val i = (wake.toEpochDay() - first.toEpochDay()).toInt()
                val awake = s.stages.filter { it.stage == SleepSessionRecord.STAGE_TYPE_AWAKE || it.stage == SleepSessionRecord.STAGE_TYPE_OUT_OF_BED }
                    .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                if (i in 0 until days) sleep[i] += (Duration.between(s.startTime, s.endTime).toMinutes() - awake).coerceAtLeast(0)
            }
        }

        var resting: Int? = null
        if (has(RestingHeartRateRecord::class)) runCatching {
            resting = client.readRecords(
                ReadRecordsRequest(
                    recordType = RestingHeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(today.minusDays(7).atStartOfDay(zone).toInstant(), Instant.now()),
                ),
            ).records.maxByOrNull { it.time }?.beatsPerMinute?.toInt()
        }
        var avg: Int? = null
        if (has(HeartRateRecord::class)) runCatching {
            avg = client.aggregate(
                AggregateRequest(
                    metrics = setOf(HeartRateRecord.BPM_AVG),
                    timeRangeFilter = TimeRangeFilter.between(today.atStartOfDay(zone).toInstant(), Instant.now()),
                ),
            )[HeartRateRecord.BPM_AVG]?.toInt()
        }
        var weight: Double? = null
        if (has(WeightRecord::class)) runCatching {
            weight = client.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(today.minusDays(60).atStartOfDay(zone).toInstant(), Instant.now()),
                ),
            ).records.maxByOrNull { it.time }?.weight?.inKilograms
        }
        val workouts = ArrayList<Workout>()
        if (has(ExerciseSessionRecord::class)) runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(first.atStartOfDay(zone).toInstant(), Instant.now()),
                ),
            ).records.sortedByDescending { it.startTime }.forEach { e ->
                workouts += Workout(
                    e.title?.takeIf { it.isNotBlank() } ?: exerciseName(e.exerciseType),
                    e.startTime.atZone(zone).toLocalDate(),
                    Duration.between(e.startTime, e.endTime).toMinutes(),
                )
            }
        }
        return HealthSnapshot(
            days = dayList, steps = steps.toList(), activeKcal = active.toList(), sleepMin = sleep.toList(),
            restingHr = resting, avgHr = avg, weightKg = weight, workouts = workouts, loadedAt = System.currentTimeMillis(),
        )
    }

    private fun exerciseName(t: Int): String = when (t) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Ходьба"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Бег"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Велосипед"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Плавание"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Поход"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Йога"
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Пилатес"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Силовая"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "Интервальная"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Эллипс"
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Гребля"
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "Танцы"
        ExerciseSessionRecord.EXERCISE_TYPE_SKIING -> "Лыжи"
        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "Теннис"
        ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "Футбол"
        ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "Растяжка"
        else -> "Тренировка"
    }
}
