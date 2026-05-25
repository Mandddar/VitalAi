package com.vitalai.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.vitalai.app.domain.model.enums.InsightCategory;
import com.vitalai.app.domain.model.enums.InsightSeverity;

import java.util.Date;

/**
 * InsightEntity
 *
 * Room entity representing a row in the {@code insights} table.
 * Each row records one AI-generated health insight surfaced to the user —
 * a discrete, actionable observation derived from the user's health metrics,
 * sleep sessions, workouts, medications, and health conditions.
 *
 * Design notes
 * ────────────
 * • Foreign key to {@link UserEntity} uses ON DELETE CASCADE — deleting a
 *   user automatically removes all their insight records without any
 *   manual Repository cleanup.
 *
 * • Insights are generated asynchronously by the on-device TFLite model
 *   and/or the cloud AI pipeline (Claude / Gemini) running in a
 *   WorkManager worker. They are never created synchronously on the UI
 *   thread. The UI observes the table via LiveData/Flow and refreshes
 *   the insight feed reactively.
 *
 * • {@code category} maps to {@link InsightCategory} — the broad domain
 *   of health the insight addresses (e.g. HEART_RATE, SLEEP, WORKOUT,
 *   MEDICATION, NUTRITION). Stored as TEXT via TypeConverters. Used to
 *   filter the insight feed by health domain and to route the insight to
 *   the correct detail screen.
 *
 * • {@code severity} maps to {@link InsightSeverity} — the clinical
 *   urgency of the insight (e.g. INFO, WARNING, CRITICAL). Stored as
 *   TEXT via TypeConverters. Drives notification priority: CRITICAL
 *   insights trigger high-priority FCM push notifications; INFO insights
 *   are surfaced passively in the feed.
 *
 * • {@code title} and {@code body} are the human-readable content of
 *   the insight. {@code title} is a short summary (≤ 80 chars, suitable
 *   for a notification title or card header). {@code body} is the full
 *   explanation with context and recommendation (no length constraint —
 *   rendered in a scrollable detail view).
 *
 * • {@code relatedMetricType} is an optional free-form string linking
 *   this insight back to the specific {@link com.vitalai.app.domain.model.enums.MetricType}
 *   that triggered it (e.g. "HEART_RATE", "BLOOD_OXYGEN_SPO2"). Stored
 *   as TEXT rather than a typed FK because an insight may be derived from
 *   multiple metric types (e.g. a stress insight combining HRV + sleep
 *   score) and a single string is a pragmatic cross-reference for the
 *   detail screen's "View related data" deep-link.
 *
 * • {@code relatedEntityId} and {@code relatedEntityTable} together form
 *   a soft polymorphic reference to the specific row (sleep session,
 *   workout, medication, etc.) that triggered this insight. Using a
 *   typed FK would require one nullable FK column per child table, which
 *   is impractical at scale. The Repository resolves the reference at
 *   read time using the table name as a discriminator.
 *   Examples:
 *     relatedEntityTable = "sleep_sessions", relatedEntityId = 42
 *     relatedEntityTable = "workouts",       relatedEntityId = 17
 *     relatedEntityTable = "health_metrics", relatedEntityId = 301
 *
 * • {@code isRead} tracks whether the user has opened or dismissed the
 *   insight card. Defaults to false. Updated by the Repository when the
 *   user taps the card or swipes it away. Drives the unread badge count
 *   on the insight feed tab.
 *
 * • {@code isDismissed} allows the user to permanently hide an insight
 *   without deleting the row (preserving the audit trail for the AI
 *   feedback loop). Dismissed insights are excluded from the active
 *   feed query but remain queryable for analytics.
 *
 * • {@code expiresAt} is an optional TTL — some insights are time-
 *   sensitive (e.g. "Your resting HR has been elevated for 3 days") and
 *   should be auto-expired from the feed. The Repository/WorkManager
 *   purge worker uses this field to clean up stale insights. Null means
 *   the insight never auto-expires.
 *
 * • {@code confidenceScore} is the AI model's confidence in the insight
 *   (0.0–1.0). Used internally to suppress low-confidence insights below
 *   a configurable threshold and to display a "How sure is VitalAI?"
 *   indicator in the detail view. Nullable — rule-based insights
 *   generated without a probabilistic model omit this field.
 *
 * • Four indexes are declared:
 *     1. (user_id, created_at DESC) — primary feed query: "latest N
 *        insights for user X", the main insight feed load pattern.
 *     2. (user_id, category)        — category-filtered feed: "all SLEEP
 *        insights for user X" for the domain-filtered tab views.
 *     3. (user_id, severity)        — severity-filtered queries: fast
 *        lookup of CRITICAL / WARNING insights for the alert pipeline
 *        and notification badge.
 *     4. (user_id, is_read)         — unread count queries: drives the
 *        badge on the insight feed tab without a full table scan.
 *
 * Architecture layer : Data / Local
 * Table name         : insights
 * Related DAOs       : InsightDao
 * Parent FK          : users.id  (CASCADE delete + update)
 */
@Entity(
        tableName = "insights",
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
                // Primary feed load: latest insights for a user
                @Index(value = {"user_id", "created_at"}),
                // Domain-filtered tab views
                @Index(value = {"user_id", "category"}),
                // Alert pipeline and notification badge
                @Index(value = {"user_id", "severity"}),
                // Unread badge count without full table scan
                @Index(value = {"user_id", "is_read"})
        }
)
public class InsightEntity {

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
    // Classification
    // ──────────────────────────────────────────────────────────────────────

    /**
     * The broad health domain this insight addresses.
     * Stored as TEXT via TypeConverters.
     *
     * Expected values (defined in {@link InsightCategory}):
     *   HEART_RATE    — HR / HRV anomalies, resting HR trends
     *   BLOOD_PRESSURE— BP patterns, hypertension risk signals
     *   SLEEP         — sleep quality, stage deficits, consistency
     *   WORKOUT       — training load, recovery, performance trends
     *   NUTRITION     — calorie balance, hydration, glucose patterns
     *   MEDICATION    — adherence reminders, interaction warnings
     *   STRESS        — HRV-based stress index, recovery readiness
     *   GENERAL       — cross-domain or onboarding insights
     *
     * Used to route the insight to the correct detail screen and to
     * filter the feed by domain in the category tab bar.
     */
    @NonNull
    @ColumnInfo(name = "category")
    public InsightCategory category;

    /**
     * Clinical urgency / notification priority of this insight.
     * Stored as TEXT via TypeConverters.
     *
     * Expected values (defined in {@link InsightSeverity}):
     *   INFO     — informational nudge, no immediate action required
     *   WARNING  — elevated concern, user should review soon
     *   CRITICAL — urgent, may require medical attention; triggers a
     *              high-priority FCM push notification immediately
     *
     * The notification dispatch layer in the Repository checks this
     * field after every insert to decide whether to fire a push.
     */
    @NonNull
    @ColumnInfo(name = "severity")
    public InsightSeverity severity;

    // ──────────────────────────────────────────────────────────────────────
    // Content
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Short human-readable summary of the insight (≤ 80 characters).
     * Used as the notification title and the insight card headline.
     * Examples:
     *   "Resting HR elevated for 3 consecutive days"
     *   "Deep sleep below recommended threshold this week"
     *   "New personal best: 5K pace improved by 12 seconds"
     */
    @NonNull
    @ColumnInfo(name = "title")
    public String title;

    /**
     * Full explanation of the insight with context, data evidence, and
     * an actionable recommendation. Rendered in the insight detail view.
     * No length constraint — may include multiple paragraphs.
     * Examples:
     *   "Your resting heart rate has averaged 82 bpm over the last 3 days,
     *    up from your 30-day baseline of 68 bpm. Elevated resting HR can
     *    indicate stress, dehydration, or the onset of illness. Consider
     *    reviewing your sleep quality and hydration levels. If this persists
     *    for more than 5 days, consult your physician."
     */
    @NonNull
    @ColumnInfo(name = "body")
    public String body;

    // ──────────────────────────────────────────────────────────────────────
    // Provenance — Triggering Data
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Optional soft reference to the primary {@link com.vitalai.app.domain.model.enums.MetricType}
     * that triggered this insight, stored as the enum's name() string.
     *
     * Examples: "HEART_RATE", "HEART_RATE_VARIABILITY", "BLOOD_OXYGEN_SPO2"
     *
     * Nullable — insights derived from aggregated or cross-metric signals
     * (e.g. a stress score combining HRV + sleep + activity) may not map
     * cleanly to a single metric type.
     *
     * Used by the detail screen's "View related data" deep-link to open
     * the corresponding metric chart pre-filtered to the relevant period.
     */
    @Nullable
    @ColumnInfo(name = "related_metric_type")
    public String relatedMetricType;

    /**
     * PK of the specific row in {@link #relatedEntityTable} that most
     * directly triggered this insight.
     *
     * Examples:
     *   42  → sleep_sessions row 42  (a poor-sleep insight)
     *   17  → workouts row 17        (a training-load insight)
     *   301 → health_metrics row 301 (a single anomalous reading)
     *
     * Nullable — insight may be derived from an aggregate trend rather
     * than a single identifiable row.
     *
     * A typed Room FK is intentionally avoided here; one nullable FK
     * column per possible child table would be unscalable. The Repository
     * resolves the reference using {@link #relatedEntityTable} as a
     * discriminator at read time.
     */
    @Nullable
    @ColumnInfo(name = "related_entity_id")
    public Long relatedEntityId;

    /**
     * Name of the table containing the row identified by
     * {@link #relatedEntityId}. Acts as a polymorphic discriminator.
     *
     * Expected values:
     *   "sleep_sessions" | "workouts" | "health_metrics" |
     *   "medications"    | "health_conditions"
     *
     * Nullable — null when {@link #relatedEntityId} is also null.
     * The Repository must validate that this string matches an actual
     * table name before attempting a lookup.
     */
    @Nullable
    @ColumnInfo(name = "related_entity_table")
    public String relatedEntityTable;

    // ──────────────────────────────────────────────────────────────────────
    // AI Model Metadata
    // ──────────────────────────────────────────────────────────────────────

    /**
     * The AI model's confidence in this insight, expressed as a
     * probability in [0.0, 1.0].
     *
     * 0.0 — maximally uncertain (insight at chance level).
     * 1.0 — fully certain (deterministic rule-based insight).
     *
     * Insights below the user-configurable threshold stored in
     * {@code notification_preferences} are suppressed before insertion.
     * Displayed as a "How sure is VitalAI?" indicator in the detail view
     * to support informed user trust calibration.
     *
     * Nullable — rule-based insights (e.g. medication reminder past due)
     * generated without a probabilistic model omit this field.
     */
    @Nullable
    @ColumnInfo(name = "confidence_score")
    public Double confidenceScore;

    /**
     * Identifier of the model version that generated this insight.
     * Used for A/B testing, post-hoc accuracy auditing, and rollback
     * decisions when a model update degrades insight quality.
     * Examples: "tflite-v2.3.1", "claude-sonnet-4-20250514", "rule-engine-v1"
     *
     * Nullable — legacy rows inserted before model versioning was added
     * will have a null value here.
     */
    @Nullable
    @ColumnInfo(name = "model_version")
    public String modelVersion;

    // ──────────────────────────────────────────────────────────────────────
    // User Interaction State
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Whether the user has opened or acknowledged this insight card.
     * Defaults to false at insert time.
     * Set to true by the Repository when the user taps the card in the
     * feed or opens the detail view.
     * Drives the unread badge count on the insight feed tab.
     * SQLite stores as INTEGER (0 = false, 1 = true).
     */
    @ColumnInfo(name = "is_read", defaultValue = "0")
    public boolean isRead;

    /**
     * Whether the user has permanently dismissed this insight from the
     * active feed. Defaults to false.
     * Set to true by the Repository when the user swipes the card away
     * or taps "Don't show again".
     *
     * Dismissed insights are excluded from the active feed query
     * ({@code WHERE is_dismissed = 0}) but retained in the table for:
     *   • AI feedback loop training (negative signal).
     *   • Audit trail and user data export.
     *   • Re-surfacing if the underlying condition worsens again.
     *
     * SQLite stores as INTEGER (0 = false, 1 = true).
     */
    @ColumnInfo(name = "is_dismissed", defaultValue = "0")
    public boolean isDismissed;

    // ──────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Optional TTL after which this insight is auto-expired from the
     * active feed by the WorkManager purge worker.
     *
     * Null — insight never auto-expires (e.g. a landmark personal best,
     * a chronic condition warning, or a medication interaction alert that
     * must persist until dismissed by the user).
     *
     * Non-null examples:
     *   • "Your HR was elevated during last night's sleep" → expires
     *     after 24 hours (no longer actionable the following day).
     *   • "Drink more water today" → expires at midnight.
     *
     * The purge worker sets {@link #isDismissed} = true (rather than
     * deleting the row) when {@code NOW() > expiresAt}, preserving the
     * audit trail while removing the insight from the active feed.
     *
     * Stored as epoch millis via TypeConverters.
     */
    @Nullable
    @ColumnInfo(name = "expires_at")
    public Date expiresAt;

    // ──────────────────────────────────────────────────────────────────────
    // Timestamps
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant when this insight was first written to the local DB.
     * Set once at insert time; never modified thereafter.
     * Used as the sort key for the primary feed query (latest first).
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "created_at")
    public Date createdAt;

    /**
     * UTC instant of the most recent update to this row.
     * Updated when {@link #isRead}, {@link #isDismissed}, or
     * {@link #confidenceScore} are revised after initial insert.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "updated_at")
    public Date updatedAt;

    // ──────────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Full constructor used by the Repository when inserting a new insight.
     * Room uses direct field assignment when reading rows back from the DB.
     *
     * @param userId              Local PK of the owning {@link UserEntity}.
     * @param category            Health domain of the insight (required).
     * @param severity            Clinical urgency level (required).
     * @param title               Short summary ≤ 80 chars (required).
     * @param body                Full explanation and recommendation (required).
     * @param relatedMetricType   Triggering MetricType name, or null.
     * @param relatedEntityId     PK of the triggering row, or null.
     * @param relatedEntityTable  Table of the triggering row, or null.
     * @param confidenceScore     Model confidence [0.0–1.0], or null.
     * @param modelVersion        Model identifier string, or null.
     * @param isRead              Read state (false at insert time).
     * @param isDismissed         Dismissed state (false at insert time).
     * @param expiresAt           TTL instant, or null if non-expiring.
     * @param createdAt           Insert timestamp (set by Repository).
     * @param updatedAt           Last-update timestamp (set by Repository).
     */
    public InsightEntity(
            long userId,
            @NonNull InsightCategory category,
            @NonNull InsightSeverity severity,
            @NonNull String title,
            @NonNull String body,
            @Nullable String relatedMetricType,
            @Nullable Long relatedEntityId,
            @Nullable String relatedEntityTable,
            @Nullable Double confidenceScore,
            @Nullable String modelVersion,
            boolean isRead,
            boolean isDismissed,
            @Nullable Date expiresAt,
            @NonNull Date createdAt,
            @NonNull Date updatedAt) {

        this.userId             = userId;
        this.category           = category;
        this.severity           = severity;
        this.title              = title;
        this.body               = body;
        this.relatedMetricType  = relatedMetricType;
        this.relatedEntityId    = relatedEntityId;
        this.relatedEntityTable = relatedEntityTable;
        this.confidenceScore    = confidenceScore;
        this.modelVersion       = modelVersion;
        this.isRead             = isRead;
        this.isDismissed        = isDismissed;
        this.expiresAt          = expiresAt;
        this.createdAt          = createdAt;
        this.updatedAt          = updatedAt;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Convenience factories
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Factory for a fully specified insight — the standard path used by
     * the AI pipeline when it has all metadata available.
     * {@code isRead} and {@code isDismissed} default to false.
     * Timestamps are set to now automatically.
     *
     * @param userId              Local PK of the owning user.
     * @param category            Health domain of the insight.
     * @param severity            Clinical urgency level.
     * @param title               Short card headline (≤ 80 chars).
     * @param body                Full explanation and recommendation.
     * @param relatedMetricType   Triggering MetricType name, or null.
     * @param relatedEntityId     PK of the triggering row, or null.
     * @param relatedEntityTable  Table of the triggering row, or null.
     * @param confidenceScore     Model confidence [0.0–1.0], or null.
     * @param modelVersion        Model identifier string, or null.
     * @param expiresAt           TTL instant, or null if non-expiring.
     * @return Ready-to-insert {@link InsightEntity}.
     */
    public static InsightEntity create(
            long userId,
            @NonNull InsightCategory category,
            @NonNull InsightSeverity severity,
            @NonNull String title,
            @NonNull String body,
            @Nullable String relatedMetricType,
            @Nullable Long relatedEntityId,
            @Nullable String relatedEntityTable,
            @Nullable Double confidenceScore,
            @Nullable String modelVersion,
            @Nullable Date expiresAt) {

        Date now = new Date();
        return new InsightEntity(
                userId, category, severity, title, body,
                relatedMetricType, relatedEntityId, relatedEntityTable,
                confidenceScore, modelVersion,
                false, false, expiresAt, now, now);
    }

    /**
     * Factory for a rule-based insight where no probabilistic confidence
     * score exists — e.g. a deterministic medication reminder ("You have
     * not logged Metoprolol today") or a threshold-breach alert
     * ("SpO₂ reading below 90%"). Model version defaults to the rule
     * engine identifier.
     *
     * @param userId             Local PK of the owning user.
     * @param category           Health domain of the insight.
     * @param severity           Clinical urgency level.
     * @param title              Short card headline (≤ 80 chars).
     * @param body               Full explanation and recommendation.
     * @param relatedMetricType  Triggering MetricType name, or null.
     * @param relatedEntityId    PK of the triggering row, or null.
     * @param relatedEntityTable Table of the triggering row, or null.
     * @param expiresAt          TTL instant, or null if non-expiring.
     * @return Ready-to-insert {@link InsightEntity}.
     */
    public static InsightEntity createRuleBased(
            long userId,
            @NonNull InsightCategory category,
            @NonNull InsightSeverity severity,
            @NonNull String title,
            @NonNull String body,
            @Nullable String relatedMetricType,
            @Nullable Long relatedEntityId,
            @Nullable String relatedEntityTable,
            @Nullable Date expiresAt) {

        return create(userId, category, severity, title, body,
                relatedMetricType, relatedEntityId, relatedEntityTable,
                null, "rule-engine-v1", expiresAt);
    }

    /**
     * Minimal factory for a simple informational insight with no
     * provenance linkage — used for onboarding nudges, goal milestone
     * celebrations, and general wellness tips where no specific row
     * or metric type triggered the content.
     *
     * @param userId   Local PK of the owning user.
     * @param category Health domain of the insight.
     * @param title    Short card headline (≤ 80 chars).
     * @param body     Full explanation and recommendation.
     * @return Ready-to-insert {@link InsightEntity}.
     */
    public static InsightEntity createGeneral(
            long userId,
            @NonNull InsightCategory category,
            @NonNull String title,
            @NonNull String body) {

        return create(userId, category, InsightSeverity.INFO, title, body,
                null, null, null, null, null, null);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Marks this insight as read and stamps {@link #updatedAt}.
     * Call this in the Repository when the user opens the insight card
     * or detail view, then pass the entity to {@code InsightDao.update()}.
     */
    public void markAsRead() {
        this.isRead    = true;
        this.updatedAt = new Date();
    }

    /**
     * Marks this insight as dismissed and stamps {@link #updatedAt}.
     * The row is retained for the AI feedback loop and audit trail;
     * the active feed query filters it out via {@code WHERE is_dismissed = 0}.
     * Call this in the Repository when the user swipes the card away,
     * then pass the entity to {@code InsightDao.update()}.
     */
    public void dismiss() {
        this.isDismissed = true;
        this.isRead      = true; // dismissing implicitly acknowledges the insight
        this.updatedAt   = new Date();
    }

    /**
     * Returns true if this insight has passed its TTL and should be
     * treated as expired by the active feed query and purge worker.
     * A null {@link #expiresAt} means the insight never expires.
     */
    public boolean isExpired() {
        return expiresAt != null && new Date().after(expiresAt);
    }

    /**
     * Refreshes {@code updated_at} to the current instant.
     * Call this in the Repository before any partial update via
     * {@code InsightDao.update()}.
     */
    public void touch() {
        this.updatedAt = new Date();
    }

    @Override
    public String toString() {
        return "InsightEntity{"
                + "id=" + id
                + ", userId=" + userId
                + ", category=" + category
                + ", severity=" + severity
                + ", title='" + title + '\''
                + ", relatedMetricType='" + relatedMetricType + '\''
                + ", relatedEntityTable='" + relatedEntityTable + '\''
                + ", relatedEntityId=" + relatedEntityId
                + ", confidenceScore=" + confidenceScore
                + ", modelVersion='" + modelVersion + '\''
                + ", isRead=" + isRead
                + ", isDismissed=" + isDismissed
                + ", expiresAt=" + expiresAt
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + '}';
    }
}