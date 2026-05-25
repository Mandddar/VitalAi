package com.vitalai.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.vitalai.app.domain.model.enums.GoalStatus;
import com.vitalai.app.domain.model.enums.GoalType;
import com.vitalai.app.domain.model.enums.MetricType;

import java.util.Date;

/**
 * GoalEntity
 *
 * Room entity representing a row in the {@code goals} table.
 * Each row records one health goal set by the user — a target value
 * for a specific metric or behaviour that the user wants to achieve
 * by a given deadline.
 *
 * Design notes
 * ────────────
 * • Foreign key to {@link UserEntity} uses ON DELETE CASCADE — deleting
 *   a user automatically removes all their goals without any manual
 *   Repository cleanup.
 *
 * • {@code goalType} maps to {@link GoalType} — the broad category of
 *   the goal (e.g. METRIC_TARGET, WORKOUT_FREQUENCY, SLEEP_DURATION,
 *   WEIGHT_LOSS, HABIT). Stored as TEXT via TypeConverters. Drives
 *   which progress-calculation strategy the Repository applies and
 *   which detail UI is shown.
 *
 * • {@code status} maps to {@link GoalStatus} — the lifecycle state of
 *   the goal (ACTIVE, COMPLETED, FAILED, PAUSED, CANCELLED). Stored as
 *   TEXT via TypeConverters. Only ACTIVE goals are included in the
 *   progress dashboard; completed and failed goals are retained for the
 *   history view and the AI coaching context.
 *
 * • {@code linkedMetricType} is a soft reference to the
 *   {@link MetricType} enum stored as its name() string. It links a
 *   METRIC_TARGET goal to the specific metric being tracked (e.g.
 *   "HEART_RATE", "WEIGHT", "STEPS"). Nullable — non-metric goals
 *   (e.g. WORKOUT_FREQUENCY, HABIT) do not map to a single MetricType.
 *   A typed FK is avoided for the same reason as in
 *   {@link InsightEntity#relatedMetricType} — a goal may conceptually
 *   span multiple metric types.
 *
 * • Progress is tracked via three numeric fields:
 *     {@code targetValue}  — the numeric goal the user is aiming for.
 *     {@code currentValue} — the latest progress snapshot, updated by
 *                            the Repository each time a new metric
 *                            reading arrives. Nullable — not meaningful
 *                            for all goal types (e.g. HABIT goals track
 *                            streak days, not a numeric value).
 *     {@code unit}         — human-readable unit label for display
 *                            (e.g. "kg", "bpm", "steps/day", "hours").
 *                            Stored as free-form TEXT; unit conversion
 *                            is a UI-layer concern.
 *
 * • {@code startDate} is when the user committed to the goal.
 *   {@code targetDate} is the optional deadline. Null deadline means
 *   the goal is open-ended ("I want to reach 10,000 steps/day
 *   eventually"). The AI coaching layer uses the deadline proximity to
 *   escalate nudge frequency.
 *
 * • {@code completedAt} is set by the Repository the instant the
 *   {@code currentValue} crosses {@code targetValue} (for ascending
 *   goals) or drops below it (for descending goals such as weight
 *   loss). Setting {@code completedAt} also triggers a status
 *   transition to COMPLETED and fires a GENERAL/INFO
 *   {@link InsightEntity} celebrating the achievement.
 *
 * • {@code isAscending} distinguishes the direction of progress:
 *   TRUE  — higher is better (steps, sleep hours, water intake).
 *   FALSE — lower is better (resting HR, weight, stress score).
 *   The Repository uses this flag to determine when to transition
 *   status to COMPLETED and to render the progress bar correctly.
 *
 * • {@code reminderFrequency} is a free-form string parsed by the
 *   WorkManager scheduling layer into a notification cadence. Mirrors
 *   the same design choice as {@link MedicationEntity#frequency}.
 *   Examples: "Daily", "Weekly", "Every Monday", "On metric update".
 *   Nullable — user may opt out of reminders for a goal.
 *
 * • {@code notes} is an optional user-authored description or
 *   motivation statement. Fed into the AI coaching context so the
 *   assistant can reference the user's stated motivation when
 *   generating encouragement insights.
 *
 * • Three indexes are declared:
 *     1. (user_id, status)          — primary dashboard query: "all
 *        ACTIVE goals for user X"; also used for COMPLETED history.
 *     2. (user_id, goal_type)       — type-filtered queries: "all
 *        WORKOUT_FREQUENCY goals for user X" for the training plan
 *        summary card.
 *     3. (user_id, target_date)     — deadline proximity queries:
 *        "goals for user X expiring in the next 7 days" for the
 *        AI coaching escalation pipeline.
 *
 * Architecture layer : Data / Local
 * Table name         : goals
 * Related DAOs       : GoalDao
 * Parent FK          : users.id  (CASCADE delete + update)
 */
@Entity(
        tableName = "goals",
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
                // Primary dashboard query: active / completed goals
                @Index(value = {"user_id", "status"}),
                // Type-filtered goal summary cards
                @Index(value = {"user_id", "goal_type"}),
                // Deadline proximity for AI coaching escalation
                @Index(value = {"user_id", "target_date"})
        }
)
public class GoalEntity {

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
    // Classification
    // ──────────────────────────────────────────────────────────────────────

    /**
     * The broad category of this goal.
     * Stored as TEXT via TypeConverters.
     *
     * Expected values (defined in {@link GoalType}):
     *   METRIC_TARGET      — reach a specific numeric metric value
     *                        (e.g. resting HR ≤ 60 bpm, weight ≤ 75 kg)
     *   WORKOUT_FREQUENCY  — exercise N times per week / month
     *   SLEEP_DURATION     — achieve N hours of sleep per night on average
     *   STEP_COUNT         — hit a daily step target
     *   WEIGHT_LOSS        — lose N kg by a target date
     *   HYDRATION          — drink N mL of water per day
     *   HABIT              — build or break a general behaviour habit
     *   CUSTOM             — user-defined goal with free-form description
     *
     * Drives which progress-calculation strategy the Repository applies
     * and which goal-detail UI screen is shown.
     */
    @NonNull
    @ColumnInfo(name = "goal_type")
    public GoalType goalType;

    /**
     * Lifecycle state of this goal.
     * Stored as TEXT via TypeConverters.
     *
     * Expected values (defined in {@link GoalStatus}):
     *   ACTIVE    — in progress; shown on the dashboard
     *   COMPLETED — target reached; retained for history and AI context
     *   FAILED    — deadline passed without reaching target; retained
     *               for history and used as a negative coaching signal
     *   PAUSED    — temporarily suspended by the user
     *   CANCELLED — abandoned by the user before completion
     *
     * Transitions are managed exclusively by the Repository:
     *   ACTIVE → COMPLETED : currentValue crosses targetValue
     *   ACTIVE → FAILED    : NOW() > targetDate && target not reached
     *   ACTIVE → PAUSED    : user action
     *   PAUSED → ACTIVE    : user action
     *   ACTIVE → CANCELLED : user action
     */
    @NonNull
    @ColumnInfo(name = "status")
    public GoalStatus status;

    // ──────────────────────────────────────────────────────────────────────
    // Linked Metric
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Soft reference to the {@link MetricType} this goal tracks, stored
     * as the enum's name() string.
     *
     * Examples: "HEART_RATE", "WEIGHT", "STEPS", "BLOOD_GLUCOSE",
     *           "SLEEP_SCORE", "HEART_RATE_VARIABILITY"
     *
     * Nullable — non-metric goals (WORKOUT_FREQUENCY, HABIT, CUSTOM)
     * do not map to a single MetricType. Also null for WEIGHT_LOSS
     * goals that track the delta rather than an absolute metric reading.
     *
     * Used by the Repository to automatically update {@link #currentValue}
     * when a new {@link HealthMetricEntity} row is inserted for the
     * matching metric type, and by the detail screen's "View data"
     * deep-link to open the corresponding metric chart.
     */
    @Nullable
    @ColumnInfo(name = "linked_metric_type")
    public String linkedMetricType;

    // ──────────────────────────────────────────────────────────────────────
    // Goal Definition
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Human-readable title of the goal, authored by the user or
     * pre-populated by the AI goal-suggestion feature.
     * Examples:
     *   "Lower my resting heart rate to 60 bpm"
     *   "Run 3 times per week"
     *   "Sleep 8 hours a night"
     *   "Lose 5 kg before summer"
     */
    @NonNull
    @ColumnInfo(name = "title")
    public String title;

    /**
     * The numeric target the user is aiming to reach.
     *
     * Interpretation per GoalType:
     *   METRIC_TARGET     → absolute metric value (e.g. 60.0 bpm, 75.0 kg)
     *   WORKOUT_FREQUENCY → sessions per week (e.g. 3.0)
     *   SLEEP_DURATION    → hours per night (e.g. 8.0)
     *   STEP_COUNT        → steps per day (e.g. 10000.0)
     *   WEIGHT_LOSS       → kg to lose (e.g. 5.0, a delta not an absolute)
     *   HYDRATION         → mL per day (e.g. 2500.0)
     *   HABIT / CUSTOM    → streak days or user-defined numeric target
     *
     * Stored as REAL (double) to accommodate both integer counts and
     * fractional values uniformly, consistent with
     * {@link HealthMetricEntity#value}.
     */
    @ColumnInfo(name = "target_value")
    public double targetValue;

    /**
     * The latest progress snapshot toward {@link #targetValue}.
     * Updated by the Repository each time a relevant
     * {@link HealthMetricEntity} row is inserted for
     * {@link #linkedMetricType}, or when the user manually logs progress
     * for non-metric goals (e.g. logging a workout session for a
     * WORKOUT_FREQUENCY goal).
     *
     * Nullable — not yet populated for newly created goals where no
     * relevant metric reading has been recorded since goal creation,
     * or for CUSTOM goals where progress tracking is entirely manual.
     *
     * The UI renders this as a progress bar:
     *   progress % = (currentValue / targetValue) × 100  [ascending]
     *   progress % = (startValue - currentValue) /
     *                (startValue - targetValue) × 100    [descending]
     */
    @Nullable
    @ColumnInfo(name = "current_value")
    public Double currentValue;

    /**
     * The value of {@link #linkedMetricType} (or the relevant measure)
     * at the moment the goal was created. Used as the baseline for
     * descending goals (weight loss, HR reduction) to calculate
     * percentage progress correctly.
     *
     * Examples:
     *   Weight-loss goal: startValue = 82.0 kg, targetValue = 77.0 kg
     *   → progress = (82.0 - currentValue) / (82.0 - 77.0) × 100
     *
     * Nullable — not required for ascending goals where progress is
     * simply currentValue / targetValue, or for goals where the
     * baseline is unknown at creation time.
     */
    @Nullable
    @ColumnInfo(name = "start_value")
    public Double startValue;

    /**
     * Human-readable unit label for displaying target and current values.
     * Free-form TEXT — unit conversion is a UI/ViewModel concern.
     *
     * Examples: "bpm", "kg", "steps/day", "hours/night",
     *           "sessions/week", "mL/day", "days"
     *
     * Nullable — HABIT and CUSTOM goals may have no meaningful unit.
     */
    @Nullable
    @ColumnInfo(name = "unit")
    public String unit;

    /**
     * Direction of progress toward the goal.
     *
     * TRUE  — higher currentValue is better progress toward targetValue.
     *         Examples: steps/day, sleep hours, HRV, water intake.
     * FALSE — lower currentValue is better progress toward targetValue.
     *         Examples: resting HR, weight, stress score, blood glucose.
     *
     * Used by the Repository to:
     *   • Determine when to transition status to COMPLETED.
     *   • Render the progress bar in the correct direction.
     *   • Generate appropriately directional coaching insights
     *     ("keep it up" vs "keep it down").
     *
     * SQLite stores as INTEGER (0 = false, 1 = true).
     */
    @ColumnInfo(name = "is_ascending", defaultValue = "1")
    public boolean isAscending;

    // ──────────────────────────────────────────────────────────────────────
    // Timeline
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC date when the user committed to this goal.
     * Used as the progress timeline start point for streak calculations
     * and weekly/monthly trend charts within the goal detail view.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "start_date")
    public Date startDate;

    /**
     * Optional deadline by which the user wants to achieve the goal.
     * Null means the goal is open-ended with no fixed deadline.
     *
     * The AI coaching layer uses proximity to this date to escalate
     * nudge frequency (daily reminders in the final week vs weekly
     * reminders earlier in the journey). The WorkManager failure-check
     * worker transitions status to FAILED when NOW() > targetDate and
     * the goal has not been completed.
     *
     * Stored as epoch millis via TypeConverters.
     */
    @Nullable
    @ColumnInfo(name = "target_date")
    public Date targetDate;

    /**
     * UTC instant when {@link #currentValue} first crossed
     * {@link #targetValue} in the {@link #isAscending} direction,
     * triggering an automatic status transition to COMPLETED.
     *
     * Set by the Repository; never set manually. Null until the goal
     * is completed. Retained permanently for the achievement history
     * view and for the AI coaching context ("You hit your step goal
     * 3 days ahead of schedule!").
     *
     * Stored as epoch millis via TypeConverters.
     */
    @Nullable
    @ColumnInfo(name = "completed_at")
    public Date completedAt;

    // ──────────────────────────────────────────────────────────────────────
    // Reminders
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Free-form reminder frequency string parsed by the WorkManager
     * scheduling layer into a notification cadence. Mirrors the same
     * design choice as {@link MedicationEntity#frequency}.
     *
     * Examples: "Daily", "Weekly", "Every Monday", "On metric update",
     *           "3 days before deadline"
     *
     * Nullable — user may opt out of goal reminders entirely. When null,
     * no WorkManager reminder job is scheduled for this goal.
     */
    @Nullable
    @ColumnInfo(name = "reminder_frequency")
    public String reminderFrequency;

    // ──────────────────────────────────────────────────────────────────────
    // Notes
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Optional user-authored motivation statement or goal description.
     * Examples:
     *   "I want to lower my HR so I can keep up with my kids."
     *   "Doctor recommended I reach 75 kg before my next check-up."
     *
     * Fed into the AI coaching context so the assistant can reference
     * the user's stated motivation when generating encouragement
     * insights and celebrating milestones. Also displayed in the goal
     * detail view beneath the progress bar.
     */
    @Nullable
    @ColumnInfo(name = "notes")
    public String notes;

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
     * Updated when {@link #currentValue}, {@link #status},
     * {@link #completedAt}, or {@link #reminderFrequency} change.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "updated_at")
    public Date updatedAt;

    // ──────────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Full constructor used by the Repository when inserting a new goal.
     * Room uses direct field assignment when reading rows back from the DB.
     *
     * @param userId              Local PK of the owning {@link UserEntity}.
     * @param goalType            Goal category (required).
     * @param status              Lifecycle state — ACTIVE at creation (required).
     * @param linkedMetricType    MetricType name string, or null.
     * @param title               Human-readable goal title (required).
     * @param targetValue         Numeric target to reach (required).
     * @param currentValue        Latest progress snapshot, or null.
     * @param startValue          Baseline value at goal creation, or null.
     * @param unit                Display unit label, or null.
     * @param isAscending         True if higher = better progress.
     * @param startDate           Goal commitment date (required).
     * @param targetDate          Optional deadline, or null.
     * @param completedAt         Completion instant, or null.
     * @param reminderFrequency   Reminder cadence string, or null.
     * @param notes               User motivation note, or null.
     * @param createdAt           Insert timestamp (set by Repository).
     * @param updatedAt           Last-update timestamp (set by Repository).
     */
    public GoalEntity(
            long userId,
            @NonNull GoalType goalType,
            @NonNull GoalStatus status,
            @Nullable String linkedMetricType,
            @NonNull String title,
            double targetValue,
            @Nullable Double currentValue,
            @Nullable Double startValue,
            @Nullable String unit,
            boolean isAscending,
            @NonNull Date startDate,
            @Nullable Date targetDate,
            @Nullable Date completedAt,
            @Nullable String reminderFrequency,
            @Nullable String notes,
            @NonNull Date createdAt,
            @NonNull Date updatedAt) {

        this.userId             = userId;
        this.goalType           = goalType;
        this.status             = status;
        this.linkedMetricType   = linkedMetricType;
        this.title              = title;
        this.targetValue        = targetValue;
        this.currentValue       = currentValue;
        this.startValue         = startValue;
        this.unit               = unit;
        this.isAscending        = isAscending;
        this.startDate          = startDate;
        this.targetDate         = targetDate;
        this.completedAt        = completedAt;
        this.reminderFrequency  = reminderFrequency;
        this.notes              = notes;
        this.createdAt          = createdAt;
        this.updatedAt          = updatedAt;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Convenience factories
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Factory for a fully specified goal — the standard path used when
     * the user configures all options including a linked metric, baseline,
     * unit, deadline, and reminder cadence.
     * Status defaults to ACTIVE; completedAt defaults to null.
     * Timestamps are set to now automatically.
     *
     * @param userId             Local PK of the owning user.
     * @param goalType           Goal category.
     * @param linkedMetricType   MetricType name string, or null.
     * @param title              Human-readable goal title.
     * @param targetValue        Numeric target to reach.
     * @param currentValue       Initial progress snapshot, or null.
     * @param startValue         Baseline value at goal creation, or null.
     * @param unit               Display unit label, or null.
     * @param isAscending        True if higher = better progress.
     * @param startDate          Goal commitment date.
     * @param targetDate         Optional deadline, or null.
     * @param reminderFrequency  Reminder cadence string, or null.
     * @param notes              User motivation note, or null.
     * @return Ready-to-insert {@link GoalEntity}.
     */
    public static GoalEntity create(
            long userId,
            @NonNull GoalType goalType,
            @Nullable String linkedMetricType,
            @NonNull String title,
            double targetValue,
            @Nullable Double currentValue,
            @Nullable Double startValue,
            @Nullable String unit,
            boolean isAscending,
            @NonNull Date startDate,
            @Nullable Date targetDate,
            @Nullable String reminderFrequency,
            @Nullable String notes) {

        Date now = new Date();
        return new GoalEntity(
                userId, goalType, GoalStatus.ACTIVE, linkedMetricType,
                title, targetValue, currentValue, startValue, unit,
                isAscending, startDate, targetDate, null,
                reminderFrequency, notes, now, now);
    }

    /**
     * Factory for a metric-linked goal — the most common path when the
     * user picks a metric from the dashboard and sets a target value.
     * The {@code linkedMetricType} is provided as a {@link MetricType}
     * enum and converted to its name() string automatically.
     *
     * @param userId           Local PK of the owning user.
     * @param metricType       The MetricType this goal tracks.
     * @param title            Human-readable goal title.
     * @param targetValue      Numeric target to reach in the metric's
     *                         canonical unit.
     * @param startValue       Current metric value at goal creation
     *                         (used as baseline for descending goals).
     * @param unit             Display unit label (e.g. "bpm", "kg").
     * @param isAscending      True if higher = better (steps, HRV);
     *                         false if lower = better (HR, weight).
     * @param targetDate       Optional deadline, or null.
     * @param reminderFrequency Reminder cadence string, or null.
     * @return Ready-to-insert {@link GoalEntity}.
     */
    public static GoalEntity createMetricGoal(
            long userId,
            @NonNull MetricType metricType,
            @NonNull String title,
            double targetValue,
            @Nullable Double startValue,
            @Nullable String unit,
            boolean isAscending,
            @Nullable Date targetDate,
            @Nullable String reminderFrequency) {

        return create(userId, GoalType.METRIC_TARGET, metricType.name(),
                title, targetValue, startValue, startValue, unit,
                isAscending, new Date(), targetDate,
                reminderFrequency, null);
    }

    /**
     * Minimal factory for a simple open-ended goal with no deadline,
     * reminder, baseline, or linked metric — used for HABIT and CUSTOM
     * goal types where the user provides a title and target only.
     *
     * @param userId      Local PK of the owning user.
     * @param goalType    Goal category (typically HABIT or CUSTOM).
     * @param title       Human-readable goal title.
     * @param targetValue Numeric target (e.g. streak days).
     * @param isAscending True if higher = better progress.
     * @return Ready-to-insert {@link GoalEntity}.
     */
    public static GoalEntity createSimple(
            long userId,
            @NonNull GoalType goalType,
            @NonNull String title,
            double targetValue,
            boolean isAscending) {

        return create(userId, goalType, null, title, targetValue,
                null, null, null, isAscending,
                new Date(), null, null, null);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Updates the current progress value and checks whether the goal
     * has been completed. If completed, transitions status to COMPLETED,
     * sets {@link #completedAt} to now, and stamps {@link #updatedAt}.
     *
     * Call this in the Repository whenever a new metric reading arrives
     * for {@link #linkedMetricType}, then pass the entity to
     * {@code GoalDao.update()}.
     *
     * @param newValue The latest metric value or progress count.
     */
    public void updateProgress(double newValue) {
        this.currentValue = newValue;
        this.updatedAt    = new Date();

        boolean achieved = isAscending
                ? newValue >= targetValue
                : newValue <= targetValue;

        if (achieved && status == GoalStatus.ACTIVE) {
            this.status      = GoalStatus.COMPLETED;
            this.completedAt = this.updatedAt;
        }
    }

    /**
     * Transitions this goal to FAILED status and stamps {@link #updatedAt}.
     * Called by the WorkManager deadline-check worker when NOW() passes
     * {@link #targetDate} without the goal having been completed.
     * Only valid for ACTIVE goals — no-op if already in a terminal state.
     */
    public void markFailed() {
        if (status == GoalStatus.ACTIVE) {
            this.status    = GoalStatus.FAILED;
            this.updatedAt = new Date();
        }
    }

    /**
     * Pauses this goal and stamps {@link #updatedAt}.
     * Only valid for ACTIVE goals.
     */
    public void pause() {
        if (status == GoalStatus.ACTIVE) {
            this.status    = GoalStatus.PAUSED;
            this.updatedAt = new Date();
        }
    }

    /**
     * Resumes a paused goal back to ACTIVE and stamps {@link #updatedAt}.
     * Only valid for PAUSED goals.
     */
    public void resume() {
        if (status == GoalStatus.PAUSED) {
            this.status    = GoalStatus.ACTIVE;
            this.updatedAt = new Date();
        }
    }

    /**
     * Cancels this goal and stamps {@link #updatedAt}.
     * Valid for ACTIVE and PAUSED goals. The row is retained for history;
     * use a DAO delete only for permanent removal.
     */
    public void cancel() {
        if (status == GoalStatus.ACTIVE || status == GoalStatus.PAUSED) {
            this.status    = GoalStatus.CANCELLED;
            this.updatedAt = new Date();
        }
    }

    /**
     * Returns the progress toward the goal as a fraction in [0.0, 1.0],
     * clamped to 1.0 on completion. Returns 0.0 if {@link #currentValue}
     * is null (no progress recorded yet).
     *
     * For ascending goals : currentValue / targetValue
     * For descending goals: (startValue - currentValue) /
     *                       (startValue - targetValue)
     *                       Falls back to currentValue / targetValue
     *                       if startValue is null.
     */
    public float progressFraction() {
        if (currentValue == null) return 0f;

        double fraction;
        if (isAscending || startValue == null) {
            fraction = currentValue / targetValue;
        } else {
            double range = startValue - targetValue;
            fraction = range == 0 ? 1.0 : (startValue - currentValue) / range;
        }
        return (float) Math.min(1.0, Math.max(0.0, fraction));
    }

    /**
     * Returns true if this goal has a deadline that has already passed,
     * regardless of current status. Used by the WorkManager failure-check
     * worker and the UI deadline-warning banner.
     */
    public boolean isDeadlinePassed() {
        return targetDate != null && new Date().after(targetDate);
    }

    /**
     * Refreshes {@code updated_at} to the current instant.
     * Call this in the Repository before any partial update via
     * {@code GoalDao.update()}.
     */
    public void touch() {
        this.updatedAt = new Date();
    }

    @Override
    public String toString() {
        return "GoalEntity{"
                + "id=" + id
                + ", userId=" + userId
                + ", goalType=" + goalType
                + ", status=" + status
                + ", title='" + title + '\''
                + ", targetValue=" + targetValue
                + ", currentValue=" + currentValue
                + ", unit='" + unit + '\''
                + ", isAscending=" + isAscending
                + ", startDate=" + startDate
                + ", targetDate=" + targetDate
                + ", completedAt=" + completedAt
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + '}';
    }
}