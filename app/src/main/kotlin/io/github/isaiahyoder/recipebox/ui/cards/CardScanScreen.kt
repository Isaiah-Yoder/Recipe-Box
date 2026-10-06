package io.github.isaiahyoder.recipebox.ui.cards

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.isaiahyoder.recipebox.BuildFlavor
import io.github.isaiahyoder.recipebox.appContainer
import io.github.isaiahyoder.recipebox.cards.OnDeviceAiStatus
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardScanScreen(onDraft: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm = viewModel {
        CardScanViewModel(container.photos, container.cardReader, createSavedStateHandle())
    }
    val photos by vm.photoNames.collectAsStateWithLifecycle()
    val key by container.settings.geminiKey.collectAsStateWithLifecycle()
    val onDevice by container.onDeviceCards.status.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { container.onDeviceCards.refresh() }

    // The camera writes into a file the app shares with it, then the app saves its own copy.
    var cameraFile by rememberSaveable { mutableStateOf<String?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = cameraFile?.let(::File)
        if (saved && file != null) vm.add(listOf(Uri.fromFile(file)))
        cameraFile = null
    }
    val choosePhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) {
        vm.add(it)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan a recipe card") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Add a photo of each side of one recipe card, front first. The photos are saved with the recipe.",
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (photos.isEmpty()) {
                Box(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .height(110.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("No photos yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                CardPhotoRow(photos, onRemove = vm::remove, onMoveEarlier = vm::moveEarlier)
            }

            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val file = File(context.cacheDir, "camera/card-${System.currentTimeMillis()}.jpg")
                        file.parentFile?.mkdirs()
                        cameraFile = file.path
                        takePhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", file))
                    },
                    enabled = !vm.adding && vm.reading == null,
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Text("Take photo", Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = { choosePhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !vm.adding && vm.reading == null,
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Text("Choose photos", Modifier.padding(start = 8.dp))
                }
            }

            Text(
                readerHint(key != null, onDevice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            vm.error?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    Text(message, Modifier.padding(16.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            val reading = vm.reading
            if (reading != null || vm.adding) {
                Row(
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(
                        if (reading != null) "Reading with ${reading.label}…" else "Saving photos…",
                        Modifier.padding(start = 16.dp),
                    )
                }
            } else {
                Button(
                    onClick = { vm.read(onDraft) },
                    enabled = photos.isNotEmpty(),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    Text("Read card")
                }
                TextButton(
                    onClick = { vm.typeInstead(onDraft) },
                    enabled = photos.isNotEmpty(),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    Text("Type it in myself", textAlign = TextAlign.Center)
                }
            }
        }
    }
}

private fun readerHint(hasKey: Boolean, onDevice: OnDeviceAiStatus): String = when {
    !BuildFlavor.OWN_GEMINI_KEY && onDevice == OnDeviceAiStatus.READY -> "Cards are read with this phone's built-in AI."
    !BuildFlavor.OWN_GEMINI_KEY -> "Cards are read with basic text recognition, which often misreads handwriting."
    hasKey -> "Cards are read with Gemini, using your key from Settings."
    onDevice == OnDeviceAiStatus.READY -> "Cards are read with this phone's built-in AI. A Gemini key in Settings reads handwriting better."
    else -> "Without a Gemini key, cards are read with basic text recognition, which often misreads handwriting. " +
        "You can add a key in Settings."
}

private const val MAX_PHOTOS = 6
