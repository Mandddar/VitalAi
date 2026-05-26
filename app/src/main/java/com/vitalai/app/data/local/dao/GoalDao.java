package com.vitalai.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.vitalai.app.data.local.entity.GoalEntity;
import com.vitalai.app.domain.model.enums.GoalStatus;
import com.vitalai.app.domain.model.enums.GoalType;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * GoalDao
 *
 * Room DAO for all read and write operations against the {@code goals} table.
 *
 * Architecture layer : Data / Local
 * Table              : goals
 * Entity             : {@link GoalEntity}
 */
@Dao
public interface GoalDao {

    // ──────────────────────────────────────────────────────────────────────
    // Insert
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Inserts a single health goal.
     *
     * @param goal The entity to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(GoalEntity goal);

    /**
     * Inserts a list of goals.
     *
     * @param goals List of entities to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<GoalEntity> goals);

    // ──────────────────────────────────────────────────────────────────────
    // Query — Active & History
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Observes all ACTIVE goals for a user.
     * Used for the main progress dashboard.
     *
     * @param userId Local PK of the owner.
     * @return {@link LiveData} containing the list of active goals.
     */
    @Query("SELECT * FROM goals WHERE user_id = :userId AND status = 'ACTIVE' ORDER BY target_date ASC")
    LiveData<List<GoalEntity>> observeActiveGoals(long userId);

    /**
     * Observes goals by their specific status (COMPLETED, FAILED, etc.).
     *
     * @param userId Local PK of the owner.
     * @param status The status to filter by.
     * @return {@link LiveData} containing the filtered goals.
     */
    @Query("SELECT * FROM goals WHERE user_id = :userId AND status = :status ORDER BY updated_at DESC")
    LiveData<List<GoalEntity>> observeGoalsByStatus(long userId, GoalStatus status);

    /**
     * Fetches a snapshot of goals by type.
     *
     * @param userId   Local PK of the owner.
     * @param goalType The broad category of the goal.
     * @return {@link Single} emitting the list of matching goals.
     */
    @Query("SELECT * FROM goals WHERE user_id = :userId AND goal_type = :goalType ORDER BY start_date DESC")
    Single<List<GoalEntity>> getByUserIdAndType(long userId, GoalType goalType);

    /**
     * Returns goals for a user that have a deadline within a specific range.
     * Used by the AI coaching layer to identify approaching deadlines.
     *
     * @param userId Local PK of the owner.
     * @param from   Start of the window (epoch millis).
     * @param to     End of the window (epoch millis).
     * @return {@link Single} emitting matching goals.
     */
    @Query("SELECT * FROM goals WHERE user_id = :userId AND target_date >= :from AND target_date <= :to")
    Single<List<GoalEntity>> getGoalsInDeadlineRange(long userId, long from, long to);

    // ──────────────────────────────────────────────────────────────────────
    // Query — Metrics & Automation
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns all ACTIVE goals linked to a specific metric type.
     * Used by the Repository to auto-update progress when new metrics arrive.
     *
     * @param userId           Local PK of the owner.
     * @param linkedMetricType The metric type string (e.g. "HEART_RATE").
     * @return {@link Single} emitting the matching active goals.
     */
    @Query("SELECT * FROM goals WHERE user_id = :userId AND linked_metric_type = :linkedMetricType AND status = 'ACTIVE'")
    Single<List<GoalEntity>> getActiveGoalsByMetric(long userId, String linkedMetricType);

    // ──────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates an existing goal record.
     *
     * @param goal The entity with updated fields.
     * @return {@link Completable} indicating operation completion.
     */
    @Update
    Completable update(GoalEntity goal);

    // ──────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Deletes a specific goal record.
     *
     * @param goal The entity to delete.
     * @return {@link Completable} indicating operation completion.
     */
    @Delete
    Completable delete(GoalEntity goal);

    /**
     * Deletes all goals for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("DELETE FROM goals WHERE user_id = :userId")
    Completable deleteAllByUserId(long userId);
}
