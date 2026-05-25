package com.vitalai.app.presentation.ui.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.vitalai.app.ui.MainActivity;
import com.vitalai.app.databinding.ActivitySplashBinding;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * SplashActivity
 *
 * Initial entry point for the application.
 * Displays the branding for 1.5 seconds, checks for an active session,
 * and routes to the appropriate activity.
 */
@SuppressLint("CustomSplashScreen")
@AndroidEntryPoint
public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;
    private static final int SPLASH_DELAY = 2500; // Increased to 2.5 seconds to be sure
    private static final String TAG = "SplashActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: SplashActivity started");
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        new Handler(Looper.getMainLooper()).postDelayed(this::checkAuthStateAndNavigate, SPLASH_DELAY);
    }

    private void checkAuthStateAndNavigate() {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            Log.d(TAG, "checkAuthStateAndNavigate: User logged in, navigating to MainActivity");
            startActivity(new Intent(this, MainActivity.class));
        } else {
            Log.d(TAG, "checkAuthStateAndNavigate: No user, navigating to AuthActivity");
            startActivity(new Intent(this, AuthActivity.class));
        }
        finish();
    }
}
