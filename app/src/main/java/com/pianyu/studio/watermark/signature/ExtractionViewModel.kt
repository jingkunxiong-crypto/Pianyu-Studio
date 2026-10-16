package com.pianyu.studio.watermark.signature

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pianyu.studio.watermark.photo.PhotoRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class ExtractionState(val source: Bitmap?=null,val settings: ExtractionSettings=ExtractionSettings(),
    val result: Bitmap?=null,val loading: Boolean=false,val processing: Boolean=false,
    val background: BackgroundKey?=null,val exporting: Boolean=false,
    val wasResized: Boolean=false,val message: String?=null,val error: String?=null)

class ExtractionViewModel(application: Application): AndroidViewModel(application) {
    private val mutable=MutableStateFlow(ExtractionState())
    val state=mutable.asStateFlow()
    private var processing: Job?=null
    private var generation=0
    fun consumeMessage() {mutable.value=mutable.value.copy(message=null)}
    fun importPhoto(uri: Uri,smart: Boolean=false,onReady: ()->Unit) {
        if(mutable.value.loading) return
        mutable.value=mutable.value.copy(loading=true)
        viewModelScope.launch {
            try {
                val photo=withContext(Dispatchers.IO) {
                    PhotoRepository(File(getApplication<Application>().cacheDir,"signature-extraction"))
                        .import(getApplication<Application>().contentResolver,uri)
                }
                photo.file.delete()
                processing?.cancel();generation++
                val background=withContext(Dispatchers.Default) {SignatureExtractor.detectBackground(photo.preview)}
                val settings=ExtractionSettings(colorKey=smart,cutoff=if(smart) 240f else 210f)
                mutable.value=ExtractionState(source=photo.preview,background=background,wasResized=maxOf(photo.width,photo.height)>2048)
                onReady();update(settings)
            } catch(error: CancellationException) {throw error}
            catch(_: OutOfMemoryError) {mutable.value=mutable.value.copy(loading=false,message="图片过大，请选择较小的签名图片。 ")}
            catch(error: Exception) {mutable.value=mutable.value.copy(loading=false,message=error.message ?: "读取签名照片失败。")}
        }
    }
    fun update(settings: ExtractionSettings) {
        val source=mutable.value.source ?: return
        processing?.cancel()
        val request=++generation
        mutable.value=mutable.value.copy(settings=settings,processing=true,result=null,error=null)
        processing=viewModelScope.launch {
            delay(150)
            try {
                val result=withContext(Dispatchers.Default) {SignatureExtractor.extract(source,settings)}
                if(request==generation) mutable.value=mutable.value.copy(result=result,processing=false)
            } catch(error: CancellationException) {throw error}
            catch(_: OutOfMemoryError) {if(request==generation) mutable.value=mutable.value.copy(processing=false,error="图片处理内存不足，请缩小图片后重试。 ")}
            catch(error: Exception) {
                if(request==generation) mutable.value=mutable.value.copy(processing=false,error="提取失败，请调整选区或重新选图。")
            }
        }
    }
    fun exportPng(uri: Uri) {
        val current=mutable.value
        val result=current.result
        if(result==null || current.processing || current.exporting) {mutable.value=current.copy(message="请等待有效的透明签名预览后再导出。 ");return}
        mutable.value=current.copy(exporting=true)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val bytes=java.io.ByteArrayOutputStream().use {buffer ->
                        check(result.compress(Bitmap.CompressFormat.PNG,100,buffer)) {"PNG 编码失败。"};buffer.toByteArray()
                    }
                    requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri,"w")) {"无法写入目标文件。"}.use {it.write(bytes);it.flush()}
                }
                mutable.value=mutable.value.copy(message="透明 PNG 已导出。")
            } catch(e: CancellationException) {throw e}
            catch(_: OutOfMemoryError) {mutable.value=mutable.value.copy(message="导出内存不足，请缩小图片后重试。 ")}
            catch(_: Exception) {mutable.value=mutable.value.copy(message="PNG 导出失败，请重新选择保存位置。 ")}
            finally {mutable.value=mutable.value.copy(exporting=false)}
        }
    }
    fun clear() {generation++;processing?.cancel();mutable.value=ExtractionState()}
}
