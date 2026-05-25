package com.vitalai.app.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.vitalai.app.data.local.converter.Converters;
import com.vitalai.app.domain.model.enums.AlertType;

import java.util.Date;

/**
 * NotificationPreferenceEntity
 *
 * Room entity representing a row in the {@code notification_preferences} table.
 * Each row stores the user's opt-in/out and scheduling settings for one discrete
 * notification category ({@link AlertType}) — the single source of truth that the
 * WorkManager scheduling layer, the FCM push pipeline, and the in-app alert router
 * all consult before firing any notification at the user.
 *
 * <p>Purpose
 * ─────────
 * VitalAI generates notifications from several independent pipelines:
 * <ul>
 *   <li>The TFLite anomaly-detection worker (abnormal HR, SpO₂, BP)</li>
 *   <li>The AI insight generator (CRITICAL / WARNING {@link InsightEntity} rows)</li>
 *   <li>The WorkManager medication-reminder job</li>
 *   <li>The goal-deadline and goal-completion workers</li>
 *   <li>The hydration-nudge scheduler</li>
 *   <li>The achievement-unlock celebratory notification</li>
 *   <li>The weekly health summary digest</li>
 * </ul>
 * Rather than scattering preference booleans across multiple tables, this entity
 * centralises every per-category opt-in flag and scheduling constraint in one place.
 * One row per (user_id, alert_type) covers the full notification surface.
 *
 * <p>Design notes
 * ────────────
 * <ul>
 *   <li>Foreign key to {@link UserEntity} uses ON DELETE CASCADE — deleting
 *       a user automatically removes all their preference rows without any
 *       manual Repository cleanup.</li>
 *
 *   <li>A (user_id, alert_type) UNIQUE index enforces one preference row per
 *       notification category per user. The Repository performs an upsert
 *       (insert-or-replace) on first use; subsequent writes are updates.
 *       Missing rows are treated as "default settings apply" by the
 *       notification layer so the absence of a row never silently blocks
 *       a notification — it merely means the user has not yet customised
 *       that category.</li>
 *
 *   <li>{@code alertType} maps to the {@link AlertType} enum stored as TEXT
 *       via {@link Converters}.
 *       One row per value covers the complete notification surface.
 *       Examples:
 *       <ul>
 *         <li>ANOMALY_HEART_RATE     — abnormal HR detected by TFLite</li>
 *         <li>ANOMALY_BLOOD_PRESSURE — abnormal BP reading</li>
 *         <li>ANOMALY_SPO2          — low blood-oxygen alert</li>
 *         <li>INSIGHT_CRITICAL      — CRITICAL severity InsightEntity</li>
 *         <li>INSIGHT_WARNING       — WARNING severity InsightEntity</li>
 *         <li>INSIGHT_INFO          — informational InsightEntity digest</li>
 *         <li>MEDICATION_REMINDER   — scheduled medication dose reminder</li>
 *         <li>GOAL_DEADLINE         — approaching goal target date</li>
 *         <li>GOAL_COMPLETED        — goal reached / completed</li>
 *         <li>HYDRATION_NUDGE       — periodic hydration reminder</li>
 *         <li>ACHIEVEMENT_UNLOCKED  — new trophy / achievement earned</li>
 *         <li>WEEKLY_SUMMARY        — weekly health digest notification</li>
 *         <li>DEVICE_SYNC           — BLE device sync status alerts</li>
 *         <li>STREAK_AT_RISK        — streak about to break nudge</li>
 *       </ul>
 *   </li>
 *
 *   <li>{@code isEnabled} is the master opt-in flag for this category.
 *       When false, no notification of this type is ever sent — regardless
 *       of all other fields. The notification layer checks this flag first;
 *       a false value short-circuits all further scheduling logic.
 *       Defaults to true (opted in) at insert time for all categories,
 *       matching the onboarding experience where all notifications start
 *       enabled and the user selectively turns off unwanted categories.</li>
 *
 *   <li>{@code channelPush} and {@code channelInApp} are independent
 *       delivery-channel toggles. Both can be true simultaneously: the user
 *       may want a push notification AND an in-app alert card for anomalies,
 *       but only an in-app card (no push) for informational insights.
 *       Both default to true at insert time. At least one channel should
 *       remain enabled when {@code isEnabled} is true; the UI enforces this
 *       constraint so the DB layer does not need to.</li>
 *
 *   <li>{@code quietHoursStart} and {@code quietHoursEnd} define a daily
 *       window during which non-urgent notifications are suppressed.
 *       Stored as "HH:mm" 24-hour strings (e.g. "22:30", "07:00") parsed
 *       by the WorkManager scheduling layer at runtime. Nullable — null
 *       means no quiet-hours restriction applies to this category.
 *       Critical/anomaly alert types ({@code ANOMALY_*}, {@code INSIGHT_CRITICAL})
 *       should ignore quiet hours entirely; this is enforced in the
 *       notification router, not the DB.
 *       Using plain strings rather than storing minutes-since-midnight integers
 *       keeps the values human-readable and avoids timezone complications —
 *       the scheduling layer always interprets these times in the device's
 *       local timezone.</li>
 *
 *   <li>{@code frequencyCap} is the maximum number of times this notification
 *       category may fire within the {@code frequencyCapWindowHours} window.
 *       Nullable — null means no cap (fire every time the trigger condition
 *       is met). Used to prevent notification fatigue for high-frequency
 *       triggers such as hydration nudges (cap at 3 per day) or anomaly
 *       alerts (cap at 1 per hour per metric type).
 *       The WorkManager notification-rate-limiter consults this field and
 *       tracks fire counts in a separate in-memory cache (not persisted —
 *       resets on app restart, which is intentional).</li>
 *
 *   <li>{@code frequencyCapWindowHours} is the rolling window (in hours)
 *       over which {@code frequencyCap} is enforced. Nullable — must be
 *       non-null when {@code frequencyCap} is non-null; the Repository
 *       validates this pairing. Typical values: 1 (anomaly rate limiting),
 *       24 (daily hydration nudge cap), 168 (weekly summary).</li>
 *
 *   <li>{@code minSeverityLevel} is an optional floor on the
 *       {@link InsightSeverity} of {@link InsightEntity} rows that trigger
 *       push notifications for insight-type alert categories. Stored as a
 *       free-form TEXT label matching {@link InsightSeverity} enum names.
 *       Nullable — applies only to {@code INSIGHT_*} alert types; ignored
 *       for non-insight categories. When set, the notification router
 *       suppresses push delivery for insights below this severity while
 *       still writing the in-app card. Example: a user sets
 *       {@code minSeverityLevel = "WARNING"} to block INFO insight pushes
 *       but still receive CRITICAL and WARNING alerts.
 *       Recommended values: "INFO", "WARNING", "CRITICAL".</li>
 *
 *   <li>{@code vibrate} and {@code sound} are per-category Android
 *       notification behaviour flags. On Android 8+ (API 26+) these are
 *       enforced at the NotificationChannel level — the Repository must
 *       recreate the channel with revised settings whenever either flag
 *       changes. On older APIs they are applied per-notification via
 *       {@code NotificationCompat.Builder}. Both default to true.</li>
 *
 *   <li>{@code lastNotifiedAt} is the UTC instant this category last fired
 *       a notification (push or in-app). Written by the notification
 *       dispatcher immediately after delivery. Used by the rate-limiter as
 *       the window anchor for {@code frequencyCap} enforcement and by the
 *       UI to display "last notified N minutes ago" in the preferences
 *       detail screen. Nullable — null until the first notification fires
 *       for this category. Stored as epoch millis via TypeConverters.</li>
 *
 *   <li>Three indexes are declared:
 *       <ol>
 *         <li>(user_id, alert_type) UNIQUE — one preference per category
 *             per user; serves the fast "get preference for user X, type Y"
 *             lookup made by the notification router on every potential
 *             alert fire.</li>
 *         <li>(user_id, is_enabled) — lets the WorkManager scheduling
 *             bootstrap query fetch all enabled preference rows for a user
 *             in one scan when (re)scheduling WorkManager jobs after
 *             app launch or after a user changes notification settings.</li>
 *         <li>(user_id, last_notified_at) — supports rate-limiter window
 *             queries: "for user X, which categories fired within the last
 *             N hours?" without a full table scan.</li>
 *       </ol>
 *   </li>
 * </ul>
 *
 * <p>Architecture layer : Data / Local
 * <br>Table name         : notification_preferences
 * <br>Related DAOs       : NotificationPreferenceDao
 * <br>Parent FK          : users.id  (CASCADE delete + update)
 */
@Entity(
        tableName = "notification_preferences",
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
                // Uniqueness guard + fast "get preference for category Y" lookup
                @Index(value = {"user_id", "alert_type"}, unique = true),
                // WorkManager bootstrap: fetch all enabled categories for a user
                @Index(value = {"user_id", "is_enabled"}),
                // Rate-limiter window query: categories fired within last N hours
                @Index(value = {"user_id", "last_notified_at"})
        }
)
public class NotificationPreferenceEntity {

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
    // Notification Category
    // ──────────────────────────────────────────────────────────────────────

    /**
     * The notification category these preferences govern.
     * Stored as TEXT via {@link Converters}.
     *
     * <p>Together with {@code userId}, this forms the unique key of the table.
     * One row per enum value covers the full VitalAI notification surface:
     * <ul>
     *   <li>{@code ANOMALY_HEART_RATE}    — TFLite HR anomaly alert</li>
     *   <li>{@code ANOMALY_BLOOD_PRESSURE}— TFLite BP anomaly alert</li>
     *   <li>{@code ANOMALY_SPO2}          — Low blood-oxygen alert</li>
     *   <li>{@code INSIGHT_CRITICAL}      — CRITICAL severity insight push</li>
     *   <li>{@code INSIGHT_WARNING}       — WARNING severity insight push</li>
     *   <li>{@code INSIGHT_INFO}          — INFO insight digest</li>
     *   <li>{@code MEDICATION_REMINDER}   — Scheduled dose reminder</li>
     *   <li>{@code GOAL_DEADLINE}         — Approaching goal target date</li>
     *   <li>{@code GOAL_COMPLETED}        — Goal reached / completed</li>
     *   <li>{@code HYDRATION_NUDGE}       — Periodic hydration reminder</li>
     *   <li>{@code ACHIEVEMENT_UNLOCKED}  — New trophy earned</li>
     *   <li>{@code WEEKLY_SUMMARY}        — Weekly health digest</li>
     *   <li>{@code DEVICE_SYNC}           — BLE device sync status</li>
     *   <li>{@code STREAK_AT_RISK}        — Streak about to break nudge</li>
     * </ul>
     */
    @NonNull
    @ColumnInfo(name = "alert_type")
    public AlertType alertType;

    // ──────────────────────────────────────────────────────────────────────
    // Master Enable Flag
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Master opt-in flag for this notification category.
     *
     * <p>FALSE — no notification of this type is ever sent. The notification
     * router short-circuits immediately on a false value, bypassing all
     * scheduling, channel, and rate-limiting logic.
     * TRUE  — notifications may fire for this category, subject to the
     * channel, quiet-hours, frequency-cap, and severity constraints below.
     *
     * <p>Defaults to true (opted in) at insert time for all categories.
     * The onboarding flow enables every category by default; users
     * selectively disable unwanted categories in the Notification Settings
     * screen.
     *
     * <p>SQLite stores as INTEGER (0 = false, 1 = true).
     */
    @ColumnInfo(name = "is_enabled", defaultValue = "1")
    public boolean isEnabled;

    // ──────────────────────────────────────────────────────────────────────
    // Delivery Channels
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Whether this category is delivered as an FCM push notification.
     *
     * <p>When true, the WorkManager notification dispatcher calls
     * {@code NotificationManager.notify()} and, for critical categories,
     * also fires an FCM data message via the backend so the notification
     * reaches the device even if the app is in the background or killed.
     *
     * <p>On Android 8+ (API 26+), toggling this flag requires recreating
     * the associated {@code NotificationChannel} with updated importance
     * settings. The Repository must trigger a channel rebuild in the
     * NotificationChannelManager whenever this field changes.
     *
     * <p>Defaults to true. SQLite stores as INTEGER (0 = false, 1 = true).
     */
    @ColumnInfo(name = "channel_push", defaultValue = "1")
    public boolean channelPush;

    /**
     * Whether this category is delivered as an in-app alert card.
     *
     * <p>When true, a notification card is inserted into the in-app
     * notification tray (the bell-icon feed in the main navigation bar)
     * in addition to — or instead of — a system push notification.
     * The UI observes the notification table via LiveData/Flow and
     * refreshes the tray badge reactively.
     *
     * <p>In-app delivery is the preferred channel for low-urgency
     * categories (e.g. {@code INSIGHT_INFO}, {@code WEEKLY_SUMMARY})
     * where a system push would feel intrusive but the user still
     * benefits from seeing the content in-app.
     *
     * <p>Defaults to true. SQLite stores as INTEGER (0 = false, 1 = true).
     */
    @ColumnInfo(name = "channel_in_app", defaultValue = "1")
    public boolean channelInApp;

    // ──────────────────────────────────────────────────────────────────────
    // Quiet Hours
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Local-time start of the daily quiet-hours window, in 24-hour "HH:mm"
     * format (e.g. {@code "22:30"}).
     *
     * <p>During [quietHoursStart, quietHoursEnd), non-urgent notifications
     * of this category are suppressed. The WorkManager scheduler checks
     * the current device local time against this window before firing.
     *
     * <p><b>Critical categories ignore quiet hours</b> — the notification
     * router bypasses this field entirely for {@code ANOMALY_*} and
     * {@code INSIGHT_CRITICAL} alert types, since safety-relevant alerts
     * must always reach the user immediately.
     *
     * <p>Nullable — null means no quiet-hours window applies to this
     * category. Must be non-null when {@link #quietHoursEnd} is non-null;
     * the Repository validates this pairing.
     *
     * <p>Stored as plain TEXT interpreted in the device's local timezone
     * by the scheduling layer. Using "HH:mm" strings avoids timezone
     * ambiguity (no epoch offset needed) and is directly human-readable
     * in DB inspection tools.
     *
     * <p>Example: {@code "22:30"} — quiet hours start at 10:30 PM.
     */
    @Nullable
    @ColumnInfo(name = "quiet_hours_start")
    public String quietHoursStart;

    /**
     * Local-time end of the daily quiet-hours window, in 24-hour "HH:mm"
     * format (e.g. {@code "07:00"}).
     *
     * <p>Paired with {@link #quietHoursStart} to define the suppression
     * window. Supports overnight windows: if {@code quietHoursEnd} is
     * earlier in the day than {@code quietHoursStart}, the window spans
     * midnight (e.g. 22:30 → 07:00). The WorkManager scheduler handles
     * this midnight-crossing case explicitly.
     *
     * <p>Nullable — must be non-null when {@link #quietHoursStart} is
     * non-null. The Repository validates this pairing.
     *
     * <p>Example: {@code "07:00"} — quiet hours end at 7:00 AM.
     */
    @Nullable
    @ColumnInfo(name = "quiet_hours_end")
    public String quietHoursEnd;

    // ──────────────────────────────────────────────────────────────────────
    // Frequency Cap
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Maximum number of times this notification category may fire within
     * the rolling {@link #frequencyCapWindowHours} window.
     *
     * <p>Used to prevent notification fatigue from high-frequency triggers.
     * Examples:
     * <ul>
     *   <li>HYDRATION_NUDGE    : cap = 3, window = 24h → at most 3 nudges/day</li>
     *   <li>ANOMALY_HEART_RATE : cap = 1, window = 1h  → at most 1 alert/hour</li>
     *   <li>INSIGHT_INFO       : cap = 2, window = 24h → at most 2 info pushes/day</li>
     * </ul>
     *
     * <p>The WorkManager rate-limiter tracks fire counts in an in-memory
     * counter (not persisted — resets on app restart, which is intentional
     * to avoid permanently blocking safety alerts after a crash). It consults
     * {@link #lastNotifiedAt} as the window anchor to determine whether the
     * cap has been reached.
     *
     * <p>Nullable — null means no cap; the category fires every time the
     * trigger condition is met (appropriate for {@code MEDICATION_REMINDER}
     * and {@code ACHIEVEMENT_UNLOCKED} where every event is user-meaningful).
     * Must be non-null when {@link #frequencyCapWindowHours} is non-null;
     * the Repository validates this pairing.
     */
    @Nullable
    @ColumnInfo(name = "frequency_cap")
    public Integer frequencyCap;

    /**
     * The rolling window in hours over which {@link #frequencyCap} is enforced.
     *
     * <p>Nullable — must be non-null when {@link #frequencyCap} is non-null.
     *
     * <p>Typical values:
     * <ul>
     *   <li>1   — per-hour cap for anomaly alerts</li>
     *   <li>24  — daily cap for hydration nudges and info insights</li>
     *   <li>168 — weekly cap for summary digests</li>
     * </ul>
     */
    @Nullable
    @ColumnInfo(name = "frequency_cap_window_hours")
    public Integer frequencyCapWindowHours;

    // ──────────────────────────────────────────────────────────────────────
    // Insight Severity Filter
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Minimum {@link com.vitalai.app.domain.model.enums.InsightSeverity}
     * level required for a push notification to fire for insight-type
     * alert categories ({@code INSIGHT_CRITICAL}, {@code INSIGHT_WARNING},
     * {@code INSIGHT_INFO}).
     *
     * <p>When set, the notification router suppresses push delivery for
     * {@link InsightEntity} rows whose severity is below this threshold,
     * while still writing the in-app notification card so the content
     * remains discoverable in the tray.
     *
     * <p>Example: a user sets {@code minSeverityLevel = "WARNING"} to
     * block {@code INFO} insight pushes but still receive {@code WARNING}
     * and {@code CRITICAL} alerts as system notifications.
     *
     * <p>Recommended values (matching {@code InsightSeverity} enum names):
     * {@code "INFO"}, {@code "WARNING"}, {@code "CRITICAL"}.
     * Stored as free-form TEXT rather than a typed enum reference so that
     * adding new severity levels in the future does not require a migration
     * of existing preference rows.
     *
     * <p>Nullable — applies only to {@code INSIGHT_*} alert types. The
     * notification router ignores this field for all non-insight categories.
     * When null for an insight category, all severities trigger push delivery.
     */
    @Nullable
    @ColumnInfo(name = "min_severity_level")
    public String minSeverityLevel;

    // ──────────────────────────────────────────────────────────────────────
    // Android Notification Behaviour
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Whether notifications in this category should vibrate the device.
     *
     * <p>On Android 8+ (API 26+), this is enforced at the
     * {@code NotificationChannel} level — toggling this flag requires the
     * Repository to notify the {@code NotificationChannelManager} so it
     * can call {@code channel.enableVibration(vibrate)} and recreate the
     * channel. On older APIs it is applied per-notification via
     * {@code NotificationCompat.Builder.setVibrate()}.
     *
     * <p>Defaults to true. SQLite stores as INTEGER (0 = false, 1 = true).
     */
    @ColumnInfo(name = "vibrate", defaultValue = "1")
    public boolean vibrate;

    /**
     * Whether notifications in this category should play an audible sound.
     *
     * <p>On Android 8+ (API 26+), managed at the {@code NotificationChannel}
     * level via {@code channel.setSound()}. The Repository must trigger a
     * channel rebuild in the {@code NotificationChannelManager} whenever
     * this field changes. On older APIs it is applied per-notification via
     * {@code NotificationCompat.Builder.setSound()}.
     *
     * <p>Defaults to true. SQLite stores as INTEGER (0 = false, 1 = true).
     */
    @ColumnInfo(name = "sound", defaultValue = "1")
    public boolean sound;

    // ──────────────────────────────────────────────────────────────────────
    // Delivery History
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant at which the most recent notification in this category
     * was delivered (push or in-app).
     *
     * <p>Written by the notification dispatcher immediately after a
     * successful delivery attempt. Used by the WorkManager rate-limiter
     * as the rolling-window anchor for {@link #frequencyCap} enforcement.
     * Also displayed in the Notification Settings detail screen as
     * "Last notified N minutes ago" to help users understand how often
     * a given category fires.
     *
     * <p>Nullable — null until the first notification fires for this
     * category (i.e. a preference row may exist but have never yet
     * delivered a notification, e.g. for {@code ACHIEVEMENT_UNLOCKED}
     * on a brand-new account).
     *
     * <p>Stored as epoch millis via TypeConverters.
     */
    @Nullable
    @ColumnInfo(name = "last_notified_at")
    public Date lastNotifiedAt;

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
     * Refreshed by the Repository on every preference change or after
     * recording a delivery in {@link #lastNotifiedAt}.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "updated_at")
    public Date updatedAt;

    // ──────────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Full constructor used by the Repository when inserting or upserting a
     * preference row. Room uses direct field assignment when reading rows
     * back from the DB.
     *
     * @param userId                   Local PK of the owning {@link UserEntity}.
     * @param alertType                Notification category (required).
     * @param isEnabled                Master opt-in flag.
     * @param channelPush              True if push notifications are enabled.
     * @param channelInApp             True if in-app alert cards are enabled.
     * @param quietHoursStart          Start of quiet-hours window ("HH:mm"), or null.
     * @param quietHoursEnd            End of quiet-hours window ("HH:mm"), or null.
     * @param frequencyCap             Max fires per window, or null for no cap.
     * @param frequencyCapWindowHours  Window size in hours, or null when no cap.
     * @param minSeverityLevel         Minimum insight severity for push, or null.
     * @param vibrate                  True if vibration is enabled.
     * @param sound                    True if sound is enabled.
     * @param lastNotifiedAt           Most recent delivery instant, or null.
     * @param createdAt                Insert timestamp (set by Repository).
     * @param updatedAt                Last-update timestamp (set by Repository).
     */
    public NotificationPreferenceEntity(
            long userId,
            @NonNull AlertType alertType,
            boolean isEnabled,
            boolean channelPush,
            boolean channelInApp,
            @Nullable String quietHoursStart,
            @Nullable String quietHoursEnd,
            @Nullable Integer frequencyCap,
            @Nullable Integer frequencyCapWindowHours,
            @Nullable String minSeverityLevel,
            boolean vibrate,
            boolean sound,
            @Nullable Date lastNotifiedAt,
            @NonNull Date createdAt,
            @NonNull Date updatedAt) {

        this.userId                  = userId;
        this.alertType               = alertType;
        this.isEnabled               = isEnabled;
        this.channelPush             = channelPush;
        this.channelInApp            = channelInApp;
        this.quietHoursStart         = quietHoursStart;
        this.quietHoursEnd           = quietHoursEnd;
        this.frequencyCap            = frequencyCap;
        this.frequencyCapWindowHours = frequencyCapWindowHours;
        this.minSeverityLevel        = minSeverityLevel;
        this.vibrate                 = vibrate;
        this.sound                   = sound;
        this.lastNotifiedAt          = lastNotifiedAt;
        this.createdAt               = createdAt;
        this.updatedAt               = updatedAt;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Convenience factories
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Factory for a fully-enabled default preference row — all notifications
     * on, both channels active, no quiet hours, no frequency cap, vibrate
     * and sound on. This is the standard row created during onboarding for
     * every {@link AlertType} before the user has customised anything.
     * Timestamps set to now.
     *
     * @param userId    Local PK of the owning user.
     * @param alertType Notification category.
     * @return Ready-to-insert {@link NotificationPreferenceEntity}.
     */
    public static NotificationPreferenceEntity createDefault(
            long userId,
            @NonNull AlertType alertType) {

        Date now = new Date();
        return new NotificationPreferenceEntity(
                userId, alertType,
                /*isEnabled=*/           true,
                /*channelPush=*/         true,
                /*channelInApp=*/        true,
                /*quietHoursStart=*/     null,
                /*quietHoursEnd=*/       null,
                /*frequencyCap=*/        null,
                /*frequencyCapWindowHours=*/ null,
                /*minSeverityLevel=*/    null,
                /*vibrate=*/             true,
                /*sound=*/               true,
                /*lastNotifiedAt=*/      null,
                now, now);
    }

    /**
     * Factory for a critical-alert preference row — always enabled on both
     * channels, no quiet hours (critical alerts bypass quiet hours in the
     * router regardless, but storing null here makes the intent explicit),
     * rate-limited to one fire per hour to prevent alert flooding.
     * Vibrate and sound always on for critical safety alerts.
     * Timestamps set to now.
     *
     * <p>Suitable for: {@code ANOMALY_HEART_RATE}, {@code ANOMALY_BLOOD_PRESSURE},
     * {@code ANOMALY_SPO2}, {@code INSIGHT_CRITICAL}.
     *
     * @param userId    Local PK of the owning user.
     * @param alertType Notification category (should be a critical type).
     * @return Ready-to-insert {@link NotificationPreferenceEntity}.
     */
    public static NotificationPreferenceEntity createCriticalAlert(
            long userId,
            @NonNull AlertType alertType) {

        Date now = new Date();
        return new NotificationPreferenceEntity(
                userId, alertType,
                /*isEnabled=*/               true,
                /*channelPush=*/             true,
                /*channelInApp=*/            true,
                /*quietHoursStart=*/         null,
                /*quietHoursEnd=*/           null,
                /*frequencyCap=*/            1,
                /*frequencyCapWindowHours=*/ 1,
                /*minSeverityLevel=*/        null,
                /*vibrate=*/                 true,
                /*sound=*/                   true,
                /*lastNotifiedAt=*/          null,
                now, now);
    }

    /**
     * Factory for a nudge-style preference row — enabled, push and in-app
     * both on, with a standard overnight quiet-hours window (22:00–07:00)
     * and a daily frequency cap to prevent notification fatigue.
     * Suitable for: {@code HYDRATION_NUDGE}, {@code STREAK_AT_RISK}.
     * Timestamps set to now.
     *
     * @param userId       Local PK of the owning user.
     * @param alertType    Notification category (should be a nudge type).
     * @param dailyCap     Maximum fires per 24-hour window.
     * @return Ready-to-insert {@link NotificationPreferenceEntity}.
     */
    public static NotificationPreferenceEntity createNudge(
            long userId,
            @NonNull AlertType alertType,
            int dailyCap) {

        Date now = new Date();
        return new NotificationPreferenceEntity(
                userId, alertType,
                /*isEnabled=*/               true,
                /*channelPush=*/             true,
                /*channelInApp=*/            true,
                /*quietHoursStart=*/         "22:00",
                /*quietHoursEnd=*/           "07:00",
                /*frequencyCap=*/            dailyCap,
                /*frequencyCapWindowHours=*/ 24,
                /*minSeverityLevel=*/        null,
                /*vibrate=*/                 true,
                /*sound=*/                   false,
                /*lastNotifiedAt=*/          null,
                now, now);
    }

    /**
     * Factory for a silent-digest preference row — enabled but push-only
     * with sound and vibrate off, a weekly frequency cap, and a quiet-hours
     * window so the weekly summary never wakes the user at night.
     * Suitable for: {@code WEEKLY_SUMMARY}, {@code INSIGHT_INFO}.
     * Timestamps set to now.
     *
     * @param userId    Local PK of the owning user.
     * @param alertType Notification category.
     * @return Ready-to-insert {@link NotificationPreferenceEntity}.
     */
    public static NotificationPreferenceEntity createSilentDigest(
            long userId,
            @NonNull AlertType alertType) {

        Date now = new Date();
        return new NotificationPreferenceEntity(
                userId, alertType,
                /*isEnabled=*/               true,
                /*channelPush=*/             true,
                /*channelInApp=*/             true,
                /*quietHoursStart=*/         "22:00",
                /*quietHoursEnd=*/           "08:00",
                /*frequencyCap=*/            1,
                /*frequencyCapWindowHours=*/ 168,
                /*minSeverityLevel=*/        "INFO",
                /*vibrate=*/                 false,
                /*sound=*/                   false,
                /*lastNotifiedAt=*/          null,
                now, now);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Disables this notification category entirely and stamps
     * {@link #updatedAt}. Call this in the Repository when the user
     * toggles a category off in the Notification Settings screen, then
     * persist via {@code NotificationPreferenceDao.update()}.
     * The WorkManager scheduler should cancel all pending jobs for this
     * category after this call.
     */
    public void disable() {
        this.isEnabled  = false;
        this.updatedAt  = new Date();
    }

    /**
     * Re-enables this notification category and stamps {@link #updatedAt}.
     * Call this in the Repository when the user toggles a category back on,
     * then persist via {@code NotificationPreferenceDao.update()}.
     * The WorkManager scheduler should reschedule jobs for this category
     * after this call.
     */
    public void enable() {
        this.isEnabled  = true;
        this.updatedAt  = new Date();
    }

    /**
     * Sets or clears the quiet-hours window and stamps {@link #updatedAt}.
     * Pass null for both parameters to remove quiet hours entirely.
     * The Repository must validate that both params are either both
     * non-null or both null before persisting.
     *
     * @param start Quiet-hours start in "HH:mm" format, or null to clear.
     * @param end   Quiet-hours end in "HH:mm" format, or null to clear.
     */
    public void setQuietHours(@Nullable String start, @Nullable String end) {
        this.quietHoursStart = start;
        this.quietHoursEnd   = end;
        this.updatedAt       = new Date();
    }

    /**
     * Updates the frequency cap settings and stamps {@link #updatedAt}.
     * Pass null for both parameters to remove the cap entirely.
     * The Repository must validate that both params are either both
     * non-null or both null before persisting.
     *
     * @param cap         Maximum fires per window, or null to remove cap.
     * @param windowHours Rolling window size in hours, or null to remove cap.
     */
    public void setFrequencyCap(@Nullable Integer cap, @Nullable Integer windowHours) {
        this.frequencyCap            = cap;
        this.frequencyCapWindowHours = windowHours;
        this.updatedAt               = new Date();
    }

    /**
     * Records a successful notification delivery — stamps
     * {@link #lastNotifiedAt} and {@link #updatedAt} to the current instant.
     * Call this in the notification dispatcher immediately after a push or
     * in-app card is successfully delivered, then persist via
     * {@code NotificationPreferenceDao.update()}.
     */
    public void recordDelivery() {
        Date now            = new Date();
        this.lastNotifiedAt = now;
        this.updatedAt      = now;
    }

    /**
     * Returns whether a new notification may fire right now according to
     * the frequency cap. Returns {@code true} (fire allowed) when:
     * <ul>
     *   <li>No cap is configured ({@link #frequencyCap} is null), or</li>
     *   <li>This category has never fired ({@link #lastNotifiedAt} is null), or</li>
     *   <li>The time elapsed since {@link #lastNotifiedAt} exceeds the cap
     *       window ({@link #frequencyCapWindowHours}).</li>
     * </ul>
     *
     * <p><b>Note:</b> this helper checks only the window-boundary condition.
     * The full rate-limiter in WorkManager additionally tracks the fire count
     * within the current window for caps > 1 using an in-memory counter.
     * This method is a fast pre-check suitable for single-fire-per-window caps.
     *
     * @return {@code true} if delivery is permitted by the frequency cap.
     */
    public boolean isWithinFrequencyCap() {
        if (frequencyCap == null || frequencyCapWindowHours == null) {
            return true;
        }
        if (lastNotifiedAt == null) {
            return true;
        }
        long windowMs      = frequencyCapWindowHours * 3_600_000L;
        long elapsedMs     = System.currentTimeMillis() - lastNotifiedAt.getTime();
        return elapsedMs >= windowMs;
    }

    /**
     * Refreshes {@code updated_at} to the current instant.
     * Call this in the Repository before any partial update via
     * {@code NotificationPreferenceDao.update()}.
     */
    public void touch() {
        this.updatedAt = new Date();
    }

    @Override
    public String toString() {
        return "NotificationPreferenceEntity{"
                + "id=" + id
                + ", userId=" + userId
                + ", alertType=" + alertType
                + ", isEnabled=" + isEnabled
                + ", channelPush=" + channelPush
                + ", channelInApp=" + channelInApp
                + ", quietHoursStart='" + quietHoursStart + '\''
                + ", quietHoursEnd='" + quietHoursEnd + '\''
                + ", frequencyCap=" + frequencyCap
                + ", frequencyCapWindowHours=" + frequencyCapWindowHours
                + ", minSeverityLevel='" + minSeverityLevel + '\''
                + ", vibrate=" + vibrate
                + ", sound=" + sound
                + ", lastNotifiedAt=" + lastNotifiedAt
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + '}';
    }
}