package com.vitalai.app.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.vitalai.app.data.local.converter.Converters;
import com.vitalai.app.data.local.dao.AchievementDao;
import com.vitalai.app.data.local.dao.ChatMessageDao;
import com.vitalai.app.data.local.dao.DeviceDao;
import com.vitalai.app.data.local.dao.GoalDao;
import com.vitalai.app.data.local.dao.HealthConditionDao;
import com.vitalai.app.data.local.dao.HealthMetricDao;
import com.vitalai.app.data.local.dao.HydrationLogDao;
import com.vitalai.app.data.local.dao.InsightDao;
import com.vitalai.app.data.local.dao.MedicationDao;
import com.vitalai.app.data.local.dao.ModelMetadataDao;
import com.vitalai.app.data.local.dao.MoodLogDao;
import com.vitalai.app.data.local.dao.NotificationPreferenceDao;
import com.vitalai.app.data.local.dao.UserBaselineDao;
import com.vitalai.app.data.local.dao.SleepSessionDao;
import com.vitalai.app.data.local.dao.SleepStageDao;
import com.vitalai.app.data.local.dao.UserDao;
import com.vitalai.app.data.local.dao.WorkoutDao;
import com.vitalai.app.data.local.entity.AchievementEntity;
import com.vitalai.app.data.local.entity.ChatMessageEntity;
import com.vitalai.app.data.local.entity.DeviceEntity;
import com.vitalai.app.data.local.entity.GoalEntity;
import com.vitalai.app.data.local.entity.HealthConditionEntity;
import com.vitalai.app.data.local.entity.HealthMetricEntity;
import com.vitalai.app.data.local.entity.HydrationLogEntity;
import com.vitalai.app.data.local.entity.InsightEntity;
import com.vitalai.app.data.local.entity.MedicationEntity;
import com.vitalai.app.data.local.entity.ModelMetadataEntity;
import com.vitalai.app.data.local.entity.MoodLogEntity;
import com.vitalai.app.data.local.entity.NotificationPreferenceEntity;
import com.vitalai.app.data.local.entity.SleepSessionEntity;
import com.vitalai.app.data.local.entity.SleepStageEntity;
import com.vitalai.app.data.local.entity.UserBaselineEntity;
import com.vitalai.app.data.local.entity.UserEntity;
import com.vitalai.app.data.local.entity.WorkoutEntity;

/**
 * VitalAIDatabase
 *
 * The single Room database for the VitalAI application.
 *
 * Architecture notes
 * ──────────────────
 * • This class is the central entry point for all local persistence.
 *   It lists all 17 Room entities and wires the shared {@link TypeConverters}.
 *
 * • The singleton instance is provided by Hilt via {@code AppModule}; do NOT
 *   call {@link androidx.room.Room#databaseBuilder} anywhere else in the app.
 *
 * • Version history
 *   ──────────────────────────────────────────────────────────────────────
 *   Version 1 — Initial schema (all 17 entities). 2025-xx-xx
 *
 *   MIGRATION STRATEGY (add entries here as the schema evolves):
 *   ──────────────────────────────────────────────────────────────────────
 *   When the schema changes (new column, new table, index modification, etc.):
 *     1. Increment {@code version} in the @Database annotation.
 *     2. Create a {@code Migration} constant in a companion object or in a
 *        dedicated {@code DatabaseMigrations} class.
 *     3. Register it via {@code .addMigrations(MIGRATION_X_Y)} in
 *        {@code AppModule#provideVitalAIDatabase}.
 *     4. Remove {@code .fallbackToDestructiveMigration()} once at least one
 *        explicit Migration is registered, or keep it guarded behind
 *        {@code BuildConfig.DEBUG} only.
 *
 *   Example skeleton:
 *   <pre>
 *     static final Migration MIGRATION_1_2 = new Migration(1, 2) {
 *         {@literal @}Override
 *         public void migrate(@NonNull SupportSQLiteDatabase db) {
 *             db.execSQL("ALTER TABLE users ADD COLUMN timezone TEXT");
 *         }
 *     };
 *   </pre>
 *
 * • SQLCipher integration is wired in {@code AppModule} via
 *   {@code SupportFactory}. See the commented block in that module for the
 *   passphrase / key-management wiring that must be completed before release.
 *
 * Architecture layer : Data / Local
 * Database file name : vitalai_database.db
 * Provided by        : Hilt — {@code AppModule#provideVitalAIDatabase}
 */
@Database(
        entities = {
                // ── Core user ──────────────────────────────────────────────
                UserEntity.class,

                // ── Health data ─────────────────────────────────────────────
                HealthMetricEntity.class,
                HealthConditionEntity.class,
                MedicationEntity.class,

                // ── Sleep ───────────────────────────────────────────────────
                SleepSessionEntity.class,
                SleepStageEntity.class,

                // ── Activity ────────────────────────────────────────────────
                WorkoutEntity.class,
                HydrationLogEntity.class,

                // ── Mental wellness ─────────────────────────────────────────
                MoodLogEntity.class,

                // ── AI & insights ───────────────────────────────────────────
                InsightEntity.class,
                ChatMessageEntity.class,
                ModelMetadataEntity.class,
                UserBaselineEntity.class,

                // ── Goals & gamification ────────────────────────────────────
                GoalEntity.class,
                AchievementEntity.class,

                // ── Devices & notifications ─────────────────────────────────
                DeviceEntity.class,
                NotificationPreferenceEntity.class
        },
        version = 1,
        exportSchema = true          // generates JSON schema files for version control
)
@TypeConverters(Converters.class)
public abstract class VitalAIDatabase extends RoomDatabase {

    // ──────────────────────────────────────────────────────────────────────
    // DAO accessors — Sprint 1
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns the {@link HealthMetricDao} for querying and mutating the
     * {@code health_metrics} table.
     *
     * @return Room-generated implementation of {@link HealthMetricDao}.
     */
    public abstract HealthMetricDao healthMetricDao();

    public abstract UserDao userDao();

    public abstract HealthConditionDao healthConditionDao();

    public abstract MedicationDao medicationDao();

    public abstract SleepSessionDao sleepSessionDao();

    public abstract SleepStageDao sleepStageDao();

    public abstract WorkoutDao workoutDao();

    public abstract InsightDao insightDao();

    public abstract ChatMessageDao chatMessageDao();

    public abstract GoalDao goalDao();

    public abstract AchievementDao achievementDao();

    public abstract HydrationLogDao hydrationLogDao();

    public abstract MoodLogDao moodLogDao();

    public abstract DeviceDao deviceDao();

    public abstract ModelMetadataDao modelMetadataDao();

    public abstract UserBaselineDao userBaselineDao();

    public abstract NotificationPreferenceDao notificationPreferenceDao();

    // ──────────────────────────────────────────────────────────────────────
    // DAO accessors — TODO: add in future sprints
    // ──────────────────────────────────────────────────────────────────────
    //
    // Uncomment and implement each DAO as its sprint is completed.
    // Follow the same pattern: public abstract XxxDao xxxDao();
    //
    // public abstract UserDao userDao();
    // public abstract HealthConditionDao healthConditionDao();
    // public abstract MedicationDao medicationDao();
    // public abstract SleepSessionDao sleepSessionDao();
    // public abstract SleepStageDao sleepStageDao();
    // public abstract WorkoutDao workoutDao();
    // public abstract HydrationLogDao hydrationLogDao();
    // public abstract MoodLogDao moodLogDao();
    // public abstract InsightDao insightDao();
    // public abstract ChatMessageDao chatMessageDao();
    // public abstract ModelMetadataDao modelMetadataDao();
    // public abstract UserBaselineDao userBaselineDao();
    // public abstract GoalDao goalDao();
    // public abstract AchievementDao achievementDao();
    // public abstract DeviceDao deviceDao();
    // public abstract NotificationPreferenceDao notificationPreferenceDao();
}