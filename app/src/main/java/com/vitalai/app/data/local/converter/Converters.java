package com.vitalai.app.data.local.converter;

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
    public static Long fromDate(Date date) {
        return date == null ? null : date.getTime();
    }

    @TypeConverter
    public static Date toDate(Long epochMillis) {
        return epochMillis == null ? null : new Date(epochMillis);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Sex
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    public static String fromSex(Sex sex) {
        return sex == null ? null : sex.name();
    }

    @TypeConverter
    public static Sex toSex(String value) {
        return value == null ? null : Sex.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // BloodType
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    public static String fromBloodType(BloodType bloodType) {
        return bloodType == null ? null : bloodType.name();
    }

    @TypeConverter
    public static BloodType toBloodType(String value) {
        return value == null ? null : BloodType.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // ActivityLevel
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    public static String fromActivityLevel(ActivityLevel level) {
        return level == null ? null : level.name();
    }

    @TypeConverter
    public static ActivityLevel toActivityLevel(String value) {
        return value == null ? null : ActivityLevel.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // SleepPreference
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    public static String fromSleepPreference(SleepPreference pref) {
        return pref == null ? null : pref.name();
    }

    @TypeConverter
    public static SleepPreference toSleepPreference(String value) {
        return value == null ? null : SleepPreference.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // PrimaryGoal
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    public static String fromPrimaryGoal(PrimaryGoal goal) {
        return goal == null ? null : goal.name();
    }

    @TypeConverter
    public static PrimaryGoal toPrimaryGoal(String value) {
        return value == null ? null : PrimaryGoal.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // MetricType
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    public static String fromMetricType(MetricType type) {
        return type == null ? null : type.name();
    }

    @TypeConverter
    public static MetricType toMetricType(String value) {
        return value == null ? null : MetricType.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // MetricSource
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    public static String fromMetricSource(MetricSource source) {
        return source == null ? null : source.name();
    }

    @TypeConverter
    public static MetricSource toMetricSource(String value) {
        return value == null ? null : MetricSource.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // SleepStage
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    public static String fromSleepStage(SleepStage stage) {
        return stage == null ? null : stage.name();
    }

    @TypeConverter
    public static SleepStage toSleepStage(String value) {
        return value == null ? null : SleepStage.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // InsightCategory
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    public static String fromInsightCategory(InsightCategory category) {
        return category == null ? null : category.name();
    }

    @TypeConverter
    public static InsightCategory toInsightCategory(String value) {
        return value == null ? null : InsightCategory.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // InsightSeverity
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    public static String fromInsightSeverity(InsightSeverity severity) {
        return severity == null ? null : severity.name();
    }

    @TypeConverter
    public static InsightSeverity toInsightSeverity(String value) {
        return value == null ? null : InsightSeverity.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // ChatRole
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    public static String fromChatRole(ChatRole role) {
        return role == null ? null : role.name();
    }

    @TypeConverter
    public static ChatRole toChatRole(String value) {
        return value == null ? null : ChatRole.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // GoalType
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    public static String fromGoalType(GoalType type) {
        return type == null ? null : type.name();
    }

    @TypeConverter
    public static GoalType toGoalType(String value) {
        return value == null ? null : GoalType.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // GoalStatus
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    public static String fromGoalStatus(GoalStatus status) {
        return status == null ? null : status.name();
    }

    @TypeConverter
    public static GoalStatus toGoalStatus(String value) {
        return value == null ? null : GoalStatus.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // HydrationSource
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    public static String fromHydrationSource(HydrationSource source) {
        return source == null ? null : source.name();
    }

    @TypeConverter
    public static HydrationSource toHydrationSource(String value) {
        return value == null ? null : HydrationSource.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // DeviceType
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    public static String fromDeviceType(DeviceType type) {
        return type == null ? null : type.name();
    }

    @TypeConverter
    public static DeviceType toDeviceType(String value) {
        return value == null ? null : DeviceType.valueOf(value);
    }

    // ──────────────────────────────────────────────────────────────────────
    // AlertType
    // ──────────────────────────────────────────────────────────────────────

    @TypeConverter
    public static String fromAlertType(AlertType type) {
        return type == null ? null : type.name();
    }

    @TypeConverter
    public static AlertType toAlertType(String value) {
        return value == null ? null : AlertType.valueOf(value);
    }
}