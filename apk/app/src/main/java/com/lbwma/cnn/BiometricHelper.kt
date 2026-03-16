package com.lbwma.cnn

import android.content.Context
import android.content.SharedPreferences
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.concurrent.Executor

object BiometricHelper {

    private const val PREFS_NAME = "cnn_secure_prefs"
    private const val KEY_USERNAME = "saved_username"
    private const val KEY_PASSWORD = "saved_password"

    fun isBiometricAvailable(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(BiometricManager::class.java)
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                    BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    fun hasSavedCredentials(context: Context): Boolean {
        return try {
            val prefs = getEncryptedPrefs(context)
            prefs.getString(KEY_USERNAME, null) != null
        } catch (_: Exception) {
            false
        }
    }

    fun saveCredentials(context: Context, username: String, password: String) {
        try {
            getEncryptedPrefs(context).edit()
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .apply()
        } catch (_: Exception) { }
    }

    fun getSavedCredentials(context: Context): Pair<String, String>? {
        return try {
            val prefs = getEncryptedPrefs(context)
            val u = prefs.getString(KEY_USERNAME, null) ?: return null
            val p = prefs.getString(KEY_PASSWORD, null) ?: return null
            u to p
        } catch (_: Exception) {
            null
        }
    }

    fun clearCredentials(context: Context) {
        try {
            getEncryptedPrefs(context).edit().clear().apply()
        } catch (_: Exception) { }
    }

    fun authenticate(
        context: Context,
        executor: Executor,
        onSuccess: () -> Unit,
        onDismiss: () -> Unit
    ) {
        val prompt = BiometricPrompt.Builder(context)
            .setTitle("CNN Conversores")
            .setSubtitle("Confirme sua digital para entrar")
            .setNegativeButton("Usar senha", executor) { _, _ -> }
            .build()

        prompt.authenticate(
            CancellationSignal(),
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onDismiss()
                }
            }
        )
    }

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
