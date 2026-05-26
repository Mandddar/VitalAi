package com.vitalai.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.vitalai.app.data.local.entity.HydrationLogEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

/**
 * HydrationLogDao
 *
 * Room DAO for all read and write operations against the {@code hydration_logs} table.
 *
 * Architecture layer : Data / Local
 * Table              : hydration_logs
 * Entity             : {@link HydrationLogEntity}
 */
@Dao
public interface HydrationLogDao {

    // ──────────────────────────────────────────────────────────────────────
    // Insert
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Inserts a single hydration log record.
     *
     * @param log The entity to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(HydrationLogEntity log);

    /**
     * Inserts a list of hydration log records.
     *
     * @param logs List of entities to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<HydrationLogEntity> logs);

    // ──────────────────────────────────────────────────────────────────────
    // Query — Timeline & Aggregation
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Observes all logs for a user on a specific date range, oldest first.
     * Used for the daily intake timeline.
     *
     * @param userId Local PK of the owner.
     * @param from   Start of the window (epoch millis).
     * @param to     End of the window (epoch millis).
     * @return {@link LiveData} containing the matching logs.
     */
    @Query("SELECT * FROM hydration_logs WHERE user_id = :userId AND logged_at >= :from AND logged_at <= :to ORDER BY logged_at ASC")
    LiveData<List<HydrationLogEntity>> observeLogsInRange(long userId, long from, long to);

    /**
     * Returns a snapshot of logs in a range.
     *
     * @param userId Local PK of the owner.
     * @param from   Start of the window (epoch millis).
     * @param to     End of the window (epoch millis).
     * @return {@link Single} emitting the list of logs.
     */
    @Query("SELECT * FROM hydration_logs WHERE user_id = :userId AND logged_at >= :from AND logged_at <= :to ORDER BY logged_at ASC")
    Single<List<HydrationLogEntity>> getLogsInRange(long userId, long from, long to);

    /**
     * Calculates the total amount (mL) consumed by a user in a specific range.
     *
     * @param userId Local PK of the owner.
     * @param from   Start of the window (epoch millis).
     * @param to     End of the window (epoch millis).
     * @return {@link Single} emitting the total volume.
     */
    @Query("SELECT SUM(amount_ml) FROM hydration_logs WHERE user_id = :userId AND logged_at >= :from AND logged_at <= :to")
    Single<Double> getTotalInRange(long userId, long from, long to);

    /**
     * Observes the daily total intake for a user today.
     *
     * @param userId Local PK of the owner.
     * @param from   Start of today (epoch millis).
     * @param to     End of today (epoch millis).
     * @return {@link LiveData} emitting the total volume.
     */
    @Query("SELECT SUM(amount_ml) FROM hydration_logs WHERE user_id = :userId AND logged_at >= :from AND logged_at <= :to")
    LiveData<Double> observeTotalInRange(long userId, long from, long to);

    /**
     * Observes all unconfirmed AI-suggested entries for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link LiveData} emitting the list of suggested entries.
     */
    @Query("SELECT * FROM hydration_logs WHERE user_id = :userId AND is_ai_suggested = 1 ORDER BY logged_at DESC")
    LiveData<List<HydrationLogEntity>> observeAiSuggestions(long userId);

    // ──────────────────────────────────────────────────────────────────────
    // Query — Statistics & Trends
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns daily hydration totals grouped by date.
     *
     * @param userId Local PK of the owner.
     * @param from   Start of the range.
     * @param to     End of the range.
     * @return {@link Single} emitting daily totals.
     */
    @Query("SELECT date(logged_at / 1000, 'unixepoch') AS date, SUM(amount_ml) AS totalMl " +
            "FROM hydration_logs " +
            "WHERE user_id = :userId AND logged_at >= :from AND logged_at <= :to " +
            "GROUP BY date " +
            "ORDER BY date ASC")
    Single<List<DailyTotalResult>> getDailyTotals(long userId, long from, long to);

    // ──────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates an existing hydration log.
     *
     * @param log The entity with updated fields.
     * @return {@link Completable} indicating operation completion.
     */
    @Update
    Completable update(HydrationLogEntity log);

    // ──────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Deletes a specific hydration log record.
     *
     * @param log The entity to delete.
     * @return {@link Completable} indicating operation completion.
     */
    @Delete
    Completable delete(HydrationLogEntity log);

    /**
     * Deletes all logs for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("DELETE FROM hydration_logs WHERE user_id = :userId")
    Completable deleteAllByUserId(long userId);

    // ──────────────────────────────────────────────────────────────────────
    // Export
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Streams all logs for a user for data export.
     *
     * @param userId Local PK of the owner.
     * @return {@link Observable} emitting individual log entities.
     */
    @Query("SELECT * FROM hydration_logs WHERE user_id = :userId ORDER BY logged_at ASC")
    Observable<HydrationLogEntity> streamAllForExport(long userId);

    // ──────────────────────────────────────────────────────────────────────
    // Projection POJO
    // ──────────────────────────────────────────────────────────────────────

    class DailyTotalResult {
        public String date;
        public double totalMl;
    }
}
