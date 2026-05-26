package com.vitalai.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.vitalai.app.data.local.entity.UserBaselineEntity;
import com.vitalai.app.domain.model.enums.MetricType;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * UserBaselineDao
 *
 * Room DAO for all read and write operations against the {@code user_baselines} table.
 *
 * Architecture layer : Data / Local
 * Table              : user_baselines
 * Entity             : {@link UserBaselineEntity}
 */
@Dao
public interface UserBaselineDao {

    // ──────────────────────────────────────────────────────────────────────
    // Insert / Upsert
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Inserts a baseline record or replaces it if one exists for the same metric.
     *
     * @param baseline The entity to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(UserBaselineEntity baseline);

    // ──────────────────────────────────────────────────────────────────────
    // Query — Contextual Retrieval
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Fetches the current personal baseline for a specific metric and user.
     * Used by the anomaly detector and coaching layer.
     *
     * @param userId     Local PK of the owner.
     * @param metricType The health metric category.
     * @return {@link Single} emitting the baseline entity.
     */
    @Query("SELECT * FROM user_baselines WHERE user_id = :userId AND metric_type = :metricType LIMIT 1")
    Single<UserBaselineEntity> getByMetric(long userId, MetricType metricType);

    /**
     * Observes the baseline for a specific metric.
     *
     * @param userId     Local PK of the owner.
     * @param metricType The health metric category.
     * @return {@link LiveData} containing the baseline.
     */
    @Query("SELECT * FROM user_baselines WHERE user_id = :userId AND metric_type = :metricType LIMIT 1")
    LiveData<UserBaselineEntity> observeByMetric(long userId, MetricType metricType);

    /**
     * Returns all computed baselines for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Single} emitting the list of baselines.
     */
    @Query("SELECT * FROM user_baselines WHERE user_id = :userId")
    Single<List<UserBaselineEntity>> getAllForUser(long userId);

    /**
     * Fetches all baselines marked as stale for a user.
     * Used by the WorkManager refresh job to identify pending calculations.
     *
     * @param userId Local PK of the owner.
     * @return {@link Single} emitting the list of stale baselines.
     */
    @Query("SELECT * FROM user_baselines WHERE user_id = :userId AND is_stale = 1")
    Single<List<UserBaselineEntity>> getStaleBaselines(long userId);

    // ──────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates an existing baseline record.
     *
     * @param baseline The entity with updated fields.
     * @return {@link Completable} indicating operation completion.
     */
    @Update
    Completable update(UserBaselineEntity baseline);

    // ──────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Deletes a specific baseline record.
     *
     * @param baseline The entity to delete.
     * @return {@link Completable} indicating operation completion.
     */
    @Delete
    Completable delete(UserBaselineEntity baseline);

    /**
     * Removes all baseline records for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("DELETE FROM user_baselines WHERE user_id = :userId")
    Completable deleteAllByUserId(long userId);
}
