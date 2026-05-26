package com.vitalai.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.vitalai.app.data.local.entity.NotificationPreferenceEntity;
import com.vitalai.app.domain.model.enums.AlertType;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * NotificationPreferenceDao
 *
 * Room DAO for all read and write operations against the {@code notification_preferences} table.
 *
 * Architecture layer : Data / Local
 * Table              : notification_preferences
 * Entity             : {@link NotificationPreferenceEntity}
 */
@Dao
public interface NotificationPreferenceDao {

    // ──────────────────────────────────────────────────────────────────────
    // Insert / Upsert
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Inserts a notification preference or replaces it if a record for the 
     * same category already exists for the user.
     *
     * @param preference The entity to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(NotificationPreferenceEntity preference);

    /**
     * Inserts a list of notification preferences in a single transaction.
     * Used during onboarding to bootstrap default settings.
     *
     * @param preferences List of entities to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<NotificationPreferenceEntity> preferences);

    // ──────────────────────────────────────────────────────────────────────
    // Query — Settings UI & Routing
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Observes all notification preferences for a user.
     * Drives the Notification Settings screen.
     *
     * @param userId Local PK of the owner.
     * @return {@link LiveData} containing the list of preferences.
     */
    @Query("SELECT * FROM notification_preferences WHERE user_id = :userId ORDER BY alert_type ASC")
    LiveData<List<NotificationPreferenceEntity>> observeAll(long userId);

    /**
     * Fetches the specific preference for a given alert type.
     * Consulted by the notification router before firing an alert.
     *
     * @param userId    Local PK of the owner.
     * @param alertType The category of notification (e.g. ANOMALY_HEART_RATE).
     * @return {@link Single} emitting the preference record.
     */
    @Query("SELECT * FROM notification_preferences WHERE user_id = :userId AND alert_type = :alertType LIMIT 1")
    Single<NotificationPreferenceEntity> getByType(long userId, AlertType alertType);

    /**
     * Observes the preference for a specific alert type.
     *
     * @param userId    Local PK of the owner.
     * @param alertType The category of notification.
     * @return {@link LiveData} containing the preference.
     */
    @Query("SELECT * FROM notification_preferences WHERE user_id = :userId AND alert_type = :alertType LIMIT 1")
    LiveData<NotificationPreferenceEntity> observeByType(long userId, AlertType alertType);

    /**
     * Fetches all enabled notification categories for a user.
     * Used by the WorkManager scheduler to bootstrap periodic jobs.
     *
     * @param userId Local PK of the owner.
     * @return {@link Single} emitting the list of enabled preferences.
     */
    @Query("SELECT * FROM notification_preferences WHERE user_id = :userId AND is_enabled = 1")
    Single<List<NotificationPreferenceEntity>> getEnabledPreferences(long userId);

    // ──────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates an existing preference record (e.g. toggling push/in-app channels).
     *
     * @param preference The entity with updated fields.
     * @return {@link Completable} indicating operation completion.
     */
    @Update
    Completable update(NotificationPreferenceEntity preference);

    // ──────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Deletes a specific preference record.
     *
     * @param preference The entity to delete.
     * @return {@link Completable} indicating operation completion.
     */
    @Delete
    Completable delete(NotificationPreferenceEntity preference);

    /**
     * Removes all notification preferences for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("DELETE FROM notification_preferences WHERE user_id = :userId")
    Completable deleteAllByUserId(long userId);
}
