package com.vitalai.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.vitalai.app.data.local.entity.MoodLogEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

/**
 * MoodLogDao
 *
 * Room DAO for all read and write operations against the {@code mood_logs} table.
 *
 * Architecture layer : Data / Local
 * Table              : mood_logs
 * Entity             : {@link MoodLogEntity}
 */
@Dao
public interface MoodLogDao {

    // ──────────────────────────────────────────────────────────────────────
    // Insert
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Inserts a single mood log record.
     *
     * @param log The entity to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(MoodLogEntity log);

    /**
     * Inserts a list of mood log records.
     *
     * @param logs List of entities to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<MoodLogEntity> logs);

    // ──────────────────────────────────────────────────────────────────────
    // Query — Timeline & Trends
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Observes all mood logs for a user in a specific range, newest first.
     * Used for the mood timeline and feed.
     *
     * @param userId Local PK of the owner.
     * @param from   Start of the window (epoch millis).
     * @param to     End of the window (epoch millis).
     * @return {@link LiveData} containing the matching logs.
     */
    @Query("SELECT * FROM mood_logs WHERE user_id = :userId AND logged_at >= :from AND logged_at <= :to ORDER BY logged_at DESC")
    LiveData<List<MoodLogEntity>> observeLogsInRange(long userId, long from, long to);

    /**
     * Returns a snapshot of logs in a range.
     *
     * @param userId Local PK of the owner.
     * @param from   Start of the window (epoch millis).
     * @param to     End of the window (epoch millis).
     * @return {@link Single} emitting the list of logs.
     */
    @Query("SELECT * FROM mood_logs WHERE user_id = :userId AND logged_at >= :from AND logged_at <= :to ORDER BY logged_at DESC")
    Single<List<MoodLogEntity>> getLogsInRange(long userId, long from, long to);

    /**
     * Calculates the average mood score for a user in a specific range.
     *
     * @param userId Local PK of the owner.
     * @param from   Start of the window (epoch millis).
     * @param to     End of the window (epoch millis).
     * @return {@link Single} emitting the average score.
     */
    @Query("SELECT AVG(mood_score) FROM mood_logs WHERE user_id = :userId AND logged_at >= :from AND logged_at <= :to")
    Single<Double> getAverageScoreInRange(long userId, long from, long to);

    /**
     * Fetches the single most recent mood log for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Single} emitting the latest log.
     */
    @Query("SELECT * FROM mood_logs WHERE user_id = :userId ORDER BY logged_at DESC LIMIT 1")
    Single<MoodLogEntity> getLatestLog(long userId);

    /**
     * Fetches logs filtered by logging context (e.g. "POST_WORKOUT").
     *
     * @param userId  Local PK of the owner.
     * @param context Programmatic context string.
     * @return {@link Single} emitting the matching logs.
     */
    @Query("SELECT * FROM mood_logs WHERE user_id = :userId AND context = :context ORDER BY logged_at DESC")
    Single<List<MoodLogEntity>> getByContext(long userId, String context);

    // ──────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates an existing mood log.
     *
     * @param log The entity with updated fields.
     * @return {@link Completable} indicating operation completion.
     */
    @Update
    Completable update(MoodLogEntity log);

    // ──────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Deletes a specific mood log record.
     *
     * @param log The entity to delete.
     * @return {@link Completable} indicating operation completion.
     */
    @Delete
    Completable delete(MoodLogEntity log);

    /**
     * Deletes all mood logs for a specific user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("DELETE FROM mood_logs WHERE user_id = :userId")
    Completable deleteAllByUserId(long userId);

    // ──────────────────────────────────────────────────────────────────────
    // Export & Sync
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Streams all non-private logs for a user for data export/sync.
     *
     * @param userId Local PK of the owner.
     * @return {@link Observable} emitting individual log entities.
     */
    @Query("SELECT * FROM mood_logs WHERE user_id = :userId AND is_private = 0 ORDER BY logged_at ASC")
    Observable<MoodLogEntity> streamNonPrivateForExport(long userId);

    /**
     * Streams all logs for a user for local CSV backup.
     *
     * @param userId Local PK of the owner.
     * @return {@link Observable} emitting individual log entities.
     */
    @Query("SELECT * FROM mood_logs WHERE user_id = :userId ORDER BY logged_at ASC")
    Observable<MoodLogEntity> streamAllForExport(long userId);
}
