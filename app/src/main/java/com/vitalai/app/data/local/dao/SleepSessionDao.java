package com.vitalai.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.vitalai.app.data.local.entity.SleepSessionEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

/**
 * SleepSessionDao
 *
 * Room DAO for all CRUD and query operations against the {@code sleep_sessions} table.
 *
 * Design notes
 * ────────────
 * • Insert uses {@link OnConflictStrategy#REPLACE} to support upsert semantics —
 *   re-inserting a session that has been corrected (e.g. updated end_time after
 *   sync with HealthConnect) will overwrite the old row cleanly.
 *
 * • All read operations use RxJava 3 unless reactive UI observation is needed,
 *   in which case {@link LiveData} is used (marked where applicable).
 *
 * • {@link Completable} is used for fire-and-forget write operations (insert
 *   batch, update, delete) so the Repository can chain them in an Rx pipeline
 *   and observe completion/errors on the correct scheduler.
 *
 * • Epoch millis are used for all date range parameters to stay consistent with
 *   the TypeConverters that store {@link java.util.Date} as {@code LONG}.
 *
 * • The {@code getSleepSummaries} query uses a projection POJO
 *   ({@link SleepSummaryResult}) to avoid loading full rows for dashboard
 *   list cards.
 *
 * Architecture layer : Data / Local
 * Package            : com.vitalai.app.data.local.dao
 * Entity             : {@link SleepSessionEntity}
 * Table              : sleep_sessions
 */
@Dao
public interface SleepSessionDao {

    // ──────────────────────────────────────────────────────────────────────
    // Insert
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Inserts a single sleep session into the database.
     *
     * Uses {@link OnConflictStrategy#REPLACE}: if a row with the same
     * primary key already exists it is replaced in full. This supports
     * upsert semantics when re-syncing sessions from a wearable or
     * HealthConnect.
     *
     * @param session The session entity to insert.
     * @return {@link Single} emitting the new row ID on success.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Single<Long> insert(SleepSessionEntity session);

    /**
     * Inserts a batch of sleep sessions in a single transaction.
     *
     * Intended for bulk-import use cases such as initial HealthConnect
     * sync or restoring a local backup. Conflict strategy is REPLACE —
     * existing rows with matching IDs are overwritten.
     *
     * @param sessions List of session entities to insert.
     * @return {@link Completable} that completes when all rows are written.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<SleepSessionEntity> sessions);

    // ──────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates an existing sleep session row.
     *
     * The Repository is responsible for calling {@link SleepSessionEntity#touch()}
     * (or {@link SleepSessionEntity#close(java.util.Date)} /
     * {@link SleepSessionEntity#applyScore(int)}) to refresh {@code updated_at}
     * before passing the entity here.
     *
     * @param session The entity with updated fields. Must have a valid {@code id}.
     * @return {@link Completable} that completes on success.
     */
    @Update
    Completable update(SleepSessionEntity session);

    // ──────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Deletes a specific sleep session by entity reference (matched on PK).
     *
     * @param session The entity to delete.
     * @return {@link Completable} that completes on success.
     */
    @Delete
    Completable delete(SleepSessionEntity session);

    /**
     * Deletes all sleep sessions for a user whose {@code start_time} is
     * strictly before the given cutoff.
     *
     * Used by the data-retention worker to enforce a rolling history window
     * (e.g. "keep only the last 365 days of sleep data").
     *
     * @param userId  Local PK of the owning user.
     * @param before  Cutoff epoch millis (exclusive upper bound on retention).
     *                Rows with {@code start_time < before} are removed.
     * @return {@link Completable} that completes when the deletion is done.
     */
    @Query("DELETE FROM sleep_sessions " +
            "WHERE user_id    = :userId " +
            "  AND start_time < :before")
    Completable deleteSessionsOlderThan(long userId, long before);

    // ──────────────────────────────────────────────────────────────────────
    // Single-row lookup
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns the full session entity for the given primary key.
     *
     * Used by the session-detail screen and by the WorkManager sleep-scoring
     * worker to load the session it needs to score.
     *
     * @param sessionId Primary key of the session to load.
     * @return {@link Single} emitting the entity, or an error if not found.
     *         Use {@code .onErrorReturn} in the Repository to handle missing rows.
     */
    @Query("SELECT * FROM sleep_sessions WHERE id = :sessionId LIMIT 1")
    Single<SleepSessionEntity> getById(long sessionId);

    /**
     * Returns a reactive stream of the session entity for the given PK.
     *
     * Suitable for the session-detail screen when it needs to observe
     * score updates pushed by the WorkManager worker in real time.
     *
     * @param sessionId Primary key of the session to observe.
     * @return {@link LiveData} that emits the entity whenever the row changes.
     */
    @Query("SELECT * FROM sleep_sessions WHERE id = :sessionId LIMIT 1")
    LiveData<SleepSessionEntity> observeById(long sessionId);

    // ──────────────────────────────────────────────────────────────────────
    // Range queries
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns all sleep sessions for a user whose {@code start_time} falls
     * within the given window, ordered from most recent to oldest.
     *
     * Primary consumer: the weekly/monthly sleep trend chart and the sleep
     * history list screen.
     *
     * Uses the composite index {@code (user_id, start_time)} for an
     * efficient range scan without a full-table read.
     *
     * @param userId Local PK of the owning user.
     * @param from   Window start epoch millis (inclusive).
     * @param to     Window end   epoch millis (inclusive).
     * @return {@link Single} emitting the matching sessions ordered by
     *         {@code start_time DESC}.
     */
    @Query("SELECT * FROM sleep_sessions " +
            "WHERE user_id    = :userId " +
            "  AND start_time >= :from " +
            "  AND start_time <= :to " +
            "ORDER BY start_time DESC")
    Single<List<SleepSessionEntity>> getSessionsInRange(long userId, long from, long to);

    /**
     * Returns a reactive stream of sleep sessions for a user whose
     * {@code start_time} falls within the given window.
     *
     * Suitable for live-updating the sleep history list when a new session
     * is inserted or scored in the background.
     *
     * @param userId Local PK of the owning user.
     * @param from   Window start epoch millis (inclusive).
     * @param to     Window end   epoch millis (inclusive).
     * @return {@link LiveData} that re-emits the list whenever any row in
     *         the result set changes.
     */
    @Query("SELECT * FROM sleep_sessions " +
            "WHERE user_id    = :userId " +
            "  AND start_time >= :from " +
            "  AND start_time <= :to " +
            "ORDER BY start_time DESC")
    LiveData<List<SleepSessionEntity>> observeSessionsInRange(long userId, long from, long to);

    // ──────────────────────────────────────────────────────────────────────
    // Latest N sessions
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns the {@code limit} most recent completed sleep sessions for
     * a user (completed = {@code end_time IS NOT NULL}).
     *
     * Used by the dashboard summary card ("Last 7 nights") and the
     * AI insight pipeline that evaluates recent sleep trends.
     *
     * @param userId Local PK of the owning user.
     * @param limit  Maximum number of sessions to return.
     * @return {@link Single} emitting up to {@code limit} sessions ordered
     *         by {@code start_time DESC}.
     */
    @Query("SELECT * FROM sleep_sessions " +
            "WHERE user_id  = :userId " +
            "  AND end_time IS NOT NULL " +
            "ORDER BY start_time DESC " +
            "LIMIT :limit")
    Single<List<SleepSessionEntity>> getLatestSessions(long userId, int limit);

    /**
     * Returns the single most recent completed session for a user.
     *
     * Used by the "last night" summary on the home dashboard card.
     *
     * @param userId Local PK of the owning user.
     * @return {@link Single} emitting the latest completed session, or an
     *         error if none exists. Use {@code .onErrorReturn} in the
     *         Repository to handle the empty-state gracefully.
     */
    @Query("SELECT * FROM sleep_sessions " +
            "WHERE user_id  = :userId " +
            "  AND end_time IS NOT NULL " +
            "ORDER BY start_time DESC " +
            "LIMIT 1")
    Single<SleepSessionEntity> getLatestSession(long userId);

    // ──────────────────────────────────────────────────────────────────────
    // Ongoing session
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns the ongoing sleep session for a user (one with no
     * {@code end_time}), if any exists.
     *
     * There should never be more than one ongoing session per user at a
     * time; the LIMIT 1 guards against edge-case duplicates.
     *
     * Used by the sleep-tracking foreground service to check whether a
     * session is already in progress before starting a new one.
     *
     * @param userId Local PK of the owning user.
     * @return {@link Single} emitting the ongoing entity, or an error if
     *         none exists (use {@code .onErrorReturn(null)} in the Repository
     *         to treat absence as null).
     */
    @Query("SELECT * FROM sleep_sessions " +
            "WHERE user_id  = :userId " +
            "  AND end_time IS NULL " +
            "ORDER BY start_time DESC " +
            "LIMIT 1")
    Single<SleepSessionEntity> getOngoingSession(long userId);

    // ──────────────────────────────────────────────────────────────────────
    // Daily aggregates — for trend charts
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns per-night aggregate statistics (total sleep duration, average
     * sleep score, average deep-sleep minutes, average REM minutes) grouped
     * by calendar date of {@code start_time}.
     *
     * The date bucket is derived from {@code start_time} using SQLite's
     * {@code date()} function:
     *   date(start_time / 1000, 'unixepoch') → "YYYY-MM-DD"
     *
     * Duration is computed inline as
     *   (end_time - start_time) / 60000 − awake_minutes (nulls treated as 0).
     * Only completed sessions (end_time IS NOT NULL) are included to avoid
     * skewing aggregates with partial data.
     *
     * Results are ordered chronologically for direct consumption by
     * trend-chart adapters.
     *
     * Mapped to {@link NightlyAggregateResult}.
     *
     * @param userId Local PK of the owning user.
     * @param from   Start of the aggregation window (epoch millis, inclusive).
     * @param to     End of the aggregation window (epoch millis, inclusive).
     * @return {@link Single} emitting one {@link NightlyAggregateResult}
     *         per calendar night that has at least one completed session.
     */
    @Query("SELECT " +
            "    date(start_time / 1000, 'unixepoch')              AS date, " +
            "    COUNT(*)                                           AS sessionCount, " +
            "    SUM((end_time - start_time) / 60000 " +
            "        - COALESCE(awake_minutes, 0))                  AS totalSleepMinutes, " +
            "    AVG(sleep_score)                                   AS avgSleepScore, " +
            "    AVG(deep_sleep_minutes)                            AS avgDeepSleepMinutes, " +
            "    AVG(rem_sleep_minutes)                             AS avgRemSleepMinutes, " +
            "    AVG(light_sleep_minutes)                           AS avgLightSleepMinutes " +
            "FROM sleep_sessions " +
            "WHERE user_id    = :userId " +
            "  AND end_time   IS NOT NULL " +
            "  AND start_time >= :from " +
            "  AND start_time <= :to " +
            "GROUP BY date(start_time / 1000, 'unixepoch') " +
            "ORDER BY date ASC")
    Single<List<NightlyAggregateResult>> getNightlyAggregates(long userId, long from, long to);

    // ──────────────────────────────────────────────────────────────────────
    // Score-based insight queries
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns the {@code limit} lowest-scored completed sessions for a
     * user within an optional date range.
     *
     * Used by the insight-generation pipeline to surface
     * "Your worst-sleep nights this month" cards.
     *
     * Backed by the composite index {@code (user_id, sleep_score)}.
     *
     * @param userId Local PK of the owning user.
     * @param from   Window start epoch millis (inclusive).
     * @param to     Window end   epoch millis (inclusive).
     * @param limit  Maximum number of sessions to return.
     * @return {@link Single} emitting sessions ordered by
     *         {@code sleep_score ASC} (worst first).
     */
    @Query("SELECT * FROM sleep_sessions " +
            "WHERE user_id    = :userId " +
            "  AND end_time   IS NOT NULL " +
            "  AND sleep_score IS NOT NULL " +
            "  AND start_time >= :from " +
            "  AND start_time <= :to " +
            "ORDER BY sleep_score ASC " +
            "LIMIT :limit")
    Single<List<SleepSessionEntity>> getWorstScoredSessions(
            long userId, long from, long to, int limit);

    /**
     * Returns the average sleep score for a user within a date range.
     *
     * Used by the weekly sleep quality summary card and the AI
     * coaching prompt ("Your average sleep score this week was X").
     *
     * @param userId Local PK of the owning user.
     * @param from   Window start epoch millis (inclusive).
     * @param to     Window end   epoch millis (inclusive).
     * @return {@link Single} emitting the average score as a Double,
     *         or an error if no scored sessions exist in the window.
     */
    @Query("SELECT AVG(sleep_score) FROM sleep_sessions " +
            "WHERE user_id     = :userId " +
            "  AND end_time    IS NOT NULL " +
            "  AND sleep_score IS NOT NULL " +
            "  AND start_time  >= :from " +
            "  AND start_time  <= :to")
    Single<Double> getAverageSleepScore(long userId, long from, long to);

    // ──────────────────────────────────────────────────────────────────────
    // Count
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns the total number of completed sleep sessions recorded for
     * a user, regardless of date range.
     *
     * Used by the {@code AchievementEntity} unlock check
     * ("Log 30 sleep sessions").
     *
     * @param userId Local PK of the owning user.
     * @return {@link Single} emitting the count as an Integer.
     */
    @Query("SELECT COUNT(*) FROM sleep_sessions " +
            "WHERE user_id = :userId " +
            "  AND end_time IS NOT NULL")
    Single<Integer> countCompletedSessions(long userId);

    // ──────────────────────────────────────────────────────────────────────
    // Export — full data dump
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Streams all sleep sessions for a user ordered by start time,
     * for use by the CSV / data-export WorkManager worker.
     *
     * Returns {@link Observable} rather than {@link Single} so the
     * export worker can process rows incrementally via
     * {@code .buffer(200)} without loading the entire history into
     * memory — important for users with years of data.
     *
     * @param userId Local PK of the owning user.
     * @return {@link Observable} emitting each {@link SleepSessionEntity}
     *         row individually, ordered by {@code start_time ASC}.
     */
    @Query("SELECT * FROM sleep_sessions " +
            "WHERE user_id = :userId " +
            "ORDER BY start_time ASC")
    Observable<SleepSessionEntity> streamAllForExport(long userId);

    /**
     * Returns all sleep sessions for a user within a bounded date range
     * as a single list. Used when the export dialog has a "from / to"
     * date filter applied.
     *
     * @param userId Local PK of the owning user.
     * @param from   Export window start (epoch millis, inclusive).
     * @param to     Export window end   (epoch millis, inclusive).
     * @return {@link Single} emitting the full list ordered by
     *         {@code start_time ASC}.
     */
    @Query("SELECT * FROM sleep_sessions " +
            "WHERE user_id    = :userId " +
            "  AND start_time >= :from " +
            "  AND start_time <= :to " +
            "ORDER BY start_time ASC")
    Single<List<SleepSessionEntity>> getAllInRangeForExport(long userId, long from, long to);

    // ──────────────────────────────────────────────────────────────────────
    // Projection POJO — NightlyAggregateResult
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Non-entity projection returned by {@link #getNightlyAggregates}.
     *
     * Room maps the query's column aliases directly onto these public fields
     * by name. No {@code @Entity} annotation — this class is never persisted;
     * it exists only as a typed result carrier.
     *
     * Fields:
     *   date                  — calendar date string "YYYY-MM-DD" (UTC)
     *   sessionCount          — number of sessions that started on this date
     *   totalSleepMinutes     — sum of net sleep minutes (total − awake) across all
     *                           sessions on this date
     *   avgSleepScore         — mean AI sleep-quality score for the night
     *   avgDeepSleepMinutes   — mean deep-sleep minutes across sessions
     *   avgRemSleepMinutes    — mean REM minutes across sessions
     *   avgLightSleepMinutes  — mean light-sleep minutes across sessions
     */
    class NightlyAggregateResult {

        /** Calendar date in "YYYY-MM-DD" format (UTC, derived from start_time). */
        public String date;

        /** Number of completed sessions that started on this calendar date. */
        public int sessionCount;

        /**
         * Total net sleep minutes for the night:
         * SUM((end_time − start_time) / 60000 − awake_minutes).
         */
        public long totalSleepMinutes;

        /** Average AI sleep score (0–100) across sessions on this date. */
        public double avgSleepScore;

        /** Average deep-sleep (N3) minutes across sessions on this date. */
        public double avgDeepSleepMinutes;

        /** Average REM-sleep minutes across sessions on this date. */
        public double avgRemSleepMinutes;

        /** Average light-sleep (N1/N2) minutes across sessions on this date. */
        public double avgLightSleepMinutes;
    }
}