package com.vitalai.app.presentation.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

/**
 * AuthViewModel
 *
 * Shared ViewModel for all authentication-related fragments (Login, Register, Forgot Password).
 * Manages Firebase Authentication state and exposes LiveData for the UI to observe.
 *
 * Architecture: MVVM + Clean Architecture
 * Layer: Presentation / ViewModel
 */
@HiltViewModel
public class AuthViewModel extends ViewModel {

    private final FirebaseAuth firebaseAuth;

    private final MutableLiveData<FirebaseUser> _user = new MutableLiveData<>();
    /**
     * Observable authentication state. Contains the current FirebaseUser or null if not logged in.
     */
    public final LiveData<FirebaseUser> user = _user;

    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    /**
     * Observable loading state for showing/hiding progress indicators.
     */
    public final LiveData<Boolean> isLoading = _isLoading;

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    /**
     * Observable error messages to be displayed in the UI (e.g., via Snackbars or Dialogs).
     */
    public final LiveData<String> error = _error;

    private final MutableLiveData<Boolean> _isResetSent = new MutableLiveData<>(false);
    /**
     * Observable flag indicating a password reset email was successfully sent.
     */
    public final LiveData<Boolean> isResetSent = _isResetSent;

    @Inject
    public AuthViewModel() {
        // FirebaseAuth is accessed via singleton here. In a stricter Clean Architecture setup,
        // this would be injected via an AuthRepository, but for this Sprint, we interact
        // directly with Firebase to maintain high development velocity.
        this.firebaseAuth = FirebaseAuth.getInstance();
        _user.setValue(firebaseAuth.getCurrentUser());
    }

    /**
     * Authenticates a user with email and password.
     */
    public void login(String email, String password) {
        if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
            _error.setValue("Email and password cannot be empty.");
            return;
        }

        _isLoading.setValue(true);
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    _isLoading.setValue(false);
                    if (task.isSuccessful()) {
                        _user.setValue(firebaseAuth.getCurrentUser());
                    } else {
                        _error.setValue(getErrorMessage(task.getException()));
                    }
                });
    }

    /**
     * Creates a new user account with email and password.
     * Also updates the user's display name and sends a verification email.
     */
    public void register(String name, String email, String password) {
        if (name == null || name.isEmpty() || email == null || email.isEmpty() || password == null || password.isEmpty()) {
            _error.setValue("All fields are required.");
            return;
        }

        _isLoading.setValue(true);
        firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
                        if (firebaseUser != null) {
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();
                            
                            firebaseUser.updateProfile(profileUpdates)
                                    .addOnCompleteListener(profileTask -> {
                                        firebaseUser.sendEmailVerification();
                                        _user.setValue(firebaseUser);
                                        _isLoading.setValue(false);
                                    });
                        } else {
                            _isLoading.setValue(false);
                        }
                    } else {
                        _isLoading.setValue(false);
                        _error.setValue(getErrorMessage(task.getException()));
                    }
                });
    }

    /**
     * Authenticates a user using a Google ID Token.
     */
    public void signInWithGoogle(String idToken) {
        _isLoading.setValue(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    _isLoading.setValue(false);
                    if (task.isSuccessful()) {
                        _user.setValue(firebaseAuth.getCurrentUser());
                    } else {
                        _error.setValue(getErrorMessage(task.getException()));
                    }
                });
    }

    /**
     * Sends a password reset email to the specified address.
     */
    public void sendPasswordReset(String email) {
        if (email == null || email.isEmpty()) {
            _error.setValue("Please enter your email address.");
            return;
        }

        _isLoading.setValue(true);
        firebaseAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    _isLoading.setValue(false);
                    if (task.isSuccessful()) {
                        _isResetSent.setValue(true);
                    } else {
                        _error.setValue(getErrorMessage(task.getException()));
                    }
                });
    }

    /**
     * Logs out the current user and clears the session.
     */
    public void logout() {
        firebaseAuth.signOut();
        _user.setValue(null);
    }

    /**
     * Clears the current error message.
     */
    public void clearError() {
        _error.setValue(null);
    }

    /**
     * Helper to extract human-readable messages from Firebase exceptions.
     */
    private String getErrorMessage(Exception exception) {
        if (exception != null) {
            return exception.getLocalizedMessage();
        }
        return "An authentication error occurred. Please try again.";
    }
}
