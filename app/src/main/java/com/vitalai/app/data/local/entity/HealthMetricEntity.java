package com.vitalai.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.vitalai.app.data.local.converter.Converters;
import com.vitalai.app.domain.model.enums.MetricSource;
import com.vitalai.app.domain.model.enums.MetricType;

import java.util.Date;

/**
 * HealthMetricEntity
 *
 * Room entity representing a single health measurement in the
 * {@code health_metrics} table.
 *
 * Design notes
 * ────────────
 * • Each row is one discrete measurement of one metric type at one point
 *   in time. This flat, time-series design makes it trivial to query
 *   "all heart-rate readings in the last 24 h" with a single indexed
 *   range scan on (user_id, metric_type, timestamp).
 *
 * • Foreign key to {@link UserEntity} uses ON DELETE CASCADE — when a
 *   user record is deleted all their metrics are automatically purged,
 *   keeping the database consistent without requiring manual cleanup in
 *   the Repository layer.
 *
 * • ON UPDATE CASCADE propagates any (rare) PK change on users.id to
 *   all child rows automatically.
 *
 * • Three composite indexes are declared:
 *     1. (user_id, metric_type, timestamp) — the primary query pattern:
 *        "give me all HEART_RATE readings for user X between T1 and T2".
 *     2. (user_id, timestamp)              — for dashboard "all metrics
 *        in last N hours" queries without a type filter.
 *     3. (user_id, is_anomaly)             — fast lookup of flagged
 *        readings for the anomaly-alert pipeline.
 *
 * • {@code value} is stored as REAL (64-bit IEEE 754 double). All unit
 *   normalisation (e.g. converting mg/dL ↔ mmol/L for blood glucose)
 *   is the responsibility of the domain/Repository layer; the DB always
 *   stores the canonical SI/standard unit for each MetricType.
 *
 * • {@code deviceId} is nullable — manually entered metrics and
 *   HealthConnect readings may not have an associated device MAC/ID.
 *
 * • {@code isAnomaly} defaults to false. The ML pipeline (TFLite model
 *   running in a WorkManager worker) updates this flag asynchronously
 *   after insertion; UI must not assume it is set immediately.
 *
 * Architecture layer : Data / Local
 * Table name         : health_metrics
 * Related DAOs       : HealthMetricDao
 * Parent FK          : users.id  (CASCADE delete + update)
 */
@Entity(
        tableName = "health_metrics",
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
                // Primary query pattern: user + type + time range
                @Index(value = {"user_id", "metric_type", "timestamp"}),
                // Dashboard: all metrics for user in time window
                @Index(value = {"user_id", "timestamp"}),
                // Anomaly alert pipeline
                @Index(value = {"user_id", "is_anomaly"})
        }
)
public class HealthMetricEntity {

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
     * Must be indexed (declared above) — Room requires an index on every
     * FK child column for efficient parent-row lookup and cascade operations.
     */
    @ColumnInfo(name = "user_id", index = true)
    public long userId;

    // ──────────────────────────────────────────────────────────────────────
    // Measurement Core
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant at which this measurement was taken (not inserted).
     * For BLE devices this is the device timestamp; for HealthConnect it
     * is the sample's {@code time} field; for manual entry it is the
     * user-selected time.
     * Stored as epoch millis via {@link Converters#fromDate}.
     */
    @NonNull
    @ColumnInfo(name = "timestamp")
    public Date timestamp;

    /**
     * The kind of health measurement this row represents.
     *
     * Canonical units per type:
     *   HEART_RATE              → bpm        (beats per minute)
     *   HEART_RATE_VARIABILITY  → ms         (milliseconds, RMSSD)
     *   BLOOD_PRESSURE_SYSTOLIC → mmHg
     *   BLOOD_PRESSURE_DIASTOLIC→ mmHg
     *   BLOOD_OXYGEN_SPO2       → %          (0–100)
     *   BLOOD_GLUCOSE           → mg/dL
     *   BODY_TEMPERATURE        → °C
     *   RESPIRATORY_RATE        → breaths/min
     *   STEPS                   → count      (integer stored as double)
     *   CALORIES_BURNED         → kcal
     *   DISTANCE                → metres
     *   WEIGHT                  → kg
     *   BODY_FAT_PERCENTAGE     → %          (0–100)
     *   BMI                     → kg/m²
     *   STRESS_SCORE            → 0–100      (model output)
     *   SLEEP_SCORE             → 0–100      (model output)
     *   HYDRATION_ML            → mL
     *   MOOD_SCORE              → 1–5        (integer stored as double)
     *
     * Stored as TEXT via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "metric_type")
    public MetricType metricType;

    /**
     * The numeric measurement value in the canonical unit for
     * {@link #metricType}. Using {@code double} (REAL in SQLite) covers
     * all metric types — integer counts (steps) are losslessly
     * representable as doubles up to 2^53.
     */
    @ColumnInfo(name = "value")
    public double value;

    /**
     * How this measurement was collected.
     *
     * Values:
     *   BLE            — streamed from a Bluetooth Low Energy peripheral
     *   HEALTH_CONNECT — read from Android Health Connect data store
     *   CSV_IMPORT     — imported from a user-uploaded CSV file
     *   SYNTHETIC      — generated by the AI/interpolation pipeline
     *   MANUAL         — entered directly by the user
     *
     * Stored as TEXT via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "source")
    public MetricSource source;

    /**
     * Identifier of the device that produced this reading.
     * For BLE devices: the MAC address string (e.g. "AA:BB:CC:DD:EE:FF").
     * For HealthConnect: the originating app package name.
     * Null for MANUAL and SYNTHETIC sources.
     */
    @Nullable
    @ColumnInfo(name = "device_id")
    public String deviceId;

    /**
     * Whether the ML anomaly-detection model has flagged this reading.
     *
     * FALSE  — normal reading (default at insert time).
     * TRUE   — flagged as statistically anomalous relative to the user's
     *          personal baseline stored in {@code user_baselines}.
     *
     * Updated asynchronously by the TFLite inference WorkManager worker.
     * The UI should observe this field via LiveData/Flow and refresh the
     * anomaly badge without requiring a full list reload.
     *
     * SQLite stores booleans as INTEGER (0/1); Room maps Java boolean
     * to this automatically.
     */
    @ColumnInfo(name = "is_anomaly", defaultValue = "0")
    public boolean isAnomaly;

    // ──────────────────────────────────────────────────────────────────────
    // Timestamps
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant when this row was first written to the local database.
     * Distinct from {@link #timestamp} — a CSV import may insert rows with
     * historical timestamps; {@code createdAt} always reflects the actual
     * insert time and is used for sync / audit purposes.
     */
    @NonNull
    @ColumnInfo(name = "created_at")
    public Date createdAt;

    /**
     * UTC instant of the most recent update to this row.
     * Primarily updated when the ML pipeline sets {@link #isAnomaly}
     * or when a manual correction is applied.
     */
    @NonNull
    @ColumnInfo(name = "updated_at")
    public Date updatedAt;

    // ──────────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Full constructor used by the Repository when inserting a new metric.
     * Room uses direct field assignment when reading rows back from the DB.
     */
    public HealthMetricEntity(
            long userId,
            @NonNull Date timestamp,
            @NonNull MetricType metricType,
            double value,
            @NonNull MetricSource source,
            @Nullable String deviceId,
            boolean isAnomaly,
            @NonNull Date createdAt,
            @NonNull Date updatedAt) {

        this.userId     = userId;
        this.timestamp  = timestamp;
        this.metricType = metricType;
        this.value      = value;
        this.source     = source;
        this.deviceId   = deviceId;
        this.isAnomaly  = isAnomaly;
        this.createdAt  = createdAt;
        this.updatedAt  = updatedAt;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Convenience factories
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Factory for a fresh metric reading arriving from a device or
     * HealthConnect. Timestamps are set to now; {@code isAnomaly} defaults
     * to {@code false} and is updated later by the ML worker.
     *
     * @param userId     Local user PK (from {@link UserEntity#id}).
     * @param timestamp  Measurement instant (device/source time, not now).
     * @param metricType Kind of measurement.
     * @param value      Numeric value in canonical unit.
     * @param source     Collection method.
     * @param deviceId   Device identifier, or null.
     * @return Ready-to-insert {@link HealthMetricEntity}.
     */
    public static HealthMetricEntity create(
            long userId,
            @NonNull Date timestamp,
            @NonNull MetricType metricType,
            double value,
            @NonNull MetricSource source,
            @Nullable String deviceId) {

        Date now = new Date();
        return new HealthMetricEntity(
                userId, timestamp, metricType, value,
                source, deviceId, false, now, now);
    }

    /**
     * Convenience overload for manually entered metrics with no device.
     */
    public static HealthMetricEntity createManual(
            long userId,
            @NonNull Date timestamp,
            @NonNull MetricType metricType,
            double value) {

        return create(userId, timestamp, metricType, value,
                MetricSource.MANUAL, null);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Marks this reading as anomalous and stamps {@code updated_at}.
     * Call this in the Repository after the ML worker returns a positive
     * result, then pass the entity to {@code HealthMetricDao.update()}.
     */
    public void flagAsAnomaly() {
        this.isAnomaly  = true;
        this.updatedAt  = new Date();
    }

    /** Refreshes {@code updated_at} to now. */
    public void touch() {
        this.updatedAt = new Date();
    }

    @Override
    public String toString() {
        return "HealthMetricEntity{"
                + "id=" + id
                + ", userId=" + userId
                + ", timestamp=" + timestamp
                + ", metricType=" + metricType
                + ", value=" + value
                + ", source=" + source
                + ", deviceId='" + deviceId + '\''
                + ", isAnomaly=" + isAnomaly
                + '}';
    }
}