package com.vitalai.app.presentation.ui.fragment.auth;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.vitalai.app.R;
import com.vitalai.app.databinding.FragmentRegisterBinding;
import com.vitalai.app.presentation.viewmodel.AuthViewModel;
import com.vitalai.app.ui.MainActivity;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * RegisterFragment
 *
 * Handles new user registration with email, password and name.
 * Includes password strength validation.
 */
@AndroidEntryPoint
public class RegisterFragment extends Fragment {

    private FragmentRegisterBinding binding;
    private AuthViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(AuthViewModel.class);

        setupListeners();
        observeViewModel();
    }

    private void setupListeners() {
        binding.etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePasswordStrength(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.btnRegister.setOnClickListener(v -> {
            String name = binding.etName.getText().toString().trim();
            String email = binding.etEmail.getText().toString().trim();
            String password = binding.etPassword.getText().toString().trim();
            String confirmPassword = binding.etConfirmPassword.getText().toString().trim();

            if (!password.equals(confirmPassword)) {
                Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            viewModel.register(name, email, password);
        });

        binding.tvLogin.setOnClickListener(v -> 
            Navigation.findNavController(v).popBackStack()
        );
    }

    private void updatePasswordStrength(String password) {
        if (password.isEmpty()) {
            binding.passwordStrengthIndicator.setVisibility(View.INVISIBLE);
            return;
        }

        binding.passwordStrengthIndicator.setVisibility(View.VISIBLE);
        int score = 0;
        if (password.length() >= 8) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[0-9].*")) score++;
        if (password.matches(".*[^a-zA-Z0-9].*")) score++;

        int progress = (score * 25);
        binding.passwordStrengthIndicator.setProgress(progress);

        int color;
        if (score <= 1) color = Color.RED;
        else if (score <= 3) color = Color.YELLOW;
        else color = Color.GREEN;

        binding.passwordStrengthIndicator.setIndicatorColor(color);
    }

    private void observeViewModel() {
        viewModel.user.observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                Toast.makeText(requireContext(), "Registration successful. Please verify your email.", Toast.LENGTH_LONG).show();
                startActivity(new Intent(requireContext(), MainActivity.class));
                requireActivity().finish();
            }
        });

        viewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.btnRegister.setEnabled(!isLoading);
        });

        viewModel.error.observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                viewModel.clearError();
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
