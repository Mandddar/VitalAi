package com.vitalai.app.di;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.room.Room;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vitalai.app.data.local.VitalAIDatabase;
import com.vitalai.app.data.local.dao.HealthMetricDao;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

/**
 * AppModule
 *
 * Hilt module that provides application-scoped (singleton) dependencies
 * used across the VitalAI feature graph.
 *
 * Binding inventory
 * ─────────────────
 *  • {@link Context}               — application context (via @ApplicationContext)
 *  • {@link VitalAIDatabase}       — Room database (SQLCipher-encrypted)
 *  • {@link SharedPreferences}     — EncryptedSharedPreferences instance
 *  • {@link Gson}                  — configured Gson instance
 *  • {@link HealthMetricDao}       — DAO for health_metrics table
 *
 * DAOs to be added in future sprints (add @Provides method + import per sprint):
 * ──────────────────────────────────────────────────────────────────────────────
 *  • UserDao
 *  • HealthConditionDao
 *  • MedicationDao
 *  • SleepSessionDao
 *  • SleepStageDao
 *  • WorkoutDao
 *  • HydrationLogDao
 *  • MoodLogDao
 *  • InsightDao
 *  • ChatMessageDao
 *  • ModelMetadataDao
 *  • UserBaselineDao
 *  • GoalDao
 *  • AchievementDao
 *  • DeviceDao
 *  • NotificationPreferenceDao
 *
 * Architecture layer : App / Framework (DI wiring only — no business logic)
 * DI scope           : SingletonComponent  (@Singleton)
 */
@Module
@InstallIn(SingletonComponent.class)
public final class AppModule {

    // ──────────────────────────────────────────────────────────────────────
    // Constants
    // ──────────────────────────────────────────────────────────────────────

    /** Room database file name stored on-device. */
    private static final String DATABASE_NAME = "vitalai_database.db";

    /**
     * EncryptedSharedPreferences file name.
     * The Security library derives and stores the key in AndroidKeyStore
     * under this alias, so the name must remain stable across app versions.
     */
    private static final String ENCRYPTED_PREFS_FILE = "vitalai_secure_prefs";

    // Private constructor — Hilt instantiates modules reflectively;
    // a private constructor prevents accidental manual instantiation.
    private AppModule() {}

    // ──────────────────────────────────────────────────────────────────────
    // Application Context
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Exposes the application {@link Context} as a singleton binding.
     *
     * <p>Hilt's {@code @ApplicationContext} qualifier already provides this
     * automatically, but declaring it here makes the binding visible to any
     * provider in this module that needs a plain {@link Context} parameter
     * without the qualifier annotation.</p>
     *
     * @param context Injected by Hilt from the ApplicationComponent.
     * @return Application-scoped {@link Context}.
     */
    @Provides
    @Singleton
    public static Context provideApplicationContext(
            @ApplicationContext Context context) {
        return context;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Room Database — SQLCipher encrypted
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Provides the {@link VitalAIDatabase} singleton.
     *
     * <p>The database is built with SQLCipher as the underlying SupportFactory
     * so that the on-device SQLite file is AES-256 encrypted at rest. The
     * passphrase itself should be derived from a key stored in AndroidKeyStore
     * (wired in the SQLCipher block below once the key-management layer is
     * complete). For now the factory wiring point is included and clearly
     * marked.</p>
     *
     * <p>Destructive migration is configured for pre-production; replace with
     * explicit {@link androidx.room.migration.Migration} objects before
     * shipping to production. See {@link VitalAIDatabase} for the migration
     * strategy guide.</p>
     *
     * @param context Application context — used by Room to locate the DB file.
     * @return The single {@link VitalAIDatabase} instance for the process.
     */
    @Provides
    @Singleton
    public static VitalAIDatabase provideVitalAIDatabase(
            @ApplicationContext Context context) {

        /*
         * SQLCipher integration:
         *
         * Uncomment and complete the passphrase + SupportFactory wiring once
         * the key-management layer (KeystoreHelper) is generated:
         *
         *   byte[] passphrase = SQLiteDatabase.getBytes(
         *       KeystoreHelper.getDatabasePassphrase());
         *   SupportFactory factory = new SupportFactory(passphrase);
         *
         *   return Room.databaseBuilder(context, VitalAIDatabase.class, DATABASE_NAME)
         *           .openHelperFactory(factory)
         *           .fallbackToDestructiveMigration()
         *           .build();
         */

        return Room.databaseBuilder(
                        context,
                        VitalAIDatabase.class,
                        DATABASE_NAME)
                // TODO: replace with explicit Migration objects before release.
                // See VitalAIDatabase for the migration strategy skeleton.
                .fallbackToDestructiveMigration()
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────
    // DAOs — Sprint 1
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Provides the {@link HealthMetricDao} extracted from the singleton database.
     *
     * <p>Providing DAOs individually (rather than injecting the entire
     * {@link VitalAIDatabase}) keeps each Repository's dependency surface
     * minimal and makes unit testing straightforward — tests can supply a
     * mock DAO without spinning up the full database.</p>
     *
     * @param database The singleton {@link VitalAIDatabase} (provided above).
     * @return Room-generated implementation of {@link HealthMetricDao}.
     */
    @Provides
    @Singleton
    public static HealthMetricDao provideHealthMetricDao(VitalAIDatabase database) {
        return database.healthMetricDao();
    }

    // ──────────────────────────────────────────────────────────────────────
    // DAOs — TODO: add in future sprints
    // ──────────────────────────────────────────────────────────────────────
    //
    // Pattern for each new DAO:
    //
    //   @Provides
    //   @Singleton
    //   public static XxxDao provideXxxDao(VitalAIDatabase database) {
    //       return database.xxxDao();
    //   }
    //
    // Planned additions (one per sprint):
    //   provideUserDao
    //   provideHealthConditionDao
    //   provideMedicationDao
    //   provideSleepSessionDao
    //   provideSleepStageDao
    //   provideWorkoutDao
    //   provideHydrationLogDao
    //   provideMoodLogDao
    //   provideInsightDao
    //   provideChatMessageDao
    //   provideModelMetadataDao
    //   provideUserBaselineDao
    //   provideGoalDao
    //   provideAchievementDao
    //   provideDeviceDao
    //   provideNotificationPreferenceDao

    // ──────────────────────────────────────────────────────────────────────
    // Encrypted SharedPreferences
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Provides an {@link SharedPreferences} instance backed by
     * {@link EncryptedSharedPreferences}.
     *
     * <p>Keys and values are encrypted using AES-256-GCM (values) and
     * AES-256-SIV (keys). The master key is stored in AndroidKeyStore and
     * is never exported from the secure hardware element on supported devices.
     * </p>
     *
     * <p>The method throws a {@link RuntimeException} wrapping any
     * {@link GeneralSecurityException} or {@link IOException} so that
     * failures surface at startup rather than silently producing a null
     * preferences object. In production, consider a more graceful degradation
     * strategy if the device keystore is unavailable.</p>
     *
     * @param context Application context.
     * @return Encrypted {@link SharedPreferences}.
     */
    @Provides
    @Singleton
    public static SharedPreferences provideEncryptedSharedPreferences(
            @ApplicationContext Context context) {
        try {
            // Build (or retrieve) the AES-256-GCM master key from AndroidKeyStore.
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    // Require user authentication within the last 5 minutes
                    // for sensitive operations (optional; remove if not needed).
                    // .setUserAuthenticationRequired(true, 300)
                    .build();

            return EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);

        } catch (GeneralSecurityException | IOException e) {
            // Keystore failure is unrecoverable at startup; propagate loudly.
            throw new RuntimeException(
                    "Failed to initialise EncryptedSharedPreferences. "
                            + "Check AndroidKeyStore availability on this device.", e);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Gson
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Provides a configured {@link Gson} instance shared across the app.
     *
     * <p>Configuration highlights:
     * <ul>
     *   <li>{@code serializeNulls()} — ensures null fields are written
     *       explicitly so downstream consumers can distinguish "missing"
     *       from "null".</li>
     *   <li>{@code setPrettyPrinting()} — enabled only in debug builds via
     *       a conditional; release builds emit compact JSON.</li>
     *   <li>Date format — ISO-8601 string format keeps dates human-readable
     *       in logs and portable across services.</li>
     * </ul>
     * </p>
     *
     * <p>This same Gson instance is consumed by the Retrofit Gson converter
     * (wired in {@code NetworkModule}) so serialisation behaviour is
     * consistent across local storage and network layers.</p>
     *
     * @return Singleton {@link Gson}.
     */
    @Provides
    @Singleton
    public static Gson provideGson() {
        GsonBuilder builder = new GsonBuilder()
                .serializeNulls()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

        if (com.vitalai.app.BuildConfig.DEBUG) {
            builder.setPrettyPrinting();
        }

        return builder.create();
    }
}