package com.vitalai.app.data.local.dao;

import androidx.lifecycle.LiveData;

import com.vitalai.app.data.local.entity.HealthMetricEntity;
import com.vitalai.app.domain.model.enums.MetricType;

import java.util.Date;
import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

/**
 * HealthMetricDao
 *
 * Room DAO for all read and write operations against the
 * {@code health_metrics} table ({@link HealthMetricEntity}).
 *
 * Return-type strategy
 * ────────────────────
 * • {@link Completable}          — fire-and-forget writes (insert, delete,
 *                                  update) where the caller only needs to know
 *                                  success / failure, not a result value.
 * • {@link Single}               — one-shot reads that return exactly once:
 *                                  aggregations, counts, export snapshots, and
 *                                  latest-N queries that run on demand.
 * • {@link Observable}           — one-shot reads that return a list on demand
 *                                  but do NOT need to re-emit on DB changes
 *                                  (e.g. background CSV export worker).
 * • {@link LiveData}             — reactive reads observed by the UI layer;
 *                                  Room re-emits automatically when the table
 *                                  changes. Used for the live metric feed and
 *                                  anomaly badge.
 *
 * Threading
 * ─────────
 * All RxJava operations must be subscribed on an IO scheduler
 * ({@code .subscribeOn(Schedulers.io())}). LiveData is delivered on the
 * main thread by Room automatically. Neither the DAO nor the Repository
 * should block the main thread.
 *
 * Architecture layer : Data / Local
 * Table              : health_metrics
 * Entity             : {@link HealthMetricEntity}
 * Used by            : HealthMetricRepository
 */
@Dao
public interface HealthMetricDao {

    // ──────────────────────────────────────────────────────────────────────
    // Insert
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Inserts a single health metric reading.
     * {@code REPLACE} conflict strategy — if a row with the same primary
     * key already exists (e.g. a retry after a partial commit) it is
     * silently replaced. BLE duplicates are de-duplicated upstream in
     * the ingestion layer before reaching this DAO.
     *
     * @param metric The metric entity to insert.
     * @return {@link Completable} that completes on success or errors on
     *         constraint violation / DB error.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(HealthMetricEntity metric);

    /**
     * Inserts a batch of health metric readings in a single transaction.
     * Use this for HealthConnect bulk imports, CSV imports, and BLE
     * burst-mode packet processing to minimise transaction overhead.
     *
     * @param metrics List of metric entities to insert.
     * @return {@link Completable} that completes when all rows are written.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<HealthMetricEntity> metrics);

    /**
     * Returns the total number of metric rows for a given user.
     * Used by the synthetic data seeding logic to check whether
     * metrics have already been generated.
     *
     * @param userId Local PK of the owning user.
     * @return {@link Single} emitting the row count.
     */
    @Query("SELECT COUNT(*) FROM health_metrics WHERE user_id = :userId")
    Single<Integer> countByUser(long userId);


    // ──────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates an existing metric row — primarily used by the ML anomaly
     * worker to set {@link HealthMetricEntity#isAnomaly} after asynchronous
     * inference, and by the Repository to apply manual corrections.
     *
     * @param metric The entity with updated fields (matched by PK).
     * @return {@link Completable} that completes on success.
     */
    @Update
    Completable update(HealthMetricEntity metric);

    // ──────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Deletes a single metric row by entity reference (matched by PK).
     * Used when the user manually removes an incorrect reading.
     *
     * @param metric The entity to delete.
     * @return {@link Completable} that completes on success.
     */
    @Delete
    Completable delete(HealthMetricEntity metric);

    /**
     * Deletes all metric rows for a user that were recorded before the
     * given cutoff instant. Designed for the scheduled data-retention
     * cleanup worker (e.g. "delete raw readings older than 90 days
     * once they have been aggregated into daily summaries").
     *
     * Note: the cutoff applies to {@code timestamp} (the measurement
     * instant), not {@code created_at} (the insert instant), so that
     * CSV imports of historical data are purged on the correct schedule.
     *
     * @param userId  Local PK of the owning user.
     * @param cutoff  Epoch-millis threshold; rows with
     *                {@code timestamp < cutoff} are deleted.
     * @return {@link Completable} that completes when all matching rows
     *         have been removed.
     */
    @Query("DELETE FROM health_metrics " +
            "WHERE user_id = :userId " +
            "  AND timestamp < :cutoff")
    Completable deleteOlderThan(long userId, long cutoff);

    // ──────────────────────────────────────────────────────────────────────
    // Reactive reads — LiveData (UI-observed, auto-refreshing)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns a {@link LiveData}-wrapped list of metric readings for a
     * user, filtered by metric type and a time window, ordered
     * chronologically. Room re-emits the list whenever any row in
     * {@code health_metrics} changes, allowing the metric chart UI to
     * update in real time as BLE data streams in.
     *
     * Backed by the composite index (user_id, metric_type, timestamp).
     *
     * @param userId     Local PK of the owning user.
     * @param metricType The metric category to filter on.
     * @param from       Start of the time window (epoch millis, inclusive).
     * @param to         End of the time window (epoch millis, inclusive).
     * @return Live list of matching {@link HealthMetricEntity} rows,
     *         oldest first.
     */
    @Query("SELECT * FROM health_metrics " +
            "WHERE user_id     = :userId " +
            "  AND metric_type = :metricType " +
            "  AND timestamp  >= :from " +
            "  AND timestamp  <= :to " +
            "ORDER BY timestamp ASC")
    LiveData<List<HealthMetricEntity>> observeMetricsByTypeAndRange(
            long userId,
            String metricType,
            long from,
            long to);

    /**
     * Returns a {@link LiveData}-wrapped list of all anomaly-flagged
     * readings for a user, ordered by most-recent first. Observed by
     * the anomaly alert badge on the dashboard tab bar.
     *
     * Backed by the composite index (user_id, is_anomaly).
     *
     * @param userId Local PK of the owning user.
     * @return Live list of anomalous {@link HealthMetricEntity} rows.
     */
    @Query("SELECT * FROM health_metrics " +
            "WHERE user_id   = :userId " +
            "  AND is_anomaly = 1 " +
            "ORDER BY timestamp DESC")
    LiveData<List<HealthMetricEntity>> observeAnomalies(long userId);

    // ──────────────────────────────────────────────────────────────────────
    // On-demand reads — Single (one-shot, RxJava)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Fetches the most recent {@code limit} readings for a user and
     * metric type, ordered most-recent first. Used by the metric detail
     * screen's "Last N readings" summary card and by the context-window
     * builder that assembles the AI chat system prompt.
     *
     * Backed by the composite index (user_id, metric_type, timestamp).
     *
     * @param userId     Local PK of the owning user.
     * @param metricType The metric category to filter on.
     * @param limit      Maximum number of rows to return.
     * @return {@link Single} emitting an ordered list (newest first)
     *         of at most {@code limit} rows.
     */
    @Query("SELECT * FROM health_metrics " +
            "WHERE user_id     = :userId " +
            "  AND metric_type = :metricType " +
            "ORDER BY timestamp DESC " +
            "LIMIT :limit")
    Single<List<HealthMetricEntity>> getLatestMetrics(
            long userId,
            String metricType,
            int limit);

    /**
     * Fetches metric readings for a user, filtered by type and time range,
     * ordered chronologically. Used by background workers (anomaly ML
     * pipeline, goal-progress updater) that need a snapshot of readings
     * without UI reactivity.
     *
     * @param userId     Local PK of the owning user.
     * @param metricType The metric category to filter on.
     * @param from       Start of the time window (epoch millis, inclusive).
     * @param to         End of the time window (epoch millis, inclusive).
     * @return {@link Single} emitting the matching rows oldest-first.
     */
    @Query("SELECT * FROM health_metrics " +
            "WHERE user_id     = :userId " +
            "  AND metric_type = :metricType " +
            "  AND timestamp  >= :from " +
            "  AND timestamp  <= :to " +
            "ORDER BY timestamp ASC")
    Single<List<HealthMetricEntity>> getMetricsByTypeAndRange(
            long userId,
            String metricType,
            long from,
            long to);

    /**
     * Returns the count of anomaly-flagged readings for a user within
     * a time window. Used by the insight-generation pipeline to decide
     * whether to surface a WARNING or CRITICAL insight (e.g. "5 anomalous
     * heart-rate readings in the last 24 hours").
     *
     * Backed by the composite index (user_id, is_anomaly).
     *
     * @param userId Local PK of the owning user.
     * @param from   Start of the time window (epoch millis, inclusive).
     * @param to     End of the time window (epoch millis, inclusive).
     * @return {@link Single} emitting the anomaly count as an integer.
     */
    @Query("SELECT COUNT(*) FROM health_metrics " +
            "WHERE user_id    = :userId " +
            "  AND is_anomaly = 1 " +
            "  AND timestamp >= :from " +
            "  AND timestamp <= :to")
    Single<Integer> countAnomaliesInRange(long userId, long from, long to);

    /**
     * Returns the single most recent reading for a user and metric type.
     * Used by the dashboard summary cards (e.g. "Last heart rate: 72 bpm")
     * and by the goal-progress updater to snapshot the current metric value
     * when {@link com.vitalai.app.data.local.entity.GoalEntity#linkedMetricType}
     * matches this metric type.
     *
     * @param userId     Local PK of the owning user.
     * @param metricType The metric category to query.
     * @return {@link Single} emitting the most recent entity, or an error
     *         if no rows exist (use {@code .onErrorReturn} in the Repository
     *         to handle the empty-table case gracefully).
     */
    @Query("SELECT * FROM health_metrics " +
            "WHERE user_id     = :userId " +
            "  AND metric_type = :metricType " +
            "ORDER BY timestamp DESC " +
            "LIMIT 1")
    Single<HealthMetricEntity> getLatestMetric(long userId, String metricType);

    // ──────────────────────────────────────────────────────────────────────
    // Aggregation — DailyAggregate projection
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns daily aggregate statistics (average, minimum, maximum, and
     * reading count) for a user and metric type, grouped by calendar date.
     *
     * The date bucket is derived from {@code timestamp} using SQLite's
     * {@code date()} function with the Unix epoch seconds form
     * ({@code timestamp / 1000} converts epoch millis to seconds):
     *   date(timestamp / 1000, 'unixepoch') → "YYYY-MM-DD"
     *
     * Results are ordered chronologically (oldest bucket first) for
     * direct consumption by trend-chart adapters.
     *
     * Mapped to {@link DailyAggregateResult} — a non-entity POJO
     * defined as a static inner class below.
     *
     * @param userId     Local PK of the owning user.
     * @param metricType The metric category to aggregate.
     * @param from       Start of the aggregation window (epoch millis).
     * @param to         End of the aggregation window (epoch millis).
     * @return {@link Single} emitting one {@link DailyAggregateResult}
     *         per calendar day that has at least one reading.
     */
    @Query("SELECT " +
            "    date(timestamp / 1000, 'unixepoch') AS date, " +
            "    AVG(value)                           AS avgValue, " +
            "    MIN(value)                           AS minValue, " +
            "    MAX(value)                           AS maxValue, " +
            "    COUNT(*)                             AS readingCount " +
            "FROM health_metrics " +
            "WHERE user_id     = :userId " +
            "  AND metric_type = :metricType " +
            "  AND timestamp  >= :from " +
            "  AND timestamp  <= :to " +
            "GROUP BY date(timestamp / 1000, 'unixepoch') " +
            "ORDER BY date ASC")
    Single<List<DailyAggregateResult>> getDailyAggregates(
            long userId,
            String metricType,
            long from,
            long to);

    // ──────────────────────────────────────────────────────────────────────
    // Export — Observable (one-shot streaming read)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Fetches all metric rows for a user across all metric types,
     * ordered by metric type then chronologically within each type.
     * Intended for the CSV / data-export feature that runs in a
     * background WorkManager worker.
     *
     * Returns {@link Observable} rather than {@link Single} so the
     * export worker can process rows incrementally via
     * {@code .buffer(500)} without loading the entire table into
     * memory at once — important for users with years of data.
     *
     * @param userId Local PK of the owning user.
     * @return {@link Observable} emitting each {@link HealthMetricEntity}
     *         row individually, ordered by metric_type ASC, timestamp ASC.
     */
    @Query("SELECT * FROM health_metrics " +
            "WHERE user_id = :userId " +
            "ORDER BY metric_type ASC, timestamp ASC")
    Observable<HealthMetricEntity> streamAllForExport(long userId);

    /**
     * Fetches all metric rows for a user within a specific date range
     * across all metric types, as a single list. Used when the user
     * selects a bounded date range for export (e.g. "Export last 30 days").
     *
     * @param userId Local PK of the owning user.
     * @param from   Export window start (epoch millis, inclusive).
     * @param to     Export window end (epoch millis, inclusive).
     * @return {@link Single} emitting the complete list of matching rows
     *         ordered by metric_type ASC, timestamp ASC.
     */
    @Query("SELECT * FROM health_metrics " +
            "WHERE user_id    = :userId " +
            "  AND timestamp >= :from " +
            "  AND timestamp <= :to " +
            "ORDER BY metric_type ASC, timestamp ASC")
    Single<List<HealthMetricEntity>> getAllInRangeForExport(
            long userId,
            long from,
            long to);

    // ──────────────────────────────────────────────────────────────────────
    // Projection POJO — DailyAggregateResult
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Non-entity projection returned by {@link #getDailyAggregates}.
     *
     * Room maps the query's column aliases directly onto these public
     * fields by name. No {@code @Entity} annotation — this class is
     * never persisted; it exists only as a typed result carrier.
     *
     * Fields:
     *   date         — calendar date string "YYYY-MM-DD" (UTC)
     *   avgValue     — mean of all readings in the day bucket
     *   minValue     — minimum reading in the day bucket
     *   maxValue     — maximum reading in the day bucket
     *   readingCount — number of individual readings in the bucket
     */
    class DailyAggregateResult {

        /** Calendar date in "YYYY-MM-DD" format (UTC). */
        public String date;

        /** Mean metric value across all readings in this day bucket. */
        public double avgValue;

        /** Minimum metric value recorded during the day. */
        public double minValue;

        /** Maximum metric value recorded during the day. */
        public double maxValue;

        /** Total number of individual readings in this day bucket. */
        public int readingCount;
    }
}