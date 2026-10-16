package com.pianyu.studio.watermark.photo

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class BatchResult(val name: String, val saved: SavedPhoto?=null, val error: String?=null)
data class BatchState(val uris: List<Uri> = emptyList(), val busy: Boolean=false, val stopping: Boolean=false,
    val preview: ImportedPhoto?=null, val loading: Boolean=false, val previewError: String?=null,
    val current: Int=0, val results: List<BatchResult> = emptyList(), val finished: Boolean=false)
class BatchStudioViewModel(application: Application): AndroidViewModel(application) {
    private val repository=PhotoRepository(File(application.cacheDir,"batch-session"))
    private val exporter=PhotoExporter(application)
    private val presetsRepository=PresetRepository(File(application.filesDir,"watermark-presets"))
    private val mutable=MutableStateFlow(BatchState())
    val state=mutable.asStateFlow()
    private val presetList=MutableStateFlow<List<WatermarkPreset>>(emptyList())
    val presets=presetList.asStateFlow()
    private val messages=MutableStateFlow<String?>(null)
    val message=messages.asStateFlow()
    init {
        File(application.cacheDir,"batch-session").listFiles()?.forEach {it.delete()}
        try {presetList.value=presetsRepository.list()} catch (_: Exception) {messages.value="预设读取失败，原文件已保留。"}
    }
    fun consumeMessage() {messages.value=null}
    fun select(uris: List<Uri>) {
        if(mutable.value.busy || mutable.value.loading) return
        if(uris.size>20) {messages.value="一次最多选择 20 张照片，请重新选择。";return}
        if(uris.isEmpty()) return
        mutable.value=BatchState(uris.distinct(),loading=true)
        viewModelScope.launch {
            try {
                val photo=withContext(Dispatchers.IO) {
                    repository.import(getApplication<Application>().contentResolver,uris.first()).also {it.file.delete()}
                }
                mutable.value=mutable.value.copy(preview=photo,loading=false)
            } catch(e: CancellationException) {throw e}
            catch (_: OutOfMemoryError) {mutable.value=mutable.value.copy(loading=false,previewError="首张照片预览内存不足。")}
            catch(e: Exception) {mutable.value=mutable.value.copy(loading=false,previewError="首张照片无法预览，批量处理时将逐张检查。")}
        }
    }
    fun savePreset(p: WatermarkPreset, done: () -> Unit) {
        try {presetsRepository.save(p);presetList.value=presetsRepository.list();done();messages.value="预设已保存。"}
        catch (e: Exception) {messages.value=e.message ?: "预设保存失败。"}
    }
    fun deletePreset(p: WatermarkPreset) {
        try {presetsRepository.delete(p.id);presetList.value=presetsRepository.list()}
        catch (_: Exception) {messages.value="删除失败，请重试。"}
    }
    fun stop() {if(mutable.value.busy) mutable.value=mutable.value.copy(stopping=true)}
    fun start(template: PhotoExportRequest) {
        val uris=mutable.value.uris
        if(mutable.value.busy || mutable.value.loading || uris.isEmpty()) return
        mutable.value=mutable.value.copy(busy=true,stopping=false,current=0,results=emptyList(),finished=false)
        viewModelScope.launch {
            try {
                for((index,uri) in uris.withIndex()) {
                    if(mutable.value.stopping) break
                    mutable.value=mutable.value.copy(current=index+1)
                    val result=withContext(Dispatchers.IO) {
                        var photo: ImportedPhoto?=null;var exported: ExportedPhoto?=null
                        try {
                            photo=repository.import(getApplication<Application>().contentResolver,uri)
                            exported=exporter.render(template.copy(photo=photo))
                            BatchResult(photo.name,saved=exporter.saveToAlbum(exported))
                        } catch(e: CancellationException) {throw e}
                        catch (_: OutOfMemoryError) {BatchResult(photo?.name ?: "照片 ${index+1}",error="内存不足，请选择较小照片")}
                        catch(e: Exception) {BatchResult(photo?.name ?: "照片 ${index+1}",error=e.message ?: "处理失败")}
                        finally {exported?.file?.delete();photo?.file?.delete();photo?.preview?.recycle()}
                    }
                    mutable.value=mutable.value.copy(results=mutable.value.results+result)
                }
            } finally {mutable.value=mutable.value.copy(busy=false,finished=true)}
        }
    }
}
