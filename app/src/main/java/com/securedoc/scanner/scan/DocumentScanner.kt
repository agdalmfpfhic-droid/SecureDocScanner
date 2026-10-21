package com.securedoc.scanner.scan

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.securedoc.scanner.data.model.ScanProfile
import com.securedoc.scanner.ui.LocalAppContainer
import com.securedoc.scanner.ui.findActivity

/**
 * Lance ML Kit Document Scanner (mode complet) :
 * détection des bords, recadrage, correction de perspective, rotation automatique,
 * filtres, suppression des ombres et taches, import depuis la galerie.
 * Aucune permission CAMERA n'est nécessaire : ML Kit gère son propre flux.
 * Le traitement est fait sur l'appareil.
 */
@Composable
fun rememberDocumentScanner(
    onResult: (List<Uri>) -> Unit,
    onError: (String) -> Unit,
): (ScanProfile) -> Unit {
    val activity = LocalContext.current.findActivity()
    val lock = LocalAppContainer.current.lockController
    val currentOnError by rememberUpdatedState(onError)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages.orEmpty()
                .map { it.imageUri }
            if (uris.isNotEmpty()) onResult(uris)
        }
    }

    return remember(activity, launcher) {
        { profile: ScanProfile ->
            val options = GmsDocumentScannerOptions.Builder()
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .setGalleryImportAllowed(true)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .apply { profile.pageLimit?.let { setPageLimit(it) } }
                .build()
            GmsDocumentScanning.getClient(options)
                .getStartScanIntent(activity)
                .addOnSuccessListener { sender ->
                    lock.expectExternal()
                    launcher.launch(IntentSenderRequest.Builder(sender).build())
                }
                .addOnFailureListener {
                    currentOnError(
                        "Scanner indisponible : vérifiez que les services Google Play sont à jour. (${it.message})"
                    )
                }
        }
    }
}
