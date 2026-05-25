package com.vitalai.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.vitalai.app.data.local.converter.Converters;
import com.vitalai.app.domain.model.enums.ActivityLevel;
import com.vitalai.app.domain.model.enums.BloodType;
import com.vitalai.app.domain.model.enums.PrimaryGoal;
import com.vitalai.app.domain.model.enums.Sex;
import com.vitalai.app.domain.model.enums.SleepPreference;

import java.util.Date;

/**
 * UserEntity
 *
 * Room entity representing a row in the {@code users} table.
 *
 * Design notes
 * ────────────
 * • {@code firebase_uid} carries a UNIQUE index — it is the stable
 *   cross-device identity used when syncing with Firestore. The local
 *   auto-generated {@code id} is used as the FK target for all child
 *   tables (health_conditions, medications, health_metrics, etc.) to
 *   keep joins cheap and integer-typed.
 *
 * • {@code email} also has a UNIQUE index to prevent duplicate local
 *   accounts and to support "find by email" queries without a full scan.
 *
 * • All enum fields (sex, blood_type, activity_level, sleep_preference,
 *   primary_goal) are stored as TEXT via {@link Converters}.
 *   This keeps them human-readable in DB-Browser and safe against enum
 *   value reordering.
 *
 * • Physical measurements use SI units throughout:
 *     height → centimetres (height_cm)
 *     weight → kilograms   (weight_kg)
 *   Conversion to imperial is a UI-layer concern only.
 *
 * • {@code created_at} is set once at insert time and never updated.
 *   {@code updated_at} must be refreshed by the DAO/Repository on every
 *   write — see {@code UserDao.update()}.
 *
 * • Nullable fields use {@link Nullable}; mandatory fields use {@link NonNull}.
 *   Room respects these annotations when generating the schema.
 *
 * Architecture layer : Data / Local
 * Table name         : users
 * Related DAOs       : UserDao
 * Related FKs        : health_conditions.user_id, medications.user_id,
 *                      health_metrics.user_id, sleep_sessions.user_id,
 *                      workouts.user_id, insights.user_id,
 *                      chat_messages.user_id, goals.user_id,
 *                      achievements.user_id, hydration_logs.user_id,
 *                      mood_logs.user_id, devices.user_id,
 *                      user_baselines.user_id,
 *                      notification_preferences.user_id
 */
@Entity(
        tableName = "users",
        indices = {
                @Index(value = {"firebase_uid"}, unique = true),
                @Index(value = {"email"},        unique = true)
        }
)
public class UserEntity {
    // KSP Cache Invalidation Trigger: Forces regeneration of UserDao_Impl

    // ──────────────────────────────────────────────────────────────────────
    // Primary Key
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Local auto-generated integer primary key.
     * Used as the FK target in all child tables.
     * Do NOT confuse with {@code firebaseUid} — they serve different roles.
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id;

    // ──────────────────────────────────────────────────────────────────────
    // Identity & Authentication
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Firebase Authentication UID.
     * Nullable only during the brief window between local account creation
     * and the first successful Firebase sign-in. Must be populated before
     * any Firestore sync is attempted.
     */
    @Nullable
    @ColumnInfo(name = "firebase_uid")
    public String firebaseUid;

    @NonNull
    @ColumnInfo(name = "name")
    public String name;

    @NonNull
    @ColumnInfo(name = "email")
    public String email;

    // ──────────────────────────────────────────────────────────────────────
    // Profile — Demographics
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Date of birth. Stored as epoch millis via {@link Converters#fromDate}.
     * Age is always computed at query time — never stored — to avoid
     * the record going stale.
     */
    @Nullable
    @ColumnInfo(name = "dob")
    public Date dob;

    /**
     * Biological sex.
     * Stored as TEXT (enum name) via TypeConverters.
     * Values: MALE | FEMALE | OTHER | PREFER_NOT_TO_SAY
     */
    @Nullable
    @ColumnInfo(name = "sex")
    public Sex sex;

    // ──────────────────────────────────────────────────────────────────────
    // Profile — Physical Measurements (SI units)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Height in centimetres. Stored as REAL for sub-cm precision.
     * Range validation (e.g. 50–300 cm) is enforced in the domain layer.
     */
    @Nullable
    @ColumnInfo(name = "height_cm")
    public Float heightCm;

    /**
     * Body weight in kilograms. Updated whenever a new measurement is logged;
     * historical weights are tracked in {@code health_metrics}.
     */
    @Nullable
    @ColumnInfo(name = "weight_kg")
    public Float weightKg;

    /**
     * ABO/Rh blood type.
     * Values: A_POSITIVE | A_NEGATIVE | B_POSITIVE | B_NEGATIVE |
     *         AB_POSITIVE | AB_NEGATIVE | O_POSITIVE | O_NEGATIVE | UNKNOWN
     */
    @Nullable
    @ColumnInfo(name = "blood_type")
    public BloodType bloodType;

    // ──────────────────────────────────────────────────────────────────────
    // Profile — Health Preferences
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Self-reported physical activity level.
     * Used by the AI layer to contextualise metric anomalies and
     * personalise recommendations.
     * Values: SEDENTARY | LIGHTLY_ACTIVE | MODERATELY_ACTIVE |
     *         VERY_ACTIVE | EXTREMELY_ACTIVE
     */
    @Nullable
    @ColumnInfo(name = "activity_level")
    public ActivityLevel activityLevel;

    /**
     * Chronotype / sleep preference.
     * Used to align sleep coaching recommendations with the user's
     * natural rhythm.
     * Values: EARLY_BIRD | NIGHT_OWL | FLEXIBLE
     */
    @Nullable
    @ColumnInfo(name = "sleep_preference")
    public SleepPreference sleepPreference;

    /**
     * The user's primary health goal driving their use of VitalAI.
     * Influences which insights and AI nudges are surfaced first.
     * Values: LOSE_WEIGHT | GAIN_MUSCLE | IMPROVE_SLEEP | REDUCE_STRESS |
     *         MANAGE_CONDITION | GENERAL_WELLNESS | CUSTOM
     */
    @Nullable
    @ColumnInfo(name = "primary_goal")
    public PrimaryGoal primaryGoal;

    // ──────────────────────────────────────────────────────────────────────
    // Profile — Media
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Remote URL of the user's avatar image (Firebase Storage or CDN).
     * Loaded via Glide with a local disk cache. Null until the user
     * uploads a photo.
     */
    @Nullable
    @ColumnInfo(name = "avatar_url")
    public String avatarUrl;

    // ──────────────────────────────────────────────────────────────────────
    // Timestamps
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC timestamp of when this user record was first created locally.
     * Set once at insert; never modified thereafter.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "created_at")
    public Date createdAt;

    /**
     * UTC timestamp of the most recent local update to this record.
     * The Repository must call {@code System.currentTimeMillis()} and
     * set this field on every update operation.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "updated_at")
    public Date updatedAt;

    // ──────────────────────────────────────────────────────────────────────
    // Constructors
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Full constructor used by the Repository when creating a new user.
     * Room also uses the no-arg path (field assignment) when reading from DB.
     */
    public UserEntity(
            @NonNull String name,
            @NonNull String email,
            @NonNull Date createdAt,
            @NonNull Date updatedAt) {
        this.name      = name;
        this.email     = email;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Convenience factory
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Factory method for creating a brand-new user with timestamps
     * automatically set to "now". Use this in the Repository layer
     * rather than calling the constructor directly.
     *
     * @param name  Display name (required).
     * @param email Email address (required; must be unique in the table).
     * @return A fully initialised {@link UserEntity} ready for insertion.
     */
    public static UserEntity create(@NonNull String name, @NonNull String email) {
        Date now = new Date();
        return new UserEntity(name, email, now, now);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Stamps {@code updated_at} with the current time.
     * Call this in the Repository before passing the entity to
     * {@code UserDao.update()}.
     */
    public void touch() {
        this.updatedAt = new Date();
    }

    @Override
    public String toString() {
        return "UserEntity{"
                + "id=" + id
                + ", firebaseUid='" + firebaseUid + '\''
                + ", name='" + name + '\''
                + ", email='" + email + '\''
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + '}';
    }
}