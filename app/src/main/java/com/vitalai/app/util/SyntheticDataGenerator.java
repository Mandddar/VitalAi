package com.vitalai.app.util;

import com.vitalai.app.data.local.dao.HealthMetricDao;
import com.vitalai.app.data.local.entity.HealthMetricEntity;
import com.vitalai.app.domain.model.enums.MetricSource;
import com.vitalai.app.domain.model.enums.MetricType;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * SyntheticDataGenerator
 *
 * Generates 30 days of realistic, physiologically plausible health data
 * and inserts it into the {@code health_metrics} table via
 * {@link HealthMetricDao}.
 *
 * Intended use
 * ────────────
 * Called once on first app launch (guarded by a SharedPreferences flag in
 * {@code VitalAIApplication}) to populate the database for demo, testing,
 * and AI-pipeline warm-up purposes.
 *
 * Data patterns
 * ─────────────
 * All times use the device's local calendar (midnight = start of day).
 * Readings are generated at realistic intervals per metric type:
 *
 *   HEART_RATE            — every 5 min; sinusoidal daily curve with
 *                           Gaussian noise; 3-5 hard anomaly spikes
 *                           (is_anomaly = true).
 *   STEPS                 — every 15 min; zero during sleep, random
 *                           bursts during waking hours; 5 000-12 000/day.
 *   BLOOD_OXYGEN_SPO2     — every 30 min; 95-99% normally; 2 nighttime
 *                           dips to 89-93% (is_anomaly = true).
 *   STRESS_SCORE          — every 30 min; inverse of HRV proxy, elevated
 *                           during work hours (09:00-18:00).
 *   HYDRATION_ML          — random 4-10 logs/day; slightly under-hydrated
 *                           on high-step days.
 *
 * Sleep data (duration 6-8.5 h) is inserted as four HEALTH_METRIC rows
 * representing the aggregate stage proportions for that night:
 *   SLEEP_SCORE           — one composite score row per night (0-100).
 *   (Granular SleepStageEntity rows are out of scope here; those belong
 *    in a future SyntheticSleepGenerator that writes to sleep_sessions /
 *    sleep_stages tables.)
 *
 * Anomaly injection
 * ─────────────────
 * Heart rate: 3-5 randomly chosen 5-minute windows across the 30 days
 *   receive a spike to 140-185 bpm with is_anomaly = true.
 * SpO2: exactly 2 nights receive a single dip reading (89-93%)
 *   between 02:00-04:00 with is_anomaly = true.
 *
 * Architecture layer : Util / Data seeding
 * Package            : com.vitalai.app.util
 * Dependencies       : HealthMetricDao (Hilt-injected)
 * Threading          : all DB work runs on {@link Schedulers#io()}
 */
@Singleton
public class SyntheticDataGenerator {

    // ──────────────────────────────────────────────────────────────────────
    // Constants
    // ──────────────────────────────────────────────────────────────────────

    /** Number of historical days to generate (today = day 0, inclusive). */
    private static final int DAYS = 30;

    // Heart rate curve parameters
    private static final double HR_RESTING_BASE   = 65.0;  // bpm, midday rest
    private static final double HR_SLEEP_BASE      = 58.0;  // bpm, deep sleep trough
    private static final double HR_ACTIVE_PEAK     = 100.0; // bpm, afternoon peak
    private static final double HR_NOISE_SIGMA      = 4.0;  // Gaussian SD, bpm
    private static final double HR_ANOMALY_MIN      = 140.0;
    private static final double HR_ANOMALY_MAX      = 185.0;
    private static final int    HR_ANOMALY_COUNT_MIN = 3;
    private static final int    HR_ANOMALY_COUNT_MAX = 5;    // inclusive

    // SpO2 parameters
    private static final double SPO2_NORMAL_MIN  = 95.0;
    private static final double SPO2_NORMAL_MAX  = 99.0;
    private static final double SPO2_DIP_MIN     = 89.0;
    private static final double SPO2_DIP_MAX     = 93.0;
    private static final int    SPO2_DIP_COUNT   = 2;

    // Steps parameters (per-day totals)
    private static final int STEPS_DAY_MIN   = 5_000;
    private static final int STEPS_DAY_MAX   = 12_000;

    // Hydration parameters (mL per log)
    private static final double HYDRATION_LOG_MIN   = 150.0; // one small glass
    private static final double HYDRATION_LOG_MAX   = 400.0; // one large bottle
    private static final int    HYDRATION_LOGS_MIN  = 4;
    private static final int    HYDRATION_LOGS_MAX  = 10;
    /** Daily step threshold above which the user is considered "high activity"
     *  and logs one fewer hydration entry (slightly under-hydrated). */
    private static final int    HIGH_ACTIVITY_STEP_THRESHOLD = 9_000;

    // Sleep parameters
    private static final double SLEEP_HOURS_MIN = 6.0;
    private static final double SLEEP_HOURS_MAX = 8.5;

    // Stress parameters
    private static final double STRESS_BASE_MIN      = 10.0;
    private static final double STRESS_BASE_MAX      = 30.0;
    private static final double STRESS_WORK_BOOST    = 25.0; // added 09:00-18:00
    private static final double STRESS_NOISE_SIGMA   = 8.0;

    // ──────────────────────────────────────────────────────────────────────
    // Dependencies
    // ──────────────────────────────────────────────────────────────────────

    private final HealthMetricDao healthMetricDao;
    private final Random          rng;

    // ──────────────────────────────────────────────────────────────────────
    // Constructor (Hilt-injected)
    // ──────────────────────────────────────────────────────────────────────

    @Inject
    public SyntheticDataGenerator(HealthMetricDao healthMetricDao) {
        this.healthMetricDao = healthMetricDao;
        this.rng             = new Random();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Public API
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Generates all synthetic metric rows for the given user and inserts
     * them into Room in a single batched write.
     *
     * <p>The returned {@link Completable} is cold — nothing happens until
     * subscribed. The caller (VitalAIApplication) is responsible for
     * subscribing on an IO scheduler and handling errors.</p>
     *
     * @param userId Local PK of the user to seed data for
     *               (from {@link com.vitalai.app.data.local.entity.UserEntity#id}).
     * @return {@link Completable} that completes when all rows are written,
     *         or errors if the DB insert fails.
     */
    public Completable generate(long userId) {
        return Completable.defer(() -> {
                    List<HealthMetricEntity> batch = buildAllMetrics(userId);
                    return healthMetricDao.insertAll(batch);
                })
                .subscribeOn(Schedulers.io());
    }

    // ──────────────────────────────────────────────────────────────────────
    // Data construction
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Builds the complete list of {@link HealthMetricEntity} rows for all
     * 30 days. Generation order: heart rate → SpO2 → steps → stress →
     * hydration → sleep score. Anomaly injection is done as a post-pass
     * over the heart-rate and SpO2 sub-lists.
     */
    private List<HealthMetricEntity> buildAllMetrics(long userId) {
        // Midnight of the reference day (today at 00:00:00.000 local time)
        Calendar ref = midnightToday();

        List<HealthMetricEntity> all = new ArrayList<>(7_000); // pre-size estimate

        // ── Per-day generation ─────────────────────────────────────────────

        // Decide anomaly-injection days up front so we can mark them during
        // the heart-rate loop rather than doing an expensive second pass.
        int totalHrSlots  = DAYS * 288; // 288 × 5-min slots per day
        boolean[] hrAnomalySlots = pickAnomalySlots(
                totalHrSlots,
                HR_ANOMALY_COUNT_MIN,
                HR_ANOMALY_COUNT_MAX);

        // Pick 2 distinct nights for SpO2 dips (night index 0 = first generated night)
        int[] spo2DipNights = pickDistinctInts(DAYS, SPO2_DIP_COUNT);

        int hrSlotIndex = 0; // running index across all days × 5-min slots

        for (int dayOffset = -(DAYS - 1); dayOffset <= 0; dayOffset++) {

            // Midnight of this day
            Calendar dayCal = (Calendar) ref.clone();
            dayCal.add(Calendar.DAY_OF_YEAR, dayOffset);
            long dayMidnight = dayCal.getTimeInMillis();

            // Per-day step total — used by multiple generators
            int dailySteps = randInt(STEPS_DAY_MIN, STEPS_DAY_MAX);
            boolean highActivity = dailySteps >= HIGH_ACTIVITY_STEP_THRESHOLD;

            // Heart rate (every 5 min = 288 readings/day)
            hrSlotIndex = buildHeartRateDay(
                    userId, dayMidnight, hrAnomalySlots, hrSlotIndex, all);

            // SpO2 (every 30 min = 48 readings/day)
            int dayIndex = dayOffset + (DAYS - 1); // 0-based index
            boolean hasSpo2Dip = containsInt(spo2DipNights, dayIndex);
            buildSpO2Day(userId, dayMidnight, hasSpo2Dip, all);

            // Steps (every 15 min = 96 slots/day)
            buildStepsDay(userId, dayMidnight, dailySteps, all);

            // Stress (every 30 min = 48 readings/day)
            buildStressDay(userId, dayMidnight, all);

            // Hydration (4-10 random logs/day)
            buildHydrationDay(userId, dayMidnight, highActivity, all);

            // Sleep score (1 composite row per night, written at sleep-start time)
            buildSleepScoreNight(userId, dayMidnight, all);
        }

        return all;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Heart rate
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Generates 288 heart-rate readings for a single day (one per 5 minutes).
     *
     * Curve shape (hour h, 0-23):
     *   Sleep trough  : 00:00-06:00  → low, ~58 bpm
     *   Morning rise  : 06:00-08:00  → ramp up
     *   Daytime       : 08:00-20:00  → sinusoidal peak around 14:00, ~100 bpm
     *   Evening wind  : 20:00-24:00  → declining back toward resting
     *
     * Mathematically modelled as a sum of two sinusoids:
     *   base(h) = HR_RESTING_BASE
     *           + A_day  × sin(π × (h - 6) / 14)   [daytime arc, 06:00-20:00]
     *           - A_sleep × sin(π × (h / 6))        [sleep trough, 00:00-06:00]
     *   + Gaussian noise (σ = HR_NOISE_SIGMA)
     *
     * @param userId         Local user PK.
     * @param dayMidnight    Epoch-millis of 00:00:00 for this day.
     * @param anomalySlots   Boolean flag array across ALL days' slots.
     * @param slotIndex      Running index into {@code anomalySlots} at the
     *                       start of this day.
     * @param out            Accumulator list to append to.
     * @return Updated {@code slotIndex} (slotIndex + 288).
     */
    private int buildHeartRateDay(
            long userId,
            long dayMidnight,
            boolean[] anomalySlots,
            int slotIndex,
            List<HealthMetricEntity> out) {

        final int slotsPerDay    = 288; // 24 h × 60 min / 5 min
        final long slotMillis    = 5L * 60 * 1_000;

        for (int slot = 0; slot < slotsPerDay; slot++, slotIndex++) {
            long tsMillis = dayMidnight + slot * slotMillis;
            double hourFrac = slot / 12.0; // each slot = 5 min → 12 slots/hour

            double bpm;
            boolean isAnomaly = anomalySlots[slotIndex];

            if (isAnomaly) {
                // Hard spike — anomalous tachycardia
                bpm = HR_ANOMALY_MIN + rng.nextDouble() * (HR_ANOMALY_MAX - HR_ANOMALY_MIN);
            } else {
                bpm = heartRateCurve(hourFrac) + gaussian(0, HR_NOISE_SIGMA);
                bpm = clamp(bpm, 45.0, 135.0); // physiological safety clamp
            }

            out.add(metric(userId, tsMillis, MetricType.HEART_RATE,
                    round1(bpm), isAnomaly));
        }

        return slotIndex;
    }

    /**
     * Returns the baseline heart rate (bpm) for a given fractional hour
     * of the day using a dual-sinusoid model.
     *
     * @param h Hour of day as a double (0.0 = midnight, 23.99 = 23:59).
     */
    private double heartRateCurve(double h) {
        if (h < 6.0) {
            // Sleep trough: smoothly descend from resting to sleep-base
            // and back. Model as an inverted half-cosine over [0, 6].
            double t = h / 6.0; // 0→1 across sleep window
            return HR_SLEEP_BASE + (HR_RESTING_BASE - HR_SLEEP_BASE)
                    * (1.0 - Math.cos(Math.PI * t)) / 2.0;
        } else if (h < 20.0) {
            // Daytime arc: peaks around 14:00
            double t = (h - 6.0) / 14.0; // 0→1 across [06:00, 20:00]
            return HR_RESTING_BASE
                    + (HR_ACTIVE_PEAK - HR_RESTING_BASE) * Math.sin(Math.PI * t);
        } else {
            // Evening wind-down: [20:00, 24:00] → back toward resting
            double t = (h - 20.0) / 4.0; // 0→1 across [20:00, 24:00]
            return HR_ACTIVE_PEAK
                    - (HR_ACTIVE_PEAK - HR_RESTING_BASE) * t;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // SpO2
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Generates 48 SpO2 readings for a single day (one per 30 minutes).
     * On nights that were chosen for a dip, one reading between 02:00-04:00
     * is replaced with a hypoxic dip (89-93%) flagged as anomalous.
     */
    private void buildSpO2Day(
            long userId,
            long dayMidnight,
            boolean hasDipThisNight,
            List<HealthMetricEntity> out) {

        final int slotsPerDay  = 48; // 24 h × 60 min / 30 min
        final long slotMillis  = 30L * 60 * 1_000;

        // Slots 4-7 cover 02:00-03:59 (slot 4 = 02:00, slot 7 = 03:30)
        int dipSlot = hasDipThisNight ? (4 + rng.nextInt(4)) : -1;

        for (int slot = 0; slot < slotsPerDay; slot++) {
            long tsMillis  = dayMidnight + slot * slotMillis;
            boolean isDip  = (slot == dipSlot);
            double spo2;

            if (isDip) {
                spo2 = SPO2_DIP_MIN + rng.nextDouble() * (SPO2_DIP_MAX - SPO2_DIP_MIN);
            } else {
                spo2 = SPO2_NORMAL_MIN + rng.nextDouble()
                        * (SPO2_NORMAL_MAX - SPO2_NORMAL_MIN);
            }

            out.add(metric(userId, tsMillis, MetricType.BLOOD_OXYGEN_SPO2,
                    round1(spo2), isDip));
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Steps
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Distributes a day's total step count across 96 fifteen-minute slots.
     *
     * Sleep hours (22:00-06:00, slots 88-95 and 0-23) are forced to zero.
     * Active slots (06:00-22:00, slots 24-87 = 64 slots) receive a share of
     * the total with a log-normal-style burst pattern: most slots are low,
     * a few bursts (walks/gym) produce higher counts.
     */
    private void buildStepsDay(
            long userId,
            long dayMidnight,
            int dailySteps,
            List<HealthMetricEntity> out) {

        final int slotsPerDay  = 96;   // 24 h × 60 min / 15 min
        final long slotMillis  = 15L * 60 * 1_000;

        // Sleep slots: 0-23 (midnight to 05:45) and 88-95 (22:00 to 23:45)
        // Active slots: 24-87 (06:00 to 21:45)
        final int activeStart = 24;
        final int activeEnd   = 87;
        final int activeSlots = activeEnd - activeStart + 1; // 64

        // Generate raw weights for active slots (exponential distribution
        // simulates burst-style activity)
        double[] weights = new double[activeSlots];
        double totalWeight = 0.0;
        for (int i = 0; i < activeSlots; i++) {
            // Most slots near zero, occasional spikes
            weights[i] = Math.pow(rng.nextDouble(), 3); // strongly right-skewed
            totalWeight += weights[i];
        }

        // Allocate steps proportionally to weights
        int[] slotSteps = new int[slotsPerDay]; // defaults to 0 (sleep slots)
        int allocated = 0;
        for (int i = 0; i < activeSlots - 1; i++) {
            int steps = (int) Math.round(dailySteps * weights[i] / totalWeight);
            slotSteps[activeStart + i] = steps;
            allocated += steps;
        }
        // Last active slot absorbs rounding residual
        slotSteps[activeEnd] = Math.max(0, dailySteps - allocated);

        for (int slot = 0; slot < slotsPerDay; slot++) {
            long tsMillis = dayMidnight + slot * slotMillis;
            out.add(metric(userId, tsMillis, MetricType.STEPS,
                    slotSteps[slot], false));
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Stress
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Generates 48 stress-score readings for a single day (one per 30 min).
     *
     * Stress is modelled as the inverse of an HRV proxy:
     *   - Outside work hours (before 09:00, after 18:00): low baseline
     *     (10-30) + Gaussian noise.
     *   - Work hours (09:00-18:00): baseline + STRESS_WORK_BOOST.
     *   - Sleep hours (22:00-06:00): minimal stress (5-15).
     *
     * All values clamped to [0, 100].
     */
    private void buildStressDay(
            long userId,
            long dayMidnight,
            List<HealthMetricEntity> out) {

        final int slotsPerDay = 48;
        final long slotMillis = 30L * 60 * 1_000;

        for (int slot = 0; slot < slotsPerDay; slot++) {
            long tsMillis   = dayMidnight + slot * slotMillis;
            double hourFrac = slot / 2.0; // each slot = 30 min, 2 slots/hour

            double stress = stressCurve(hourFrac)
                    + gaussian(0, STRESS_NOISE_SIGMA);
            stress = clamp(stress, 0.0, 100.0);

            out.add(metric(userId, tsMillis, MetricType.STRESS_SCORE,
                    round1(stress), false));
        }
    }

    private double stressCurve(double h) {
        if (h < 6.0 || h >= 22.0) {
            // Sleep — minimal stress
            return 5.0 + rng.nextDouble() * 10.0;
        } else if (h >= 9.0 && h < 18.0) {
            // Work hours — elevated stress
            double base = STRESS_BASE_MIN
                    + rng.nextDouble() * (STRESS_BASE_MAX - STRESS_BASE_MIN);
            return base + STRESS_WORK_BOOST;
        } else {
            // Morning / evening — baseline
            return STRESS_BASE_MIN
                    + rng.nextDouble() * (STRESS_BASE_MAX - STRESS_BASE_MIN);
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Hydration
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Generates 4-10 hydration log entries for a single day at random
     * waking-hour timestamps (07:00-22:00).
     *
     * On high-activity days, one fewer log is generated to model the
     * common real-world pattern of under-hydration during intense exercise.
     */
    private void buildHydrationDay(
            long userId,
            long dayMidnight,
            boolean highActivity,
            List<HealthMetricEntity> out) {

        int logCount = randInt(HYDRATION_LOGS_MIN, HYDRATION_LOGS_MAX);
        if (highActivity && logCount > HYDRATION_LOGS_MIN) {
            logCount--; // slightly under-hydrated on active days
        }

        // Waking window: 07:00-22:00 = 15 hours = 54 000 000 ms
        final long wakeStart = dayMidnight + 7L  * 3_600_000;
        final long wakeEnd   = dayMidnight + 22L * 3_600_000;
        final long wakeWindow = wakeEnd - wakeStart;

        for (int i = 0; i < logCount; i++) {
            long tsMillis = wakeStart + (long) (rng.nextDouble() * wakeWindow);
            double ml = HYDRATION_LOG_MIN
                    + rng.nextDouble() * (HYDRATION_LOG_MAX - HYDRATION_LOG_MIN);

            out.add(metric(userId, tsMillis, MetricType.HYDRATION_ML,
                    round1(ml), false));
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Sleep score
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Generates one composite SLEEP_SCORE row per night.
     *
     * Sleep duration is drawn uniformly from [6.0, 8.5] hours.
     * The score (0-100) is derived from sleep duration with added noise:
     *   score ≈ 50 + (durationHours - 6.0) × (50 / 2.5) + noise(σ=5)
     * i.e., 6 h → ~50, 8.5 h → ~100, clamped to [30, 100].
     *
     * The timestamp is placed at the estimated sleep-start time
     * (22:00-23:59 of the given day's midnight), consistent with the
     * nightly pattern used by sleep-tracking devices.
     */
    private void buildSleepScoreNight(
            long userId,
            long dayMidnight,
            List<HealthMetricEntity> out) {

        double durationHours = SLEEP_HOURS_MIN
                + rng.nextDouble() * (SLEEP_HOURS_MAX - SLEEP_HOURS_MIN);

        // Sleep-start: random time between 22:00-23:30 the same calendar day
        long sleepStartOffset = (22L * 3_600_000)
                + (long) (rng.nextDouble() * 90L * 60_000); // 0-90 min after 22:00
        long sleepStartMs = dayMidnight + sleepStartOffset;

        // Score model: linearly scaled from duration, plus noise
        double score = 50.0 + (durationHours - 6.0) * (50.0 / 2.5)
                + gaussian(0, 5.0);
        score = clamp(score, 30.0, 100.0);

        out.add(metric(userId, sleepStartMs, MetricType.SLEEP_SCORE,
                round1(score), false));
    }

    // ──────────────────────────────────────────────────────────────────────
    // Entity factory
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Constructs a {@link HealthMetricEntity} with {@code SYNTHETIC} source,
     * inserting both {@code createdAt} and {@code updatedAt} as the current
     * instant (matching the behaviour of
     * {@link HealthMetricEntity#create(long, Date, MetricType, double, MetricSource, String)}).
     */
    private HealthMetricEntity metric(
            long userId,
            long timestampMillis,
            MetricType type,
            double value,
            boolean isAnomaly) {

        Date ts  = new Date(timestampMillis);
        Date now = new Date();
        return new HealthMetricEntity(
                userId,
                ts,
                type,
                value,
                MetricSource.SYNTHETIC,
                /* deviceId */ null,
                isAnomaly,
                now,
                now);
    }

    // ──────────────────────────────────────────────────────────────────────
    // Anomaly slot selection helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Returns a boolean array of length {@code totalSlots} where exactly
     * {@code count} randomly chosen slots are {@code true}.
     * Count is chosen uniformly from [minCount, maxCount].
     */
    private boolean[] pickAnomalySlots(int totalSlots, int minCount, int maxCount) {
        int count = minCount + rng.nextInt(maxCount - minCount + 1);
        boolean[] flags = new boolean[totalSlots];
        // Fisher-Yates partial shuffle to pick `count` distinct indices
        int[] indices = new int[totalSlots];
        for (int i = 0; i < totalSlots; i++) indices[i] = i;
        for (int i = 0; i < count; i++) {
            int j = i + rng.nextInt(totalSlots - i);
            int tmp = indices[i]; indices[i] = indices[j]; indices[j] = tmp;
            flags[indices[i]] = true;
        }
        return flags;
    }

    /**
     * Returns an array of {@code count} distinct random ints in [0, range).
     * Uses partial Fisher-Yates for O(range) time and O(range) space —
     * acceptable here since range = DAYS = 30.
     */
    private int[] pickDistinctInts(int range, int count) {
        int[] pool = new int[range];
        for (int i = 0; i < range; i++) pool[i] = i;
        for (int i = 0; i < count; i++) {
            int j = i + rng.nextInt(range - i);
            int tmp = pool[i]; pool[i] = pool[j]; pool[j] = tmp;
        }
        int[] result = new int[count];
        System.arraycopy(pool, 0, result, 0, count);
        return result;
    }

    private boolean containsInt(int[] arr, int value) {
        for (int v : arr) if (v == value) return true;
        return false;
    }

    // ──────────────────────────────────────────────────────────────────────
    // Math / time utilities
    // ──────────────────────────────────────────────────────────────────────

    /** Returns a Gaussian sample with given mean and standard deviation. */
    private double gaussian(double mean, double sigma) {
        return mean + rng.nextGaussian() * sigma;
    }

    /** Clamps {@code v} to [{@code min}, {@code max}]. */
    private double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Rounds to 1 decimal place (avoids DB bloat from double precision). */
    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** Returns a random int uniformly in [min, max] (inclusive). */
    private int randInt(int min, int max) {
        return min + rng.nextInt(max - min + 1);
    }

    /**
     * Returns a {@link Calendar} set to midnight (00:00:00.000) of today
     * in the device's local timezone.
     */
    private Calendar midnightToday() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE,      0);
        cal.set(Calendar.SECOND,      0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }
}