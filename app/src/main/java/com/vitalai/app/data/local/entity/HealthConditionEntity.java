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
 * HealthConditionEntity
 *
 * Room entity representing a row in the {@code health_conditions} table.
 * Each row records one diagnosed medical condition belonging to a user.
 *
 * Design notes
 * ────────────
 * • Foreign key to {@link UserEntity} uses ON DELETE CASCADE — deleting a
 *   user automatically removes all their health conditions, preserving
 *   referential integrity without manual Repository cleanup.
 *
 * • {@code severity} is stored as plain TEXT rather than an enum because
 *   condition severity is clinically fluid (mild → moderate → severe →
 *   remission) and may need free-form values from external data sources
 *   (e.g. HL7/FHIR imports). A CHECK constraint would be the right place
 *   to enforce a controlled vocabulary if needed in a future migration.
 *   Typical values: "MILD" | "MODERATE" | "SEVERE" | "IN_REMISSION"
 *
 * • {@code diagnosedDate} is nullable — the user may know they have a
 *   condition without knowing the exact diagnosis date, or it may be
 *   entered later.
 *
 * • This table is read by the AI inference layer to contextualise anomaly
 *   detection (e.g. a known arrhythmia suppresses certain heart-rate
 *   alerts) and by the medication interaction checker.
 *
 * • Index on {@code user_id} alone is sufficient here — queries are
 *   always "all conditions for user X", never cross-user or type-filtered.
 *
 * Architecture layer : Data / Local
 * Table name         : health_conditions
 * Related DAOs       : HealthConditionDao
 * Parent FK          : users.id  (CASCADE delete + update)
 */
@Entity(
        tableName = "health_conditions",
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
public class HealthConditionEntity {

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
    // Condition Data
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Human-readable name of the diagnosed condition.
     * Examples: "Type 2 Diabetes", "Hypertension", "Atrial Fibrillation".
     * Free-form string — no enum constraint — to accommodate the full
     * breadth of ICD-10 diagnoses and user-entered conditions.
     */
    @NonNull
    @ColumnInfo(name = "condition_name")
    public String conditionName;

    /**
     * Clinical severity of the condition at the time of last update.
     *
     * Recommended values (not enforced at DB level):
     *   "MILD"         — manageable, minimal impact on daily life
     *   "MODERATE"     — requires active management / medication
     *   "SEVERE"       — significantly impacts daily functioning
     *   "IN_REMISSION" — previously active, currently not symptomatic
     *   "CHRONIC"      — long-term, stable but ongoing
     *
     * Nullable — severity may be unknown at the time of data entry
     * (e.g. newly added condition pending clinical review).
     */
    @Nullable
    @ColumnInfo(name = "severity")
    public String severity;

    /**
     * Date the condition was formally diagnosed.
     * Stored as epoch millis via
     * {@link Converters#fromDate}.
     *
     * Nullable — the user may know they have a condition but not recall
     * or know the exact diagnosis date.
     */
    @Nullable
    @ColumnInfo(name = "diagnosed_date")
    public Date diagnosedDate;

    // ──────────────────────────────────────────────────────────────────────
    // Timestamps
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant when this row was first written to the local database.
     * Set once at insert time and never modified thereafter.
     * Stored as epoch millis via TypeConverters.
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
     * Full constructor used by the Repository when inserting a new condition.
     * Room uses direct field assignment when reading rows back from the DB.
     *
     * @param userId        Local PK of the owning {@link UserEntity}.
     * @param conditionName Name of the diagnosed condition (required).
     * @param severity      Severity descriptor, or null if unknown.
     * @param diagnosedDate Date of diagnosis, or null if unknown.
     * @param createdAt     Insert timestamp (set by Repository).
     * @param updatedAt     Last-update timestamp (set by Repository).
     */
    public HealthConditionEntity(
            long userId,
            @NonNull String conditionName,
            @Nullable String severity,
            @Nullable Date diagnosedDate,
            @NonNull Date createdAt,
            @NonNull Date updatedAt) {

        this.userId        = userId;
        this.conditionName = conditionName;
        this.severity      = severity;
        this.diagnosedDate = diagnosedDate;
        this.createdAt     = createdAt;
        this.updatedAt     = updatedAt;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Convenience factories
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Factory for a fully specified condition with all fields known.
     * Timestamps are set to now automatically.
     *
     * @param userId        Local PK of the owning user.
     * @param conditionName Condition name (required).
     * @param severity      Severity string (nullable).
     * @param diagnosedDate Date of diagnosis (nullable).
     * @return Ready-to-insert {@link HealthConditionEntity}.
     */
    public static HealthConditionEntity create(
            long userId,
            @NonNull String conditionName,
            @Nullable String severity,
            @Nullable Date diagnosedDate) {

        Date now = new Date();
        return new HealthConditionEntity(
                userId, conditionName, severity, diagnosedDate, now, now);
    }

    /**
     * Minimal factory for quickly recording a condition name without
     * severity or diagnosis date — both can be filled in later via update.
     *
     * @param userId        Local PK of the owning user.
     * @param conditionName Condition name (required).
     * @return Ready-to-insert {@link HealthConditionEntity}.
     */
    public static HealthConditionEntity createMinimal(
            long userId,
            @NonNull String conditionName) {

        return create(userId, conditionName, null, null);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Refreshes {@code updated_at} to the current instant.
     * Call this in the Repository before passing the entity to
     * {@code HealthConditionDao.update()}.
     */
    public void touch() {
        this.updatedAt = new Date();
    }

    @Override
    public String toString() {
        return "HealthConditionEntity{"
                + "id=" + id
                + ", userId=" + userId
                + ", conditionName='" + conditionName + '\''
                + ", severity='" + severity + '\''
                + ", diagnosedDate=" + diagnosedDate
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + '}';
    }
}