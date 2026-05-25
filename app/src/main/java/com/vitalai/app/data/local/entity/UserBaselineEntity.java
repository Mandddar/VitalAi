package com.vitalai.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.vitalai.app.data.local.converter.Converters;
import com.vitalai.app.domain.model.enums.MetricType;

import java.util.Date;

/**
 * UserBaselineEntity
 *
 * Room entity representing a row in the {@code user_baselines} table.
 * Each row stores one computed personal baseline value for a specific
 * {@link MetricType} belonging to a user — the rolling reference point
 * used by the ML anomaly-detection pipeline, the AI coaching layer, and
 * the goals-progress system to contextualise new metric readings.
 *
 * <p>Purpose
 * ─────────
 * Raw health metrics are meaningless without personalised context.
 * A resting heart rate of 65 bpm is unremarkable for a sedentary adult
 * but would be elevated for an elite endurance athlete whose norm is 42 bpm.
 * This table provides that norm — one row per (user_id, metric_type) pair —
 * so that anomaly detection, trend commentary, and goal-progress messages
 * all reference <em>the user's own history</em> rather than population averages.
 *
 * <p>Design notes
 * ────────────
 * <ul>
 *   <li>Foreign key to {@link UserEntity} uses ON DELETE CASCADE — deleting
 *       a user automatically removes all their baseline rows without any
 *       manual Repository cleanup.</li>
 *
 *   <li>A (user_id, metric_type) UNIQUE index enforces one baseline row
 *       per metric per user. The Repository performs an upsert (insert or
 *       replace) when recalculating baselines, so there is never a need to
 *       query-then-update in a separate round trip.</li>
 *
 *   <li>{@code metricType} maps to the {@link MetricType} enum stored as
 *       TEXT via {@link Converters}.
 *       This keeps the column human-readable and safe against enum ordinal
 *       reordering. Examples: HEART_RATE, BLOOD_PRESSURE_SYSTOLIC,
 *       BLOOD_GLUCOSE, STEPS, WEIGHT, HRV, RESPIRATORY_RATE, SPO2.</li>
 *
 *   <li>{@code baselineValue} is the primary reference number (e.g. mean
 *       resting HR = 68.4 bpm). The ML worker writes this after processing
 *       a rolling window of recent {@link HealthMetricEntity} rows
 *       (typically the last 30 days of clean readings, configurable per
 *       metric type in the domain layer).</li>
 *
 *   <li>{@code stdDev} is the standard deviation of the readings used to
 *       compute {@code baselineValue}. Nullable — available only when the
 *       sample size is sufficient (configurable floor, e.g. ≥ 7 readings).
 *       The ML anomaly model uses mean ± k·σ as the normal band; the UI
 *       uses it to render the shaded "normal range" on sparkline charts.</li>
 *
 *   <li>{@code minValue} and {@code maxValue} capture the observed range
 *       of the window used for baseline computation. Nullable — omitted
 *       when the sample size is too small to be meaningful. Used by the AI
 *       layer to report ranges ("Your resting HR typically falls between
 *       60 and 76 bpm") and by the anomaly detector to set hard outer
 *       bounds independent of σ.</li>
 *
 *   <li>{@code sampleSize} records the number of data points used in the
 *       baseline calculation. The anomaly pipeline and AI coaching layer
 *       use this as a confidence signal — a baseline built from 3 readings
 *       should be treated with more uncertainty than one built from 120.</li>
 *
 *   <li>{@code windowDays} is the rolling-window length (in calendar days)
 *       over which the baseline was computed. Different metric types warrant
 *       different windows (e.g. HR baseline may use 30 days; weight may use
 *       90 days to smooth seasonal fluctuations). Stored here so the UI and
 *       coaching layer can describe the baseline accurately ("based on your
 *       last 30 days").</li>
 *
 *   <li>{@code computedAt} is the UTC instant the baseline was last
 *       recalculated. The WorkManager baseline-refresh job compares this
 *       against a per-metric staleness threshold to decide whether a
 *       recalculation is due. It is also displayed in the health-detail
 *       screen ("Baseline last updated 3 days ago").</li>
 *
 *   <li>{@code source} is a free-form TEXT label describing how the
 *       baseline was computed. Recommended values:
 *       <ul>
 *         <li>"ML_ROLLING_MEAN"    — computed by the TFLite/WorkManager pipeline</li>
 *         <li>"HEALTH_CONNECT_AVG" — imported aggregate from Android Health Connect</li>
 *         <li>"USER_OVERRIDE"      — manually set by the user in the profile settings</li>
 *         <li>"ONBOARDING_INPUT"   — entered during the onboarding questionnaire</li>
 *       </ul>
 *       Stored as TEXT rather than an enum to remain open-ended as new
 *       ingestion pipelines are added.</li>
 *
 *   <li>{@code unit} mirrors the display-unit convention used in
 *       {@link HealthMetricEntity}: it is the label shown to the user
 *       (e.g. "bpm", "mmHg", "mg/dL", "steps", "kg", "ms").
 *       Nullable — omitted for dimensionless scores such as HRV SDNN
 *       when the unit is implied by the metric type.</li>
 *
 *   <li>{@code isStale} is a cached flag set by the WorkManager job when
 *       the baseline has exceeded its staleness threshold and a fresh
 *       calculation is pending. The ML anomaly detector treats stale
 *       baselines with reduced confidence, and the UI surfaces a subtle
 *       "baseline updating" indicator. Cleared to false once the
 *       recalculation completes. Defaults to false at insert time.
 *       SQLite stores as INTEGER (0 = false, 1 = true).</li>
 *
 *   <li>{@code created_at} is set once at insert time and never modified.
 *       {@code updated_at} is refreshed on every upsert by the Repository.</li>
 * </ul>
 *
 * <p>Architecture layer : Data / Local
 * <br>Table name         : user_baselines
 * <br>Related DAOs       : UserBaselineDao
 * <br>Parent FK          : users.id  (CASCADE delete + update)
 */
@Entity(
        tableName = "user_baselines",
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
                // Uniqueness guard: one baseline per metric per user.
                // Also serves the fast "get baseline for user X, metric Y" lookup.
                @Index(value = {"user_id", "metric_type"}, unique = true),
                // Staleness sweep: WorkManager queries all stale baselines for a user
                @Index(value = {"user_id", "is_stale"}),
                // Recency check: "most recently computed baseline for user X"
                @Index(value = {"user_id", "computed_at"})
        }
)
public class UserBaselineEntity {

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
    // Metric Identity
    // ──────────────────────────────────────────────────────────────────────

    /**
     * The health metric type this baseline applies to.
     * Stored as TEXT via {@link Converters}.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code HEART_RATE}              — resting HR in bpm</li>
     *   <li>{@code BLOOD_PRESSURE_SYSTOLIC} — systolic BP in mmHg</li>
     *   <li>{@code BLOOD_PRESSURE_DIASTOLIC}— diastolic BP in mmHg</li>
     *   <li>{@code BLOOD_GLUCOSE}           — fasting glucose in mg/dL</li>
     *   <li>{@code STEPS}                   — daily step count</li>
     *   <li>{@code WEIGHT}                  — body weight in kg</li>
     *   <li>{@code HRV}                     — heart-rate variability (SDNN, ms)</li>
     *   <li>{@code RESPIRATORY_RATE}        — breaths per minute</li>
     *   <li>{@code SPO2}                    — blood oxygen saturation (%)</li>
     *   <li>{@code BODY_TEMPERATURE}        — core temp in °C</li>
     * </ul>
     *
     * Together with {@code userId}, this forms the unique key of the table.
     */
    @NonNull
    @ColumnInfo(name = "metric_type")
    public MetricType metricType;

    // ──────────────────────────────────────────────────────────────────────
    // Baseline Statistics
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Personal baseline reference value for this metric type.
     * Typically the rolling mean of recent clean readings over
     * {@link #windowDays} calendar days.
     *
     * <p>Examples:
     * <ul>
     *   <li>68.4  — mean resting HR over last 30 days (bpm)</li>
     *   <li>118.2 — mean systolic BP over last 30 days (mmHg)</li>
     *   <li>7840  — mean daily step count over last 30 days</li>
     *   <li>72.5  — mean weight over last 90 days (kg)</li>
     * </ul>
     *
     * This is the primary number used by the ML anomaly detector as the
     * centre of the normal band (mean ± k·{@link #stdDev}), and by the
     * AI coaching layer when framing progress ("You're averaging 500 steps
     * above your 30-day baseline").
     */
    @ColumnInfo(name = "baseline_value")
    public double baselineValue;

    /**
     * Standard deviation of the readings used to compute {@link #baselineValue}.
     *
     * <p>Used by the ML anomaly detector to define the normal band as
     * {@code baselineValue ± k·stdDev} where {@code k} is a per-metric
     * sensitivity constant configured in the domain layer (e.g. k = 2.0
     * for heart rate, k = 1.5 for blood glucose).
     *
     * <p>Also used by the UI to render the shaded normal-range band on the
     * health-metric sparkline and trend charts.
     *
     * <p>Nullable — omitted when {@link #sampleSize} is below the configured
     * minimum for reliable σ estimation (e.g. fewer than 7 readings). The
     * anomaly detector falls back to a population-norm σ when this is null.
     */
    @Nullable
    @ColumnInfo(name = "std_dev")
    public Double stdDev;

    /**
     * Minimum observed value within the baseline computation window.
     *
     * <p>Used by the AI layer for range descriptions ("Your resting HR
     * typically falls between 60 and 76 bpm") and by the anomaly detector
     * as the hard lower bound independent of σ.
     *
     * <p>Nullable — omitted when the sample size is too small to establish
     * a meaningful range (below the configured minimum in the domain layer).
     */
    @Nullable
    @ColumnInfo(name = "min_value")
    public Double minValue;

    /**
     * Maximum observed value within the baseline computation window.
     *
     * <p>Paired with {@link #minValue} to define the full observed range.
     * Used as the hard upper bound in the anomaly detector and as the
     * upper fence for trend-chart Y-axis scaling.
     *
     * <p>Nullable — omitted when the sample size is too small.
     */
    @Nullable
    @ColumnInfo(name = "max_value")
    public Double maxValue;

    /**
     * Number of data points ({@link HealthMetricEntity} rows) that were
     * included in the baseline calculation.
     *
     * <p>Serves as a confidence signal:
     * <ul>
     *   <li>The ML anomaly pipeline attenuates alert sensitivity when this
     *       is low (e.g. fewer than 7 readings → "personalised baseline
     *       still forming", use population norms as a fallback).</li>
     *   <li>The AI coaching layer qualifies baseline-relative statements
     *       with appropriate uncertainty when the sample is small.</li>
     *   <li>The UI may render a "baseline calibrating" message when
     *       {@code sampleSize} is below the domain-layer threshold.</li>
     * </ul>
     */
    @ColumnInfo(name = "sample_size")
    public int sampleSize;

    /**
     * Length in calendar days of the rolling window used to compute
     * this baseline.
     *
     * <p>Different metric types may use different windows to suit their
     * natural variability:
     * <ul>
     *   <li>Heart rate, HRV    → 30 days (fast-adapting, high-frequency)</li>
     *   <li>Blood pressure      → 30 days</li>
     *   <li>Weight              → 90 days (slow-changing; short windows
     *       overreact to day-to-day fluctuations)</li>
     *   <li>Blood glucose       → 14 days (closely tied to recent diet)</li>
     *   <li>Steps               → 30 days</li>
     * </ul>
     *
     * Stored here (rather than looked up from a config map) so the UI can
     * accurately describe the baseline ("based on your last 30 days") even
     * if the window configuration changes in a future app version.
     */
    @ColumnInfo(name = "window_days")
    public int windowDays;

    // ──────────────────────────────────────────────────────────────────────
    // Computation Provenance
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant at which this baseline was last calculated or refreshed.
     *
     * <p>The WorkManager baseline-refresh job compares this against a
     * per-metric staleness threshold (e.g. "recalculate HR baseline if
     * {@code computedAt} is more than 24 hours ago") to decide whether a
     * fresh run is required. This field is also displayed in the health-detail
     * screen as "Baseline last updated N days ago".
     *
     * <p>Set to the current instant by the Repository on every upsert.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "computed_at")
    public Date computedAt;

    /**
     * How this baseline was computed or entered.
     * Free-form TEXT — open-ended to accommodate new ingestion pipelines
     * without requiring a schema migration.
     *
     * <p>Recommended values:
     * <ul>
     *   <li>{@code "ML_ROLLING_MEAN"}     — computed by the TFLite /
     *       WorkManager baseline-recalculation job</li>
     *   <li>{@code "HEALTH_CONNECT_AVG"}  — derived from an aggregate
     *       imported via Android Health Connect</li>
     *   <li>{@code "USER_OVERRIDE"}        — manually set by the user in
     *       profile / health settings</li>
     *   <li>{@code "ONBOARDING_INPUT"}     — value entered during the
     *       onboarding questionnaire (highest uncertainty; replaced once
     *       enough real readings accumulate)</li>
     * </ul>
     *
     * Nullable — null for legacy rows inserted before provenance tracking
     * was introduced.
     */
    @Nullable
    @ColumnInfo(name = "source")
    public String source;

    /**
     * Display unit label for {@link #baselineValue}, {@link #stdDev},
     * {@link #minValue}, and {@link #maxValue}.
     *
     * <p>Mirrors the unit convention in {@link HealthMetricEntity}.
     * Examples: {@code "bpm"}, {@code "mmHg"}, {@code "mg/dL"},
     * {@code "steps"}, {@code "kg"}, {@code "ms"}, {@code "%"}, {@code "°C"}.
     *
     * <p>Nullable — omitted for dimensionless scores where the unit is
     * unambiguously implied by the metric type (e.g. HRV SDNN). The UI
     * falls back to the metric type's default unit label when null.
     */
    @Nullable
    @ColumnInfo(name = "unit")
    public String unit;

    // ──────────────────────────────────────────────────────────────────────
    // Staleness Flag
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Whether this baseline has exceeded its staleness threshold and is
     * pending a fresh recalculation.
     *
     * <p>FALSE (default) — baseline is current and trusted.
     * TRUE  — marked stale by the WorkManager staleness-check job because
     *         {@link #computedAt} is older than the per-metric threshold,
     *         or because a significant number of new readings have been
     *         ingested since the last computation.
     *
     * <p>While stale:
     * <ul>
     *   <li>The ML anomaly detector uses the stale baseline with a widened
     *       normal band (reduced sensitivity) to avoid false positives.</li>
     *   <li>The UI surfaces a subtle "updating baseline" indicator on the
     *       health-detail and anomaly-alert screens.</li>
     *   <li>The AI coaching layer may hedge baseline-relative statements
     *       ("based on your recent readings, which are still being analysed").</li>
     * </ul>
     *
     * <p>Cleared to false by the Repository immediately after a successful
     * recalculation and upsert. Set to true by the staleness-check worker
     * before enqueueing the recalculation task.
     *
     * <p>SQLite stores as INTEGER (0 = false, 1 = true).
     */
    @ColumnInfo(name = "is_stale", defaultValue = "0")
    public boolean isStale;

    // ──────────────────────────────────────────────────────────────────────
    // Timestamps
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant when this row was first written to the local database.
     * Set once at insert time; never modified thereafter.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "created_at")
    public Date createdAt;

    /**
     * UTC instant of the most recent update to this row.
     * Refreshed by the Repository on every upsert — after each baseline
     * recalculation or manual override.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "updated_at")
    public Date updatedAt;

    // ──────────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Full constructor used by the Repository when inserting or upserting a
     * baseline. Room uses direct field assignment when reading rows back
     * from the DB.
     *
     * @param userId        Local PK of the owning {@link UserEntity}.
     * @param metricType    Health metric type this baseline covers (required).
     * @param baselineValue Rolling mean / reference value (required).
     * @param stdDev        Standard deviation of the sample, or null.
     * @param minValue      Observed minimum in the window, or null.
     * @param maxValue      Observed maximum in the window, or null.
     * @param sampleSize    Number of readings used in the calculation.
     * @param windowDays    Rolling-window length in calendar days.
     * @param computedAt    Instant the baseline was last calculated (required).
     * @param source        Computation provenance label, or null.
     * @param unit          Display unit label, or null.
     * @param isStale       True if the baseline needs recalculation.
     * @param createdAt     Insert timestamp (set by Repository).
     * @param updatedAt     Last-update timestamp (set by Repository).
     */
    public UserBaselineEntity(
            long userId,
            @NonNull MetricType metricType,
            double baselineValue,
            @Nullable Double stdDev,
            @Nullable Double minValue,
            @Nullable Double maxValue,
            int sampleSize,
            int windowDays,
            @NonNull Date computedAt,
            @Nullable String source,
            @Nullable String unit,
            boolean isStale,
            @NonNull Date createdAt,
            @NonNull Date updatedAt) {

        this.userId         = userId;
        this.metricType     = metricType;
        this.baselineValue  = baselineValue;
        this.stdDev         = stdDev;
        this.minValue       = minValue;
        this.maxValue       = maxValue;
        this.sampleSize     = sampleSize;
        this.windowDays     = windowDays;
        this.computedAt     = computedAt;
        this.source         = source;
        this.unit           = unit;
        this.isStale        = isStale;
        this.createdAt      = createdAt;
        this.updatedAt      = updatedAt;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Convenience factories
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Factory for a freshly computed ML baseline — the standard path used
     * when the WorkManager recalculation job finishes processing a rolling
     * window of {@link HealthMetricEntity} rows.
     *
     * <p>{@code isStale} defaults to false; timestamps set to now.
     *
     * @param userId        Local PK of the owning user.
     * @param metricType    Health metric type.
     * @param mean          Rolling mean value.
     * @param stdDev        Standard deviation, or null if sample too small.
     * @param minValue      Window minimum, or null if sample too small.
     * @param maxValue      Window maximum, or null if sample too small.
     * @param sampleSize    Number of readings used.
     * @param windowDays    Rolling-window length in days.
     * @param unit          Display unit label, or null.
     * @return Ready-to-upsert {@link UserBaselineEntity}.
     */
    public static UserBaselineEntity createFromMl(
            long userId,
            @NonNull MetricType metricType,
            double mean,
            @Nullable Double stdDev,
            @Nullable Double minValue,
            @Nullable Double maxValue,
            int sampleSize,
            int windowDays,
            @Nullable String unit) {

        Date now = new Date();
        return new UserBaselineEntity(
                userId, metricType, mean, stdDev, minValue, maxValue,
                sampleSize, windowDays, now,
                "ML_ROLLING_MEAN", unit, false, now, now);
    }

    /**
     * Factory for a baseline entered manually by the user (e.g. a clinician-
     * provided reference value entered in the profile settings screen).
     *
     * <p>{@code stdDev}, {@code minValue}, {@code maxValue} are null — manual
     * entries carry no statistical spread. {@code sampleSize} is set to 1.
     * {@code isStale} defaults to false; timestamps set to now.
     *
     * @param userId        Local PK of the owning user.
     * @param metricType    Health metric type.
     * @param value         User-provided reference value.
     * @param windowDays    Context window the user expressed the value over
     *                      (pass 0 if not applicable).
     * @param unit          Display unit label, or null.
     * @return Ready-to-upsert {@link UserBaselineEntity}.
     */
    public static UserBaselineEntity createUserOverride(
            long userId,
            @NonNull MetricType metricType,
            double value,
            int windowDays,
            @Nullable String unit) {

        Date now = new Date();
        return new UserBaselineEntity(
                userId, metricType, value, null, null, null,
                1, windowDays, now,
                "USER_OVERRIDE", unit, false, now, now);
    }

    /**
     * Factory for an onboarding-questionnaire baseline — the initial
     * reference value derived from values the user enters during setup
     * (e.g. "What is your typical resting heart rate?").
     *
     * <p>These have the lowest confidence and should be replaced as soon as
     * sufficient real readings accumulate. {@code isStale} is set to true
     * immediately so the WorkManager job recalculates at the next opportunity.
     *
     * @param userId        Local PK of the owning user.
     * @param metricType    Health metric type.
     * @param value         Onboarding-input reference value.
     * @param unit          Display unit label, or null.
     * @return Ready-to-insert {@link UserBaselineEntity}.
     */
    public static UserBaselineEntity createFromOnboarding(
            long userId,
            @NonNull MetricType metricType,
            double value,
            @Nullable String unit) {

        Date now = new Date();
        return new UserBaselineEntity(
                userId, metricType, value, null, null, null,
                1, 0, now,
                "ONBOARDING_INPUT", unit,
                /*isStale=*/ true, now, now);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Marks this baseline as stale and stamps {@link #updatedAt}.
     * Call this in the WorkManager staleness-check job before enqueueing
     * the recalculation task, then persist via {@code UserBaselineDao.update()}.
     */
    public void markStale() {
        this.isStale   = true;
        this.updatedAt = new Date();
    }

    /**
     * Applies updated statistics from a fresh ML calculation, clears the
     * stale flag, and stamps {@link #computedAt} and {@link #updatedAt}.
     * Call this in the Repository after the recalculation job completes,
     * then persist via {@code UserBaselineDao.update()}.
     *
     * @param mean       New rolling mean value.
     * @param stdDev     New standard deviation, or null if sample too small.
     * @param minValue   New window minimum, or null if sample too small.
     * @param maxValue   New window maximum, or null if sample too small.
     * @param sampleSize Number of readings used in the new calculation.
     * @param windowDays Rolling-window length used (may differ from previous
     *                   if the configuration was updated).
     */
    public void applyRecalculation(
            double mean,
            @Nullable Double stdDev,
            @Nullable Double minValue,
            @Nullable Double maxValue,
            int sampleSize,
            int windowDays) {

        Date now            = new Date();
        this.baselineValue  = mean;
        this.stdDev         = stdDev;
        this.minValue       = minValue;
        this.maxValue       = maxValue;
        this.sampleSize     = sampleSize;
        this.windowDays     = windowDays;
        this.computedAt     = now;
        this.source         = "ML_ROLLING_MEAN";
        this.isStale        = false;
        this.updatedAt      = now;
    }

    /**
     * Returns the upper bound of the normal band as mean + k·σ.
     * Returns null if {@link #stdDev} is not available.
     *
     * @param k Sensitivity multiplier (e.g. 2.0 for ±2σ).
     */
    @Nullable
    public Double upperBound(double k) {
        return stdDev != null ? baselineValue + k * stdDev : null;
    }

    /**
     * Returns the lower bound of the normal band as mean − k·σ.
     * Returns null if {@link #stdDev} is not available.
     *
     * @param k Sensitivity multiplier (e.g. 2.0 for ±2σ).
     */
    @Nullable
    public Double lowerBound(double k) {
        return stdDev != null ? baselineValue - k * stdDev : null;
    }

    /**
     * Refreshes {@code updated_at} to the current instant.
     * Call this in the Repository before any partial update via
     * {@code UserBaselineDao.update()}.
     */
    public void touch() {
        this.updatedAt = new Date();
    }

    @Override
    public String toString() {
        return "UserBaselineEntity{"
                + "id=" + id
                + ", userId=" + userId
                + ", metricType=" + metricType
                + ", baselineValue=" + baselineValue
                + ", stdDev=" + stdDev
                + ", minValue=" + minValue
                + ", maxValue=" + maxValue
                + ", sampleSize=" + sampleSize
                + ", windowDays=" + windowDays
                + ", computedAt=" + computedAt
                + ", source='" + source + '\''
                + ", unit='" + unit + '\''
                + ", isStale=" + isStale
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + '}';
    }
}