package com.pianyu.studio.collage.ui

import com.pianyu.studio.ui.*
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.pianyu.studio.collage.ui.theme.GeyiDeepBlue
import com.pianyu.studio.collage.ui.theme.GeyiGraphite

private const val MaxPhotoCount = 9

private enum class GeyiScreen {
    Home,
    Editor,
}

@Composable
fun GeyiApp(onExit: () -> Unit = {}, onSign: (Uri) -> Unit = {}) {
    var selectedPhotoUris by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var currentScreen by rememberSaveable { mutableStateOf(GeyiScreen.Home) }
    val snackbarHostState = remember { SnackbarHostState() }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MaxPhotoCount),
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedPhotoUris = uris.distinct().take(MaxPhotoCount).map(Uri::toString)
            currentScreen = GeyiScreen.Home
        }
    }

    val openPhotoPicker = {
        photoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    BackHandler { if (currentScreen == GeyiScreen.Editor) currentScreen = GeyiScreen.Home else onExit() }

    Box(Modifier.fillMaxSize()) {
        StudioBackground()
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().padding(innerPadding)) {
                if (currentScreen == GeyiScreen.Home) StudioToolbar("拼图", onExit)
            when (currentScreen) {
                GeyiScreen.Home -> HomeScreen(
                    innerPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    photoUris = selectedPhotoUris,
                    onStart = openPhotoPicker,
                    onRemove = { index ->
                        selectedPhotoUris = selectedPhotoUris.filterIndexed { itemIndex, _ ->
                            itemIndex != index
                        }
                    },
                    onContinue = { currentScreen = GeyiScreen.Editor },
                    showBrandMark = true,
                )

                GeyiScreen.Editor -> EditorScreen(
                    innerPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    photoUris = selectedPhotoUris,
                    snackbarHostState = snackbarHostState,
                    onSign = onSign,
                    onReplacePhoto = { index, uri ->
                        if (index in selectedPhotoUris.indices) {
                            selectedPhotoUris = selectedPhotoUris.toMutableList().apply {
                                this[index] = uri.toString()
                            }
                        }
                    },
                    onNewCollage = {
                        selectedPhotoUris = emptyList()
                        currentScreen = GeyiScreen.Home
                        openPhotoPicker()
                    },
                    onBack = { currentScreen = GeyiScreen.Home },
                )
            }
            }
        }
    }
}
