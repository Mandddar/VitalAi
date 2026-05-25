package com.vitalai.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.vitalai.app.domain.model.enums.DeviceType;

import java.util.Date;

/**
 * DeviceEntity
 *
 * Room entity representing a row in the {@code devices} table.
 * Each row records one paired peripheral or data-source integration
 * belonging to a user — a Bluetooth Low Energy wearable, a smart
 * scale, a blood glucose monitor, a blood pressure cuff, or a
 * third-party platform integration (Android Health Connect, a
 * connected fitness app).
 *
 * Design notes
 * ────────────
 * • Foreign key to {@link UserEntity} uses ON DELETE CASCADE — deleting
 *   a user automatically removes all their paired device records without
 *   any manual Repository cleanup. BLE bond state on the Android OS
 *   level is managed separately by the BLE layer; only the app-level
 *   pairing record is removed by the cascade.
 *
 * • {@code deviceType} maps to {@link DeviceType} — the broad category
 *   of the peripheral (e.g. SMARTWATCH, FITNESS_TRACKER, SMART_SCALE,
 *   BLOOD_PRESSURE_MONITOR, GLUCOSE_MONITOR, PULSE_OXIMETER,
 *   HEALTH_CONNECT). Stored as TEXT via TypeConverters. Drives which
 *   BLE GATT profile or API the data-ingestion layer uses and which
 *   metric types are expected from this device.
 *
 * • {@code macAddress} is the Bluetooth MAC address for BLE peripherals
 *   (format "AA:BB:CC:DD:EE:FF"). For non-BLE integrations (e.g.
 *   Health Connect, third-party app APIs) this field is null and
 *   {@code externalId} carries the platform-specific identifier
 *   instead. A unique index on (user_id, mac_address) prevents the
 *   same physical device from being paired twice by the same user;
 *   nulls are excluded from the uniqueness check (SQLite treats each
 *   NULL as distinct).
 *
 * • {@code externalId} holds the platform-specific identifier for
 *   non-BLE sources:
 *     Health Connect → originating app package name
 *     Garmin Connect → Garmin device serial number
 *     Fitbit API     → Fitbit device ID from the OAuth token response
 *   Nullable — BLE peripherals identified by MAC address omit this.
 *
 * • {@code displayName} is the user-assigned or auto-populated friendly
 *   name shown in the Connected Devices screen. For BLE devices this
 *   is initially set from the GATT Generic Access Profile Device Name
 *   characteristic and can be renamed by the user. For integrations
 *   it is the platform display name (e.g. "Android Health Connect").
 *
 * • {@code manufacturer} and {@code modelName} are populated from the
 *   GATT Device Information Service (DIS) characteristics during the
 *   initial connection handshake (Manufacturer Name String 0x2A29,
 *   Model Number String 0x2A24). Used to display device details in the
 *   UI and to select the correct proprietary data-parsing strategy when
 *   a manufacturer uses non-standard GATT profiles. Both nullable —
 *   not all devices expose DIS characteristics, and platform
 *   integrations have no hardware manufacturer.
 *
 * • {@code firmwareVersion} is read from the GATT Firmware Revision
 *   String characteristic (0x2A26) during pairing and refreshed on
 *   each reconnection. Used to gate features that require a minimum
 *   firmware version and to surface "firmware update available" alerts.
 *   Nullable — not exposed by all peripherals.
 *
 * • {@code batteryLevel} is the last-known battery percentage (0–100)
 *   read from the GATT Battery Service (0x2A19). Updated on every
 *   reconnection and via Battery Level Notifications if the device
 *   supports them. Nullable — not all devices expose battery state;
 *   never available for platform integrations.
 *
 * • {@code isConnected} reflects the current logical connection state
 *   as known to the app — not the OS-level BLE link state, which is
 *   managed by the BLE layer. Updated by the BleConnectionManager
 *   callbacks on connect/disconnect events. Defaults to false at
 *   pairing time; the BLE layer sets it to true once the GATT
 *   connection is established and services are discovered.
 *   SQLite stores as INTEGER (0/1).
 *
 * • {@code isActive} lets the user soft-disable a paired device without
 *   unpairing it (e.g. "I'm not wearing my smartwatch this week").
 *   Inactive devices are excluded from the BLE scan/reconnect loop and
 *   from the metric-ingestion pipeline but remain in the device list so
 *   the user can re-enable without re-pairing. Defaults to true.
 *   SQLite stores as INTEGER (0/1).
 *
 * • {@code isPrimary} flags the device the user has designated as their
 *   main data source for a given {@link DeviceType}. When multiple
 *   devices of the same type are paired (e.g. two heart-rate monitors),
 *   the Repository uses this flag to resolve conflicts: the primary
 *   device's readings take precedence in the METRIC_SOURCE priority
 *   chain. Only one device per (user_id, device_type) combination
 *   should have isPrimary = true; the Repository enforces this by
 *   clearing the flag on all sibling rows before setting it on the
 *   new primary. Defaults to false; set to true when the user has
 *   only one device of a type (single-device auto-promotion logic
 *   lives in the Repository). SQLite stores as INTEGER (0/1).
 *
 * • {@code supportedMetrics} is a comma-separated list of
 *   {@link com.vitalai.app.domain.model.enums.MetricType} name strings
 *   representing the metric types this device can produce. Populated
 *   during the pairing handshake by querying the device's GATT service
 *   UUID list against a known capability registry. Used by the
 *   data-ingestion layer to register the correct GATT characteristic
 *   notification subscriptions and by the UI to display "This device
 *   tracks: Heart Rate, SpO₂, Sleep" in the device detail screen.
 *   Nullable — populated asynchronously after pairing; null until the
 *   capability query completes.
 *   Example: "HEART_RATE,HEART_RATE_VARIABILITY,BLOOD_OXYGEN_SPO2,
 *             STEPS,SLEEP_SCORE"
 *
 * • {@code lastSyncedAt} is the UTC instant of the most recent
 *   successful data sync from this device. Updated by the ingestion
 *   pipeline after each successful batch of metric rows is inserted.
 *   Displayed in the Connected Devices screen as "Last synced: 2 hours
 *   ago". Nullable — null until the first successful sync after pairing.
 *   Stored as epoch millis via TypeConverters.
 *
 * • {@code lastSeenAt} is the UTC instant the device was last detected
 *   in a BLE scan or successfully connected to — even if no new data
 *   was synced. Updated on every GATT connect event regardless of
 *   whether new metric rows were produced. Used by the BLE reconnect
 *   scheduler to prioritise recently-seen devices and by the UI to
 *   display "Last seen: 5 minutes ago" when a sync is pending.
 *   Nullable — null for platform integrations and for devices that
 *   have never been seen since pairing (edge case: user pairs via
 *   QR/NFC without a prior BLE scan).
 *   Stored as epoch millis via TypeConverters.
 *
 * • Four indexes are declared:
 *     1. (user_id, mac_address) UNIQUE   — prevents duplicate pairing
 *        of the same BLE device; nulls excluded from uniqueness check.
 *     2. (user_id, device_type)          — type-filtered device list:
 *        "all heart-rate monitors for user X" for the primary-device
 *        conflict-resolution query and the type-specific settings screen.
 *     3. (user_id, is_active)            — active-device filter: fast
 *        lookup of devices included in the BLE scan/reconnect loop
 *        without a full table scan.
 *     4. (user_id, last_synced_at)       — staleness queries: "devices
 *        for user X that have not synced in the last 24 h" for the
 *        sync-health alert pipeline.
 *
 * Architecture layer : Data / Local
 * Table name         : devices
 * Related DAOs       : DeviceDao
 * Parent FK          : users.id  (CASCADE delete + update)
 */
@Entity(
        tableName = "devices",
        foreignKeys = {
                @ForeignKey(
                        entity        = UserEntity.class,
                        parentColumns = "id",
                        childColumns  = "user_id",
                        onDelete      = ForeignKey.CASCADE,
                        onUpdate      = ForeignKey.CASCADE
                )
        },
        indices = {
                // Uniqueness guard: same BLE device cannot be paired twice
                @Index(value = {"user_id", "mac_address"}, unique = true),
                // Type-filtered device list and primary-device resolution
                @Index(value = {"user_id", "device_type"}),
                // Active-device filter for BLE scan/reconnect loop
                @Index(value = {"user_id", "is_active"}),
                // Staleness queries for sync-health alert pipeline
                @Index(value = {"user_id", "last_synced_at"})
        }
)
public class DeviceEntity {

    // ──────────────────────────────────────────────────────────────────────
    // Primary Key
    // ──────────────────────────────────────────────────────────────────────

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    // ──────────────────────────────────────────────────────────────────────
    // Foreign Key
    // ──────────────────────────────────────────────────────────────────────

    /**
     * References {@link UserEntity#id}.
     * Covered by all four composite indexes declared above.
     */
    @ColumnInfo(name = "user_id")
    public long userId;

    // ──────────────────────────────────────────────────────────────────────
    // Device Identity
    // ──────────────────────────────────────────────────────────────────────

    /**
     * The broad category of this device or integration.
     * Stored as TEXT via TypeConverters.
     *
     * Expected values (defined in {@link DeviceType}):
     *   SMARTWATCH              — full-featured wrist wearable (e.g. Galaxy Watch)
     *   FITNESS_TRACKER         — band-style activity tracker (e.g. Fitbit Charge)
     *   SMART_SCALE             — body composition scale (e.g. Withings Body+)
     *   BLOOD_PRESSURE_MONITOR  — cuff-style BP monitor (e.g. Omron Evolv)
     *   GLUCOSE_MONITOR         — CGM or BGM (e.g. Dexcom G7, Contour)
     *   PULSE_OXIMETER          — fingertip SpO₂ sensor
     *   CHEST_STRAP             — HR chest strap (e.g. Polar H10)
     *   SMART_BOTTLE            — hydration-tracking bottle
     *   HEALTH_CONNECT          — Android Health Connect platform integration
     *   THIRD_PARTY_APP         — other fitness/health app API integration
     *
     * Drives GATT profile selection, metric-type capability mapping,
     * and which detail UI is shown in the Connected Devices screen.
     */
    @NonNull
    @ColumnInfo(name = "device_type")
    public DeviceType deviceType;

    /**
     * Bluetooth MAC address of this BLE peripheral.
     * Format: "AA:BB:CC:DD:EE:FF" (colon-separated uppercase hex).
     *
     * Used by the BLE layer as the stable identifier for GATT
     * reconnection (via {@code BluetoothAdapter.getRemoteDevice(mac)}).
     * The (user_id, mac_address) unique index prevents duplicate
     * pairing records for the same physical device.
     *
     * Nullable — platform integrations (Health Connect, Fitbit API)
     * have no MAC address; {@link #externalId} carries their identifier
     * instead. SQLite treats each NULL as distinct, so the unique index
     * does not prevent multiple integration rows with null mac_address.
     */
    @Nullable
    @ColumnInfo(name = "mac_address")
    public String macAddress;

    /**
     * Platform-specific identifier for non-BLE integrations.
     *
     * Examples:
     *   Health Connect → "com.google.android.apps.healthdata"
     *   Garmin Connect → device serial number from the Connect IQ SDK
     *   Fitbit API     → device ID from the OAuth /devices endpoint
     *   Samsung Health → "com.sec.android.app.shealth"
     *
     * Nullable — BLE peripherals identified by {@link #macAddress}
     * omit this field.
     */
    @Nullable
    @ColumnInfo(name = "external_id")
    public String externalId;

    /**
     * User-assigned or auto-populated friendly name for this device.
     * Displayed in the Connected Devices list and device detail screen.
     *
     * Initial value sources:
     *   BLE    → GATT Generic Access Profile Device Name (0x2A00)
     *   Health Connect → "Android Health Connect"
     *   Other  → manufacturer + model (e.g. "Withings Body+")
     *
     * The user can rename any device in the settings screen; the
     * Repository persists the new name via update().
     */
    @NonNull
    @ColumnInfo(name = "display_name")
    public String displayName;

    // ──────────────────────────────────────────────────────────────────────
    // Hardware Metadata
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Hardware manufacturer name.
     * Read from the GATT Device Information Service (DIS)
     * Manufacturer Name String characteristic (0x2A29) during the
     * initial pairing handshake.
     *
     * Examples: "Polar Electro", "Withings", "Omron Healthcare",
     *           "Garmin", "Samsung Electronics"
     *
     * Used to select proprietary GATT data-parsing strategies when
     * a manufacturer uses non-standard characteristic formats, and
     * displayed in the device detail screen.
     *
     * Nullable — not all BLE devices expose DIS; always null for
     * platform integrations.
     */
    @Nullable
    @ColumnInfo(name = "manufacturer")
    public String manufacturer;

    /**
     * Hardware model identifier.
     * Read from the GATT DIS Model Number String characteristic
     * (0x2A24) during the initial pairing handshake.
     *
     * Examples: "H10", "Body+", "Evolv", "Forerunner 955",
     *           "Galaxy Watch 6"
     *
     * Used alongside {@link #manufacturer} to display device details
     * and to select model-specific data-parsing logic.
     *
     * Nullable — same conditions as {@link #manufacturer}.
     */
    @Nullable
    @ColumnInfo(name = "model_name")
    public String modelName;

    /**
     * Current firmware version string reported by the device.
     * Read from the GATT DIS Firmware Revision String characteristic
     * (0x2A26) during pairing and refreshed on every reconnection.
     *
     * Examples: "2.1.0", "FW_7.3.1-RELEASE", "1.0.4.8"
     *
     * Used to:
     *   • Gate features that require a minimum firmware version.
     *   • Surface "Firmware update available" alerts when a newer
     *     version is known (compared against a remote version manifest).
     *   • Aid support diagnosis when users report data quality issues.
     *
     * Nullable — not all devices expose this characteristic; always
     * null for platform integrations.
     */
    @Nullable
    @ColumnInfo(name = "firmware_version")
    public String firmwareVersion;

    /**
     * Last-known battery level as a percentage (0–100).
     * Read from the GATT Battery Service Battery Level characteristic
     * (0x2A19) on connection. Updated via Battery Level Notifications
     * if the device supports the Battery Service notify property.
     *
     * Displayed in the Connected Devices list as a battery icon.
     * The UI shows a low-battery warning when this value drops below
     * a configurable threshold (default: 20%).
     *
     * Nullable — not all BLE peripherals expose the Battery Service;
     * always null for platform integrations.
     */
    @Nullable
    @ColumnInfo(name = "battery_level")
    public Integer batteryLevel;

    // ──────────────────────────────────────────────────────────────────────
    // Capability Registry
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Comma-separated list of {@link com.vitalai.app.domain.model.enums.MetricType}
     * name strings representing the metric types this device can produce.
     *
     * Populated during the pairing handshake by matching the device's
     * advertised GATT service UUIDs against a known capability registry.
     * For platform integrations, populated by querying the Health Connect
     * permission set or the third-party API's data-type catalogue.
     *
     * Example values:
     *   Polar H10 chest strap:
     *     "HEART_RATE,HEART_RATE_VARIABILITY"
     *   Samsung Galaxy Watch:
     *     "HEART_RATE,HEART_RATE_VARIABILITY,BLOOD_OXYGEN_SPO2,
     *      STEPS,SLEEP_SCORE,STRESS_SCORE,BODY_TEMPERATURE"
     *   Withings Body+:
     *     "WEIGHT,BODY_FAT_PERCENTAGE,BMI"
     *   Omron Evolv BP monitor:
     *     "BLOOD_PRESSURE_SYSTOLIC,BLOOD_PRESSURE_DIASTOLIC,
     *      HEART_RATE"
     *
     * Used by:
     *   • The BLE ingestion layer to register the correct GATT
     *     characteristic notification subscriptions.
     *   • The UI to display "This device tracks: …" in the device
     *     detail screen.
     *   • The Repository to route incoming metric packets to the
     *     correct HealthMetricEntity insert path.
     *
     * Nullable — populated asynchronously after pairing completes;
     * null until the capability query finishes.
     */
    @Nullable
    @ColumnInfo(name = "supported_metrics")
    public String supportedMetrics;

    // ──────────────────────────────────────────────────────────────────────
    // Connection State
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Whether the app currently has an active logical connection to
     * this device.
     *
     * TRUE  — GATT connection established, services discovered, and
     *         the ingestion pipeline is actively receiving data.
     * FALSE — device is not connected (default at pairing time).
     *
     * Updated by BleConnectionManager callbacks:
     *   onConnectionStateChange(CONNECTED)    → set true
     *   onConnectionStateChange(DISCONNECTED) → set false
     *
     * This is the app-layer view of connection state — it may briefly
     * lag the OS-layer BLE link state during the GATT service discovery
     * phase. UI should observe this via LiveData/Flow for real-time
     * connection status indicators.
     *
     * SQLite stores as INTEGER (0 = false, 1 = true).
     */
    @ColumnInfo(name = "is_connected", defaultValue = "0")
    public boolean isConnected;

    /**
     * Whether this device is included in the BLE scan and reconnect
     * loop and the metric-ingestion pipeline.
     *
     * TRUE  (default) — device is active; the BLE scheduler scans
     *   for it and the ingestion pipeline processes its data.
     * FALSE — device is soft-disabled by the user; excluded from all
     *   scan, reconnect, and ingestion activity. The pairing record
     *   is retained so the user can re-enable without re-pairing.
     *
     * The UI shows inactive devices in a separate "Inactive" section
     * of the Connected Devices screen with a "Re-enable" button.
     *
     * SQLite stores as INTEGER (0 = false, 1 = true).
     */
    @ColumnInfo(name = "is_active", defaultValue = "1")
    public boolean isActive;

    /**
     * Whether this is the user's designated primary device for its
     * {@link #deviceType} category.
     *
     * When multiple devices of the same type are paired (e.g. two
     * HR monitors), the Repository uses this flag to resolve metric
     * conflicts: the primary device's readings are treated as the
     * authoritative source in the METRIC_SOURCE priority chain.
     *
     * Invariant: at most one row per (user_id, device_type) should
     * have isPrimary = true. The Repository enforces this by executing:
     *   UPDATE devices SET is_primary = 0
     *   WHERE user_id = :uid AND device_type = :type AND id != :newPrimaryId
     * before setting is_primary = 1 on the target row.
     *
     * Defaults to false; the Repository applies single-device
     * auto-promotion (set to true when it is the only active device
     * of its type for the user) as part of the pairing flow.
     *
     * SQLite stores as INTEGER (0 = false, 1 = true).
     */
    @ColumnInfo(name = "is_primary", defaultValue = "0")
    public boolean isPrimary;

    // ──────────────────────────────────────────────────────────────────────
    // Sync Timestamps
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant of the most recent successful data sync from this
     * device — i.e. the last time at least one new {@link HealthMetricEntity}
     * row was inserted from this device's data stream.
     *
     * Displayed as "Last synced: X ago" in the Connected Devices screen.
     * Used by the sync-health alert pipeline to identify stale devices:
     *   SELECT * FROM devices
     *   WHERE user_id = :uid
     *     AND is_active = 1
     *     AND (last_synced_at IS NULL
     *          OR last_synced_at < :staleness_threshold)
     *
     * Nullable — null until the first successful data sync after pairing.
     * Stored as epoch millis via TypeConverters.
     */
    @Nullable
    @ColumnInfo(name = "last_synced_at")
    public Date lastSyncedAt;

    /**
     * UTC instant the device was last detected in a BLE scan or
     * successfully connected to, regardless of whether new data was
     * produced.
     *
     * Updated on every GATT {@code onConnectionStateChange(CONNECTED)}
     * event and on every BLE scan result that matches this device's
     * MAC address, even if the app does not initiate a full connection.
     *
     * Displayed as "Last seen: X ago" when a sync is pending but the
     * device is in range. Used by the BLE reconnect scheduler to
     * prioritise recently-seen devices when multiple devices are
     * competing for connection slots.
     *
     * Nullable — null for platform integrations and for newly-paired
     * devices not yet detected in a scan.
     * Stored as epoch millis via TypeConverters.
     */
    @Nullable
    @ColumnInfo(name = "last_seen_at")
    public Date lastSeenAt;

    // ──────────────────────────────────────────────────────────────────────
    // Timestamps
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant when this pairing record was first written to the
     * local database. Set once at insert time; never modified thereafter.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "created_at")
    public Date createdAt;

    /**
     * UTC instant of the most recent update to this row.
     * Updated whenever connection state, battery level, firmware
     * version, sync timestamps, active/primary flags, or display name
     * are modified. Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "updated_at")
    public Date updatedAt;

    // ──────────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Full constructor used by the Repository when inserting a new device
     * pairing record. Room uses direct field assignment when reading rows
     * back from the DB.
     *
     * @param userId           Local PK of the owning {@link UserEntity}.
     * @param deviceType       Peripheral category (required).
     * @param macAddress       BLE MAC address, or null for integrations.
     * @param externalId       Platform-specific ID, or null for BLE.
     * @param displayName      Friendly name shown in the UI (required).
     * @param manufacturer     Hardware manufacturer name, or null.
     * @param modelName        Hardware model identifier, or null.
     * @param firmwareVersion  Current firmware version string, or null.
     * @param batteryLevel     Last-known battery percentage, or null.
     * @param supportedMetrics Comma-separated MetricType names, or null.
     * @param isConnected      Current logical connection state.
     * @param isActive         Whether included in scan/ingestion loop.
     * @param isPrimary        Whether designated primary for its type.
     * @param lastSyncedAt     Last successful data-sync instant, or null.
     * @param lastSeenAt       Last BLE detection/connect instant, or null.
     * @param createdAt        Insert timestamp (set by Repository).
     * @param updatedAt        Last-update timestamp (set by Repository).
     */
    public DeviceEntity(
            long userId,
            @NonNull DeviceType deviceType,
            @Nullable String macAddress,
            @Nullable String externalId,
            @NonNull String displayName,
            @Nullable String manufacturer,
            @Nullable String modelName,
            @Nullable String firmwareVersion,
            @Nullable Integer batteryLevel,
            @Nullable String supportedMetrics,
            boolean isConnected,
            boolean isActive,
            boolean isPrimary,
            @Nullable Date lastSyncedAt,
            @Nullable Date lastSeenAt,
            @NonNull Date createdAt,
            @NonNull Date updatedAt) {

        this.userId            = userId;
        this.deviceType        = deviceType;
        this.macAddress        = macAddress;
        this.externalId        = externalId;
        this.displayName       = displayName;
        this.manufacturer      = manufacturer;
        this.modelName         = modelName;
        this.firmwareVersion   = firmwareVersion;
        this.batteryLevel      = batteryLevel;
        this.supportedMetrics  = supportedMetrics;
        this.isConnected       = isConnected;
        this.isActive          = isActive;
        this.isPrimary         = isPrimary;
        this.lastSyncedAt      = lastSyncedAt;
        this.lastSeenAt        = lastSeenAt;
        this.createdAt         = createdAt;
        this.updatedAt         = updatedAt;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Convenience factories
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Factory for pairing a new BLE peripheral. {@code macAddress} is
     * required; {@code externalId} is null. Hardware metadata fields
     * ({@code manufacturer}, {@code modelName}, {@code firmwareVersion},
     * {@code batteryLevel}, {@code supportedMetrics}) are null at pairing
     * time and populated asynchronously by the GATT handshake worker.
     * {@code isConnected} starts false; the BLE layer sets it true once
     * GATT services are discovered. {@code isActive} and {@code isPrimary}
     * default to true — single-device auto-promotion applies.
     * Timestamps set to now.
     *
     * @param userId      Local PK of the owning user.
     * @param deviceType  Peripheral category.
     * @param macAddress  BLE MAC address (required for this factory).
     * @param displayName Initial friendly name from GATT Device Name.
     * @return Ready-to-insert {@link DeviceEntity}.
     */
    public static DeviceEntity createBle(
            long userId,
            @NonNull DeviceType deviceType,
            @NonNull String macAddress,
            @NonNull String displayName) {

        Date now = new Date();
        return new DeviceEntity(
                userId, deviceType, macAddress, null,
                displayName, null, null, null, null, null,
                false, true, true,
                null, null, now, now);
    }

    /**
     * Factory for registering a platform integration (Health Connect,
     * Fitbit API, Garmin Connect). {@code externalId} carries the
     * platform identifier; {@code macAddress} is null. All hardware
     * metadata fields are null (no physical peripheral). {@code isActive}
     * and {@code isPrimary} default to true. {@code isConnected} is set
     * to true immediately — integrations are considered always-connected
     * once registered (actual API availability is checked at sync time).
     * Timestamps set to now.
     *
     * @param userId      Local PK of the owning user.
     * @param deviceType  Integration category (e.g. HEALTH_CONNECT).
     * @param externalId  Platform-specific identifier.
     * @param displayName Human-readable integration name.
     * @return Ready-to-insert {@link DeviceEntity}.
     */
    public static DeviceEntity createIntegration(
            long userId,
            @NonNull DeviceType deviceType,
            @NonNull String externalId,
            @NonNull String displayName) {

        Date now = new Date();
        return new DeviceEntity(
                userId, deviceType, null, externalId,
                displayName, null, null, null, null, null,
                true, true, true,
                null, null, now, now);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates the hardware metadata fields populated during the GATT
     * Device Information Service handshake and stamps {@link #updatedAt}.
     * Call this in the Repository after the GATT DIS characteristics
     * are read, then persist via {@code DeviceDao.update()}.
     *
     * @param manufacturer    Manufacturer Name String (0x2A29), or null.
     * @param modelName       Model Number String (0x2A24), or null.
     * @param firmwareVersion Firmware Revision String (0x2A26), or null.
     */
    public void applyHardwareInfo(
            @Nullable String manufacturer,
            @Nullable String modelName,
            @Nullable String firmwareVersion) {
        this.manufacturer    = manufacturer;
        this.modelName       = modelName;
        this.firmwareVersion = firmwareVersion;
        this.updatedAt       = new Date();
    }

    /**
     * Updates the supported metrics capability list and stamps
     * {@link #updatedAt}. Call this in the Repository after the GATT
     * service UUID list has been matched against the capability registry,
     * then persist via {@code DeviceDao.update()}.
     *
     * @param supportedMetrics Comma-separated MetricType name strings.
     */
    public void applyCapabilities(@NonNull String supportedMetrics) {
        this.supportedMetrics = supportedMetrics;
        this.updatedAt        = new Date();
    }

    /**
     * Marks the device as connected and records the detection instant
     * in {@link #lastSeenAt}. Stamps {@link #updatedAt}.
     * Call this in the Repository from the BleConnectionManager's
     * {@code onConnectionStateChange(CONNECTED)} callback, then persist
     * via {@code DeviceDao.update()}.
     */
    public void markConnected() {
        Date now          = new Date();
        this.isConnected  = true;
        this.lastSeenAt   = now;
        this.updatedAt    = now;
    }

    /**
     * Marks the device as disconnected and stamps {@link #updatedAt}.
     * Call this in the Repository from the BleConnectionManager's
     * {@code onConnectionStateChange(DISCONNECTED)} callback, then
     * persist via {@code DeviceDao.update()}.
     */
    public void markDisconnected() {
        this.isConnected = false;
        this.updatedAt   = new Date();
    }

    /**
     * Records a successful data sync by updating {@link #lastSyncedAt}
     * and {@link #lastSeenAt} to now, then stamps {@link #updatedAt}.
     * Call this in the Repository after a batch of metric rows from
     * this device has been successfully inserted into {@code health_metrics},
     * then persist via {@code DeviceDao.update()}.
     */
    public void recordSync() {
        Date now          = new Date();
        this.lastSyncedAt = now;
        this.lastSeenAt   = now;
        this.updatedAt    = now;
    }

    /**
     * Updates the battery level and stamps {@link #updatedAt}.
     * Call this in the Repository when a Battery Level Notification
     * arrives or when the Battery Level characteristic is read on
     * connection, then persist via {@code DeviceDao.update()}.
     *
     * @param level Battery percentage in the range [0, 100].
     */
    public void updateBattery(int level) {
        this.batteryLevel = Math.min(100, Math.max(0, level));
        this.updatedAt    = new Date();
    }

    /**
     * Soft-disables this device, excluding it from the BLE scan and
     * ingestion pipeline, and stamps {@link #updatedAt}. Also clears
     * {@link #isConnected} — a disabled device cannot be active.
     * Call this in the Repository when the user taps "Disable" in
     * the Connected Devices screen, then persist via
     * {@code DeviceDao.update()}.
     */
    public void disable() {
        this.isActive    = false;
        this.isConnected = false;
        this.updatedAt   = new Date();
    }

    /**
     * Re-enables a soft-disabled device, returning it to the BLE scan
     * and ingestion pipeline, and stamps {@link #updatedAt}.
     * Call this in the Repository when the user taps "Re-enable" in
     * the Connected Devices screen, then persist via
     * {@code DeviceDao.update()}.
     */
    public void enable() {
        this.isActive  = true;
        this.updatedAt = new Date();
    }

    /**
     * Refreshes {@code updated_at} to the current instant.
     * Call this in the Repository before any partial update via
     * {@code DeviceDao.update()}.
     */
    public void touch() {
        this.updatedAt = new Date();
    }

    @Override
    public String toString() {
        return "DeviceEntity{"
                + "id=" + id
                + ", userId=" + userId
                + ", deviceType=" + deviceType
                + ", macAddress='" + macAddress + '\''
                + ", externalId='" + externalId + '\''
                + ", displayName='" + displayName + '\''
                + ", manufacturer='" + manufacturer + '\''
                + ", modelName='" + modelName + '\''
                + ", firmwareVersion='" + firmwareVersion + '\''
                + ", batteryLevel=" + batteryLevel
                + ", isConnected=" + isConnected
                + ", isActive=" + isActive
                + ", isPrimary=" + isPrimary
                + ", lastSyncedAt=" + lastSyncedAt
                + ", lastSeenAt=" + lastSeenAt
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + '}';
    }
}