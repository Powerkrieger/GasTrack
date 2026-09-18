package com.example.gastrack.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.gastrack.storage.ImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads a down-sampled bitmap off the main thread; null while loading or if the file is gone. */
@Composable
fun rememberScaledBitmap(path: String?, maxDim: Int): Bitmap? {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(path, maxDim) {
        bitmap = if (path == null) null else withContext(Dispatchers.IO) { ImageStorage.loadScaled(path, maxDim) }
    }
    return bitmap
}
