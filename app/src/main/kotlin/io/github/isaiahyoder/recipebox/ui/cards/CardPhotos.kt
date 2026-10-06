package io.github.isaiahyoder.recipebox.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import io.github.isaiahyoder.recipebox.R
import io.github.isaiahyoder.recipebox.appContainer

/**
 * A row of card photo thumbnails, numbered in order. Tapping one opens it
 * full screen. [onRemove] and [onMoveEarlier] add buttons for editing.
 */
@Composable
fun CardPhotoRow(
    photos: List<String>,
    modifier: Modifier = Modifier,
    onRemove: ((String) -> Unit)? = null,
    onMoveEarlier: ((String) -> Unit)? = null,
) {
    val store = LocalContext.current.appContainer.photos
    var viewing by remember { mutableStateOf<String?>(null) }

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(photos, key = { _, name -> name }) { index, name ->
            Box(Modifier.width(160.dp).height(110.dp).clip(RoundedCornerShape(12.dp))) {
                AsyncImage(
                    model = store.file(name),
                    contentDescription = stringResource(R.string.cards_photo_number, index + 1),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clickable { viewing = name },
                )
                if (photos.size > 1) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                onRemove?.let { remove ->
                    FilledTonalIconButton(
                        onClick = { remove(name) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cards_photo_remove, index + 1))
                    }
                }
                if (onMoveEarlier != null && index > 0) {
                    FilledTonalIconButton(
                        onClick = { onMoveEarlier(name) },
                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cards_photo_move_earlier, index + 1))
                    }
                }
            }
        }
    }

    viewing?.let { name -> CardPhotoViewer(store.file(name), onDismiss = { viewing = null }) }
}

/** Shows one card photo full screen. Pinch to zoom, drag to move, double-tap to reset. */
@Composable
fun CardPhotoViewer(photo: Any, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { _: Offset, zoom: Float, pan: Offset, _: Float ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = photo,
                contentDescription = stringResource(R.string.cards_photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            scale = 1f
                            offset = Offset.Zero
                        })
                    }
                    .transformable(transform)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
            IconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cards_close))
            }
        }
    }
}
