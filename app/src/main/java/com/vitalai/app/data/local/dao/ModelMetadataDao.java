package com.vitalai.app.data.local.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.vitalai.app.data.local.entity.ModelMetadataEntity;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * ModelMetadataDao
 *
 * Room DAO for managing AI/ML model metadata in the {@code model_metadata} table.
 *
 * Architecture layer : Data / Local
 * Table              : model_metadata
 * Entity             : {@link ModelMetadataEntity}
 */
@Dao
public interface ModelMetadataDao {

    // ──────────────────────────────────────────────────────────────────────
    // Insert
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Registers a new model version.
     *
     * @param metadata The entity to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(ModelMetadataEntity metadata);

    // ──────────────────────────────────────────────────────────────────────
    // Query — Inference & Management
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Fetches the currently active model for a specific role and user.
     * The primary query used by the inference layer.
     *
     * @param userId   Local PK of the owner.
     * @param modelKey Functional role identifier (e.g. "anomaly_detection").
     * @return {@link Single} emitting the active model metadata.
     */
    @Query("SELECT * FROM model_metadata WHERE user_id = :userId AND model_key = :modelKey AND is_active = 1 LIMIT 1")
    Single<ModelMetadataEntity> getActiveModel(long userId, String modelKey);

    /**
     * Fetches a specific model version for a role and user.
     *
     * @param userId       Local PK of the owner.
     * @param modelKey     Functional role identifier.
     * @param modelVersion Specific version string.
     * @return {@link Single} emitting the model metadata.
     */
    @Query("SELECT * FROM model_metadata WHERE user_id = :userId AND model_key = :modelKey AND model_version = :modelVersion LIMIT 1")
    Single<ModelMetadataEntity> getVersion(long userId, String modelKey, String modelVersion);

    /**
     * Returns the full deployment history for a specific model role.
     *
     * @param userId   Local PK of the owner.
     * @param modelKey Functional role identifier.
     * @return {@link Single} emitting the list of versions, latest activation first.
     */
    @Query("SELECT * FROM model_metadata WHERE user_id = :userId AND model_key = :modelKey ORDER BY activated_at DESC")
    Single<List<ModelMetadataEntity>> getHistory(long userId, String modelKey);

    /**
     * Returns all registered TFLite models for a user.
     * Used by the storage cleanup worker to audit files on disk.
     *
     * @param userId Local PK of the owner.
     * @return {@link Single} emitting the list of TFLite models.
     */
    @Query("SELECT * FROM model_metadata WHERE user_id = :userId AND model_type = 'TFLITE_ON_DEVICE'")
    Single<List<ModelMetadataEntity>> getTfLiteModels(long userId);

    // ──────────────────────────────────────────────────────────────────────
    // Update & Lifecycle
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates an existing model metadata record.
     *
     * @param metadata The entity with updated fields.
     * @return {@link Completable} indicating operation completion.
     */
    @Update
    Completable update(ModelMetadataEntity metadata);

    /**
     * Deactivates all models for a specific role for a user.
     * Used as a cleanup step before promoting a new version to active.
     *
     * @param userId   Local PK of the owner.
     * @param modelKey Functional role identifier.
     * @param timestamp Deprecation timestamp.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("UPDATE model_metadata SET is_active = 0, deprecated_at = :timestamp WHERE user_id = :userId AND model_key = :modelKey AND is_active = 1")
    Completable deactivateRole(long userId, String modelKey, long timestamp);

    // ──────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Deletes a specific model metadata record.
     *
     * @param metadata The entity to delete.
     * @return {@link Completable} indicating operation completion.
     */
    @Delete
    Completable delete(ModelMetadataEntity metadata);

    /**
     * Removes all model metadata for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("DELETE FROM model_metadata WHERE user_id = :userId")
    Completable deleteAllByUserId(long userId);
}
