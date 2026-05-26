package com.vitalai.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.vitalai.app.data.local.entity.MedicationEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * MedicationDao
 *
 * Room DAO for all read and write operations against the {@code medications} table.
 *
 * Architecture layer : Data / Local
 * Table              : medications
 * Entity             : {@link MedicationEntity}
 */
@Dao
public interface MedicationDao {

    // ──────────────────────────────────────────────────────────────────────
    // Insert
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Inserts a single medication record.
     * Replaces on conflict to handle retries.
     *
     * @param medication The entity to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(MedicationEntity medication);

    /**
     * Inserts a list of medications in a single transaction.
     *
     * @param medications List of entities to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<MedicationEntity> medications);

    // ──────────────────────────────────────────────────────────────────────
    // Query
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns a snapshot of all medications for a specific user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Single} emitting the list of medications.
     */
    @Query("SELECT * FROM medications WHERE user_id = :userId ORDER BY name ASC")
    Single<List<MedicationEntity>> getByUserId(long userId);

    /**
     * Observes the list of medications for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link LiveData} containing the list of medications.
     */
    @Query("SELECT * FROM medications WHERE user_id = :userId ORDER BY name ASC")
    LiveData<List<MedicationEntity>> observeByUserId(long userId);

    /**
     * Counts the number of medications recorded for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Single} emitting the count.
     */
    @Query("SELECT COUNT(*) FROM medications WHERE user_id = :userId")
    Single<Integer> countByUserId(long userId);

    // ──────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates an existing medication record.
     *
     * @param medication The entity with updated fields.
     * @return {@link Completable} indicating operation completion.
     */
    @Update
    Completable update(MedicationEntity medication);

    // ──────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Deletes a specific medication record.
     *
     * @param medication The entity to delete.
     * @return {@link Completable} indicating operation completion.
     */
    @Delete
    Completable delete(MedicationEntity medication);

    /**
     * Deletes all medications for a specific user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("DELETE FROM medications WHERE user_id = :userId")
    Completable deleteAllByUserId(long userId);
}
