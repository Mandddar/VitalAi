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
 * MoodLogEntity
 *
 * Room entity representing a row in the {@code mood_logs} table.
 * Each row records one discrete mood self-report submitted by a user —
 * a point-in-time emotional state snapshot used by the AI layer to
 * correlate psychological wellbeing with physiological metrics (sleep
 * quality, HRV, activity level, medication adherence) and to personalise
 * coaching insights.
 *
 * Design notes
 * ────────────
 * • Foreign key to {@link UserEntity} uses ON DELETE CASCADE — deleting
 *   a user automatically removes all their mood logs without any manual
 *   Repository cleanup.
 *
 * • The primary mood signal is a 1–5 integer scale ({@code moodScore})
 *   chosen for its balance of granularity and cognitive ease:
 *     1 — Very Bad    (distressed, overwhelmed)
 *     2 — Bad         (low, anxious, irritable)
 *     3 — Neutral     (okay, neither good nor bad)
 *     4 — Good        (content, calm, energised)
 *     5 — Very Good   (excellent, happy, motivated)
 *   This maps directly to the MOOD_SCORE MetricType in
 *   {@link HealthMetricEntity} so mood trends can be overlaid on
 *   metric charts without a separate aggregation pipeline. The
 *   Repository mirrors each insert here by also inserting a
 *   HealthMetricEntity row of type MOOD_SCORE for unified charting.
 *
 * • {@code energyLevel} is a secondary 1–5 scale captured alongside
 *   mood to disambiguate emotional state from physical fatigue —
 *   a user can feel happy but physically exhausted after a long
 *   workout, or energised but emotionally stressed before a deadline.
 *   Nullable — some logging surfaces (quick-log widget, notification
 *   reply) capture only the primary mood score.
 *
 * • {@code emotions} is a comma-separated list of discrete emotion
 *   labels selected from a predefined tag palette presented in the
 *   mood logging UI (e.g. "Anxious,Tired", "Happy,Calm,Grateful").
 *   Stored as a single TEXT string rather than a junction table to
 *   keep the schema simple; the Repository splits on comma for
 *   tag-frequency analytics. Nullable — the user may submit a score
 *   without selecting specific emotion tags.
 *
 * • {@code triggers} is a parallel comma-separated list of
 *   self-reported contributing factors (e.g. "Poor sleep,Work stress",
 *   "Exercise,Good weather,Social"). Used by the AI coaching layer to
 *   identify recurring trigger patterns and personalise interventions.
 *   Nullable — optional free-association field surfaced after the
 *   primary score is submitted.
 *
 * • {@code notes} is an open-ended free-text journal entry attached
 *   to this mood log. Fed into the AI chat context when the user asks
 *   reflective questions ("Why have I been feeling anxious lately?")
 *   and used as training data for the personalised coaching model.
 *   Nullable — most quick-log entries omit a written note.
 *
 * • {@code loggedAt} is the user-reported instant of the emotional
 *   state — NOT the DB insert time. For real-time logs this equals
 *   the insert time; for retrospective entries (e.g. "I forgot to
 *   log this morning") the user selects a past time. This field is
 *   the sort key for the mood timeline and the grouping key for
 *   daily/weekly trend aggregations.
 *
 * • {@code context} is a short programmatic tag describing the
 *   circumstance under which the log was submitted. Used by the AI
 *   layer to weight mood readings differently — a post-workout mood
 *   spike is expected; a mid-sleep low score warrants more attention.
 *   Examples: "MORNING_CHECK_IN", "POST_WORKOUT", "PRE_SLEEP",
 *             "MID_DAY", "POST_MEAL", "NOTIFICATION_PROMPT",
 *             "MANUAL_RETROSPECTIVE".
 *   Nullable — not all logging paths have a deterministic context.
 *
 * • {@code linkedSleepSessionId} and {@code linkedWorkoutId} are
 *   optional soft FKs to the sleep session and workout that most
 *   directly preceded or coincided with this mood log. Typed Room FKs
 *   are avoided — deleting a sleep session or workout must not
 *   cascade-delete mood logs that reference them. The Repository
 *   resolves these references at read time for the "How did you sleep
 *   before this?" and "How did you feel after that workout?" UI
 *   correlation panels.
 *   Both nullable — mood logs submitted outside a sleep/workout
 *   context omit these fields.
 *
 * • {@code isPrivate} lets the user mark a mood log as private — it
 *   is excluded from any cloud sync, export, or AI training pipeline.
 *   Defaults to false (all logs sync by default). The Repository
 *   checks this flag before any Firestore write or RLHF data export.
 *
 * • Four indexes are declared:
 *     1. (user_id, logged_at)        — primary query pattern: mood
 *        timeline view and daily/weekly trend aggregations; also
 *        serves the AI context retrieval ("last 7 days of mood").
 *     2. (user_id, mood_score)       — score-filtered queries: "all
 *        low-mood days in the last 30 days" for the AI pattern-
 *        detection pipeline and coaching insight generation.
 *     3. (user_id, context)          — context-filtered queries:
 *        "all POST_WORKOUT logs for user X" for workout-mood
 *        correlation charts.
 *     4. (user_id, is_private)       — sync filter: fast lookup of
 *        non-private logs for the Firestore sync worker without a
 *        full table scan.
 *
 * Architecture layer : Data / Local
 * Table name         : mood_logs
 * Related DAOs       : MoodLogDao
 * Parent FK          : users.id  (CASCADE delete + update)
 */
@Entity(
        tableName = "mood_logs",
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
                // Primary: mood timeline and trend aggregations
                @Index(value = {"user_id", "logged_at"}),
                // Score-filtered low-mood pattern detection
                @Index(value = {"user_id", "mood_score"}),
                // Context-filtered workout/sleep correlation charts
                @Index(value = {"user_id", "context"}),
                // Sync filter: exclude private logs from cloud export
                @Index(value = {"user_id", "is_private"})
        }
)
public class MoodLogEntity {

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
    // Primary Mood Signal
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Primary mood rating on a 1–5 integer scale.
     *
     *   1 — Very Bad   (distressed, overwhelmed, tearful)
     *   2 — Bad        (low, anxious, irritable, sad)
     *   3 — Neutral    (okay, neither good nor bad, flat)
     *   4 — Good       (content, calm, positive, energised)
     *   5 — Very Good  (excellent, happy, motivated, grateful)
     *
     * Validated to [1, 5] by the Repository before insert.
     * Mirrored to {@code health_metrics} as a MOOD_SCORE row by the
     * Repository so mood trends can be overlaid on physiological
     * metric charts without a separate query pipeline.
     */
    @ColumnInfo(name = "mood_score")
    public int moodScore;

    /**
     * Secondary energy / vitality rating on a 1–5 integer scale.
     *
     *   1 — Exhausted   (no energy, difficulty staying awake)
     *   2 — Tired       (low energy, sluggish)
     *   3 — Neutral     (neither energised nor fatigued)
     *   4 — Energised   (alert, productive, physically capable)
     *   5 — Very Energised (peak energy, highly motivated)
     *
     * Captured alongside {@link #moodScore} to separate emotional
     * state from physical fatigue. Validated to [1, 5] if non-null.
     *
     * Nullable — quick-log surfaces and notification-reply prompts
     * capture only the primary mood score to reduce friction.
     */
    @Nullable
    @ColumnInfo(name = "energy_level")
    public Integer energyLevel;

    // ──────────────────────────────────────────────────────────────────────
    // Qualitative Labels
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Comma-separated list of discrete emotion labels selected from
     * the tag palette presented in the mood logging UI.
     *
     * Example values:
     *   "Happy,Calm,Grateful"
     *   "Anxious,Tired,Overwhelmed"
     *   "Focused,Motivated"
     *   "Sad,Lonely"
     *
     * The Repository splits on comma for tag-frequency analytics
     * ("most common emotions in the last 30 days") and the AI
     * coaching layer uses tag co-occurrence to identify patterns
     * (e.g. "Anxious" frequently co-occurs with low sleep scores).
     *
     * Nullable — the user may submit a numeric score without
     * selecting any emotion tags (e.g. via the quick-log widget).
     */
    @Nullable
    @ColumnInfo(name = "emotions")
    public String emotions;

    /**
     * Comma-separated list of self-reported contributing factors
     * or situational triggers for this mood state.
     *
     * Example values:
     *   "Poor sleep,Work stress,Deadline pressure"
     *   "Exercise,Good weather,Social interaction"
     *   "Skipped medication,Argument,Traffic"
     *   "Healthy meal,Meditation,Rest day"
     *
     * Used by the AI coaching layer to identify recurring trigger
     * patterns and generate targeted interventions (e.g. "Your mood
     * tends to drop on days you report poor sleep — here are some
     * sleep hygiene tips"). Nullable — optional field surfaced after
     * the primary score and emotion tags are submitted.
     */
    @Nullable
    @ColumnInfo(name = "triggers")
    public String triggers;

    // ──────────────────────────────────────────────────────────────────────
    // Journal
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Open-ended free-text journal entry accompanying this mood log.
     * No length constraint — rendered in a scrollable detail view.
     *
     * Examples:
     *   "Woke up feeling off. Work presentation went better than
     *    expected but I still feel tense. Going for a walk later."
     *   "Really good day — managed to get a full 8 hours and the
     *    morning run felt effortless."
     *
     * Fed into the AI chat context for reflective queries ("Why have
     * I been feeling anxious lately?") and used as training signal
     * for the personalised coaching model. Excluded from AI pipelines
     * when {@link #isPrivate} is true.
     *
     * Nullable — most quick-log entries omit a written note.
     */
    @Nullable
    @ColumnInfo(name = "notes")
    public String notes;

    // ──────────────────────────────────────────────────────────────────────
    // Logging Instant & Context
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant of the emotional state being reported.
     * NOT the DB insert time — for retrospective entries the user
     * selects a past time; {@link #createdAt} always reflects the
     * actual insert.
     *
     * Used as the sort key for the mood timeline view and as the
     * grouping key for daily/weekly trend aggregations:
     *   SELECT AVG(mood_score) FROM mood_logs
     *   WHERE user_id = :uid
     *     AND logged_at BETWEEN :weekStart AND :weekEnd
     *
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "logged_at")
    public Date loggedAt;

    /**
     * Programmatic tag describing the circumstance under which this
     * log was submitted. Used by the AI layer to contextualise and
     * weight mood readings appropriately.
     *
     * Recommended values (not enforced — free-form TEXT):
     *   "MORNING_CHECK_IN"       — scheduled morning prompt
     *   "MID_DAY"                — scheduled midday prompt
     *   "PRE_SLEEP"              — bedtime wind-down prompt
     *   "POST_WORKOUT"           — triggered after workout session ends
     *   "POST_MEAL"              — triggered after meal logging
     *   "NOTIFICATION_PROMPT"    — user responded to a push notification
     *   "MANUAL"                 — user opened the log screen directly
     *   "MANUAL_RETROSPECTIVE"   — user selected a past time manually
     *
     * Nullable — not all logging paths have a deterministic context
     * label (e.g. API-imported historical data).
     */
    @Nullable
    @ColumnInfo(name = "context")
    public String context;

    // ──────────────────────────────────────────────────────────────────────
    // Correlated Session Links (soft FKs)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Soft reference to the {@code sleep_sessions} table row that most
     * directly preceded this mood log (e.g. the night's sleep before a
     * morning check-in).
     *
     * A typed Room FK is intentionally avoided — deleting a sleep
     * session must not cascade-delete mood logs that reference it.
     * The Repository resolves this at read time for the "How did your
     * sleep affect your mood?" correlation panel.
     *
     * Nullable — mood logs submitted outside a sleep context (e.g.
     * mid-day, post-workout) omit this field.
     */
    @Nullable
    @ColumnInfo(name = "linked_sleep_session_id")
    public Long linkedSleepSessionId;

    /**
     * Soft reference to the {@code workouts} table row that most
     * directly preceded or coincided with this mood log (e.g. a
     * post-workout mood check-in triggered automatically).
     *
     * A typed Room FK is intentionally avoided — deleting a workout
     * must not cascade-delete mood logs that reference it.
     * The Repository resolves this at read time for the "How did you
     * feel after that workout?" correlation panel.
     *
     * Nullable — mood logs submitted without a preceding workout
     * context omit this field.
     */
    @Nullable
    @ColumnInfo(name = "linked_workout_id")
    public Long linkedWorkoutId;

    // ──────────────────────────────────────────────────────────────────────
    // Privacy
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Whether this log is marked private by the user.
     *
     * FALSE (default) — log syncs to Firestore, is included in AI
     *   training pipelines, and appears in data exports.
     * TRUE — log is stored locally only; excluded from all cloud sync,
     *   RLHF data exports, and AI training data pipelines. Still used
     *   for on-device AI inference (the user still benefits from their
     *   own private data being considered by the local TFLite model).
     *
     * The Firestore sync worker checks this flag before writing any
     * mood log row to the cloud. Repository must enforce this contract
     * and never upload a row where is_private = 1.
     *
     * SQLite stores as INTEGER (0 = false, 1 = true).
     */
    @ColumnInfo(name = "is_private", defaultValue = "0")
    public boolean isPrivate;

    // ──────────────────────────────────────────────────────────────────────
    // Timestamps
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant when this row was first written to the local database.
     * Set once at insert time; never modified thereafter.
     * Distinct from {@link #loggedAt} — a retrospective entry may have
     * a {@code loggedAt} hours before {@code createdAt}.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "created_at")
    public Date createdAt;

    /**
     * UTC instant of the most recent update to this row.
     * Updated when {@link #notes} are edited, {@link #emotions} or
     * {@link #triggers} are revised, or {@link #isPrivate} is toggled.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "updated_at")
    public Date updatedAt;

    // ──────────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Full constructor used by the Repository when inserting a new log.
     * Room uses direct field assignment when reading rows back from the DB.
     *
     * @param userId                Local PK of the owning {@link UserEntity}.
     * @param moodScore             Primary mood rating 1–5 (required).
     * @param energyLevel           Secondary energy rating 1–5, or null.
     * @param emotions              Comma-separated emotion tags, or null.
     * @param triggers              Comma-separated trigger tags, or null.
     * @param notes                 Free-text journal entry, or null.
     * @param loggedAt              Emotional-state instant (required).
     * @param context               Logging circumstance tag, or null.
     * @param linkedSleepSessionId  Soft FK to sleep_sessions, or null.
     * @param linkedWorkoutId       Soft FK to workouts, or null.
     * @param isPrivate             True if excluded from cloud sync.
     * @param createdAt             Insert timestamp (set by Repository).
     * @param updatedAt             Last-update timestamp (set by Repository).
     */
    public MoodLogEntity(
            long userId,
            int moodScore,
            @Nullable Integer energyLevel,
            @Nullable String emotions,
            @Nullable String triggers,
            @Nullable String notes,
            @NonNull Date loggedAt,
            @Nullable String context,
            @Nullable Long linkedSleepSessionId,
            @Nullable Long linkedWorkoutId,
            boolean isPrivate,
            @NonNull Date createdAt,
            @NonNull Date updatedAt) {

        this.userId                = userId;
        this.moodScore             = moodScore;
        this.energyLevel           = energyLevel;
        this.emotions              = emotions;
        this.triggers              = triggers;
        this.notes                 = notes;
        this.loggedAt              = loggedAt;
        this.context               = context;
        this.linkedSleepSessionId  = linkedSleepSessionId;
        this.linkedWorkoutId       = linkedWorkoutId;
        this.isPrivate             = isPrivate;
        this.createdAt             = createdAt;
        this.updatedAt             = updatedAt;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Convenience factories
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Factory for a fully specified mood log — the standard path when
     * the user completes the full logging flow with score, energy,
     * emotion tags, triggers, and an optional note.
     * Timestamps set to now; {@code isPrivate} defaults to false.
     *
     * @param userId                Local PK of the owning user.
     * @param moodScore             Primary mood rating 1–5.
     * @param energyLevel           Secondary energy rating 1–5, or null.
     * @param emotions              Comma-separated emotion tags, or null.
     * @param triggers              Comma-separated trigger tags, or null.
     * @param notes                 Free-text journal entry, or null.
     * @param loggedAt              Emotional-state instant.
     * @param context               Logging circumstance tag, or null.
     * @param linkedSleepSessionId  Soft FK to sleep_sessions, or null.
     * @param linkedWorkoutId       Soft FK to workouts, or null.
     * @return Ready-to-insert {@link MoodLogEntity}.
     */
    public static MoodLogEntity create(
            long userId,
            int moodScore,
            @Nullable Integer energyLevel,
            @Nullable String emotions,
            @Nullable String triggers,
            @Nullable String notes,
            @NonNull Date loggedAt,
            @Nullable String context,
            @Nullable Long linkedSleepSessionId,
            @Nullable Long linkedWorkoutId) {

        Date now = new Date();
        return new MoodLogEntity(
                userId, moodScore, energyLevel,
                emotions, triggers, notes,
                loggedAt, context,
                linkedSleepSessionId, linkedWorkoutId,
                false, now, now);
    }

    /**
     * Minimal factory for a quick-log entry — the most common path
     * where the user selects only a mood score (e.g. via the home
     * screen widget or a notification reply action). All optional
     * fields are null; {@code loggedAt} is set to now.
     * Timestamps set to now; {@code isPrivate} defaults to false.
     *
     * @param userId    Local PK of the owning user.
     * @param moodScore Primary mood rating 1–5.
     * @param context   Logging circumstance tag, or null.
     * @return Ready-to-insert {@link MoodLogEntity}.
     */
    public static MoodLogEntity createQuick(
            long userId,
            int moodScore,
            @Nullable String context) {

        Date now = new Date();
        return new MoodLogEntity(
                userId, moodScore, null,
                null, null, null,
                now, context,
                null, null,
                false, now, now);
    }

    /**
     * Factory for a post-workout mood check-in triggered automatically
     * when a workout session ends. Links the log to the completed
     * workout via {@link #linkedWorkoutId}; context is set to
     * "POST_WORKOUT". All other optional fields are null and can be
     * enriched by the user in the check-in dialog.
     * Timestamps set to now; {@code isPrivate} defaults to false.
     *
     * @param userId      Local PK of the owning user.
     * @param moodScore   Primary mood rating 1–5.
     * @param energyLevel Secondary energy rating 1–5, or null.
     * @param workoutId   PK of the just-completed workout row.
     * @return Ready-to-insert {@link MoodLogEntity}.
     */
    public static MoodLogEntity createPostWorkout(
            long userId,
            int moodScore,
            @Nullable Integer energyLevel,
            long workoutId) {

        Date now = new Date();
        return new MoodLogEntity(
                userId, moodScore, energyLevel,
                null, null, null,
                now, "POST_WORKOUT",
                null, workoutId,
                false, now, now);
    }

    /**
     * Factory for a morning check-in that links the log to the
     * preceding night's sleep session. Context is set to
     * "MORNING_CHECK_IN"; all other optional fields are null.
     * Timestamps set to now; {@code isPrivate} defaults to false.
     *
     * @param userId         Local PK of the owning user.
     * @param moodScore      Primary mood rating 1–5.
     * @param energyLevel    Secondary energy rating 1–5, or null.
     * @param sleepSessionId PK of the most recent sleep session row.
     * @return Ready-to-insert {@link MoodLogEntity}.
     */
    public static MoodLogEntity createMorningCheckIn(
            long userId,
            int moodScore,
            @Nullable Integer energyLevel,
            long sleepSessionId) {

        Date now = new Date();
        return new MoodLogEntity(
                userId, moodScore, energyLevel,
                null, null, null,
                now, "MORNING_CHECK_IN",
                sleepSessionId, null,
                false, now, now);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Enriches a quick-log entry with emotion tags, triggers, and a
     * journal note after the user completes the full logging flow
     * in the post-submission detail screen. Stamps {@link #updatedAt}.
     *
     * Call this in the Repository when the user taps "Done" on the
     * enrichment screen, then persist via {@code MoodLogDao.update()}.
     *
     * @param emotions  Comma-separated emotion tags, or null to clear.
     * @param triggers  Comma-separated trigger tags, or null to clear.
     * @param notes     Free-text journal entry, or null to clear.
     */
    public void enrich(
            @Nullable String emotions,
            @Nullable String triggers,
            @Nullable String notes) {
        this.emotions  = emotions;
        this.triggers  = triggers;
        this.notes     = notes;
        this.updatedAt = new Date();
    }

    /**
     * Toggles the private flag and stamps {@link #updatedAt}.
     * Call this in the Repository when the user taps the privacy
     * toggle in the log detail view, then persist via
     * {@code MoodLogDao.update()}. The Firestore sync worker will
     * respect the updated flag on the next sync cycle.
     */
    public void togglePrivacy() {
        this.isPrivate = !this.isPrivate;
        this.updatedAt = new Date();
    }

    /**
     * Returns true if this log represents a low-mood state (score ≤ 2).
     * Used by the AI coaching pipeline to identify days that warrant
     * a supportive insight or a check-in nudge.
     */
    public boolean isLowMood() {
        return moodScore <= 2;
    }

    /**
     * Returns true if this log represents a high-mood state (score ≥ 4).
     * Used by the AI coaching pipeline to identify positive patterns
     * worth reinforcing in coaching insights.
     */
    public boolean isHighMood() {
        return moodScore >= 4;
    }

    /**
     * Refreshes {@code updated_at} to the current instant.
     * Call this in the Repository before any partial update via
     * {@code MoodLogDao.update()}.
     */
    public void touch() {
        this.updatedAt = new Date();
    }

    @Override
    public String toString() {
        return "MoodLogEntity{"
                + "id=" + id
                + ", userId=" + userId
                + ", moodScore=" + moodScore
                + ", energyLevel=" + energyLevel
                + ", emotions='" + emotions + '\''
                + ", triggers='" + triggers + '\''
                + ", loggedAt=" + loggedAt
                + ", context='" + context + '\''
                + ", linkedSleepSessionId=" + linkedSleepSessionId
                + ", linkedWorkoutId=" + linkedWorkoutId
                + ", isPrivate=" + isPrivate
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + '}';
    }
}