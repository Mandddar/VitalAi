package com.vitalai.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.vitalai.app.data.local.entity.HealthConditionEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * HealthConditionDao
 *
 * Room DAO for all read and write operations against the {@code health_conditions} table.
 *
 * Return-type strategy
 * ────────────────────
 * • {@link Completable} — Fire-and-forget writes (insert, update, delete).
 * • {@link Single}      — One-shot snapshot reads (batch fetch, counts).
 * • {@link LiveData}    — Reactive reads for UI observation (auto-refreshing lists).
 *
 * Architecture layer : Data / Local
 * Table              : health_conditions
 * Entity             : {@link HealthConditionEntity}
 */
@Dao
public interface HealthConditionDao {

    // ──────────────────────────────────────────────────────────────────────
    // Insert
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Inserts a single health condition record.
     * Replaces on conflict to handle retries / sync collisions.
     *
     * @param condition The entity to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(HealthConditionEntity condition);

    /**
     * Inserts a list of conditions in a single transaction.
     * Useful for initial onboarding or data sync.
     *
     * @param conditions List of entities to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<HealthConditionEntity> conditions);

    // ──────────────────────────────────────────────────────────────────────
    // Query
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns a snapshot of all conditions for a specific user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Single} emitting the list of conditions.
     */
    @Query("SELECT * FROM health_conditions WHERE user_id = :userId ORDER BY condition_name ASC")
    Single<List<HealthConditionEntity>> getByUserId(long userId);

    /**
     * Observes the list of conditions for a user.
     * Emits a new list whenever the user's conditions change in the DB.
     *
     * @param userId Local PK of the owner.
     * @return {@link LiveData} containing the list of conditions.
     */
    @Query("SELECT * FROM health_conditions WHERE user_id = :userId ORDER BY condition_name ASC")
    LiveData<List<HealthConditionEntity>> observeByUserId(long userId);

    /**
     * Counts the number of conditions recorded for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Single} emitting the count.
     */
    @Query("SELECT COUNT(*) FROM health_conditions WHERE user_id = :userId")
    Single<Integer> countByUserId(long userId);

    // ──────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates an existing condition record (matched by PK).
     *
     * @param condition The entity with updated fields.
     * @return {@link Completable} indicating operation completion.
     */
    @Update
    Completable update(HealthConditionEntity condition);

    // ──────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Deletes a specific condition record.
     *
     * @param condition The entity to delete.
     * @return {@link Completable} indicating operation completion.
     */
    @Delete
    Completable delete(HealthConditionEntity condition);

    /**
     * Deletes all conditions for a specific user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("DELETE FROM health_conditions WHERE user_id = :userId")
    Completable deleteAllByUserId(long userId);
}
