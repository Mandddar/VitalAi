package com.vitalai.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.vitalai.app.domain.model.enums.MetricSource;

import java.util.Date;

/**
 * WorkoutEntity
 *
 * Room entity representing a row in the {@code workouts} table.
 * Each row records one discrete exercise session belonging to a user.
 *
 * Design notes
 * ────────────
 * • Foreign key to {@link UserEntity} uses ON DELETE CASCADE — deleting a
 *   user automatically removes all their workout records, preserving
 *   referential integrity without manual Repository cleanup.
 *
 * • {@code workoutType} is stored as free-form TEXT rather than an enum
 *   because exercise modalities are too broad and locale-varied for a
 *   closed value set (e.g. "Running", "Cycling", "HIIT", "Yoga",
 *   "Olympic Weightlifting", "Krav Maga"). The AI layer matches these
 *   strings against a fuzzy taxonomy at inference time. A controlled
 *   vocabulary can be enforced in the domain layer if needed.
 *
 * • A session is defined by a [startTime, endTime] window. Duration in
 *   seconds is also stored as {@code durationSeconds} for two reasons:
 *     1. Some sources (HealthConnect, CSV imports) provide duration as
 *        a first-class field independent of the time bounds.
 *     2. Avoids recomputing (endTime - startTime) in every aggregation
 *        query on the weekly/monthly workout summary dashboard.
 *   The Repository must keep durationSeconds consistent with the
 *   [startTime, endTime] window whenever either is updated.
 *
 * • Caloric and distance fields use canonical SI / standard units:
 *     caloriesBurned → kcal
 *     distanceMetres → metres
 *   UI-layer conversion to miles/km is handled in the ViewModel.
 *
 * • {@code avgHeartRate} and {@code maxHeartRate} are session-level
 *   snapshots pre-aggregated here to avoid a costly join against
 *   {@code health_metrics} on every workout list load. The canonical
 *   per-second HR readings still live in {@code health_metrics}.
 *
 * • {@code source} reuses {@link MetricSource} — workouts arrive from
 *   the same pipelines as individual metrics (BLE, HealthConnect,
 *   CSV import, or manual entry).
 *
 * • {@code isAnomaly} follows the same async-ML pattern as
 *   {@link HealthMetricEntity#isAnomaly}: defaults false at insert,
 *   updated by the TFLite WorkManager worker after the session ends
 *   (e.g. flagging an unusually high HR throughout a low-intensity run).
 *
 * • Three indexes are declared:
 *     1. (user_id, start_time)    — primary query pattern: "all workouts
 *        for user X in chronological order" for history and trend charts.
 *     2. (user_id, workout_type)  — filter queries: "all Running sessions
 *        for user X" for sport-specific analytics and PB tracking.
 *     3. (user_id, is_anomaly)    — fast lookup of flagged sessions for
 *        the anomaly-alert pipeline, mirroring HealthMetricEntity's index.
 *
 * Architecture layer : Data / Local
 * Table name         : workouts
 * Related DAOs       : WorkoutDao
 * Parent FK          : users.id  (CASCADE delete + update)
 */
@Entity(
        tableName = "workouts",
        foreignKeys = {
                @ForeignKey(
                        entity        = UserEntity.class,
                        parentColumns = "id",
                        childColumns  = "user_id",
                        onDelete      = ForeignKey.CASCADE,
                        onUpdate      = ForeignKey.CASCADE
                )
        },
        indices = {
                // Primary: chronological workout history for a user
                @Index(value = {"user_id", "start_time"}),
                // Sport-specific analytics and personal-best tracking
                @Index(value = {"user_id", "workout_type"}),
                // Anomaly alert pipeline (mirrors HealthMetricEntity)
                @Index(value = {"user_id", "is_anomaly"})
        }
)
public class WorkoutEntity {

    // ──────────────────────────────────────────────────────────────────────
    // Primary Key
    // ──────────────────────────────────────────────────────────────────────

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    // ──────────────────────────────────────────────────────────────────────
    // Foreign Key
    // ──────────────────────────────────────────────────────────────────────

    /**
     * References {@link UserEntity#id}.
     * Covered by all three composite indexes declared above.
     */
    @ColumnInfo(name = "user_id")
    public long userId;

    // ──────────────────────────────────────────────────────────────────────
    // Session Window
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant when the workout began (first movement / device start).
     * For BLE devices: the device-reported session start timestamp.
     * For HealthConnect: the {@code startTime} of the ExerciseSessionRecord.
     * For manual entry: the user-selected start time.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "start_time")
    public Date startTime;

    /**
     * UTC instant when the workout ended.
     * Nullable — a session currently in progress has no end time yet.
     * The Repository must update this field (and {@link #durationSeconds})
     * when the device signals session end or the user taps "Finish".
     * Stored as epoch millis via TypeConverters.
     */
    @Nullable
    @ColumnInfo(name = "end_time")
    public Date endTime;

    /**
     * Total active workout duration in seconds.
     *
     * Stored explicitly because:
     *   • Some sources provide duration independent of wall-clock bounds
     *     (e.g. a GPS watch that pauses the timer during rest stops).
     *   • Avoids repeated (endTime - startTime) arithmetic in aggregation
     *     queries on the activity summary dashboard.
     *
     * May differ from (endTime - startTime) when auto-pause is active.
     * Nullable — not yet known for in-progress sessions.
     * Repository must keep this in sync with endTime on every update.
     */
    @Nullable
    @ColumnInfo(name = "duration_seconds")
    public Integer durationSeconds;

    // ──────────────────────────────────────────────────────────────────────
    // Workout Classification
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Free-form exercise type / modality label.
     *
     * Common values (not enforced):
     *   "Running", "Cycling", "Walking", "Swimming", "HIIT",
     *   "Strength Training", "Yoga", "Pilates", "Rowing", "Hiking",
     *   "Elliptical", "Jump Rope", "Boxing", "CrossFit", "Stretching"
     *
     * The AI insight layer performs fuzzy matching against an internal
     * taxonomy to group sessions (e.g. "Run" → "Running") for analytics.
     * Case-insensitive comparisons are handled in the Repository/ViewModel.
     */
    @NonNull
    @ColumnInfo(name = "workout_type")
    public String workoutType;

    // ──────────────────────────────────────────────────────────────────────
    // Performance Metrics
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Total energy expended during the session in kilocalories (kcal).
     * Computed by the device firmware or estimated by the AI pipeline
     * using HR, weight, age, and activity type.
     * Nullable — not available for all workout types or all devices.
     */
    @Nullable
    @ColumnInfo(name = "calories_burned")
    public Double caloriesBurned;

    /**
     * Total distance covered during the session in metres.
     * Nullable — only meaningful for locomotion-based activities
     * (running, cycling, swimming, hiking). Null for stationary workouts
     * (strength training, yoga, HIIT without GPS).
     * UI converts to km or miles based on the user's locale preference.
     */
    @Nullable
    @ColumnInfo(name = "distance_metres")
    public Double distanceMetres;

    /**
     * Total step count recorded during the session.
     * Nullable — available from pedometer-capable devices for ambulatory
     * activities. Null for cycling, swimming, and strength workouts.
     * Stored as Integer; fractional steps are not meaningful.
     */
    @Nullable
    @ColumnInfo(name = "step_count")
    public Integer stepCount;

    /**
     * Mean heart rate (bpm) recorded across the active session window.
     * Pre-aggregated snapshot — avoids a join against {@code health_metrics}
     * on every workout list load. Canonical per-second HR readings remain
     * in {@code health_metrics} for detailed analysis.
     * Nullable — not available if HR monitoring was disabled or device
     * lost contact for the majority of the session.
     */
    @Nullable
    @ColumnInfo(name = "avg_heart_rate")
    public Integer avgHeartRate;

    /**
     * Peak heart rate (bpm) recorded at any point during the session.
     * Used for training-zone analysis and overexertion alerts.
     * Pre-aggregated for the same reason as {@link #avgHeartRate}.
     * Nullable under the same conditions as {@link #avgHeartRate}.
     */
    @Nullable
    @ColumnInfo(name = "max_heart_rate")
    public Integer maxHeartRate;

    /**
     * Elevation gained during the session in metres (cumulative ascent).
     * Nullable — only available from GPS-capable devices on non-flat
     * routes. Null for treadmill, indoor cycling, and pool swimming.
     */
    @Nullable
    @ColumnInfo(name = "elevation_gain_metres")
    public Double elevationGainMetres;

    /**
     * Average pace in seconds per kilometre (sec/km).
     * Applicable to running, walking, and hiking; null for cycling,
     * strength, or any session without distance data.
     * Stored as seconds to keep it as an integer-compatible value and
     * avoid floating-point display artefacts (e.g. 5:30/km = 330 sec/km).
     * Nullable — computed by the Repository from distance and duration
     * when both are available; null otherwise.
     */
    @Nullable
    @ColumnInfo(name = "avg_pace_sec_per_km")
    public Integer avgPaceSecPerKm;

    // ──────────────────────────────────────────────────────────────────────
    // Data Provenance
    // ──────────────────────────────────────────────────────────────────────

    /**
     * How this workout record was collected.
     * Reuses {@link MetricSource} — workouts flow through the same
     * ingestion pipelines as individual health metrics.
     *
     * Values:
     *   BLE            — streamed live from a Bluetooth Low Energy device
     *   HEALTH_CONNECT — read from Android Health Connect
     *   CSV_IMPORT     — imported from a user-uploaded CSV file
     *   MANUAL         — entered directly by the user
     *   SYNTHETIC      — generated by the AI / interpolation pipeline
     *
     * Stored as TEXT via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "source")
    public MetricSource source;

    /**
     * Identifier of the device that recorded this session.
     * For BLE devices: MAC address string (e.g. "AA:BB:CC:DD:EE:FF").
     * For HealthConnect: originating app package name.
     * Null for MANUAL and SYNTHETIC sources.
     */
    @Nullable
    @ColumnInfo(name = "device_id")
    public String deviceId;

    // ──────────────────────────────────────────────────────────────────────
    // AI Anomaly Flag
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Whether the ML anomaly-detection model has flagged this session.
     *
     * FALSE  — normal workout (default at insert time).
     * TRUE   — flagged as anomalous relative to the user's personal
     *          baseline (e.g. sustained HR above threshold for workout
     *          type, abnormal calorie-to-duration ratio, atypical pace
     *          variance indicating possible device error).
     *
     * Updated asynchronously by the TFLite WorkManager worker after the
     * session ends. UI observes this via LiveData/Flow; the anomaly badge
     * refreshes without a full list reload. Mirrors the same pattern used
     * in {@link HealthMetricEntity#isAnomaly}.
     *
     * SQLite stores as INTEGER (0 = false, 1 = true).
     */
    @ColumnInfo(name = "is_anomaly", defaultValue = "0")
    public boolean isAnomaly;

    // ──────────────────────────────────────────────────────────────────────
    // Notes
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Optional free-text note entered by the user about this session.
     * Examples: "Felt great, new 5K PB", "Cut short — knee pain",
     *           "Interval set: 8×400m @ 5K pace".
     * Fed into the AI chat context when the user asks about workout history
     * or requests training load analysis.
     */
    @Nullable
    @ColumnInfo(name = "notes")
    public String notes;

    // ──────────────────────────────────────────────────────────────────────
    // Timestamps
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant when this row was first written to the local database.
     * Set once at insert time; never modified thereafter.
     * Distinct from {@link #startTime} — a CSV import may insert rows with
     * historical start times; {@code createdAt} always reflects actual
     * insert time and is used for sync / audit purposes.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "created_at")
    public Date createdAt;

    /**
     * UTC instant of the most recent update to this row.
     * Updated when {@link #endTime}, {@link #durationSeconds},
     * performance metrics, or {@link #isAnomaly} are written after
     * session end.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "updated_at")
    public Date updatedAt;

    // ──────────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Full constructor used by the Repository when inserting a new workout.
     * Room uses direct field assignment when reading rows back from the DB.
     *
     * @param userId               Local PK of the owning {@link UserEntity}.
     * @param startTime            Session start instant (required).
     * @param endTime              Session end instant, or null if in progress.
     * @param durationSeconds      Active duration in seconds, or null.
     * @param workoutType          Exercise modality label (required).
     * @param caloriesBurned       Energy expended in kcal, or null.
     * @param distanceMetres       Distance in metres, or null.
     * @param stepCount            Step count, or null.
     * @param avgHeartRate         Mean HR in bpm, or null.
     * @param maxHeartRate         Peak HR in bpm, or null.
     * @param elevationGainMetres  Cumulative ascent in metres, or null.
     * @param avgPaceSecPerKm      Average pace in sec/km, or null.
     * @param source               Data collection method (required).
     * @param deviceId             Device identifier, or null.
     * @param isAnomaly            ML anomaly flag (false at insert time).
     * @param notes                User-entered note, or null.
     * @param createdAt            Insert timestamp (set by Repository).
     * @param updatedAt            Last-update timestamp (set by Repository).
     */
    public WorkoutEntity(
            long userId,
            @NonNull Date startTime,
            @Nullable Date endTime,
            @Nullable Integer durationSeconds,
            @NonNull String workoutType,
            @Nullable Double caloriesBurned,
            @Nullable Double distanceMetres,
            @Nullable Integer stepCount,
            @Nullable Integer avgHeartRate,
            @Nullable Integer maxHeartRate,
            @Nullable Double elevationGainMetres,
            @Nullable Integer avgPaceSecPerKm,
            @NonNull MetricSource source,
            @Nullable String deviceId,
            boolean isAnomaly,
            @Nullable String notes,
            @NonNull Date createdAt,
            @NonNull Date updatedAt) {

        this.userId              = userId;
        this.startTime           = startTime;
        this.endTime             = endTime;
        this.durationSeconds     = durationSeconds;
        this.workoutType         = workoutType;
        this.caloriesBurned      = caloriesBurned;
        this.distanceMetres      = distanceMetres;
        this.stepCount           = stepCount;
        this.avgHeartRate        = avgHeartRate;
        this.maxHeartRate        = maxHeartRate;
        this.elevationGainMetres = elevationGainMetres;
        this.avgPaceSecPerKm     = avgPaceSecPerKm;
        this.source              = source;
        this.deviceId            = deviceId;
        this.isAnomaly           = isAnomaly;
        this.notes               = notes;
        this.createdAt           = createdAt;
        this.updatedAt           = updatedAt;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Convenience factories
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Factory for a fully specified, completed workout — the common case
     * when ingesting a finished session from HealthConnect or a CSV import
     * where all performance fields are known.
     * {@code isAnomaly} defaults to false; ML worker updates it later.
     * Timestamps are set to now automatically.
     *
     * @param userId               Local PK of the owning user.
     * @param startTime            Session start instant.
     * @param endTime              Session end instant.
     * @param durationSeconds      Active duration in seconds, or null.
     * @param workoutType          Exercise modality label.
     * @param caloriesBurned       Energy in kcal, or null.
     * @param distanceMetres       Distance in metres, or null.
     * @param stepCount            Step count, or null.
     * @param avgHeartRate         Mean HR in bpm, or null.
     * @param maxHeartRate         Peak HR in bpm, or null.
     * @param elevationGainMetres  Cumulative ascent in metres, or null.
     * @param avgPaceSecPerKm      Average pace in sec/km, or null.
     * @param source               Data collection method.
     * @param deviceId             Device identifier, or null.
     * @param notes                User note, or null.
     * @return Ready-to-insert {@link WorkoutEntity}.
     */
    public static WorkoutEntity create(
            long userId,
            @NonNull Date startTime,
            @Nullable Date endTime,
            @Nullable Integer durationSeconds,
            @NonNull String workoutType,
            @Nullable Double caloriesBurned,
            @Nullable Double distanceMetres,
            @Nullable Integer stepCount,
            @Nullable Integer avgHeartRate,
            @Nullable Integer maxHeartRate,
            @Nullable Double elevationGainMetres,
            @Nullable Integer avgPaceSecPerKm,
            @NonNull MetricSource source,
            @Nullable String deviceId,
            @Nullable String notes) {

        Date now = new Date();
        return new WorkoutEntity(
                userId, startTime, endTime, durationSeconds, workoutType,
                caloriesBurned, distanceMetres, stepCount,
                avgHeartRate, maxHeartRate, elevationGainMetres,
                avgPaceSecPerKm, source, deviceId, false, notes, now, now);
    }

    /**
     * Factory for a live BLE session that has just started. All performance
     * metrics are null and populated progressively as the device streams
     * data. Call {@link #close(Date)} when the session ends.
     *
     * @param userId      Local PK of the owning user.
     * @param startTime   Session start instant (device timestamp).
     * @param workoutType Exercise modality label.
     * @param source      Data collection method (typically BLE).
     * @param deviceId    BLE device MAC address.
     * @return Ready-to-insert {@link WorkoutEntity}.
     */
    public static WorkoutEntity createOngoing(
            long userId,
            @NonNull Date startTime,
            @NonNull String workoutType,
            @NonNull MetricSource source,
            @Nullable String deviceId) {

        return create(userId, startTime, null, null, workoutType,
                null, null, null, null, null, null, null,
                source, deviceId, null);
    }

    /**
     * Minimal factory for a manually entered workout with only the
     * essential fields. Performance metrics can be added later via update.
     *
     * @param userId      Local PK of the owning user.
     * @param startTime   Session start instant.
     * @param endTime     Session end instant.
     * @param workoutType Exercise modality label.
     * @return Ready-to-insert {@link WorkoutEntity}.
     */
    public static WorkoutEntity createManual(
            long userId,
            @NonNull Date startTime,
            @NonNull Date endTime,
            @NonNull String workoutType) {

        int duration = (int) ((endTime.getTime() - startTime.getTime()) / 1_000L);
        return create(userId, startTime, endTime, duration, workoutType,
                null, null, null, null, null, null, null,
                MetricSource.MANUAL, null, null);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Closes an in-progress session by setting {@link #endTime} and
     * deriving {@link #durationSeconds} from the wall-clock window.
     * Call this in the Repository when the device signals session end,
     * then pass the entity to {@code WorkoutDao.update()}.
     *
     * Note: if the device provides an authoritative active-duration value
     * (accounting for auto-pause), override {@link #durationSeconds}
     * after calling this method before persisting.
     *
     * @param endTime Session-end instant (device timestamp).
     */
    public void close(@NonNull Date endTime) {
        this.endTime         = endTime;
        this.durationSeconds = (int) ((endTime.getTime() - startTime.getTime()) / 1_000L);
        this.updatedAt       = new Date();
    }

    /**
     * Marks this session as anomalous and stamps {@link #updatedAt}.
     * Call this in the Repository after the TFLite worker returns a
     * positive result, then pass the entity to {@code WorkoutDao.update()}.
     * Mirrors {@link HealthMetricEntity#flagAsAnomaly()}.
     */
    public void flagAsAnomaly() {
        this.isAnomaly = true;
        this.updatedAt = new Date();
    }

    /**
     * Derives the wall-clock duration of this session in minutes.
     * Returns null if {@link #endTime} has not been set yet.
     * Prefer {@link #durationSeconds} / 60 for active-time duration when
     * auto-pause data is available.
     */
    @Nullable
    public Integer wallClockDurationMinutes() {
        if (endTime == null) return null;
        return (int) ((endTime.getTime() - startTime.getTime()) / 60_000L);
    }

    /**
     * Refreshes {@code updated_at} to the current instant.
     * Call this in the Repository before any partial update
     * (e.g. filling in performance metrics post-session) via
     * {@code WorkoutDao.update()}.
     */
    public void touch() {
        this.updatedAt = new Date();
    }

    @Override
    public String toString() {
        return "WorkoutEntity{"
                + "id=" + id
                + ", userId=" + userId
                + ", startTime=" + startTime
                + ", endTime=" + endTime
                + ", durationSeconds=" + durationSeconds
                + ", workoutType='" + workoutType + '\''
                + ", caloriesBurned=" + caloriesBurned
                + ", distanceMetres=" + distanceMetres
                + ", stepCount=" + stepCount
                + ", avgHeartRate=" + avgHeartRate
                + ", maxHeartRate=" + maxHeartRate
                + ", elevationGainMetres=" + elevationGainMetres
                + ", avgPaceSecPerKm=" + avgPaceSecPerKm
                + ", source=" + source
                + ", deviceId='" + deviceId + '\''
                + ", isAnomaly=" + isAnomaly
                + '}';
    }
}