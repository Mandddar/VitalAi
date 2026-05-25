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
import com.vitalai.app.data.local.dao.UserDao;

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public final class AppModule {

    private static final String DATABASE_NAME      = "vitalai_database.db";
    private static final String ENCRYPTED_PREFS_FILE = "vitalai_secure_prefs";

    // ── NO private constructor — Hilt requires a visible empty constructor
    //    on non-abstract modules. Since every @Provides method here is static,
    //    Hilt never actually instantiates the class, but the constructor must
    //    still be visible (i.e. the implicit public no-arg default). ──────────

    @Provides
    @Singleton
    public static Context provideApplicationContext(
            @ApplicationContext Context context) {
        return context;
    }

    @Provides
    @Singleton
    public static VitalAIDatabase provideVitalAIDatabase(
            @ApplicationContext Context context) {
        return Room.databaseBuilder(
                        context,
                        VitalAIDatabase.class,
                        DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .enableMultiInstanceInvalidation()  // ← ADD
                .build();
    }

    @Provides
    @Singleton
    public static HealthMetricDao provideHealthMetricDao(VitalAIDatabase database) {
        return database.healthMetricDao();
    }

    @Provides
    @Singleton
    public static UserDao provideUserDao(VitalAIDatabase database) {
        return database.userDao();
    }

    @Provides
    @Singleton
    public static SharedPreferences provideEncryptedSharedPreferences(
            @ApplicationContext Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);

        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(
                    "Failed to initialise EncryptedSharedPreferences. "
                            + "Check AndroidKeyStore availability on this device.", e);
        }
    }

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