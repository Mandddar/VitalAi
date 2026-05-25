package com.vitalai.app;

import android.app.Application;
import android.util.Log;

import androidx.work.Configuration;

import com.google.firebase.FirebaseApp;
import com.vitalai.app.data.local.dao.UserDao;
import com.vitalai.app.data.local.entity.UserEntity;
import com.vitalai.app.util.SyntheticDataGenerator;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@HiltAndroidApp
public class VitalAIApplication extends Application
        implements Configuration.Provider {

    private static final String TAG = "VitalAIApplication";

    @Inject
    androidx.hilt.work.HiltWorkerFactory workerFactory;

    @Inject
    SyntheticDataGenerator syntheticDataGenerator;

    @Inject
    UserDao userDao;

    @Override
    public void onCreate() {
        super.onCreate();
        initFirebase();
        initWorkManager();
        seedSyntheticData();
        Log.d(TAG, "VitalAI application initialised successfully.");
    }

    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(
                        BuildConfig.DEBUG ? Log.DEBUG : Log.ERROR)
                .build();
    }

    private void initFirebase() {
        FirebaseApp.initializeApp(this);
        Log.d(TAG, "Firebase initialised.");
    }

    private void initWorkManager() {
        Log.d(TAG, "WorkManager configured with HiltWorkerFactory.");
    }

    private void seedSyntheticData() {
        Log.d(TAG, "Seeding: inserting seed user...");

        UserEntity seedUser = UserEntity.create("Demo User", "demo@vitalai.app");
        seedUser.firebaseUid = "synthetic_seed_user"; // ← ADD THIS LINE

        userDao.insert(seedUser)
                .subscribeOn(Schedulers.io())
                .flatMapCompletable(userId -> {
                    if (userId == -1L) {
                        Log.d(TAG, "Seed user already exists, skipping generation.");
                        return Completable.complete();
                    }
                    Log.d(TAG, "Seed user inserted with id=" + userId + ". Generating metrics...");
                    return syntheticDataGenerator.generate(userId);
                })
                .subscribe(
                        () -> Log.d(TAG, "Synthetic data seeding complete."),
                        throwable -> Log.e(TAG, "Synthetic data seeding failed: "
                                + throwable.getMessage(), throwable)
                );
    }
}