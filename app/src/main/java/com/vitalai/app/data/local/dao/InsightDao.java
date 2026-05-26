package com.vitalai.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.vitalai.app.data.local.entity.InsightEntity;
import com.vitalai.app.domain.model.enums.InsightCategory;
import com.vitalai.app.domain.model.enums.InsightSeverity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

/**
 * InsightDao
 *
 * Room DAO for all read and write operations against the {@code insights} table.
 *
 * Architecture layer : Data / Local
 * Table              : insights
 * Entity             : {@link InsightEntity}
 */
@Dao
public interface InsightDao {

    // ──────────────────────────────────────────────────────────────────────
    // Insert
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Inserts a single AI-generated insight.
     *
     * @param insight The entity to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(InsightEntity insight);

    /**
     * Inserts a list of insights in a single transaction.
     *
     * @param insights List of entities to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<InsightEntity> insights);

    // ──────────────────────────────────────────────────────────────────────
    // Query — Active Feed (isDismissed = 0)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Observes the active (non-dismissed) insight feed for a user, latest first.
     *
     * @param userId Local PK of the owner.
     * @return {@link LiveData} containing the list of active insights.
     */
    @Query("SELECT * FROM insights WHERE user_id = :userId AND is_dismissed = 0 ORDER BY created_at DESC")
    LiveData<List<InsightEntity>> observeActiveFeed(long userId);

    /**
     * Observes active insights filtered by category.
     *
     * @param userId   Local PK of the owner.
     * @param category The health domain category.
     * @return {@link LiveData} containing the filtered active insights.
     */
    @Query("SELECT * FROM insights WHERE user_id = :userId AND is_dismissed = 0 AND category = :category ORDER BY created_at DESC")
    LiveData<List<InsightEntity>> observeActiveByCategory(long userId, InsightCategory category);

    /**
     * Fetches a snapshot of the most recent active insights.
     *
     * @param userId Local PK of the owner.
     * @param limit  Maximum number of insights to return.
     * @return {@link Single} emitting the list of insights.
     */
    @Query("SELECT * FROM insights WHERE user_id = :userId AND is_dismissed = 0 ORDER BY created_at DESC LIMIT :limit")
    Single<List<InsightEntity>> getActiveFeedSnapshot(long userId, int limit);

    // ──────────────────────────────────────────────────────────────────────
    // Query — Severity & Alerts
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns a snapshot of all active insights of a specific severity.
     * Used by the notification dispatcher to find CRITICAL / WARNING alerts.
     *
     * @param userId   Local PK of the owner.
     * @param severity Clinical urgency level.
     * @return {@link Single} emitting the matching active insights.
     */
    @Query("SELECT * FROM insights WHERE user_id = :userId AND is_dismissed = 0 AND severity = :severity ORDER BY created_at DESC")
    Single<List<InsightEntity>> getActiveBySeverity(long userId, InsightSeverity severity);

    // ──────────────────────────────────────────────────────────────────────
    // Query — Metrics & State
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Counts unread (is_read = 0) active insights for a user.
     * Drives the notification badge on the UI.
     *
     * @param userId Local PK of the owner.
     * @return {@link LiveData} emitting the unread count.
     */
    @Query("SELECT COUNT(*) FROM insights WHERE user_id = :userId AND is_dismissed = 0 AND is_read = 0")
    LiveData<Integer> observeUnreadCount(long userId);

    /**
     * Fetches all insights related to a specific metric type.
     *
     * @param userId            Local PK of the owner.
     * @param relatedMetricType The metric type string (e.g. "HEART_RATE").
     * @return {@link Single} emitting the matching insights.
     */
    @Query("SELECT * FROM insights WHERE user_id = :userId AND related_metric_type = :relatedMetricType ORDER BY created_at DESC")
    Single<List<InsightEntity>> getByMetricType(long userId, String relatedMetricType);

    // ──────────────────────────────────────────────────────────────────────
    // Update — Interaction State
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates an existing insight record.
     *
     * @param insight The entity with updated fields.
     * @return {@link Completable} indicating operation completion.
     */
    @Update
    Completable update(InsightEntity insight);

    /**
     * Marks all insights as read for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("UPDATE insights SET is_read = 1, updated_at = :timestamp WHERE user_id = :userId AND is_read = 0")
    Completable markAllAsRead(long userId, long timestamp);

    // ──────────────────────────────────────────────────────────────────────
    // Delete & Maintenance
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Deletes a specific insight record.
     *
     * @param insight The entity to delete.
     * @return {@link Completable} indicating operation completion.
     */
    @Delete
    Completable delete(InsightEntity insight);

    /**
     * Deletes all insights for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("DELETE FROM insights WHERE user_id = :userId")
    Completable deleteAllByUserId(long userId);

    /**
     * Automatically dismisses insights that have passed their expiration date.
     *
     * @param now Current epoch millis.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("UPDATE insights SET is_dismissed = 1, updated_at = :now WHERE expires_at IS NOT NULL AND expires_at < :now AND is_dismissed = 0")
    Completable dismissExpired(long now);

    // ──────────────────────────────────────────────────────────────────────
    // Export
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Streams all insights for a user for data export.
     *
     * @param userId Local PK of the owner.
     * @return {@link Observable} emitting individual insight entities.
     */
    @Query("SELECT * FROM insights WHERE user_id = :userId ORDER BY created_at ASC")
    Observable<InsightEntity> streamAllForExport(long userId);
}
