package com.vitalai.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.vitalai.app.data.local.entity.WorkoutEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

/**
 * WorkoutDao
 *
 * Room DAO for all read and write operations against the {@code workouts} table.
 *
 * Architecture layer : Data / Local
 * Table              : workouts
 * Entity             : {@link WorkoutEntity}
 */
@Dao
public interface WorkoutDao {

    // ──────────────────────────────────────────────────────────────────────
    // Insert
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Inserts a single workout record.
     *
     * @param workout The entity to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(WorkoutEntity workout);

    /**
     * Inserts a list of workout records in a single transaction.
     *
     * @param workouts List of entities to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insertAll(List<WorkoutEntity> workouts);

    // ──────────────────────────────────────────────────────────────────────
    // Query
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns a snapshot of all workouts for a specific user, newest first.
     *
     * @param userId Local PK of the owner.
     * @return {@link Single} emitting the list of workouts.
     */
    @Query("SELECT * FROM workouts WHERE user_id = :userId ORDER BY start_time DESC")
    Single<List<WorkoutEntity>> getByUserId(long userId);

    /**
     * Observes the list of workouts for a user, newest first.
     *
     * @param userId Local PK of the owner.
     * @return {@link LiveData} containing the list of workouts.
     */
    @Query("SELECT * FROM workouts WHERE user_id = :userId ORDER BY start_time DESC")
    LiveData<List<WorkoutEntity>> observeByUserId(long userId);

    /**
     * Counts the total number of workouts for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Single} emitting the count.
     */
    @Query("SELECT COUNT(*) FROM workouts WHERE user_id = :userId")
    Single<Integer> countByUserId(long userId);

    /**
     * Fetches the single most recent workout for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Single} emitting the latest workout.
     */
    @Query("SELECT * FROM workouts WHERE user_id = :userId ORDER BY start_time DESC LIMIT 1")
    Single<WorkoutEntity> getLatestWorkout(long userId);

    /**
     * Fetches all workouts of a specific type for a user, newest first.
     *
     * @param userId      Local PK of the owner.
     * @param workoutType The modality string (e.g. "Running").
     * @return {@link Single} emitting the matching workouts.
     */
    @Query("SELECT * FROM workouts WHERE user_id = :userId AND workout_type = :workoutType ORDER BY start_time DESC")
    Single<List<WorkoutEntity>> getByUserIdAndType(long userId, String workoutType);

    /**
     * Streams all workout records for a user, ordered chronologically.
     * Used for data export.
     *
     * @param userId Local PK of the owner.
     * @return {@link Observable} emitting individual workout entities.
     */
    @Query("SELECT * FROM workouts WHERE user_id = :userId ORDER BY start_time ASC")
    Observable<WorkoutEntity> streamAllForExport(long userId);

    // ──────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates an existing workout record.
     *
     * @param workout The entity with updated fields.
     * @return {@link Completable} indicating operation completion.
     */
    @Update
    Completable update(WorkoutEntity workout);

    // ──────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Deletes a specific workout record.
     *
     * @param workout The entity to delete.
     * @return {@link Completable} indicating operation completion.
     */
    @Delete
    Completable delete(WorkoutEntity workout);

    /**
     * Deletes all workouts for a specific user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("DELETE FROM workouts WHERE user_id = :userId")
    Completable deleteAllByUserId(long userId);
}
