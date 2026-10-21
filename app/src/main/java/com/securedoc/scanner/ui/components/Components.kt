@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.securedoc.scanner.ui.components

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.securedoc.scanner.data.model.DocumentSummary
import com.securedoc.scanner.data.model.Page
import com.securedoc.scanner.data.model.PageFilter
import com.securedoc.scanner.ui.LocalAppContainer
import com.securedoc.scanner.ui.formatDate
import com.securedoc.scanner.ui.pagesLabel

/** Affiche une page déchiffrée en mémoire, filtre et rotation appliqués. */
@Composable
fun PageImage(
    page: Page,
    maxSide: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val repo = LocalAppContainer.current.repository
    val bitmap by produceState<ImageBitmap?>(null, page.filePath, page.filter, page.rotation, maxSide) {
        value = runCatching { repo.render(page, maxSide).asImageBitmap() }.getOrNull()
    }
    ImageBox(bitmap, modifier, contentScale)
}

/** Aperçu d'une image pas encore enregistrée. */
@Composable
fun UriImage(uri: Uri, maxSide: Int, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val repo = LocalAppContainer.current.repository
    val bitmap by produceState<ImageBitmap?>(null, uri, maxSide) {
        value = runCatching { repo.loadPreview(uri, maxSide).asImageBitmap() }.getOrNull()
    }
    ImageBox(bitmap, modifier, contentScale)
}

@Composable
private fun ImageBox(bitmap: ImageBitmap?, modifier: Modifier, contentScale: ContentScale) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = contentScale)
        } else {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
fun DocumentRow(summary: DocumentSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val doc = summary.document
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 52.dp, height = 68.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                val cover = summary.coverPath
                if (cover != null) {
                    PageImage(
                        page = Page(
                            documentId = doc.id,
                            position = 0,
                            filePath = cover,
                            filter = summary.coverFilter ?: PageFilter.ORIGINAL,
                            rotation = summary.coverRotation ?: 0,
                            width = 0,
                            height = 0,
                        ),
                        maxSide = 240,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(Icons.Filled.Description, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(doc.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${doc.profile.label} · ${pagesLabel(summary.pageCount)}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${summary.categoryName ?: "Sans catégorie"} · ${formatDate(doc.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            if (doc.inVault) {
                Icon(Icons.Filled.Lock, "Coffre-fort", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

fun categoryIcon(name: String?): ImageVector = when (name) {
    "Administratif" -> Icons.Filled.Folder
    "Banque" -> Icons.Filled.AccountBalance
    "Identité" -> Icons.Filled.Badge
    "Travail" -> Icons.Filled.Work
    "Santé" -> Icons.Filled.HealthAndSafety
    else -> Icons.Filled.Inventory2
}
