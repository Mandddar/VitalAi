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
 * SleepSessionEntity
 *
 * Room entity representing a row in the {@code sleep_sessions} table.
 * Each row records one continuous sleep session belonging to a user.
 *
 * Design notes
 * ────────────
 * • Foreign key to {@link UserEntity} uses ON DELETE CASCADE — deleting a
 *   user automatically removes all their sleep sessions, preserving
 *   referential integrity without manual Repository cleanup.
 *
 * • A session is defined by a [startTime, endTime] window. Duration is
 *   never stored — it is always derived as (endTime - startTime) at query
 *   time to avoid the record going stale if either bound is corrected.
 *
 * • {@code dominantStage} captures the sleep stage that occupied the
 *   greatest proportion of the session (e.g. LIGHT, DEEP, REM). Stored
 *   as TEXT via {@link Converters}.
 *   Nullable — wearables that track only presence/absence of sleep (no
 *   stage breakdown) will leave this null.
 *
 * • {@code sleepScore} is the AI-computed quality score (0–100) produced
 *   by the TFLite sleep-quality model. Nullable — scored asynchronously
 *   after insertion by a WorkManager worker; UI must not assume it is
 *   available immediately.
 *
 * • {@code heartRateAvg} and {@code respiratoryRateAvg} are snapshot
 *   averages captured during the session. Storing them here avoids a
 *   costly aggregation join against {@code health_metrics} every time
 *   the sleep dashboard is loaded. The canonical per-minute readings
 *   still live in {@code health_metrics}.
 *
 * • Two indexes are declared:
 *     1. (user_id, start_time) — primary query pattern: "all sessions
 *        for user X ordered by night", also serves range queries for
 *        weekly/monthly sleep trend charts.
 *     2. (user_id, sleep_score) — fast lookup for "worst-scored nights"
 *        insight cards.
 *
 * Architecture layer : Data / Local
 * Table name         : sleep_sessions
 * Related DAOs       : SleepSessionDao
 * Parent FK          : users.id  (CASCADE delete + update)
 */
@Entity(
        tableName = "sleep_sessions",
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
                @Index(value = {"user_id", "start_time"}),
                @Index(value = {"user_id", "sleep_score"})
        }
)
public class SleepSessionEntity {

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
     * Covered by the composite index (user_id, start_time) above.
     */
    @ColumnInfo(name = "user_id")
    public long userId;

    // ──────────────────────────────────────────────────────────────────────
    // Session Window
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant when the user fell asleep (or when the wearable detected
     * sleep onset). Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "start_time")
    public Date startTime;

    /**
     * UTC instant when the user woke up (session end).
     * Nullable — a session in progress has no end time yet. The Repository
     * must update this field when the wearable signals wake-up.
     * Stored as epoch millis via TypeConverters.
     */
    @Nullable
    @ColumnInfo(name = "end_time")
    public Date endTime;

    // ──────────────────────────────────────────────────────────────────────
    // Sleep Quality
    // ──────────────────────────────────────────────────────────────────────

    /**
     * AI-computed sleep quality score for this session (0–100).
     *
     * 0–40   — poor sleep (fragmented, insufficient deep/REM, short duration)
     * 41–70  — fair sleep (some restorative stages, moderate interruptions)
     * 71–100 — good sleep (adequate deep + REM, low fragmentation)
     *
     * Nullable — set asynchronously by the TFLite WorkManager worker after
     * the session ends. UI should show a loading/pending state when null.
     */
    @Nullable
    @ColumnInfo(name = "sleep_score")
    public Integer sleepScore;

    /**
     * The sleep stage that occupied the greatest proportion of the session.
     * Stored as TEXT via TypeConverters.
     *
     * Typical values: AWAKE | LIGHT | DEEP | REM
     *
     * Nullable — devices that detect only sleep/wake (no stage granularity)
     * will not populate this field.
     */
    @Nullable
    @ColumnInfo(name = "dominant_stage")
    public SleepStage dominantStage;

    // ──────────────────────────────────────────────────────────────────────
    // Stage Breakdown (minutes)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Total minutes spent in light (N1/N2) sleep during this session.
     * Nullable — not available from devices without stage detection.
     */
    @Nullable
    @ColumnInfo(name = "light_sleep_minutes")
    public Integer lightSleepMinutes;

    /**
     * Total minutes spent in deep (N3/slow-wave) sleep during this session.
     * Nullable — not available from devices without stage detection.
     */
    @Nullable
    @ColumnInfo(name = "deep_sleep_minutes")
    public Integer deepSleepMinutes;

    /**
     * Total minutes spent in REM sleep during this session.
     * Nullable — not available from devices without stage detection.
     */
    @Nullable
    @ColumnInfo(name = "rem_sleep_minutes")
    public Integer remSleepMinutes;

    /**
     * Total minutes spent awake during the session window (e.g. middle-of-
     * night wake-ups). Nullable — not available from basic sleep trackers.
     */
    @Nullable
    @ColumnInfo(name = "awake_minutes")
    public Integer awakeMinutes;

    // ──────────────────────────────────────────────────────────────────────
    // Physiological Snapshots
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Mean heart rate (bpm) recorded across the session.
     * Pre-aggregated here to avoid joining against {@code health_metrics}
     * on every dashboard load. Canonical per-minute readings remain in
     * {@code health_metrics}.
     * Nullable — not available if HR monitoring was off during sleep.
     */
    @Nullable
    @ColumnInfo(name = "heart_rate_avg")
    public Double heartRateAvg;

    /**
     * Mean respiratory rate (breaths/min) recorded across the session.
     * Pre-aggregated for the same reason as {@link #heartRateAvg}.
     * Nullable — only available from devices with respiratory sensing.
     */
    @Nullable
    @ColumnInfo(name = "respiratory_rate_avg")
    public Double respiratoryRateAvg;

    // ──────────────────────────────────────────────────────────────────────
    // Notes
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Optional free-text note entered by the user about this sleep session.
     * Examples: "Woke up with headache", "Slept poorly due to noise".
     * Fed into the AI chat context when the user asks about sleep quality.
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
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "created_at")
    public Date createdAt;

    /**
     * UTC instant of the most recent update to this row.
     * Updated when {@link #endTime}, {@link #sleepScore}, or stage
     * breakdowns are filled in after session end.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "updated_at")
    public Date updatedAt;

    // ──────────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Full constructor used by the Repository when inserting a new session.
     * Room uses direct field assignment when reading rows back from the DB.
     *
     * @param userId              Local PK of the owning {@link UserEntity}.
     * @param startTime           Sleep-onset instant (required).
     * @param endTime             Wake-up instant, or null if session is ongoing.
     * @param sleepScore          AI quality score (0–100), or null if pending.
     * @param dominantStage       Most prevalent sleep stage, or null.
     * @param lightSleepMinutes   Minutes in light sleep, or null.
     * @param deepSleepMinutes    Minutes in deep sleep, or null.
     * @param remSleepMinutes     Minutes in REM sleep, or null.
     * @param awakeMinutes        Minutes awake during session, or null.
     * @param heartRateAvg        Mean HR (bpm) during session, or null.
     * @param respiratoryRateAvg  Mean respiratory rate, or null.
     * @param notes               User-entered note, or null.
     * @param createdAt           Insert timestamp (set by Repository).
     * @param updatedAt           Last-update timestamp (set by Repository).
     */
    public SleepSessionEntity(
            long userId,
            @NonNull Date startTime,
            @Nullable Date endTime,
            @Nullable Integer sleepScore,
            @Nullable SleepStage dominantStage,
            @Nullable Integer lightSleepMinutes,
            @Nullable Integer deepSleepMinutes,
            @Nullable Integer remSleepMinutes,
            @Nullable Integer awakeMinutes,
            @Nullable Double heartRateAvg,
            @Nullable Double respiratoryRateAvg,
            @Nullable String notes,
            @NonNull Date createdAt,
            @NonNull Date updatedAt) {

        this.userId              = userId;
        this.startTime           = startTime;
        this.endTime             = endTime;
        this.sleepScore          = sleepScore;
        this.dominantStage       = dominantStage;
        this.lightSleepMinutes   = lightSleepMinutes;
        this.deepSleepMinutes    = deepSleepMinutes;
        this.remSleepMinutes     = remSleepMinutes;
        this.awakeMinutes        = awakeMinutes;
        this.heartRateAvg        = heartRateAvg;
        this.respiratoryRateAvg  = respiratoryRateAvg;
        this.notes               = notes;
        this.createdAt           = createdAt;
        this.updatedAt           = updatedAt;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Convenience factories
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Factory for a fully specified, completed sleep session.
     * Use this when importing historical data where all fields are known.
     * Timestamps are set to now automatically.
     *
     * @param userId             Local PK of the owning user.
     * @param startTime          Sleep-onset instant.
     * @param endTime            Wake-up instant.
     * @param sleepScore         AI quality score (0–100), or null.
     * @param dominantStage      Most prevalent stage, or null.
     * @param lightSleepMinutes  Minutes in light sleep, or null.
     * @param deepSleepMinutes   Minutes in deep sleep, or null.
     * @param remSleepMinutes    Minutes in REM, or null.
     * @param awakeMinutes       Minutes awake, or null.
     * @param heartRateAvg       Mean HR, or null.
     * @param respiratoryRateAvg Mean respiratory rate, or null.
     * @param notes              User note, or null.
     * @return Ready-to-insert {@link SleepSessionEntity}.
     */
    public static SleepSessionEntity create(
            long userId,
            @NonNull Date startTime,
            @Nullable Date endTime,
            @Nullable Integer sleepScore,
            @Nullable SleepStage dominantStage,
            @Nullable Integer lightSleepMinutes,
            @Nullable Integer deepSleepMinutes,
            @Nullable Integer remSleepMinutes,
            @Nullable Integer awakeMinutes,
            @Nullable Double heartRateAvg,
            @Nullable Double respiratoryRateAvg,
            @Nullable String notes) {

        Date now = new Date();
        return new SleepSessionEntity(
                userId, startTime, endTime, sleepScore, dominantStage,
                lightSleepMinutes, deepSleepMinutes, remSleepMinutes,
                awakeMinutes, heartRateAvg, respiratoryRateAvg,
                notes, now, now);
    }

    /**
     * Minimal factory for recording the start of an ongoing sleep session.
     * All optional fields are null and must be populated when the session
     * ends and the ML worker scores it.
     *
     * @param userId    Local PK of the owning user.
     * @param startTime Sleep-onset instant detected by the wearable.
     * @return Ready-to-insert {@link SleepSessionEntity}.
     */
    public static SleepSessionEntity createOngoing(long userId, @NonNull Date startTime) {
        return create(userId, startTime, null, null, null,
                null, null, null, null, null, null, null);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Closes an ongoing session by setting {@link #endTime} and stamping
     * {@link #updatedAt}. Call this in the Repository when the wearable
     * signals wake-up, then pass the entity to {@code SleepSessionDao.update()}.
     *
     * @param endTime Wake-up instant.
     */
    public void close(@NonNull Date endTime) {
        this.endTime   = endTime;
        this.updatedAt = new Date();
    }

    /**
     * Applies the AI sleep score after the WorkManager worker completes.
     * Also stamps {@link #updatedAt}.
     *
     * @param score Quality score in the range 0–100.
     */
    public void applyScore(int score) {
        this.sleepScore = score;
        this.updatedAt  = new Date();
    }

    /**
     * Refreshes {@code updated_at} to the current instant.
     * Call this in the Repository before any partial update
     * (e.g. filling in stage breakdowns) via {@code SleepSessionDao.update()}.
     */
    public void touch() {
        this.updatedAt = new Date();
    }

    /**
     * Derives the total recorded sleep duration in minutes, excluding
     * awake minutes. Returns null if {@link #endTime} has not been set yet.
     */
    @Nullable
    public Integer totalSleepMinutes() {
        if (endTime == null) return null;
        long durationMs      = endTime.getTime() - startTime.getTime();
        int  totalMinutes    = (int) (durationMs / 60_000L);
        int  awake           = awakeMinutes != null ? awakeMinutes : 0;
        return Math.max(0, totalMinutes - awake);
    }

    @Override
    public String toString() {
        return "SleepSessionEntity{"
                + "id=" + id
                + ", userId=" + userId
                + ", startTime=" + startTime
                + ", endTime=" + endTime
                + ", sleepScore=" + sleepScore
                + ", dominantStage=" + dominantStage
                + ", lightSleepMinutes=" + lightSleepMinutes
                + ", deepSleepMinutes=" + deepSleepMinutes
                + ", remSleepMinutes=" + remSleepMinutes
                + ", awakeMinutes=" + awakeMinutes
                + ", heartRateAvg=" + heartRateAvg
                + ", respiratoryRateAvg=" + respiratoryRateAvg
                + '}';
    }
}