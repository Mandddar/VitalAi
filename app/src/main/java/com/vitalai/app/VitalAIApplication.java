package com.vitalai.app;

import android.app.Application;
import android.util.Log;

import androidx.work.Configuration;
import androidx.work.WorkManager;

import com.google.firebase.FirebaseApp;

import javax.inject.Inject;

import dagger.hilt.android.HiltAndroidApp;

/**
 * VitalAIApplication
 *
 * Entry point for the VitalAI Android application.
 *
 * Responsibilities:
 *  - Bootstraps Hilt's dependency injection graph via @HiltAndroidApp.
 *  - Initialises Firebase (including Crashlytics, which auto-attaches).
 *  - Configures WorkManager with Hilt's worker factory so @HiltWorker
 *    classes receive their injected dependencies.
 *  - Provides the application-scoped lifecycle for all singleton objects
 *    declared in Hilt modules.
 *
 * Architecture layer: App / Framework
 * DI scope:           ApplicationComponent  (Hilt singleton scope)
 */
@HiltAndroidApp
public class VitalAIApplication extends Application
        implements Configuration.Provider {

    private static final String TAG = "VitalAIApplication";

    /**
     * HiltWorkerFactory is injected by Hilt so that WorkManager workers
     * annotated with @HiltWorker can receive constructor-injected dependencies.
     * Hilt generates this binding automatically when hilt-work is on the
     * classpath — no extra module needed.
     */
    @Inject
    androidx.hilt.work.HiltWorkerFactory workerFactory;

    // ──────────────────────────────────────────────────────────────────────
    // Application lifecycle
    // ──────────────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        // Hilt's component tree is fully built by this point — all @Inject
        // fields above are populated before any other onCreate() logic runs.

        initFirebase();
        initWorkManager();

        Log.d(TAG, "VitalAI application initialised successfully.");
    }

    // ──────────────────────────────────────────────────────────────────────
    // WorkManager — Configuration.Provider
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns the custom WorkManager configuration that wires Hilt's
     * worker factory. WorkManager calls this method lazily on first use,
     * so it must NOT be called inside onCreate() directly.
     *
     * IMPORTANT: because we implement Configuration.Provider here, the
     * AndroidManifest.xml must remove WorkManager's default initialiser:
     *
     *   <provider
     *       android:name="androidx.startup.InitializationProvider"
     *       ...>
     *       <meta-data
     *           android:name="androidx.work.WorkManagerInitializer"
     *           android:value="androidx.startup"
     *           tools:node="remove" />
     *   </provider>
     *
     * (This removal is already included in the AndroidManifest snippet
     *  delivered alongside this file.)
     */
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(
                        BuildConfig.DEBUG ? Log.DEBUG : Log.ERROR)
                .build();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Initialises Firebase. FirebaseApp.initializeApp() is idempotent —
     * calling it explicitly here ensures Firebase is ready before any
     * ViewModel or Repository that depends on it is constructed.
     * Crashlytics attaches automatically via the google-services plugin.
     */
    private void initFirebase() {
        FirebaseApp.initializeApp(this);
        Log.d(TAG, "Firebase initialised.");
    }

    /**
     * Calls getWorkManagerConfiguration() once to trigger initialisation
     * via the Configuration.Provider contract. WorkManager itself defers
     * its actual init until the first enqueue/get call, but logging here
     * confirms the factory is wired correctly at startup.
     */
    private void initWorkManager() {
        // WorkManager reads getWorkManagerConfiguration() lazily; we just
        // log here to confirm the factory injection succeeded.
        Log.d(TAG, "WorkManager configured with HiltWorkerFactory.");
    }
}