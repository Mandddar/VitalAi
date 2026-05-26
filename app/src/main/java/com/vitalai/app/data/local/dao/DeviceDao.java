package com.vitalai.app.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.vitalai.app.data.local.entity.DeviceEntity;
import com.vitalai.app.domain.model.enums.DeviceType;

import java.util.List;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;

/**
 * DeviceDao
 *
 * Room DAO for managing paired peripherals and platform integrations
 * in the {@code devices} table.
 *
 * Architecture layer : Data / Local
 * Table              : devices
 * Entity             : {@link DeviceEntity}
 */
@Dao
public interface DeviceDao {

    // ──────────────────────────────────────────────────────────────────────
    // Insert
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Pairs a new device or updates an existing pairing record.
     *
     * @param device The entity to persist.
     * @return {@link Completable} indicating operation completion.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    Completable insert(DeviceEntity device);

    // ──────────────────────────────────────────────────────────────────────
    // Query — Device Management
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Observes all paired devices for a user.
     * Drives the "Connected Devices" list UI.
     *
     * @param userId Local PK of the owner.
     * @return {@link LiveData} emitting the device list.
     */
    @Query("SELECT * FROM devices WHERE user_id = :userId ORDER BY is_active DESC, display_name ASC")
    LiveData<List<DeviceEntity>> observeAll(long userId);

    /**
     * Fetches a snapshot of all active devices for the BLE reconnect loop.
     *
     * @param userId Local PK of the owner.
     * @return {@link Single} emitting the list of active devices.
     */
    @Query("SELECT * FROM devices WHERE user_id = :userId AND is_active = 1")
    Single<List<DeviceEntity>> getActiveDevices(long userId);

    /**
     * Fetches a specific BLE device by its MAC address.
     *
     * @param userId     Local PK of the owner.
     * @param macAddress Bluetooth MAC string.
     * @return {@link Single} emitting the device or an error if not found.
     */
    @Query("SELECT * FROM devices WHERE user_id = :userId AND mac_address = :macAddress LIMIT 1")
    Single<DeviceEntity> getByMacAddress(long userId, String macAddress);

    /**
     * Fetches a platform integration by its external ID (e.g. package name).
     *
     * @param userId     Local PK of the owner.
     * @param externalId Platform-specific identifier.
     * @return {@link Single} emitting the integration or an error if not found.
     */
    @Query("SELECT * FROM devices WHERE user_id = :userId AND external_id = :externalId LIMIT 1")
    Single<DeviceEntity> getByExternalId(long userId, String externalId);

    /**
     * Returns the user's primary (authoritative) device for a given type.
     *
     * @param userId Local PK of the owner.
     * @param type   The category (e.g. SMARTWATCH).
     * @return {@link Single} emitting the primary device.
     */
    @Query("SELECT * FROM devices WHERE user_id = :userId AND device_type = :type AND is_primary = 1 LIMIT 1")
    Single<DeviceEntity> getPrimaryDevice(long userId, DeviceType type);

    // ──────────────────────────────────────────────────────────────────────
    // Update
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates an existing device record (e.g. connection state, battery, name).
     *
     * @param device The entity with updated fields.
     * @return {@link Completable} indicating operation completion.
     */
    @Update
    Completable update(DeviceEntity device);

    /**
     * Clears the primary flag for all devices of a certain type for a user.
     * Used as a cleanup step before promoting a new device to primary.
     *
     * @param userId Local PK of the owner.
     * @param type   The device category.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("UPDATE devices SET is_primary = 0 WHERE user_id = :userId AND device_type = :type")
    Completable clearPrimaryStatus(long userId, DeviceType type);

    // ──────────────────────────────────────────────────────────────────────
    // Delete
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Unpairs a specific device.
     *
     * @param device The entity to delete.
     * @return {@link Completable} indicating operation completion.
     */
    @Delete
    Completable delete(DeviceEntity device);

    /**
     * Removes all device records for a user.
     *
     * @param userId Local PK of the owner.
     * @return {@link Completable} indicating operation completion.
     */
    @Query("DELETE FROM devices WHERE user_id = :userId")
    Completable deleteAllByUserId(long userId);
}
