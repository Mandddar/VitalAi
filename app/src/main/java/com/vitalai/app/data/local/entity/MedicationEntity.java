package com.vitalai.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.vitalai.app.data.local.converter.Converters;

import java.util.Date;

/**
 * MedicationEntity
 *
 * Room entity representing a row in the {@code medications} table.
 * Each row records one medication currently or previously taken by a user.
 *
 * Design notes
 * ────────────
 * • Foreign key to {@link UserEntity} uses ON DELETE CASCADE — deleting a
 *   user automatically removes all their medication records without any
 *   manual Repository cleanup.
 *
 * • {@code affectsHeartRate} and {@code affectsBloodPressure} are boolean
 *   flags used by the AI anomaly-detection layer to suppress false-positive
 *   alerts. For example, a beta-blocker legitimately lowers resting heart
 *   rate; the model must know this before flagging bradycardia. These flags
 *   are set by the user or pre-populated from a future medication database
 *   lookup.
 *
 * • {@code frequency} is free-form TEXT ("Once daily", "Twice daily",
 *   "Every 8 hours", "As needed") rather than an enum — prescription
 *   schedules are too varied and locale-dependent for a closed value set.
 *   The reminder/WorkManager layer parses this into a schedule separately.
 *
 * • {@code dosage} is stored as a plain String (e.g. "10mg", "500 mg",
 *   "1 tablet") because dosage includes both a magnitude and a unit that
 *   vary widely per medication class. Splitting into numeric + unit columns
 *   would complicate free-entry UX without meaningful query benefit.
 *
 * • Index on {@code user_id} alone — all queries are
 *   "all medications for user X"; no cross-user or type-filtered patterns
 *   exist for this table.
 *
 * Architecture layer : Data / Local
 * Table name         : medications
 * Related DAOs       : MedicationDao
 * Parent FK          : users.id  (CASCADE delete + update)
 */
@Entity(
        tableName = "medications",
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
                @Index(value = {"user_id"})
        }
)
public class MedicationEntity {

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
     * Indexed via the class-level {@code @Index} above.
     */
    @ColumnInfo(name = "user_id")
    public long userId;

    // ──────────────────────────────────────────────────────────────────────
    // Medication Data
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Brand or generic name of the medication.
     * Examples: "Metoprolol", "Lisinopril 10mg", "Aspirin".
     * Free-form — no constraint — to accommodate brand names, generics,
     * supplements, and over-the-counter drugs equally.
     */
    @NonNull
    @ColumnInfo(name = "name")
    public String name;

    /**
     * Prescribed dose as a human-readable string including unit.
     * Examples: "10 mg", "500 mg", "1 tablet", "5 mL".
     *
     * Nullable — the user may log a medication name before confirming
     * the dosage (e.g. "I take metformin but I need to check the dose").
     */
    @Nullable
    @ColumnInfo(name = "dosage")
    public String dosage;

    /**
     * How often the medication is taken.
     * Examples: "Once daily", "Twice daily", "Every 8 hours", "As needed",
     *           "Weekly", "With meals".
     *
     * Nullable — dosage timing may be unknown or variable at entry time.
     * The notification/WorkManager scheduling layer should handle null
     * frequency gracefully (no reminder scheduled).
     */
    @Nullable
    @ColumnInfo(name = "frequency")
    public String frequency;

    // ──────────────────────────────────────────────────────────────────────
    // AI Contextualisation Flags
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Whether this medication is known to affect heart rate.
     *
     * TRUE examples  : beta-blockers (metoprolol, atenolol),
     *                  stimulants (methylphenidate), thyroid hormones.
     * FALSE (default): most antibiotics, statins, antacids.
     *
     * Used by the TFLite anomaly-detection model to widen the expected
     * HR range before raising an alert, and by the AI chat layer to
     * contextualise HR-related insights.
     *
     * SQLite stores as INTEGER (0 = false, 1 = true).
     */
    @ColumnInfo(name = "affects_hr", defaultValue = "0")
    public boolean affectsHeartRate;

    /**
     * Whether this medication is known to affect blood pressure.
     *
     * TRUE examples  : ACE inhibitors (lisinopril), ARBs (losartan),
     *                  calcium channel blockers (amlodipine),
     *                  diuretics (hydrochlorothiazide), NSAIDs.
     * FALSE (default): most antihistamines, proton pump inhibitors.
     *
     * Used identically to {@link #affectsHeartRate} but for BP metrics
     * (BLOOD_PRESSURE_SYSTOLIC / BLOOD_PRESSURE_DIASTOLIC).
     *
     * SQLite stores as INTEGER (0 = false, 1 = true).
     */
    @ColumnInfo(name = "affects_bp", defaultValue = "0")
    public boolean affectsBloodPressure;

    // ──────────────────────────────────────────────────────────────────────
    // Timestamps
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant when this row was first written to the local database.
     * Set once at insert time; never modified thereafter.
     * Stored as epoch millis via
     * {@link Converters#fromDate}.
     */
    @NonNull
    @ColumnInfo(name = "created_at")
    public Date createdAt;

    /**
     * UTC instant of the most recent update to this row.
     * Must be refreshed by the Repository on every write operation.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "updated_at")
    public Date updatedAt;

    // ──────────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Full constructor used by the Repository when inserting a new
     * medication record. Room uses direct field assignment when reading
     * rows back from the DB.
     *
     * @param userId             Local PK of the owning {@link UserEntity}.
     * @param name               Medication name (required).
     * @param dosage             Dosage string, or null if unknown.
     * @param frequency          Dosing frequency string, or null if unknown.
     * @param affectsHeartRate   True if this medication influences HR.
     * @param affectsBloodPressure True if this medication influences BP.
     * @param createdAt          Insert timestamp (set by Repository).
     * @param updatedAt          Last-update timestamp (set by Repository).
     */
    public MedicationEntity(
            long userId,
            @NonNull String name,
            @Nullable String dosage,
            @Nullable String frequency,
            boolean affectsHeartRate,
            boolean affectsBloodPressure,
            @NonNull Date createdAt,
            @NonNull Date updatedAt) {

        this.userId               = userId;
        this.name                 = name;
        this.dosage               = dosage;
        this.frequency            = frequency;
        this.affectsHeartRate     = affectsHeartRate;
        this.affectsBloodPressure = affectsBloodPressure;
        this.createdAt            = createdAt;
        this.updatedAt            = updatedAt;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Convenience factories
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Factory for a fully specified medication entry.
     * Timestamps are set to now automatically.
     *
     * @param userId               Local PK of the owning user.
     * @param name                 Medication name (required).
     * @param dosage               Dosage string (nullable).
     * @param frequency            Frequency string (nullable).
     * @param affectsHeartRate     Whether the med affects HR.
     * @param affectsBloodPressure Whether the med affects BP.
     * @return Ready-to-insert {@link MedicationEntity}.
     */
    public static MedicationEntity create(
            long userId,
            @NonNull String name,
            @Nullable String dosage,
            @Nullable String frequency,
            boolean affectsHeartRate,
            boolean affectsBloodPressure) {

        Date now = new Date();
        return new MedicationEntity(
                userId, name, dosage, frequency,
                affectsHeartRate, affectsBloodPressure, now, now);
    }

    /**
     * Minimal factory — records a medication name only.
     * Dosage, frequency, and AI flags can be filled in later via update.
     * Both {@code affectsHeartRate} and {@code affectsBloodPressure}
     * default to {@code false}; update them once the user or the
     * medication-lookup service confirms the drug class.
     *
     * @param userId Local PK of the owning user.
     * @param name   Medication name (required).
     * @return Ready-to-insert {@link MedicationEntity}.
     */
    public static MedicationEntity createMinimal(long userId, @NonNull String name) {
        return create(userId, name, null, null, false, false);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Refreshes {@code updated_at} to the current instant.
     * Call this in the Repository before passing the entity to
     * {@code MedicationDao.update()}.
     */
    public void touch() {
        this.updatedAt = new Date();
    }

    @Override
    public String toString() {
        return "MedicationEntity{"
                + "id=" + id
                + ", userId=" + userId
                + ", name='" + name + '\''
                + ", dosage='" + dosage + '\''
                + ", frequency='" + frequency + '\''
                + ", affectsHeartRate=" + affectsHeartRate
                + ", affectsBloodPressure=" + affectsBloodPressure
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + '}';
    }
}