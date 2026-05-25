package com.vitalai.app.data.local.converter;

import androidx.annotation.Nullable;
import androidx.room.TypeConverter;

import com.vitalai.app.domain.model.enums.ActivityLevel;
import com.vitalai.app.domain.model.enums.AlertType;
import com.vitalai.app.domain.model.enums.BloodType;
import com.vitalai.app.domain.model.enums.ChatRole;
import com.vitalai.app.domain.model.enums.DeviceType;
import com.vitalai.app.domain.model.enums.GoalStatus;
import com.vitalai.app.domain.model.enums.GoalType;
import com.vitalai.app.domain.model.enums.HydrationSource;
import com.vitalai.app.domain.model.enums.InsightCategory;
import com.vitalai.app.domain.model.enums.InsightSeverity;
import com.vitalai.app.domain.model.enums.MetricSource;
import com.vitalai.app.domain.model.enums.MetricType;
import com.vitalai.app.domain.model.enums.PrimaryGoal;
import com.vitalai.app.domain.model.enums.Sex;
import com.vitalai.app.domain.model.enums.SleepPreference;
import com.vitalai.app.domain.model.enums.SleepStage;

import java.util.Date;

/**
 * Converters
 *
 * Registered at the {@link androidx.room.Database} level via
 * {@code @TypeConverters(TypeConverters.class)} so every DAO and entity
 * in VitalAIDatabase can use them without per-entity registration.
 *
 * Strategy
 * ────────
 * • {@link Date}  ↔  {@code long}  (epoch milliseconds — timezone-agnostic,
 *   sortable, and directly comparable in SQL WHERE / ORDER BY clauses).
 * • All enums ↔ {@code String}  (name()-based — survives obfuscation when
 *   ProGuard keeps enum names, human-readable in DB browser, and tolerant
 *   of value reordering unlike ordinal-based storage).
 *
 * Null safety
 * ───────────
 * Every converter explicitly handles {@code null} inputs and returns
 * {@code null} outputs so Room can store optional fields correctly.
 *
 * IMPORTANT — @Nullable annotations
 * ──────────────────────────────────
 * All converter methods that can accept or return null MUST carry
 * {@code @Nullable} on their parameters and return types. Room's KSP
 * processor uses these annotations to decide whether to generate
 * null-check guards ({@code if (x == null) bindNull() else bindString()})
 * in the DAO implementation. Without {@code @Nullable}, the generated
 * code calls {@code bindString()} directly, which throws an NPE when the
 * converted value is null.
 *
 * Adding new enums
 * ────────────────
 * 1. Define the enum in {@code com.vitalai.app.domain.model.enums}.
 * 2. Add a pair of {@code @TypeConverter} methods below following the
 *    existing pattern — one {@code String → Enum}, one {@code Enum → String}.
 * 3. No other registration is needed; the class-level @TypeConverters on
 *    VitalAIDatabase covers the entire database.
 *
 * Architecture layer : Data / Local
 */
public final class Converters {

    private Converters() {}

    // ──────────────────────────────────────────────────────────────────────
    // Date ↔ Long
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    @Nullable
    public static Long fromDate(@Nullable Date date) {
        return date == null ? null : date.getTime();
    }

    @TypeConverter
    @Nullable
    public static Date toDate(@Nullable Long epochMillis) {
        return epochMillis == null ? null : new Date(epochMillis);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Sex
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    @Nullable
    public static String fromSex(@Nullable Sex sex) {
        return sex == null ? null : sex.name();
    }

    @TypeConverter
    @Nullable
    public static Sex toSex(@Nullable String value) {
        return value == null ? null : Sex.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // BloodType
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    @Nullable
    public static String fromBloodType(@Nullable BloodType bloodType) {
        return bloodType == null ? null : bloodType.name();
    }

    @TypeConverter
    @Nullable
    public static BloodType toBloodType(@Nullable String value) {
        return value == null ? null : BloodType.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // ActivityLevel
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    @Nullable
    public static String fromActivityLevel(@Nullable ActivityLevel level) {
        return level == null ? null : level.name();
    }

    @TypeConverter
    @Nullable
    public static ActivityLevel toActivityLevel(@Nullable String value) {
        return value == null ? null : ActivityLevel.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // SleepPreference
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    @Nullable
    public static String fromSleepPreference(@Nullable SleepPreference pref) {
        return pref == null ? null : pref.name();
    }

    @TypeConverter
    @Nullable
    public static SleepPreference toSleepPreference(@Nullable String value) {
        return value == null ? null : SleepPreference.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // PrimaryGoal
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    @Nullable
    public static String fromPrimaryGoal(@Nullable PrimaryGoal goal) {
        return goal == null ? null : goal.name();
    }

    @TypeConverter
    @Nullable
    public static PrimaryGoal toPrimaryGoal(@Nullable String value) {
        return value == null ? null : PrimaryGoal.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // MetricType
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    @Nullable
    public static String fromMetricType(@Nullable MetricType type) {
        return type == null ? null : type.name();
    }

    @TypeConverter
    @Nullable
    public static MetricType toMetricType(@Nullable String value) {
        return value == null ? null : MetricType.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // MetricSource
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    @Nullable
    public static String fromMetricSource(@Nullable MetricSource source) {
        return source == null ? null : source.name();
    }

    @TypeConverter
    @Nullable
    public static MetricSource toMetricSource(@Nullable String value) {
        return value == null ? null : MetricSource.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // SleepStage
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    @Nullable
    public static String fromSleepStage(@Nullable SleepStage stage) {
        return stage == null ? null : stage.name();
    }

    @TypeConverter
    @Nullable
    public static SleepStage toSleepStage(@Nullable String value) {
        return value == null ? null : SleepStage.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // InsightCategory
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    @Nullable
    public static String fromInsightCategory(@Nullable InsightCategory category) {
        return category == null ? null : category.name();
    }

    @TypeConverter
    @Nullable
    public static InsightCategory toInsightCategory(@Nullable String value) {
        return value == null ? null : InsightCategory.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // InsightSeverity
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    @Nullable
    public static String fromInsightSeverity(@Nullable InsightSeverity severity) {
        return severity == null ? null : severity.name();
    }

    @TypeConverter
    @Nullable
    public static InsightSeverity toInsightSeverity(@Nullable String value) {
        return value == null ? null : InsightSeverity.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // ChatRole
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    @Nullable
    public static String fromChatRole(@Nullable ChatRole role) {
        return role == null ? null : role.name();
    }

    @TypeConverter
    @Nullable
    public static ChatRole toChatRole(@Nullable String value) {
        return value == null ? null : ChatRole.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // GoalType
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    @Nullable
    public static String fromGoalType(@Nullable GoalType type) {
        return type == null ? null : type.name();
    }

    @TypeConverter
    @Nullable
    public static GoalType toGoalType(@Nullable String value) {
        return value == null ? null : GoalType.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // GoalStatus
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    @Nullable
    public static String fromGoalStatus(@Nullable GoalStatus status) {
        return status == null ? null : status.name();
    }

    @TypeConverter
    @Nullable
    public static GoalStatus toGoalStatus(@Nullable String value) {
        return value == null ? null : GoalStatus.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // HydrationSource
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    @Nullable
    public static String fromHydrationSource(@Nullable HydrationSource source) {
        return source == null ? null : source.name();
    }

    @TypeConverter
    @Nullable
    public static HydrationSource toHydrationSource(@Nullable String value) {
        return value == null ? null : HydrationSource.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // DeviceType
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    @Nullable
    public static String fromDeviceType(@Nullable DeviceType type) {
        return type == null ? null : type.name();
    }

    @TypeConverter
    @Nullable
    public static DeviceType toDeviceType(@Nullable String value) {
        return value == null ? null : DeviceType.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // AlertType
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    @Nullable
    public static String fromAlertType(@Nullable AlertType type) {
        return type == null ? null : type.name();
    }

    @TypeConverter
    @Nullable
    public static AlertType toAlertType(@Nullable String value) {
        return value == null ? null : AlertType.valueOf(value);
    }
}