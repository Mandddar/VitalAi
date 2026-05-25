package com.vitalai.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.vitalai.app.data.local.converter.Converters;
import com.vitalai.app.domain.model.enums.SleepStage;

import java.util.Date;

/**
 * SleepStageEntity
 *
 * Room entity representing a row in the {@code sleep_stages} table.
 * Each row records one discrete sleep-stage interval within a parent
 * {@link SleepSessionEntity} — the fine-grained, epoch-level breakdown
 * that backs stage-timeline charts and deep-sleep analysis.
 *
 * Design notes
 * ────────────
 * • Relationship hierarchy:
 *     users  ──< sleep_sessions  ──< sleep_stages
 *   This entity carries two foreign keys: one to {@link UserEntity}
 *   (enabling fast "all stage intervals for user X" queries without
 *   joining through sleep_sessions) and one to {@link SleepSessionEntity}
 *   (for "all intervals in session Y" queries and cascade semantics).
 *
 * • ON DELETE CASCADE on both FKs:
 *     – Deleting a user purges all their stage rows via the users FK.
 *     – Deleting a sleep session purges its child stage rows via the
 *       session FK. Both cascades fire independently; whichever parent
 *       is deleted first wins and the other FK becomes moot.
 *
 * • Each interval is defined by [startTime, endTime]. The duration is
 *   never stored — it is always derived as (endTime - startTime) at
 *   query time to remain accurate if either bound is corrected later.
 *
 * • {@code stage} maps to the {@link SleepStage} enum and is stored as
 *   TEXT via {@link Converters}.
 *   Typical values: AWAKE | LIGHT | DEEP | REM
 *
 * • {@code confidenceScore} is the model's confidence (0.0–1.0) in the
 *   stage classification for this interval. Nullable — rule-based or
 *   accelerometer-only devices may not produce a confidence value.
 *   Used by the AI layer to weight conflicting stage signals and to
 *   suppress low-confidence anomaly alerts.
 *
 * • Three indexes are declared:
 *     1. (session_id, start_time) — primary query pattern: "all stage
 *        intervals for session Y in chronological order" for timeline
 *        rendering. Also serves as the existence check for deduplication
 *        during BLE streaming.
 *     2. (user_id, stage, start_time) — cross-session queries: "all
 *        DEEP intervals for user X in the last 30 days" for trend charts
 *        and longitudinal deep-sleep analysis without a session join.
 *     3. (session_id, stage) — aggregate queries: "total time in each
 *        stage for session Y", used to recompute SleepSessionEntity's
 *        stage-breakdown minute columns after bulk imports.
 *
 * • This table is the source of truth for per-epoch stage data.
 *   {@link SleepSessionEntity}'s {@code light_sleep_minutes},
 *   {@code deep_sleep_minutes}, {@code rem_sleep_minutes}, and
 *   {@code awake_minutes} columns are pre-aggregated caches derived
 *   from this table — they exist for dashboard read performance and
 *   must be kept in sync by the Repository after any stage write.
 *
 * Architecture layer : Data / Local
 * Table name         : sleep_stages
 * Related DAOs       : SleepStageDao
 * Parent FKs         : users.id          (CASCADE delete + update)
 *                      sleep_sessions.id (CASCADE delete + update)
 */
@Entity(
        tableName = "sleep_stages",
        foreignKeys = {
                @ForeignKey(
                        entity        = UserEntity.class,
                        parentColumns = "id",
                        childColumns  = "user_id",
                        onDelete      = ForeignKey.CASCADE,
                        onUpdate      = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity        = SleepSessionEntity.class,
                        parentColumns = "id",
                        childColumns  = "session_id",
                        onDelete      = ForeignKey.CASCADE,
                        onUpdate      = ForeignKey.CASCADE
                )
        },
        indices = {
                // Primary: timeline render for a single session
                @Index(value = {"session_id", "start_time"}),
                // Cross-session: longitudinal stage-type trend queries
                @Index(value = {"user_id", "stage", "start_time"}),
                // Aggregate: per-session stage totals recomputation
                @Index(value = {"session_id", "stage"})
        }
)
public class SleepStageEntity {

    // ──────────────────────────────────────────────────────────────────────
    // Primary Key
    // ──────────────────────────────────────────────────────────────────────

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    // ──────────────────────────────────────────────────────────────────────
    // Foreign Keys
    // ──────────────────────────────────────────────────────────────────────

    /**
     * References {@link UserEntity#id}.
     * Denormalised from the session to enable direct cross-session queries
     * on stage type without a join through {@code sleep_sessions}.
     * Covered by the composite index (user_id, stage, start_time).
     */
    @ColumnInfo(name = "user_id")
    public long userId;

    /**
     * References {@link SleepSessionEntity#id}.
     * Identifies the parent sleep session that owns this stage interval.
     * Covered by the composite indexes (session_id, start_time) and
     * (session_id, stage).
     */
    @ColumnInfo(name = "session_id")
    public long sessionId;

    // ──────────────────────────────────────────────────────────────────────
    // Stage Interval
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant at which this stage interval began.
     * For BLE wearables: the device-reported epoch boundary timestamp.
     * For HealthConnect: the {@code startTime} of the SleepStageRecord.
     * For manual entry: the user-selected time.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "start_time")
    public Date startTime;

    /**
     * UTC instant at which this stage interval ended.
     * The next interval's {@link #startTime} should equal this value
     * for gapless stage-timeline charts. Gaps indicate periods where
     * the device lost contact or did not classify a stage.
     *
     * Nullable — a stage currently in progress (BLE streaming) has no
     * end time yet; the Repository updates this field when the device
     * transitions to the next stage.
     * Stored as epoch millis via TypeConverters.
     */
    @Nullable
    @ColumnInfo(name = "end_time")
    public Date endTime;

    /**
     * The sleep stage classification for this interval.
     * Stored as TEXT via TypeConverters.
     *
     * Expected values (defined in {@link SleepStage}):
     *   AWAKE — conscious wakefulness during the sleep window
     *   LIGHT — N1 / N2 non-REM sleep (light, transitional)
     *   DEEP  — N3 slow-wave sleep (physically restorative)
     *   REM   — rapid-eye-movement sleep (cognitively restorative)
     */
    @NonNull
    @ColumnInfo(name = "stage")
    public SleepStage stage;

    // ──────────────────────────────────────────────────────────────────────
    // Model Confidence
    // ──────────────────────────────────────────────────────────────────────

    /**
     * The classification model's confidence in the {@link #stage} label
     * for this interval, expressed as a probability in [0.0, 1.0].
     *
     * 0.0 — model is maximally uncertain (random-chance equivalent).
     * 1.0 — model is fully certain of the stage label.
     *
     * Nullable — rule-based devices (accelerometer-only trackers) and
     * HealthConnect sources may not expose a confidence value.
     *
     * Used by the AI inference layer to:
     *   • Weight conflicting stage readings during sensor fusion.
     *   • Suppress DEEP / REM deficit alerts when confidence is low
     *     (threshold configurable in user_baselines).
     *   • Identify low-quality recordings for user feedback ("your
     *     device lost contact for 47 minutes last night").
     */
    @Nullable
    @ColumnInfo(name = "confidence_score")
    public Double confidenceScore;

    // ──────────────────────────────────────────────────────────────────────
    // Timestamps
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant when this row was first written to the local database.
     * Set once at insert time; never modified thereafter.
     * Distinct from {@link #startTime} — a bulk import may insert rows with
     * historical start times; {@code createdAt} reflects actual insert time
     * and is used for sync / audit purposes.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "created_at")
    public Date createdAt;

    /**
     * UTC instant of the most recent update to this row.
     * Updated when {@link #endTime} is filled in during BLE streaming,
     * or when the classification pipeline revises {@link #confidenceScore}.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "updated_at")
    public Date updatedAt;

    // ──────────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Full constructor used by the Repository when inserting a new stage
     * interval. Room uses direct field assignment when reading rows back
     * from the DB.
     *
     * @param userId          Local PK of the owning {@link UserEntity}.
     * @param sessionId       Local PK of the owning {@link SleepSessionEntity}.
     * @param startTime       Interval start instant (required).
     * @param endTime         Interval end instant, or null if in progress.
     * @param stage           Sleep stage classification (required).
     * @param confidenceScore Model confidence [0.0–1.0], or null.
     * @param createdAt       Insert timestamp (set by Repository).
     * @param updatedAt       Last-update timestamp (set by Repository).
     */
    public SleepStageEntity(
            long userId,
            long sessionId,
            @NonNull Date startTime,
            @Nullable Date endTime,
            @NonNull SleepStage stage,
            @Nullable Double confidenceScore,
            @NonNull Date createdAt,
            @NonNull Date updatedAt) {

        this.userId          = userId;
        this.sessionId       = sessionId;
        this.startTime       = startTime;
        this.endTime         = endTime;
        this.stage           = stage;
        this.confidenceScore = confidenceScore;
        this.createdAt       = createdAt;
        this.updatedAt       = updatedAt;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Convenience factories
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Factory for a fully specified, completed stage interval — the common
     * case when ingesting a finished session from HealthConnect or a CSV
     * import where both bounds and confidence are known.
     * Timestamps ({@code createdAt}, {@code updatedAt}) are set to now.
     *
     * @param userId          Local PK of the owning user.
     * @param sessionId       Local PK of the owning session.
     * @param startTime       Interval start instant.
     * @param endTime         Interval end instant.
     * @param stage           Sleep stage classification.
     * @param confidenceScore Model confidence, or null.
     * @return Ready-to-insert {@link SleepStageEntity}.
     */
    public static SleepStageEntity create(
            long userId,
            long sessionId,
            @NonNull Date startTime,
            @Nullable Date endTime,
            @NonNull SleepStage stage,
            @Nullable Double confidenceScore) {

        Date now = new Date();
        return new SleepStageEntity(
                userId, sessionId, startTime, endTime,
                stage, confidenceScore, now, now);
    }

    /**
     * Factory for a stage interval arriving from a live BLE stream where
     * the end time is not yet known. Call {@link #close(Date)} when the
     * device transitions to the next stage.
     *
     * @param userId    Local PK of the owning user.
     * @param sessionId Local PK of the owning session.
     * @param startTime Interval start instant (device timestamp).
     * @param stage     Sleep stage classification.
     * @return Ready-to-insert {@link SleepStageEntity}.
     */
    public static SleepStageEntity createOngoing(
            long userId,
            long sessionId,
            @NonNull Date startTime,
            @NonNull SleepStage stage) {

        return create(userId, sessionId, startTime, null, stage, null);
    }

    /**
     * Minimal factory for a completed interval without a confidence score —
     * used when the source device provides stage boundaries but no
     * probability output (e.g. accelerometer-only trackers).
     *
     * @param userId    Local PK of the owning user.
     * @param sessionId Local PK of the owning session.
     * @param startTime Interval start instant.
     * @param endTime   Interval end instant.
     * @param stage     Sleep stage classification.
     * @return Ready-to-insert {@link SleepStageEntity}.
     */
    public static SleepStageEntity createWithoutConfidence(
            long userId,
            long sessionId,
            @NonNull Date startTime,
            @NonNull Date endTime,
            @NonNull SleepStage stage) {

        return create(userId, sessionId, startTime, endTime, stage, null);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Closes an in-progress stage interval by setting {@link #endTime}
     * and stamping {@link #updatedAt}. Call this in the Repository when
     * the BLE device reports a stage transition, then pass the entity to
     * {@code SleepStageDao.update()}.
     *
     * @param endTime Stage-transition instant (device timestamp).
     */
    public void close(@NonNull Date endTime) {
        this.endTime   = endTime;
        this.updatedAt = new Date();
    }

    /**
     * Updates the model confidence after a post-hoc re-classification
     * (e.g. when the on-device TFLite model reruns over raw sensor data).
     * Also stamps {@link #updatedAt}.
     *
     * @param confidence Revised confidence score in [0.0, 1.0].
     */
    public void updateConfidence(double confidence) {
        this.confidenceScore = confidence;
        this.updatedAt       = new Date();
    }

    /**
     * Derives the duration of this interval in minutes.
     * Returns null if {@link #endTime} has not been set yet (interval still
     * in progress during a live BLE stream).
     */
    @Nullable
    public Integer durationMinutes() {
        if (endTime == null) return null;
        long durationMs = endTime.getTime() - startTime.getTime();
        return (int) (durationMs / 60_000L);
    }

    /**
     * Refreshes {@code updated_at} to the current instant.
     * Call this in the Repository before any partial update via
     * {@code SleepStageDao.update()}.
     */
    public void touch() {
        this.updatedAt = new Date();
    }

    @Override
    public String toString() {
        return "SleepStageEntity{"
                + "id=" + id
                + ", userId=" + userId
                + ", sessionId=" + sessionId
                + ", startTime=" + startTime
                + ", endTime=" + endTime
                + ", stage=" + stage
                + ", confidenceScore=" + confidenceScore
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + '}';
    }
}