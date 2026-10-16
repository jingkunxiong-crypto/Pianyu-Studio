package com.pianyu.studio.watermark.photo

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PhotoState(val photo: ImportedPhoto? = null, val busy: Boolean = false, val message: String? = null,
                      val status: String? = null, val lastSaved: SavedPhoto? = null, val pendingShare: SharePhoto? = null)

class PhotoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PhotoRepository(File(application.cacheDir, "photo-session"))
    private val exporter = PhotoExporter(application)
    private val mutable = MutableStateFlow(PhotoState())
    val state = mutable.asStateFlow()
    init {
        // Only one editing session is kept. A fresh process starts from the home page.
        File(application.cacheDir, "photo-session").listFiles()?.forEach { it.delete() }
        File(application.cacheDir,"exports").listFiles()?.filter {
            it.extension=="tmp" || System.currentTimeMillis()-it.lastModified()>7L*24*60*60*1000
        }?.forEach {it.delete()}
    }
    fun consumeMessage() { mutable.value = mutable.value.copy(message = null) }
    fun consumeShare() { mutable.value = mutable.value.copy(pendingShare = null) }
    fun reportMessage(message: String) {mutable.value=mutable.value.copy(message=message)}
    fun export(request: PhotoExportRequest, saveToAlbum: Boolean) {
        if(mutable.value.busy) return
        mutable.value=mutable.value.copy(busy=true,status="正在合成原尺寸照片…")
        viewModelScope.launch {
            var exported: ExportedPhoto? = null
            try {
                exported=withContext(Dispatchers.IO) {exporter.render(request)}
                if(saveToAlbum) {
                    mutable.value=mutable.value.copy(status="正在保存到相册…")
                    val saved=withContext(Dispatchers.IO) {exporter.saveToAlbum(exported)}
                    mutable.value=mutable.value.copy(lastSaved=saved,message="已保存到相册 · ${saved.width} × ${saved.height}")
                    exported.file.delete()
                } else {
                    val share=exporter.sharePhoto(exported)
                    mutable.value=mutable.value.copy(pendingShare=share)
                }
            } catch(error: CancellationException) {exported?.file?.delete();throw error
            } catch(error: OutOfMemoryError) {
                exported?.file?.delete()
                mutable.value=mutable.value.copy(message="原尺寸合成所需内存不足，请选择较小的照片后重试。")
            } catch(error: Exception) {
                exported?.file?.delete()
                mutable.value=mutable.value.copy(message=error.message ?: "导出失败，请重试。")
            } finally {
                mutable.value=mutable.value.copy(busy=false,status=null)
            }
        }
    }
    fun importPhoto(uri: Uri, onImported: () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true)
        viewModelScope.launch {
            try {
                val photo = withContext(Dispatchers.IO) { repository.import(getApplication<Application>().contentResolver, uri) }
                val previous = mutable.value.photo
                mutable.value = PhotoState(photo = photo)
                previous?.file?.delete()
                // Previous bitmap may still be in a Compose frame; let the GC reclaim it.
                onImported()
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                mutable.value = mutable.value.copy(busy = false, message = error.message ?: "导入失败，请重试。")
            }
        }
    }
}
