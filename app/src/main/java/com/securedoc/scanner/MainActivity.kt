package com.securedoc.scanner

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securedoc.scanner.ui.AppRoot
import com.securedoc.scanner.ui.LocalAppContainer
import com.securedoc.scanner.ui.theme.SecureDocTheme

/**
 * AppCompatActivity (donc FragmentActivity) : requis par BiometricPrompt.
 */
class MainActivity : AppCompatActivity() {

    private val container get() = (application as SecureDocApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        // FLAG_SECURE : bloque captures et enregistrements d'écran,
        // et masque le contenu dans l'écran des applications récentes.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val theme by container.settings.theme.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppContainer provides container) {
                SecureDocTheme(theme) { AppRoot() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        container.lockController.onStart()
    }

    override fun onStop() {
        container.lockController.onStop()
        super.onStop()
    }
}
