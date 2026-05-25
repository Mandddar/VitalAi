package com.vitalai.app.presentation.ui.fragment.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.vitalai.app.databinding.FragmentForgotPasswordBinding;
import com.vitalai.app.presentation.viewmodel.AuthViewModel;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * ForgotPasswordFragment
 *
 * Allows users to request a password reset email from Firebase.
 */
@AndroidEntryPoint
public class ForgotPasswordFragment extends Fragment {

    private FragmentForgotPasswordBinding binding;
    private AuthViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentForgotPasswordBinding.inflate(inflater, container, false);
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
        binding.btnResetPassword.setOnClickListener(v -> {
            String email = binding.etEmail.getText().toString().trim();
            viewModel.sendPasswordReset(email);
        });

        binding.tvBackToLogin.setOnClickListener(v -> 
            Navigation.findNavController(v).popBackStack()
        );
    }

    private void observeViewModel() {
        viewModel.isResetSent.observe(getViewLifecycleOwner(), isSent -> {
            if (isSent) {
                Toast.makeText(requireContext(), "Reset link sent to your email.", Toast.LENGTH_LONG).show();
                Navigation.findNavController(requireView()).popBackStack();
            }
        });

        viewModel.isLoading.observe(getViewLifecycleOwner(), isLoading -> {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            binding.btnResetPassword.setEnabled(!isLoading);
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
