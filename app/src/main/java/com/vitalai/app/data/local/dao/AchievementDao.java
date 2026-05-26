package com.vitalai.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.vitalai.app.data.local.entity.AchievementEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * AchievementDao
 *
 * Room DAO for all operations against the {@code achievements} table.
 *
 * Architecture layer : Data / Local
 * Table              : achievements
 * Entity             : {@link AchievementEntity}
 */
@Dao
public interface AchievementDao {

    // ──────────────────────────────────────────────────────────────────────
    // Insert
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Inserts an achievement.
     * Uses REPLACE strategy — re-awarding an achievement with the same key
     * updates the record (useful for updating progress).
     *
     * @param achievement The entity to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(AchievementEntity achievement);

    /**
     * Inserts a list of achievements.
     *
     * @param achievements List of entities to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<AchievementEntity> achievements);

    // ──────────────────────────────────────────────────────────────────────
    // Query — Trophy Shelf
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Observes all achievements for a user, most recent first.
     *
     * @param userId Local PK of the owner.
     * @return {@link LiveData} containing the trophy shelf.
     */
    @Query("SELECT * FROM achievements WHERE user_id = :userId ORDER BY unlocked_at DESC")
    LiveData<List<AchievementEntity>> observeAll(long userId);

    /**
     * Observes achievements filtered by category.
     *
     * @param userId   Local PK of the owner.
     * @param category The domain category (e.g. "WORKOUT").
     * @return {@link LiveData} containing the filtered trophy shelf.
     */
    @Query("SELECT * FROM achievements WHERE user_id = :userId AND category = :category ORDER BY unlocked_at DESC")
    LiveData<List<AchievementEntity>> observeByCategory(long userId, String category);

    /**
     * Fetches a snapshot of a specific achievement by its stable key.
     *
     * @param userId         Local PK of the owner.
     * @param achievementKey Programmable key (e.g. "FIRST_WORKOUT").
     * @return {@link Single} emitting the achievement entity.
     */
    @Query("SELECT * FROM achievements WHERE user_id = :userId AND achievement_key = :achievementKey LIMIT 1")
    Single<AchievementEntity> getByKey(long userId, String achievementKey);

    /**
     * Checks if the user has already earned a specific achievement.
     *
     * @param userId         Local PK of the owner.
     * @param achievementKey Programmable key.
     * @return {@link Single} emitting true if earned.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM achievements WHERE user_id = :userId AND achievement_key = :achievementKey)")
    Single<Boolean> hasAchievement(long userId, String achievementKey);

    // ──────────────────────────────────────────────────────────────────────
    // Query — Notification State
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Counts achievements that are new (is_new = 1) for a user.
     * Drives the notification badge on the profile/trophy shelf tab.
     *
     * @param userId Local PK of the owner.
     * @return {@link LiveData} emitting the count.
     */
    @Query("SELECT COUNT(*) FROM achievements WHERE user_id = :userId AND is_new = 1")
    LiveData<Integer> observeNewCount(long userId);

    // ──────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates an existing achievement record.
     *
     * @param achievement The entity with updated fields.
     * @return {@link Completable} indicating operation completion.
     */
    @Update
    Completable update(AchievementEntity achievement);

    /**
     * Marks all achievements as seen for a user.
     *
     * @param userId    Local PK of the owner.
     * @param timestamp Update timestamp.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("UPDATE achievements SET is_new = 0, updated_at = :timestamp WHERE user_id = :userId AND is_new = 1")
    Completable markAllAsSeen(long userId, long timestamp);

    // ──────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Deletes a specific achievement record.
     *
     * @param achievement The entity to delete.
     * @return {@link Completable} indicating operation completion.
     */
    @Delete
    Completable delete(AchievementEntity achievement);

    /**
     * Deletes all achievements for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("DELETE FROM achievements WHERE user_id = :userId")
    Completable deleteAllByUserId(long userId);
}
