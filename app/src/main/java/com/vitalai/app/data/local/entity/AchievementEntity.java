package com.vitalai.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * AchievementEntity
 *
 * Room entity representing a row in the {@code achievements} table.
 * Each row records one achievement unlocked by a user — a milestone
 * awarded when a health goal is reached, a streak is maintained, a
 * personal best is set, or an onboarding step is completed.
 *
 * Design notes
 * ────────────
 * • Foreign key to {@link UserEntity} uses ON DELETE CASCADE — deleting
 *   a user automatically removes all their achievement records without
 *   any manual Repository cleanup.
 *
 * • {@code achievementKey} is a stable, programmer-defined identifier
 *   (e.g. "FIRST_WORKOUT", "STEP_STREAK_7", "HEART_RATE_GOAL_MET")
 *   that is unique per user. The Repository enforces this via the
 *   (user_id, achievement_key) unique index — re-awarding the same
 *   achievement is idempotent (insert-or-ignore). New achievement types
 *   are added by extending the key vocabulary in the domain layer;
 *   no DB migration is required.
 *
 * • {@code category} is a free-form TEXT discriminator grouping
 *   achievements by domain for the trophy shelf UI. Examples:
 *   "WORKOUT", "SLEEP", "STREAK", "NUTRITION", "GOAL", "ONBOARDING".
 *   Stored as TEXT rather than an enum to keep the category set open-
 *   ended without requiring migrations when new domains are added.
 *
 * • {@code tier} encodes the achievement's prestige level as a plain
 *   TEXT label. Recommended values: "BRONZE", "SILVER", "GOLD",
 *   "PLATINUM". Nullable — some achievements are one-time events with
 *   no tier (e.g. "Completed onboarding").
 *
 * • {@code title} and {@code description} are the human-readable
 *   content of the achievement card. {@code title} is the short
 *   headline (≤ 60 chars); {@code description} is the full context
 *   shown in the detail view.
 *
 * • {@code iconResName} is the Android drawable resource name
 *   (e.g. "ic_achievement_steps_gold") resolved at runtime via
 *   {@code Resources.getIdentifier()}. Storing the name rather than
 *   the resource ID keeps the DB portable across APK builds where
 *   R.drawable IDs may be reassigned. Nullable — falls back to a
 *   default achievement icon in the UI.
 *
 * • {@code unlockedAt} is the UTC instant the achievement was first
 *   awarded. Set once by the Repository; never modified. This is the
 *   definitive "earned on" timestamp shown in the trophy shelf and
 *   used as the sort key for the achievement history list.
 *
 * • {@code linkedGoalId} is an optional soft FK to the {@code goals}
 *   table row that triggered this achievement (e.g. completing a
 *   METRIC_TARGET goal awards a "Goal Crusher" achievement). A typed
 *   Room FK is avoided to prevent cascade-delete side-effects —
 *   deleting a goal must not remove the achievement earned by reaching
 *   it. The Repository resolves the reference at read time.
 *   Nullable — achievements triggered by streaks, onboarding, or
 *   personal bests have no linked goal.
 *
 * • {@code isNew} flags achievements that have been earned but not yet
 *   seen by the user. Drives the "new achievement" badge on the trophy
 *   shelf tab and the celebratory unlock animation on first view.
 *   Set to true at insert; cleared by the Repository when the user
 *   opens the achievement detail. Defaults to true (INTEGER 1).
 *
 * • {@code progressCurrent} and {@code progressTarget} support
 *   in-progress "near miss" achievements surfaced in the UI before
 *   they are fully unlocked (e.g. "7-day streak — 5/7 days"). Both
 *   are nullable — fully awarded achievements with no incremental
 *   progress tracking omit these fields. When both are non-null,
 *   the UI renders a progress bar beneath the locked achievement card.
 *   A {@code progressCurrent >= progressTarget} condition indicates the
 *   achievement is ready to be awarded; the Repository transitions it
 *   by setting {@code unlockedAt} and clearing the progress fields.
 *
 * • Three indexes are declared:
 *     1. (user_id, achievement_key) UNIQUE — prevents duplicate awards
 *        and serves the "has user earned X?" existence check.
 *     2. (user_id, category)                — trophy shelf category tabs:
 *        "all WORKOUT achievements for user X".
 *     3. (user_id, unlocked_at)             — chronological achievement
 *        history and "most recent unlock" queries.
 *
 * Architecture layer : Data / Local
 * Table name         : achievements
 * Related DAOs       : AchievementDao
 * Parent FK          : users.id  (CASCADE delete + update)
 */
@Entity(
        tableName = "achievements",
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
                // Uniqueness guard + fast "has user earned X?" lookup
                @Index(value = {"user_id", "achievement_key"}, unique = true),
                // Trophy shelf category-tab filtering
                @Index(value = {"user_id", "category"}),
                // Chronological achievement history
                @Index(value = {"user_id", "unlocked_at"})
        }
)
public class AchievementEntity {

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
     * Covered by all three composite indexes declared above.
     */
    @ColumnInfo(name = "user_id")
    public long userId;

    // ──────────────────────────────────────────────────────────────────────
    // Identity
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Stable programmer-defined key identifying the achievement type.
     * Unique per user — enforced by the (user_id, achievement_key) index.
     *
     * Naming convention: SCREAMING_SNAKE_CASE, domain-prefixed.
     * Examples:
     *   "ONBOARDING_COMPLETE"     — finished the onboarding flow
     *   "WORKOUT_FIRST"           — logged their first workout
     *   "STEP_STREAK_7"           — 7 consecutive days hitting step goal
     *   "STEP_STREAK_30"          — 30 consecutive days hitting step goal
     *   "SLEEP_SCORE_90"          — achieved a sleep score of 90+
     *   "HEART_RATE_GOAL_MET"     — completed a resting HR goal
     *   "WEIGHT_LOSS_5KG"         — lost 5 kg from their starting weight
     *   "HYDRATION_STREAK_7"      — 7 days meeting hydration target
     *   "PERSONAL_BEST_5K"        — new personal best 5 K time
     */
    @NonNull
    @ColumnInfo(name = "achievement_key")
    public String achievementKey;

    // ──────────────────────────────────────────────────────────────────────
    // Classification
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Domain category used to group achievements on the trophy shelf.
     * Free-form TEXT — open-ended to allow new categories without
     * requiring a DB migration.
     *
     * Recommended values:
     *   "WORKOUT"     — exercise-related milestones
     *   "SLEEP"       — sleep quality and consistency milestones
     *   "STREAK"      — consecutive-day habit achievements
     *   "NUTRITION"   — hydration and diet milestones
     *   "GOAL"        — health goal completions
     *   "VITALS"      — heart rate, BP, and other metric milestones
     *   "ONBOARDING"  — app setup and first-use milestones
     */
    @NonNull
    @ColumnInfo(name = "category")
    public String category;

    /**
     * Prestige tier of the achievement.
     *
     * Recommended values: "BRONZE", "SILVER", "GOLD", "PLATINUM"
     *
     * Nullable — one-time milestone achievements (e.g. "Completed
     * onboarding", "First workout") carry no tier designation.
     * Tiered achievements typically exist in escalating variants
     * (e.g. STEP_STREAK_7 = BRONZE, STEP_STREAK_30 = SILVER,
     * STEP_STREAK_100 = GOLD).
     */
    @Nullable
    @ColumnInfo(name = "tier")
    public String tier;

    // ──────────────────────────────────────────────────────────────────────
    // Display Content
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Short headline shown on the achievement card and unlock notification.
     * Should be ≤ 60 characters.
     * Examples:
     *   "First Workout!"
     *   "7-Day Step Streak"
     *   "Sleep Score Champion"
     *   "5 kg Down — Goal Crushed"
     */
    @NonNull
    @ColumnInfo(name = "title")
    public String title;

    /**
     * Full description of what the achievement represents and how it was
     * earned. Shown in the achievement detail view beneath the icon.
     * Examples:
     *   "You logged your very first workout. Every journey starts
     *    with a single step — or rep!"
     *   "You hit your daily step goal 7 days in a row. Consistency
     *    is the foundation of every great result."
     */
    @NonNull
    @ColumnInfo(name = "description")
    public String description;

    /**
     * Android drawable resource name for the achievement badge icon.
     * Resolved at runtime via {@code Resources.getIdentifier(name, "drawable", packageName)}.
     * Storing the name rather than the R.drawable integer keeps the DB
     * portable across builds where resource IDs may be reassigned.
     *
     * Examples: "ic_achievement_workout_bronze",
     *           "ic_achievement_streak_gold",
     *           "ic_achievement_onboarding"
     *
     * Nullable — falls back to a generic achievement icon in the UI
     * when null.
     */
    @Nullable
    @ColumnInfo(name = "icon_res_name")
    public String iconResName;

    // ──────────────────────────────────────────────────────────────────────
    // Unlock State
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant when this achievement was awarded to the user.
     *
     * Non-null — the row is only inserted once the achievement is fully
     * earned. In-progress "near miss" achievements are represented by
     * rows where {@link #progressCurrent} and {@link #progressTarget}
     * are populated but {@code unlockedAt} is the insert time (the row
     * represents a locked achievement being tracked, not yet awarded).
     *
     * The Repository must treat a row as "locked" when
     * {@code progressTarget != null} and
     * {@code progressCurrent < progressTarget}, and as "unlocked" once
     * {@code progressCurrent >= progressTarget} (or immediately on
     * insert for achievements with no incremental progress).
     *
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "unlocked_at")
    public Date unlockedAt;

    /**
     * Whether the user has seen this achievement since it was unlocked.
     * Defaults to true (new, unseen) at insert time.
     * Cleared to false by the Repository when the user opens the
     * achievement detail view or dismisses the unlock notification.
     * Drives the "new achievement" badge on the trophy shelf tab.
     *
     * SQLite stores as INTEGER (1 = new/unseen, 0 = seen).
     */
    @ColumnInfo(name = "is_new", defaultValue = "1")
    public boolean isNew;

    // ──────────────────────────────────────────────────────────────────────
    // Incremental Progress (for locked / in-progress achievements)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Current progress toward unlocking this achievement.
     * Used only for achievements with incremental progress tracking
     * (e.g. a 7-day streak shows 5/7 before being awarded).
     *
     * Examples:
     *   5.0 — "5 of 7 consecutive days" for STEP_STREAK_7
     *   3.2 — "3.2 of 5.0 kg lost" for WEIGHT_LOSS_5KG
     *
     * Nullable — achievements with no incremental tracking (e.g.
     * "First Workout") omit this field; they are inserted directly
     * as fully unlocked rows.
     *
     * Updated by the Repository each time a relevant metric or goal
     * event fires. When {@code progressCurrent >= progressTarget},
     * the Repository marks the achievement as fully unlocked.
     */
    @Nullable
    @ColumnInfo(name = "progress_current")
    public Double progressCurrent;

    /**
     * Total progress value required to fully unlock this achievement.
     * Paired with {@link #progressCurrent} to render the progress bar
     * beneath locked achievement cards.
     *
     * Examples:
     *   7.0 — 7 consecutive days required for STEP_STREAK_7
     *   5.0 — 5 kg required for WEIGHT_LOSS_5KG
     *
     * Nullable — null when {@link #progressCurrent} is also null
     * (no incremental tracking needed).
     */
    @Nullable
    @ColumnInfo(name = "progress_target")
    public Double progressTarget;

    // ──────────────────────────────────────────────────────────────────────
    // Provenance
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Optional soft reference to the {@code goals} table row whose
     * completion triggered this achievement.
     *
     * A typed Room FK is intentionally avoided: deleting a goal must
     * not cascade-delete the achievement earned by completing it —
     * the trophy is a permanent record of the user's success.
     * The Repository resolves the reference at read time for the
     * "View goal" deep-link in the achievement detail view.
     *
     * Nullable — achievements triggered by streaks, personal bests,
     * onboarding steps, or metric thresholds have no linked goal.
     */
    @Nullable
    @ColumnInfo(name = "linked_goal_id")
    public Long linkedGoalId;

    // ──────────────────────────────────────────────────────────────────────
    // Timestamps
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant when this row was first written to the local database.
     * Set once at insert time; never modified thereafter.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "created_at")
    public Date createdAt;

    /**
     * UTC instant of the most recent update to this row.
     * Updated when {@link #isNew} is cleared, or when
     * {@link #progressCurrent} advances toward {@link #progressTarget}.
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
     * achievement. Room uses direct field assignment when reading rows
     * back from the DB.
     *
     * @param userId           Local PK of the owning {@link UserEntity}.
     * @param achievementKey   Stable programmatic key (required).
     * @param category         Domain category string (required).
     * @param tier             Prestige tier label, or null.
     * @param title            Short card headline (required).
     * @param description      Full detail description (required).
     * @param iconResName      Drawable resource name, or null.
     * @param unlockedAt       Award instant (required).
     * @param isNew            True if the user has not yet seen this award.
     * @param progressCurrent  Current progress value, or null.
     * @param progressTarget   Target progress value, or null.
     * @param linkedGoalId     PK of the triggering goal row, or null.
     * @param createdAt        Insert timestamp (set by Repository).
     * @param updatedAt        Last-update timestamp (set by Repository).
     */
    public AchievementEntity(
            long userId,
            @NonNull String achievementKey,
            @NonNull String category,
            @Nullable String tier,
            @NonNull String title,
            @NonNull String description,
            @Nullable String iconResName,
            @NonNull Date unlockedAt,
            boolean isNew,
            @Nullable Double progressCurrent,
            @Nullable Double progressTarget,
            @Nullable Long linkedGoalId,
            @NonNull Date createdAt,
            @NonNull Date updatedAt) {

        this.userId           = userId;
        this.achievementKey   = achievementKey;
        this.category         = category;
        this.tier             = tier;
        this.title            = title;
        this.description      = description;
        this.iconResName      = iconResName;
        this.unlockedAt       = unlockedAt;
        this.isNew            = isNew;
        this.progressCurrent  = progressCurrent;
        this.progressTarget   = progressTarget;
        this.linkedGoalId     = linkedGoalId;
        this.createdAt        = createdAt;
        this.updatedAt        = updatedAt;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Convenience factories
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Factory for a fully unlocked achievement with no incremental
     * progress tracking — the standard path for one-time milestone
     * awards (e.g. "First Workout", "Onboarding Complete").
     * {@code isNew} defaults to true; timestamps set to now.
     *
     * @param userId         Local PK of the owning user.
     * @param achievementKey Stable programmatic key.
     * @param category       Domain category string.
     * @param tier           Prestige tier label, or null.
     * @param title          Short card headline.
     * @param description    Full detail description.
     * @param iconResName    Drawable resource name, or null.
     * @param linkedGoalId   Triggering goal PK, or null.
     * @return Ready-to-insert {@link AchievementEntity}.
     */
    public static AchievementEntity createUnlocked(
            long userId,
            @NonNull String achievementKey,
            @NonNull String category,
            @Nullable String tier,
            @NonNull String title,
            @NonNull String description,
            @Nullable String iconResName,
            @Nullable Long linkedGoalId) {

        Date now = new Date();
        return new AchievementEntity(
                userId, achievementKey, category, tier,
                title, description, iconResName,
                now, true, null, null,
                linkedGoalId, now, now);
    }

    /**
     * Factory for an in-progress (locked) achievement that surfaces a
     * progress bar in the UI before it is fully awarded — e.g. tracking
     * a 7-day streak where the user is on day 3.
     *
     * The row is inserted immediately so the UI can show progress; the
     * Repository calls {@link #advanceProgress(double)} each time a
     * relevant event fires, and {@link #unlock()} when the target is met.
     * {@code isNew} is false until the achievement is fully unlocked —
     * in-progress rows do not trigger the unseen badge.
     * Timestamps set to now.
     *
     * @param userId           Local PK of the owning user.
     * @param achievementKey   Stable programmatic key.
     * @param category         Domain category string.
     * @param tier             Prestige tier label, or null.
     * @param title            Short card headline.
     * @param description      Full detail description.
     * @param iconResName      Drawable resource name, or null.
     * @param progressCurrent  Starting progress value (e.g. 3.0).
     * @param progressTarget   Progress required to unlock (e.g. 7.0).
     * @return Ready-to-insert {@link AchievementEntity}.
     */
    public static AchievementEntity createInProgress(
            long userId,
            @NonNull String achievementKey,
            @NonNull String category,
            @Nullable String tier,
            @NonNull String title,
            @NonNull String description,
            @Nullable String iconResName,
            double progressCurrent,
            double progressTarget) {

        Date now = new Date();
        return new AchievementEntity(
                userId, achievementKey, category, tier,
                title, description, iconResName,
                now, false, progressCurrent, progressTarget,
                null, now, now);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Advances the incremental progress counter and checks whether the
     * achievement has been fully earned. If {@code progressCurrent}
     * reaches or exceeds {@code progressTarget}, calls {@link #unlock()}
     * automatically. Stamps {@link #updatedAt}.
     *
     * Call this in the Repository each time a relevant streak tick,
     * metric reading, or goal event fires for an in-progress achievement,
     * then persist via {@code AchievementDao.update()}.
     *
     * No-op if {@link #progressTarget} is null (not a tracked achievement).
     *
     * @param newCurrent The updated progress value.
     */
    public void advanceProgress(double newCurrent) {
        this.progressCurrent = newCurrent;
        this.updatedAt       = new Date();

        if (progressTarget != null && newCurrent >= progressTarget) {
            unlock();
        }
    }

    /**
     * Marks this achievement as fully unlocked: sets {@link #isNew} to
     * true (triggers the unseen badge) and stamps {@link #unlockedAt}
     * and {@link #updatedAt} to now.
     *
     * Called automatically by {@link #advanceProgress(double)} when the
     * target is met, or directly by the Repository for one-time awards
     * that do not use incremental progress. Safe to call multiple times —
     * subsequent calls are no-ops once {@code isNew} is already true.
     */
    public void unlock() {
        Date now        = new Date();
        this.isNew      = true;
        this.unlockedAt = now;
        this.updatedAt  = now;
    }

    /**
     * Clears the {@link #isNew} flag and stamps {@link #updatedAt}.
     * Call this in the Repository when the user opens the achievement
     * detail view or dismisses the unlock notification, then persist
     * via {@code AchievementDao.update()}.
     */
    public void markSeen() {
        this.isNew     = false;
        this.updatedAt = new Date();
    }

    /**
     * Returns the unlock progress as a fraction in [0.0, 1.0], clamped
     * to 1.0 on completion. Returns 1.0 if there is no incremental
     * progress target (achievement is always fully awarded on insert).
     * Returns 0.0 if {@link #progressCurrent} is null.
     */
    public float progressFraction() {
        if (progressTarget == null || progressTarget == 0) return 1f;
        if (progressCurrent == null) return 0f;
        return (float) Math.min(1.0, Math.max(0.0, progressCurrent / progressTarget));
    }

    /**
     * Returns true if this row represents a fully unlocked achievement
     * (either no progress tracking, or current has reached target).
     */
    public boolean isUnlocked() {
        if (progressTarget == null) return true;
        return progressCurrent != null && progressCurrent >= progressTarget;
    }

    /**
     * Refreshes {@code updated_at} to the current instant.
     * Call this in the Repository before any partial update via
     * {@code AchievementDao.update()}.
     */
    public void touch() {
        this.updatedAt = new Date();
    }

    @Override
    public String toString() {
        return "AchievementEntity{"
                + "id=" + id
                + ", userId=" + userId
                + ", achievementKey='" + achievementKey + '\''
                + ", category='" + category + '\''
                + ", tier='" + tier + '\''
                + ", title='" + title + '\''
                + ", unlockedAt=" + unlockedAt
                + ", isNew=" + isNew
                + ", progressCurrent=" + progressCurrent
                + ", progressTarget=" + progressTarget
                + ", linkedGoalId=" + linkedGoalId
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + '}';
    }
}