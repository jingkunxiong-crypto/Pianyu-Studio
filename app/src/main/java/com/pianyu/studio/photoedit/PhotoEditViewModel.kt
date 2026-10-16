package com.pianyu.studio.photoedit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pianyu.studio.photoedit.editing.EditSettings
import com.pianyu.studio.photoedit.editing.PhotoEditorEngine
import com.pianyu.studio.watermark.photo.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

data class EditState(val photo: ImportedPhoto? = null, val settings: EditSettings = EditSettings(),
    val preview: Bitmap? = null, val busy: Boolean = false, val rendering: Boolean = false,
    val error: String? = null, val saved: SavedPhoto? = null)

class PhotoEditViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(EditState())
    val state = mutable.asStateFlow()
    private var input: String? = null
    private var previewJob: Job? = null
    private val directory = File(application.cacheDir, "edit-session")
    init { directory.listFiles()?.forEach { it.delete() } }

    fun open(uri: String) {
        if (input == uri && mutable.value.photo != null) return
        previewJob?.cancel()
        mutable.value = EditState(busy = true)
        viewModelScope.launch {
            try {
                val photo = withContext(Dispatchers.IO) {
                    PhotoRepository(directory).import(getApplication<Application>().contentResolver, Uri.parse(uri))
                }
                input = uri
                mutable.value = EditState(photo = photo, preview = photo.preview)
                directory.listFiles()?.filter { it != photo.file }?.forEach { it.delete() }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { mutable.value = EditState(error = e.message ?: "无法打开照片") }
        }
    }

    fun update(settings: EditSettings) {
        if (mutable.value.busy) return
        previewJob?.cancel()
        val photo = mutable.value.photo ?: return
        mutable.value = mutable.value.copy(settings = settings.normalized(), rendering = true, saved = null)
        previewJob = viewModelScope.launch {
            delay(90)
            try {
                val result = withContext(Dispatchers.Default) {
                    PhotoEditorEngine.applyEdits(photo.preview, settings)
                }
                mutable.value = mutable.value.copy(preview = result, rendering = false)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { mutable.value = mutable.value.copy(rendering = false, error = e.message) }
        }
    }

    fun clearError() { mutable.value = mutable.value.copy(error = null) }
    fun reportError(message: String) { mutable.value = mutable.value.copy(error = message) }

    fun export() {
        val current = mutable.value
        val photo = current.photo ?: return
        if (current.busy) return
        mutable.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        while (maxOf(photo.width, photo.height) / inSampleSize > 4096) inSampleSize *= 2
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val decoded = BitmapFactory.decodeFile(photo.file.path, options) ?: error("无法读取照片")
                    var oriented: Bitmap? = null
                    var output: Bitmap? = null
                    val file = File(getApplication<Application>().cacheDir, "exports/Pianyu_Edit_${UUID.randomUUID()}.jpg")
                    file.parentFile?.mkdirs()
                    try {
                        oriented = PhotoRepository.orient(decoded, photo.orientation)
                        output = PhotoEditorEngine.applyEdits(oriented, current.settings)
                        file.outputStream().use { check(output.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
                        PhotoExporter(getApplication()).saveToAlbum(ExportedPhoto(file, output.width, output.height, PhotoFormat.JPG), "照片编辑")
                    } finally {
                        output?.recycle()
                        if (oriented !== decoded) oriented?.recycle()
                        decoded.recycle()
                        file.delete()
                    }
                }
                mutable.value = mutable.value.copy(busy = false, saved = saved)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { mutable.value = mutable.value.copy(busy = false, error = e.message ?: "保存失败")
            } catch (e: OutOfMemoryError) { mutable.value = mutable.value.copy(busy = false, error = "照片过大，内存不足。请缩小照片后重试。") }
        }
    }
}
