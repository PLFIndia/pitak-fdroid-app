package dev.khoj.pitaka.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.khoj.pitaka.data.images.CoverPaths

/**
 * Compose-level cover image renderer (Phase 8).
 *
 * Wraps Coil's [AsyncImage] with a sensible book-shape placeholder so the
 * "no cover" state is graceful rather than a blank box.
 *
 * Accepts:
 *  - http(s) URLs from Open Library / Google Books → passed to Coil as-is
 *  - relative `covers/<uuid>.jpg` references (PLAN-covers.md D1) → resolved
 *    to an absolute file under `filesDir` before handing to Coil
 *  - legacy `file://…/covers/<id>.jpg` absolute paths → resolved the same way
 *    (interim, until the Wave-4 healer rewrites them)
 *  - null / blank → shows the placeholder
 *
 * The aspect ratio is locked to 2:3 (book convention).
 */
@Composable
fun BookCoverImage(
    coverUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Local references resolve to an absolute File; remote URLs pass through.
    val model: Any? = when {
        coverUrl.isNullOrBlank() -> null
        CoverPaths.isLocal(coverUrl) ->
            CoverPaths.absoluteCoverFile(context.filesDir, coverUrl)
        else -> coverUrl
    }
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
