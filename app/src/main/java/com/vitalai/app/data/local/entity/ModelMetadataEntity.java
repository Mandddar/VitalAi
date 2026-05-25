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
 * ModelMetadataEntity
 *
 * Room entity representing a row in the {@code model_metadata} table.
 * Each row records the lifecycle state and performance profile of one
 * AI / ML model version deployed to a specific user — covering both
 * on-device TFLite models (anomaly detection, sleep scoring, mood
 * inference, stress index) and cloud model configurations (Claude /
 * Gemini API model versions used for chat and insight generation).
 *
 * Design notes
 * ────────────
 * • Foreign key to {@link UserEntity} uses ON DELETE CASCADE — deleting
 *   a user automatically removes all their model metadata records without
 *   any manual Repository cleanup. Model files on disk are cleaned up
 *   separately by the ModelManager before or after the DB cascade fires.
 *
 * • One row per (user_id, model_key, model_version) — the composite
 *   unique index on these three columns prevents duplicate registrations
 *   of the same model version for the same user. A new row is inserted
 *   whenever a previously unseen version is deployed to a user; old
 *   rows are retained for A/B testing analysis, regression detection,
 *   and rollback audit trails.
 *
 * • {@code modelKey} is a stable programmer-defined identifier for the
 *   model's functional role, independent of version. Used as the
 *   primary lookup key when the inference layer needs "the currently
 *   active anomaly-detection model for user X".
 *   Examples:
 *     "anomaly_detection"   — TFLite HR/SpO₂/BP anomaly classifier
 *     "sleep_scorer"        — TFLite sleep quality regression model
 *     "stress_index"        — TFLite HRV-based stress scoring model
 *     "mood_inference"      — TFLite mood prediction from biometrics
 *     "chat_model"          — Cloud LLM used for the AI chat feature
 *     "insight_generator"   — Cloud LLM used for AI insight generation
 *
 * • {@code modelVersion} is the specific version string of the deployed
 *   model artifact. For TFLite models this follows semantic versioning
 *   (e.g. "2.3.1"); for cloud models it is the API model identifier
 *   (e.g. "claude-sonnet-4-20250514"). Stored as TEXT; no format is
 *   enforced at the DB level — validation lives in the domain layer.
 *
 * • {@code modelType} distinguishes the deployment modality:
 *     "TFLITE_ON_DEVICE"  — binary .tflite file bundled or downloaded
 *     "CLOUD_API"         — remote model called via HTTP API
 *     "RULE_ENGINE"       — deterministic rule-based model (no file)
 *   Stored as free-form TEXT rather than an enum to remain open-ended
 *   as new deployment modalities emerge (e.g. ONNX, Core ML exports).
 *
 * • {@code isActive} marks the version currently being used for
 *   inference. At most one row per (user_id, model_key) should have
 *   isActive = true; the Repository enforces this by clearing the flag
 *   on the previous active row before activating the new one. This
 *   design allows instant rollback: set isActive = false on the current
 *   version and isActive = true on a prior version row — no re-download
 *   needed if the file is still on disk.
 *
 * • {@code filePath} is the absolute path to the .tflite model file in
 *   the app's internal storage (e.g.
 *   "/data/data/com.vitalai.app/files/models/anomaly_v2.3.1.tflite").
 *   Nullable — cloud API models and rule-engine models have no local
 *   file. The ModelManager checks this field before attempting to load
 *   a TFLite interpreter; a null value on a TFLITE_ON_DEVICE row
 *   indicates the download is still pending.
 *
 * • {@code fileSizeBytes} is the size of the model file in bytes.
 *   Displayed in the Model Management settings screen and used by the
 *   storage cleanup worker to identify large unused model files for
 *   deletion. Nullable — same conditions as {@code filePath}.
 *
 * • {@code checksum} is the SHA-256 hex digest of the model file,
 *   verified after download and before each interpreter load to detect
 *   file corruption or tampering. Nullable — same conditions as
 *   {@code filePath}; also null for rule-engine rows.
 *
 * • {@code downloadedAt} is the UTC instant the model file was
 *   fully downloaded and verified. Null until download completes.
 *   Stored as epoch millis via TypeConverters.
 *
 * • {@code activatedAt} is the UTC instant this version was first
 *   set as the active model (isActive flipped to true). Null until
 *   first activation. Retained permanently for the deployment audit
 *   trail even after the model is superseded. Stored as epoch millis.
 *
 * • {@code deprecatedAt} is the UTC instant this version was
 *   superseded by a newer version (isActive flipped to false by the
 *   Repository). Null while the model is still active or pending.
 *   Stored as epoch millis.
 *
 * • Performance metrics ({@code inferenceLatencyMs},
 *   {@code accuracyScore}, {@code f1Score}) are populated by the
 *   on-device evaluation harness after a post-deployment validation
 *   run against the user's personal held-out data subset. Used by the
 *   A/B testing pipeline to compare model versions and by the rollback
 *   logic to automatically revert if accuracy drops below threshold.
 *   All nullable — populated asynchronously; cloud models may omit
 *   latency metrics if the API does not expose them.
 *
 * • {@code totalInferences} and {@code totalErrors} are running counters
 *   incremented by the inference layer on every prediction call. Used
 *   for reliability monitoring: a high error rate triggers an automatic
 *   rollback to the previous stable version and fires a CRITICAL
 *   InsightEntity alerting the user that their AI health monitoring
 *   has been temporarily degraded. Both default to 0.
 *
 * • {@code lastInferenceAt} is the UTC instant of the most recent
 *   successful inference call. Used by the staleness monitor to detect
 *   models that have stopped producing predictions (e.g. due to a
 *   silent crash in the WorkManager worker). Nullable — null until the
 *   first inference is made. Stored as epoch millis.
 *
 * • {@code configJson} is an optional JSON blob storing model-specific
 *   runtime configuration that varies per user or per deployment:
 *   anomaly threshold overrides, feature normalisation parameters,
 *   confidence cutoff values, or API sampling parameters. Parsed by
 *   the inference layer at load time. Nullable — models using only
 *   default configuration omit this field.
 *   Example for an anomaly model:
 *   {
 *     "hrAnomalyThreshold": 0.82,
 *     "minConfidence": 0.65,
 *     "windowSizeSeconds": 300
 *   }
 *
 * • {@code abTestGroup} is the A/B test cohort assignment for this
 *   model deployment. Populated by the remote config layer when a
 *   model update is part of a controlled experiment.
 *   Examples: "control", "treatment_v2", "holdout"
 *   Nullable — null for standard (non-experimental) deployments.
 *
 * • Four indexes are declared:
 *     1. (user_id, model_key, model_version) UNIQUE — prevents duplicate
 *        registration and serves the exact-version lookup.
 *     2. (user_id, model_key, is_active)             — primary inference
 *        lookup: "the active model for role X for user Y". The most
 *        frequent query in the entire inference pipeline.
 *     3. (user_id, model_type)                       — type-filtered
 *        management queries: "all TFLite models for user X" for the
 *        storage cleanup worker.
 *     4. (user_id, activated_at)                     — deployment history
 *        timeline: "all model activations for user X ordered by date"
 *        for the Model History screen and A/B analysis export.
 *
 * Architecture layer : Data / Local
 * Table name         : model_metadata
 * Related DAOs       : ModelMetadataDao
 * Parent FK          : users.id  (CASCADE delete + update)
 */
@Entity(
        tableName = "model_metadata",
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
                // Uniqueness guard + exact-version lookup
                @Index(value = {"user_id", "model_key", "model_version"}, unique = true),
                // Primary inference lookup: active model for a given role
                @Index(value = {"user_id", "model_key", "is_active"}),
                // Type-filtered management queries for storage cleanup
                @Index(value = {"user_id", "model_type"}),
                // Deployment history timeline for A/B analysis
                @Index(value = {"user_id", "activated_at"})
        }
)
public class ModelMetadataEntity {

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
    // Model Identity
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Stable programmer-defined identifier for the model's functional
     * role, independent of version. The primary lookup key used by the
     * inference layer to resolve "the currently active model for role X".
     *
     * Naming convention: snake_case, function-descriptive.
     * Examples:
     *   "anomaly_detection"   — HR / SpO₂ / BP anomaly classifier
     *   "sleep_scorer"        — sleep quality regression model
     *   "stress_index"        — HRV-based stress scoring model
     *   "mood_inference"      — biometric-to-mood prediction model
     *   "goal_recommender"    — AI goal suggestion model
     *   "chat_model"          — LLM powering the AI chat feature
     *   "insight_generator"   — LLM powering AI insight generation
     */
    @NonNull
    @ColumnInfo(name = "model_key")
    public String modelKey;

    /**
     * Specific version string of the deployed model artifact.
     * Unique per (user_id, model_key) pair when combined with
     * {@link #modelKey} in the composite unique index.
     *
     * Format per model type:
     *   TFLite on-device → semantic version: "2.3.1", "1.0.4"
     *   Cloud API LLM    → API model ID: "claude-sonnet-4-20250514",
     *                      "gemini-2.5-pro"
     *   Rule engine      → rule-set version: "rule-engine-v1",
     *                      "rule-engine-v2.1"
     *
     * Mirrors the {@code modelVersion} field in both
     * {@link InsightEntity} and {@link ChatMessageEntity} to provide
     * a consistent model auditability chain across all AI-generated
     * content in VitalAI.
     */
    @NonNull
    @ColumnInfo(name = "model_version")
    public String modelVersion;

    /**
     * Deployment modality of this model.
     * Free-form TEXT — open-ended to accommodate new runtimes without
     * requiring a DB migration.
     *
     * Recommended values:
     *   "TFLITE_ON_DEVICE" — binary .tflite file, runs via TFLite
     *                        Interpreter on the device CPU/GPU/NNAPI
     *   "CLOUD_API"        — remote model invoked via HTTP/JSON API
     *                        (Anthropic, Google Vertex AI, etc.)
     *   "RULE_ENGINE"      — deterministic rule-based model; no file,
     *                        no network call
     *   "ONNX_ON_DEVICE"   — ONNX Runtime model (future extension)
     */
    @NonNull
    @ColumnInfo(name = "model_type")
    public String modelType;

    /**
     * Human-readable description of this model's purpose and scope.
     * Displayed in the Model Management settings screen and the
     * explainability panel ("Which AI made this prediction?").
     *
     * Examples:
     *   "Detects anomalous heart rate, SpO₂, and blood pressure
     *    readings using a personalised LSTM classifier trained on
     *    the user's 30-day rolling baseline."
     *   "Scores sleep quality 0–100 using a gradient boosted tree
     *    model trained on stage durations, HR, and HRV."
     *   "Claude claude-sonnet-4-20250514 — Anthropic's cloud LLM used
     *    to generate personalised health insights and chat responses."
     *
     * Nullable — auto-generated or legacy rows may omit a description.
     */
    @Nullable
    @ColumnInfo(name = "description")
    public String description;

    // ──────────────────────────────────────────────────────────────────────
    // Activation State
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Whether this version is the currently active model for its
     * {@link #modelKey} role.
     *
     * TRUE  — this version is loaded by the inference layer for all
     *         predictions under {@link #modelKey} for this user.
     * FALSE — superseded, pending, or rolled back (default at insert).
     *
     * Invariant: at most one row per (user_id, model_key) should have
     * isActive = true. The Repository enforces this by executing:
     *   UPDATE model_metadata SET is_active = 0
     *   WHERE user_id = :uid AND model_key = :key AND id != :newActiveId
     * before setting is_active = 1 on the target row. This design
     * allows instant rollback: flip isActive on two rows atomically
     * in a transaction — no re-download required if the old model
     * file is still on disk.
     *
     * SQLite stores as INTEGER (0 = false, 1 = true).
     */
    @ColumnInfo(name = "is_active", defaultValue = "0")
    public boolean isActive;

    // ──────────────────────────────────────────────────────────────────────
    // File Artifact (TFLite / ONNX models)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Absolute path to the model file in the app's internal storage.
     *
     * Example:
     *   "/data/data/com.vitalai.app/files/models/anomaly_v2.3.1.tflite"
     *
     * The ModelManager checks this field before attempting to load a
     * TFLite Interpreter. A null value on a TFLITE_ON_DEVICE row
     * indicates the file download is still pending or has failed.
     *
     * Nullable — always null for CLOUD_API and RULE_ENGINE model types.
     */
    @Nullable
    @ColumnInfo(name = "file_path")
    public String filePath;

    /**
     * Size of the model file in bytes.
     * Displayed in the Model Management settings screen as a
     * human-readable size (e.g. "4.2 MB"). Used by the storage
     * cleanup worker to identify large unused model files eligible
     * for deletion when device storage is low.
     *
     * Nullable — same conditions as {@link #filePath}.
     */
    @Nullable
    @ColumnInfo(name = "file_size_bytes")
    public Long fileSizeBytes;

    /**
     * SHA-256 hex digest of the model file (lowercase, no separators).
     * Example: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
     *
     * Verified by the ModelManager:
     *   1. Immediately after download completes (integrity check).
     *   2. Before each TFLite Interpreter load (tamper detection).
     * A checksum mismatch triggers a re-download and a WARNING
     * InsightEntity notifying the user that their AI model was
     * corrupted and has been automatically restored.
     *
     * Nullable — same conditions as {@link #filePath}.
     */
    @Nullable
    @ColumnInfo(name = "checksum")
    public String checksum;

    // ──────────────────────────────────────────────────────────────────────
    // Lifecycle Timestamps
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant the model file was fully downloaded and its
     * {@link #checksum} verified. Null for CLOUD_API and RULE_ENGINE
     * model types and for TFLITE rows where the download is still
     * pending or in progress.
     * Stored as epoch millis via TypeConverters.
     */
    @Nullable
    @ColumnInfo(name = "downloaded_at")
    public Date downloadedAt;

    /**
     * UTC instant this model version was first set as the active model
     * for its {@link #modelKey} role ({@link #isActive} first flipped
     * to true). Set by the Repository on first activation; never
     * modified thereafter. Null until the model has been activated at
     * least once (e.g. a downloaded-but-not-yet-promoted model).
     * Retained permanently for the deployment audit trail.
     * Stored as epoch millis via TypeConverters.
     */
    @Nullable
    @ColumnInfo(name = "activated_at")
    public Date activatedAt;

    /**
     * UTC instant this model version was superseded by a newer version
     * ({@link #isActive} flipped to false by the Repository during a
     * model promotion or rollback). Null while the model is still
     * active, pending activation, or has never been activated.
     * Stored as epoch millis via TypeConverters.
     */
    @Nullable
    @ColumnInfo(name = "deprecated_at")
    public Date deprecatedAt;

    /**
     * UTC instant of the most recent successful inference call made
     * using this model version. Updated by the inference layer on
     * every successful prediction. Null until the first inference.
     *
     * Used by the staleness monitor to detect models that have stopped
     * producing predictions silently (e.g. due to a WorkManager worker
     * crash). If lastInferenceAt is older than a configurable threshold
     * (default: 24 h) for an active model, the staleness monitor fires
     * a WARNING InsightEntity and triggers a health-check worker.
     * Stored as epoch millis via TypeConverters.
     */
    @Nullable
    @ColumnInfo(name = "last_inference_at")
    public Date lastInferenceAt;

    // ──────────────────────────────────────────────────────────────────────
    // Performance Metrics
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Mean inference latency in milliseconds measured during the
     * post-deployment validation run on the user's device.
     *
     * For TFLite models: wall-clock time from
     *   {@code Interpreter.run()} call to result array population,
     *   averaged over the validation dataset.
     * For cloud models: mean round-trip API latency including
     *   network time, measured over the first N production calls.
     *
     * Used to surface performance warnings if latency exceeds the
     * real-time threshold for the model's use case (e.g. > 200 ms
     * for a streaming HR anomaly classifier is unacceptable).
     *
     * Nullable — populated asynchronously by the evaluation harness;
     * null until the validation run completes.
     */
    @Nullable
    @ColumnInfo(name = "inference_latency_ms")
    public Double inferenceLatencyMs;

    /**
     * Overall accuracy of the model on the user's personal validation
     * subset, expressed as a fraction in [0.0, 1.0].
     *
     * For classifiers: (correct predictions / total predictions).
     * For regression models: normalised accuracy proxy
     *   (1 - mean_absolute_percentage_error), clamped to [0, 1].
     * For LLMs: human-eval score from the A/B testing pipeline.
     *
     * Used by the rollback logic: if accuracyScore drops below the
     * configurable minimum threshold (stored in {@link #configJson}),
     * the Repository automatically rolls back to the previous version
     * and fires a CRITICAL InsightEntity.
     *
     * Nullable — same conditions as {@link #inferenceLatencyMs}.
     */
    @Nullable
    @ColumnInfo(name = "accuracy_score")
    public Double accuracyScore;

    /**
     * F1 score (harmonic mean of precision and recall) of the model
     * on the user's personal validation subset, expressed in [0.0, 1.0].
     * Only meaningful for binary and multi-class classifiers; null for
     * regression models and LLMs.
     *
     * Stored alongside {@link #accuracyScore} because accuracy alone
     * is misleading on imbalanced datasets (e.g. an anomaly detector
     * that predicts "normal" 100% of the time achieves high accuracy
     * but zero F1 score).
     *
     * Nullable — same conditions as {@link #inferenceLatencyMs}.
     */
    @Nullable
    @ColumnInfo(name = "f1_score")
    public Double f1Score;

    // ──────────────────────────────────────────────────────────────────────
    // Reliability Counters
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Running count of successful inference calls made using this model
     * version since activation. Incremented by the inference layer on
     * every successful prediction. Defaults to 0.
     *
     * Used alongside {@link #totalErrors} to compute the error rate:
     *   errorRate = totalErrors / (totalInferences + totalErrors)
     * A high error rate triggers automatic rollback and a CRITICAL
     * InsightEntity notifying the user that AI monitoring is degraded.
     */
    @ColumnInfo(name = "total_inferences", defaultValue = "0")
    public long totalInferences;

    /**
     * Running count of inference errors (exceptions, timeouts, OOM
     * crashes, API failures) since activation. Defaults to 0.
     *
     * Incremented by the inference layer's catch blocks. Combined with
     * {@link #totalInferences} to compute the error rate for the
     * automatic rollback trigger.
     */
    @ColumnInfo(name = "total_errors", defaultValue = "0")
    public long totalErrors;

    // ──────────────────────────────────────────────────────────────────────
    // Configuration & Experimentation
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Optional JSON blob storing model-specific runtime configuration
     * that varies per user or per deployment. Parsed by the inference
     * layer at load time.
     *
     * Example — anomaly detection model:
     * {
     *   "hrAnomalyThreshold":    0.82,
     *   "spo2AnomalyThreshold":  0.79,
     *   "minConfidence":         0.65,
     *   "windowSizeSeconds":     300,
     *   "rollbackAccuracyFloor": 0.70
     * }
     *
     * Example — cloud LLM:
     * {
     *   "maxTokens":        1024,
     *   "temperature":      0.7,
     *   "topP":             0.95,
     *   "systemPromptHash": "a3f2c1..."
     * }
     *
     * Nullable — models using only default configuration omit this field.
     */
    @Nullable
    @ColumnInfo(name = "config_json")
    public String configJson;

    /**
     * A/B test cohort assignment for this model deployment.
     * Populated by the remote config layer when the model update is
     * part of a controlled experiment. Used as a grouping key in the
     * A/B testing analytics export to compare metric distributions
     * across cohorts.
     *
     * Examples: "control", "treatment_v2_3", "holdout_10pct"
     *
     * Nullable — null for standard (non-experimental) deployments.
     */
    @Nullable
    @ColumnInfo(name = "ab_test_group")
    public String abTestGroup;

    // ──────────────────────────────────────────────────────────────────────
    // Timestamps
    // ──────────────────────────────────────────────────────────────────────

    /**
     * UTC instant when this row was first written to the local database.
     * Set once at insert time; never modified thereafter.
     * Distinct from {@link #downloadedAt} and {@link #activatedAt} —
     * a row may be inserted (registered) before the file download starts.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "created_at")
    public Date createdAt;

    /**
     * UTC instant of the most recent update to this row.
     * Updated when any mutable field changes: {@link #isActive},
     * {@link #filePath}, {@link #checksum}, {@link #downloadedAt},
     * {@link #activatedAt}, {@link #deprecatedAt}, performance metrics,
     * reliability counters, or {@link #configJson}.
     * Stored as epoch millis via TypeConverters.
     */
    @NonNull
    @ColumnInfo(name = "updated_at")
    public Date updatedAt;

    // ──────────────────────────────────────────────────────────────────────
    // Constructor
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Full constructor used by the Repository when inserting a new
     * model metadata record. Room uses direct field assignment when
     * reading rows back from the DB.
     *
     * @param userId              Local PK of the owning {@link UserEntity}.
     * @param modelKey            Functional role identifier (required).
     * @param modelVersion        Version string (required).
     * @param modelType           Deployment modality string (required).
     * @param description         Human-readable purpose summary, or null.
     * @param isActive            Whether currently active for inference.
     * @param filePath            Absolute path to model file, or null.
     * @param fileSizeBytes       File size in bytes, or null.
     * @param checksum            SHA-256 hex digest, or null.
     * @param downloadedAt        File download completion instant, or null.
     * @param activatedAt         First activation instant, or null.
     * @param deprecatedAt        Deprecation instant, or null.
     * @param lastInferenceAt     Most recent successful inference, or null.
     * @param inferenceLatencyMs  Mean inference latency in ms, or null.
     * @param accuracyScore       Validation accuracy [0.0–1.0], or null.
     * @param f1Score             Validation F1 score [0.0–1.0], or null.
     * @param totalInferences     Cumulative successful inference count.
     * @param totalErrors         Cumulative inference error count.
     * @param configJson          Runtime configuration JSON, or null.
     * @param abTestGroup         A/B test cohort label, or null.
     * @param createdAt           Insert timestamp (set by Repository).
     * @param updatedAt           Last-update timestamp (set by Repository).
     */
    public ModelMetadataEntity(
            long userId,
            @NonNull String modelKey,
            @NonNull String modelVersion,
            @NonNull String modelType,
            @Nullable String description,
            boolean isActive,
            @Nullable String filePath,
            @Nullable Long fileSizeBytes,
            @Nullable String checksum,
            @Nullable Date downloadedAt,
            @Nullable Date activatedAt,
            @Nullable Date deprecatedAt,
            @Nullable Date lastInferenceAt,
            @Nullable Double inferenceLatencyMs,
            @Nullable Double accuracyScore,
            @Nullable Double f1Score,
            long totalInferences,
            long totalErrors,
            @Nullable String configJson,
            @Nullable String abTestGroup,
            @NonNull Date createdAt,
            @NonNull Date updatedAt) {

        this.userId             = userId;
        this.modelKey           = modelKey;
        this.modelVersion       = modelVersion;
        this.modelType          = modelType;
        this.description        = description;
        this.isActive           = isActive;
        this.filePath           = filePath;
        this.fileSizeBytes      = fileSizeBytes;
        this.checksum           = checksum;
        this.downloadedAt       = downloadedAt;
        this.activatedAt        = activatedAt;
        this.deprecatedAt       = deprecatedAt;
        this.lastInferenceAt    = lastInferenceAt;
        this.inferenceLatencyMs = inferenceLatencyMs;
        this.accuracyScore      = accuracyScore;
        this.f1Score            = f1Score;
        this.totalInferences    = totalInferences;
        this.totalErrors        = totalErrors;
        this.configJson         = configJson;
        this.abTestGroup        = abTestGroup;
        this.createdAt          = createdAt;
        this.updatedAt          = updatedAt;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Convenience factories
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Factory for registering a new TFLite on-device model version
     * at the start of the download process. All file artifact fields
     * are null at registration time and populated by subsequent calls
     * to {@link #applyDownload} once the download completes.
     * {@code isActive} defaults to false — the model is promoted via
     * {@link #activate()} only after download and checksum verification
     * succeed. Timestamps set to now.
     *
     * @param userId       Local PK of the owning user.
     * @param modelKey     Functional role identifier.
     * @param modelVersion Semantic version string (e.g. "2.3.1").
     * @param description  Human-readable purpose summary, or null.
     * @param abTestGroup  A/B test cohort, or null.
     * @return Ready-to-insert {@link ModelMetadataEntity}.
     */
    public static ModelMetadataEntity createTfLite(
            long userId,
            @NonNull String modelKey,
            @NonNull String modelVersion,
            @Nullable String description,
            @Nullable String abTestGroup) {

        Date now = new Date();
        return new ModelMetadataEntity(
                userId, modelKey, modelVersion, "TFLITE_ON_DEVICE",
                description, false,
                null, null, null,
                null, null, null, null,
                null, null, null,
                0L, 0L,
                null, abTestGroup, now, now);
    }

    /**
     * Factory for registering a cloud API model configuration.
     * No file artifact fields apply; {@code isActive} defaults to false
     * and is promoted via {@link #activate()} after any pre-flight
     * validation passes. Timestamps set to now.
     *
     * @param userId       Local PK of the owning user.
     * @param modelKey     Functional role identifier.
     * @param modelVersion API model identifier string
     *                     (e.g. "claude-sonnet-4-20250514").
     * @param description  Human-readable purpose summary, or null.
     * @param configJson   Runtime configuration JSON, or null.
     * @param abTestGroup  A/B test cohort, or null.
     * @return Ready-to-insert {@link ModelMetadataEntity}.
     */
    public static ModelMetadataEntity createCloudApi(
            long userId,
            @NonNull String modelKey,
            @NonNull String modelVersion,
            @Nullable String description,
            @Nullable String configJson,
            @Nullable String abTestGroup) {

        Date now = new Date();
        return new ModelMetadataEntity(
                userId, modelKey, modelVersion, "CLOUD_API",
                description, false,
                null, null, null,
                null, null, null, null,
                null, null, null,
                0L, 0L,
                configJson, abTestGroup, now, now);
    }

    /**
     * Factory for registering a deterministic rule-engine model.
     * No file artifact or latency fields apply; {@code isActive}
     * defaults to true — rule engines are always ready to run
     * immediately with no download or validation step required.
     * Timestamps set to now.
     *
     * @param userId       Local PK of the owning user.
     * @param modelKey     Functional role identifier.
     * @param modelVersion Rule-set version string (e.g. "rule-engine-v1").
     * @param description  Human-readable purpose summary, or null.
     * @return Ready-to-insert {@link ModelMetadataEntity}.
     */
    public static ModelMetadataEntity createRuleEngine(
            long userId,
            @NonNull String modelKey,
            @NonNull String modelVersion,
            @Nullable String description) {

        Date now = new Date();
        return new ModelMetadataEntity(
                userId, modelKey, modelVersion, "RULE_ENGINE",
                description, true,
                null, null, null,
                null, now, null, null,
                null, null, null,
                0L, 0L,
                null, null, now, now);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Utility
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Records the successful completion of a model file download.
     * Populates all file artifact fields and stamps {@link #updatedAt}.
     * Call this in the Repository after the download worker verifies
     * the checksum, then persist via {@code ModelMetadataDao.update()}.
     * The Repository should then call {@link #activate()} if this
     * version should immediately go live.
     *
     * @param filePath      Absolute path to the downloaded file.
     * @param fileSizeBytes File size in bytes.
     * @param checksum      SHA-256 hex digest of the downloaded file.
     */
    public void applyDownload(
            @NonNull String filePath,
            long fileSizeBytes,
            @NonNull String checksum) {
        Date now           = new Date();
        this.filePath      = filePath;
        this.fileSizeBytes = fileSizeBytes;
        this.checksum      = checksum;
        this.downloadedAt  = now;
        this.updatedAt     = now;
    }

    /**
     * Promotes this model version to active status, recording the
     * first-activation instant if not already set, and stamps
     * {@link #updatedAt}. The caller (Repository) must first clear
     * {@code isActive} on the previously active row for this
     * (user_id, model_key) in the same transaction.
     *
     * Call this in the Repository as part of the atomic model-promotion
     * transaction, then persist both the old and new rows via
     * {@code ModelMetadataDao.update()}.
     */
    public void activate() {
        Date now      = new Date();
        this.isActive = true;
        if (this.activatedAt == null) {
            this.activatedAt = now;
        }
        this.deprecatedAt = null;
        this.updatedAt    = now;
    }

    /**
     * Marks this model version as deprecated (no longer active) and
     * stamps {@link #deprecatedAt} and {@link #updatedAt}. The caller
     * (Repository) must set {@code isActive = true} on the replacement
     * row in the same transaction.
     *
     * Call this in the Repository as part of the atomic model-promotion
     * or rollback transaction.
     */
    public void deprecate() {
        Date now          = new Date();
        this.isActive     = false;
        this.deprecatedAt = now;
        this.updatedAt    = now;
    }

    /**
     * Records a successful inference call: increments
     * {@link #totalInferences}, updates {@link #lastInferenceAt},
     * and stamps {@link #updatedAt}.
     *
     * Call this in the inference layer after every successful
     * prediction, then persist via {@code ModelMetadataDao.update()}.
     * For high-frequency models (e.g. streaming HR anomaly at 1 Hz),
     * consider batching counter updates every N inferences to reduce
     * write pressure.
     */
    public void recordInference() {
        Date now              = new Date();
        this.totalInferences += 1;
        this.lastInferenceAt  = now;
        this.updatedAt        = now;
    }

    /**
     * Records a failed inference call: increments {@link #totalErrors}
     * and stamps {@link #updatedAt}. Call this in the inference layer's
     * catch block after every prediction failure, then persist via
     * {@code ModelMetadataDao.update()}.
     */
    public void recordError() {
        this.totalErrors += 1;
        this.updatedAt    = new Date();
    }

    /**
     * Applies post-deployment performance evaluation results and stamps
     * {@link #updatedAt}. Call this in the Repository after the
     * evaluation harness completes its validation run, then persist via
     * {@code ModelMetadataDao.update()}.
     *
     * @param inferenceLatencyMs Mean inference latency in milliseconds.
     * @param accuracyScore      Validation accuracy [0.0–1.0].
     * @param f1Score            Validation F1 score [0.0–1.0], or null
     *                           for regression models and LLMs.
     */
    public void applyEvaluation(
            double inferenceLatencyMs,
            double accuracyScore,
            @Nullable Double f1Score) {
        this.inferenceLatencyMs = inferenceLatencyMs;
        this.accuracyScore      = accuracyScore;
        this.f1Score            = f1Score;
        this.updatedAt          = new Date();
    }

    /**
     * Returns the inference error rate as a fraction in [0.0, 1.0].
     * Returns 0.0 if no inference attempts have been recorded yet.
     * An error rate above a configurable threshold (typically 0.05)
     * should trigger automatic rollback to the previous stable version.
     */
    public double errorRate() {
        long total = totalInferences + totalErrors;
        return total == 0 ? 0.0 : (double) totalErrors / total;
    }

    /**
     * Returns true if the model file has been successfully downloaded
     * and is present on disk (i.e. {@link #filePath} and
     * {@link #downloadedAt} are both non-null).
     * Always returns false for CLOUD_API and RULE_ENGINE model types.
     */
    public boolean isDownloaded() {
        return filePath != null && downloadedAt != null;
    }

    /**
     * Refreshes {@code updated_at} to the current instant.
     * Call this in the Repository before any partial update via
     * {@code ModelMetadataDao.update()}.
     */
    public void touch() {
        this.updatedAt = new Date();
    }

    @Override
    public String toString() {
        return "ModelMetadataEntity{"
                + "id=" + id
                + ", userId=" + userId
                + ", modelKey='" + modelKey + '\''
                + ", modelVersion='" + modelVersion + '\''
                + ", modelType='" + modelType + '\''
                + ", isActive=" + isActive
                + ", filePath='" + filePath + '\''
                + ", fileSizeBytes=" + fileSizeBytes
                + ", downloadedAt=" + downloadedAt
                + ", activatedAt=" + activatedAt
                + ", deprecatedAt=" + deprecatedAt
                + ", lastInferenceAt=" + lastInferenceAt
                + ", inferenceLatencyMs=" + inferenceLatencyMs
                + ", accuracyScore=" + accuracyScore
                + ", f1Score=" + f1Score
                + ", totalInferences=" + totalInferences
                + ", totalErrors=" + totalErrors
                + ", abTestGroup='" + abTestGroup + '\''
                + ", createdAt=" + createdAt
                + ", updatedAt=" + updatedAt
                + '}';
    }
}