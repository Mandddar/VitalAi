package com.vitalai.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.vitalai.app.data.local.entity.SleepStageEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

/**
 * SleepStageDao
 *
 * Room DAO for all CRUD and query operations against the {@code sleep_stages} table.
 *
 * Design notes
 * ────────────
 * • {@code sleep_stages} is a child of {@code sleep_sessions}, which is itself a
 *   child of {@code users}. Both parent FKs use ON DELETE CASCADE, so explicit
 *   bulk-delete by user or session is rarely needed — the DB handles cleanup
 *   automatically when a parent row is removed. Targeted deletes are provided
 *   for the data-retention worker and for correcting re-synced sessions.
 *
 * • Insert uses {@link OnConflictStrategy#REPLACE} to support upsert semantics
 *   when a BLE stream re-delivers a stage interval with an updated end_time or
 *   revised confidence_score.
 *
 * • All read operations return RxJava 3 types. There is no LiveData here
 *   because the stage-timeline chart fetches data on demand (Single) rather
 *   than continuously observing it.
 *
 * • The {@link StageDurationResult} projection is used by the session-level
 *   stage-breakdown recomputation logic so the Repository can refresh the
 *   cached minute columns on {@link com.vitalai.app.data.local.entity.SleepSessionEntity}
 *   after any stage write.
 *
 * Architecture layer : Data / Local
 * Package            : com.vitalai.app.data.local.dao
 * Entity             : {@link SleepStageEntity}
 * Table              : sleep_stages
 */
@Dao
public interface SleepStageDao {

    // ──────────────────────────────────────────────────────────────────────
    // Insert
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Inserts a single sleep-stage interval.
     *
     * Uses {@link OnConflictStrategy#REPLACE}: if a row with the same primary
     * key already exists it is replaced in full. Supports upsert semantics for
     * BLE streaming where a stage interval arrives incomplete (null end_time)
     * and is later re-delivered with the final end_time and confidence_score.
     *
     * @param stage The stage entity to insert.
     * @return {@link Single} emitting the new row ID on success.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Single<Long> insert(SleepStageEntity stage);

    /**
     * Inserts a batch of sleep-stage intervals in a single transaction.
     *
     * The primary use case is bulk-ingesting all stage records for a session
     * returned by HealthConnect or a CSV import. Conflict strategy is REPLACE —
     * existing rows with matching IDs are overwritten, making this safe to call
     * on re-sync without prior deletion.
     *
     * @param stages List of stage entities to insert.
     * @return {@link Completable} that completes when all rows are written.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<SleepStageEntity> stages);

    // ──────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates an existing stage row.
     *
     * The Repository must call {@link SleepStageEntity#touch()} (or
     * {@link SleepStageEntity#close(java.util.Date)}) to refresh
     * {@code updated_at} before passing the entity here.
     *
     * @param stage The entity with updated fields. Must have a valid {@code id}.
     * @return {@link Completable} that completes on success.
     */
    @Update
    Completable update(SleepStageEntity stage);

    // ──────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Deletes a specific stage interval by entity reference (matched on PK).
     *
     * @param stage The entity to delete.
     * @return {@link Completable} that completes on success.
     */
    @Delete
    Completable delete(SleepStageEntity stage);

    /**
     * Deletes all stage intervals belonging to a given session.
     *
     * Normally not needed because ON DELETE CASCADE on the session FK handles
     * child cleanup automatically. Use this explicitly when you need to
     * replace a session's stage data in full without deleting the session row
     * itself (e.g. re-classifying stages after a firmware update changes the
     * model output).
     *
     * @param sessionId Local PK of the owning sleep session.
     * @return {@link Completable} that completes when all rows are removed.
     */
    @Query("DELETE FROM sleep_stages WHERE session_id = :sessionId")
    Completable deleteAllForSession(long sessionId);

    /**
     * Deletes all stage intervals for a user whose {@code start_time} is
     * strictly before the given cutoff.
     *
     * Used by the data-retention WorkManager worker to enforce a rolling
     * history window (e.g. "keep only the last 365 days of sleep stages").
     * The parent session rows must be pruned separately via
     * {@code SleepSessionDao.deleteSessionsOlderThan}.
     *
     * @param userId Local PK of the owning user.
     * @param before Cutoff epoch millis (exclusive). Rows with
     *               {@code start_time < before} are removed.
     * @return {@link Completable} that completes when deletion is done.
     */
    @Query("DELETE FROM sleep_stages " +
            "WHERE user_id    = :userId " +
            "  AND start_time < :before")
    Completable deleteStagesOlderThan(long userId, long before);

    // ──────────────────────────────────────────────────────────────────────
    // Session timeline — primary read pattern
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns all stage intervals for a given session in chronological order.
     *
     * This is the primary data source for the sleep-stage timeline chart on
     * the session-detail screen. Results are ordered by {@code start_time ASC}
     * so the chart adapter can render them left-to-right without sorting.
     *
     * Uses the composite index {@code (session_id, start_time)} for an
     * efficient seek with no full-table scan.
     *
     * @param sessionId Local PK of the sleep session whose stages to load.
     * @return {@link Single} emitting the ordered list of stage intervals.
     *         Emits an empty list (not an error) if no rows exist.
     */
    @Query("SELECT * FROM sleep_stages " +
            "WHERE session_id = :sessionId " +
            "ORDER BY start_time ASC")
    Single<List<SleepStageEntity>> getStagesForSession(long sessionId);

    /**
     * Returns all stage intervals of a specific type within a session,
     * ordered chronologically.
     *
     * Used by the session-detail screen to highlight (e.g.) all DEEP
     * intervals in a different colour on the timeline, and by the
     * Repository when recomputing the {@code deep_sleep_minutes} cache
     * column on the parent session after partial re-classification.
     *
     * Uses the composite index {@code (session_id, stage)}.
     *
     * @param sessionId Local PK of the owning sleep session.
     * @param stage     The stage type to filter on (stored as TEXT in DB;
     *                  pass {@link com.vitalai.app.domain.model.enums.SleepStage#name()}).
     * @return {@link Single} emitting the matching stage intervals ordered
     *         by {@code start_time ASC}.
     */
    @Query("SELECT * FROM sleep_stages " +
            "WHERE session_id = :sessionId " +
            "  AND stage      = :stage " +
            "ORDER BY start_time ASC")
    Single<List<SleepStageEntity>> getStagesForSessionByType(long sessionId, String stage);

    // ──────────────────────────────────────────────────────────────────────
    // Ongoing interval — BLE streaming
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns the currently open (in-progress) stage interval for a session —
     * the one with {@code end_time IS NULL}.
     *
     * Used by the BLE streaming service to retrieve the open interval before
     * closing it ({@link SleepStageEntity#close(java.util.Date)}) when the
     * device signals a stage transition. There should never be more than one
     * open interval per session; the {@code LIMIT 1} guards against edge-case
     * duplicates from interrupted streams.
     *
     * @param sessionId Local PK of the owning sleep session.
     * @return {@link Single} emitting the open entity, or an error if none
     *         exists (use {@code .onErrorReturn(null)} in the Repository to
     *         treat absence as null).
     */
    @Query("SELECT * FROM sleep_stages " +
            "WHERE session_id = :sessionId " +
            "  AND end_time   IS NULL " +
            "ORDER BY start_time DESC " +
            "LIMIT 1")
    Single<SleepStageEntity> getOngoingStage(long sessionId);

    // ──────────────────────────────────────────────────────────────────────
    // Cross-session queries — longitudinal analysis
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns all completed stage intervals of a specific type for a user
     * within a date range, ordered chronologically.
     *
     * Primary use cases:
     *   • "Total deep-sleep time per night over the last 30 days" trend chart.
     *   • AI coaching: "You averaged only 45 min of REM sleep this week".
     *   • Anomaly pipeline: detecting prolonged AWAKE intervals across nights.
     *
     * Uses the composite index {@code (user_id, stage, start_time)} for an
     * efficient range scan without joining through {@code sleep_sessions}.
     *
     * @param userId    Local PK of the owning user.
     * @param stage     The stage type to filter on (pass
     *                  {@link com.vitalai.app.domain.model.enums.SleepStage#name()}).
     * @param from      Window start epoch millis (inclusive).
     * @param to        Window end epoch millis (inclusive).
     * @return {@link Single} emitting the matching intervals ordered by
     *         {@code start_time ASC}.
     */
    @Query("SELECT * FROM sleep_stages " +
            "WHERE user_id    = :userId " +
            "  AND stage      = :stage " +
            "  AND end_time   IS NOT NULL " +
            "  AND start_time >= :from " +
            "  AND start_time <= :to " +
            "ORDER BY start_time ASC")
    Single<List<SleepStageEntity>> getStagesByTypeInRange(
            long userId, String stage, long from, long to);

    // ──────────────────────────────────────────────────────────────────────
    // Aggregation — per-session stage-duration totals
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns the total duration (in minutes) of each stage type for a given
     * session, grouped by stage.
     *
     * This query is the canonical source used by the Repository to recompute
     * the pre-aggregated minute columns on the parent
     * {@link com.vitalai.app.data.local.entity.SleepSessionEntity}
     * ({@code light_sleep_minutes}, {@code deep_sleep_minutes},
     * {@code rem_sleep_minutes}, {@code awake_minutes}) after any stage write.
     *
     * Duration is computed inline as {@code (end_time - start_time) / 60000}.
     * Only completed intervals ({@code end_time IS NOT NULL}) are included
     * to avoid inflating totals with open BLE-streaming rows.
     *
     * Uses the composite index {@code (session_id, stage)} for an efficient
     * grouping without a full-table scan.
     *
     * Mapped to {@link StageDurationResult}.
     *
     * @param sessionId Local PK of the sleep session to aggregate.
     * @return {@link Single} emitting one {@link StageDurationResult} per
     *         stage type that has at least one completed interval in this session.
     */
    @Query("SELECT " +
            "    stage, " +
            "    SUM((end_time - start_time) / 60000) AS totalMinutes, " +
            "    COUNT(*)                              AS intervalCount " +
            "FROM sleep_stages " +
            "WHERE session_id = :sessionId " +
            "  AND end_time   IS NOT NULL " +
            "GROUP BY stage")
    Single<List<StageDurationResult>> getStageDurationsForSession(long sessionId);

    /**
     * Returns the total duration (in minutes) for a specific stage type
     * within a session.
     *
     * Lighter-weight alternative to {@link #getStageDurationsForSession} when
     * only one stage column needs refreshing (e.g. updating only
     * {@code deep_sleep_minutes} after the deep-sleep classification is revised).
     *
     * @param sessionId Local PK of the owning sleep session.
     * @param stage     The stage type to sum (pass
     *                  {@link com.vitalai.app.domain.model.enums.SleepStage#name()}).
     * @return {@link Single} emitting total minutes as a Long, or 0 if no
     *         completed intervals of this type exist.
     */
    @Query("SELECT COALESCE(SUM((end_time - start_time) / 60000), 0) " +
            "FROM sleep_stages " +
            "WHERE session_id = :sessionId " +
            "  AND stage      = :stage " +
            "  AND end_time   IS NOT NULL")
    Single<Long> getTotalMinutesForStage(long sessionId, String stage);

    // ──────────────────────────────────────────────────────────────────────
    // Count
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns the number of completed stage intervals recorded for a given
     * session.
     *
     * Used by the insight pipeline to assess recording quality — very few
     * intervals may indicate a device connectivity issue or a very short session.
     *
     * @param sessionId Local PK of the sleep session.
     * @return {@link Single} emitting the count as an Integer.
     */
    @Query("SELECT COUNT(*) FROM sleep_stages " +
            "WHERE session_id = :sessionId " +
            "  AND end_time   IS NOT NULL")
    Single<Integer> countCompletedIntervalsForSession(long sessionId);

    // ──────────────────────────────────────────────────────────────────────
    // Export
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Streams all stage intervals for a user ordered by session then
     * chronologically within each session, for the CSV / data-export worker.
     *
     * Returns {@link Observable} rather than {@link Single} so the export
     * worker can process rows incrementally via {@code .buffer(500)} without
     * loading the full stage history into memory — critical for users with
     * years of nightly per-epoch data.
     *
     * @param userId Local PK of the owning user.
     * @return {@link Observable} emitting each {@link SleepStageEntity} row
     *         individually, ordered by {@code session_id ASC, start_time ASC}.
     */
    @Query("SELECT * FROM sleep_stages " +
            "WHERE user_id = :userId " +
            "ORDER BY session_id ASC, start_time ASC")
    Observable<SleepStageEntity> streamAllForExport(long userId);

    /**
     * Returns all stage intervals for a user within a bounded date range as a
     * single list. Used when the export dialog has a "from / to" date filter.
     *
     * @param userId Local PK of the owning user.
     * @param from   Export window start (epoch millis, inclusive).
     * @param to     Export window end   (epoch millis, inclusive).
     * @return {@link Single} emitting the full list ordered by
     *         {@code session_id ASC, start_time ASC}.
     */
    @Query("SELECT * FROM sleep_stages " +
            "WHERE user_id    = :userId " +
            "  AND start_time >= :from " +
            "  AND start_time <= :to " +
            "ORDER BY session_id ASC, start_time ASC")
    Single<List<SleepStageEntity>> getAllInRangeForExport(long userId, long from, long to);

    // ──────────────────────────────────────────────────────────────────────
    // Projection POJO — StageDurationResult
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Non-entity projection returned by {@link #getStageDurationsForSession}.
     *
     * Room maps the query's column aliases directly onto these public fields
     * by name. No {@code @Entity} annotation — this class is never persisted;
     * it exists only as a typed result carrier.
     *
     * The Repository uses this to refresh the pre-aggregated minute columns
     * on the parent {@link com.vitalai.app.data.local.entity.SleepSessionEntity}
     * after any stage write:
     *
     * <pre>
     *   SleepStage.LIGHT → SleepSessionEntity.lightSleepMinutes
     *   SleepStage.DEEP  → SleepSessionEntity.deepSleepMinutes
     *   SleepStage.REM   → SleepSessionEntity.remSleepMinutes
     *   SleepStage.AWAKE → SleepSessionEntity.awakeMinutes
     * </pre>
     *
     * Fields:
     *   stage          — stage type string (matches {@link com.vitalai.app.domain.model.enums.SleepStage#name()})
     *   totalMinutes   — net duration of all completed intervals of this type
     *   intervalCount  — number of discrete intervals of this type
     */
    class StageDurationResult {

        /**
         * Stage type as stored in the DB (TEXT representation of the
         * {@link com.vitalai.app.domain.model.enums.SleepStage} enum).
         * The Repository casts this back to the enum via
         * {@code SleepStage.valueOf(stage)}.
         */
        public String stage;

        /**
         * Total duration of all completed intervals of this stage type
         * within the session, in minutes.
         * Computed as {@code SUM((end_time - start_time) / 60000)}.
         */
        public long totalMinutes;

        /**
         * Number of distinct completed intervals that contributed to
         * {@link #totalMinutes}. Useful for identifying fragmented sleep
         * (many short AWAKE intervals vs one long one).
         */
        public int intervalCount;
    }
}
