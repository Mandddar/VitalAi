package com.vitalai.app.presentation.ui.activity;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.vitalai.app.databinding.ActivityAuthBinding;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * AuthActivity
 *
 * Single Activity hosting Login, Register, and Forgot Password fragments
 * via the Navigation Component.
 */
@AndroidEntryPoint
public class AuthActivity extends AppCompatActivity {

    private ActivityAuthBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAuthBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
    }
}
