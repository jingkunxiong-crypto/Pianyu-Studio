package com.pianyu.studio.watermark.signature

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.hypot

data class SignatureState(
    val assets: List<SignatureAsset> = emptyList(),
    val loaded: Boolean = false,
    val loadFailed: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val selected: SignatureAsset? = null,
    val selectedBitmap: Bitmap? = null,
    val strokes: List<InkStroke> = emptyList(),
    val redo: List<InkStroke> = emptyList(),
    val penWidth: Float = 9f,
    val pending: ImportedSignature? = null,
    val pendingOrigin: String = "手写"
)

class SignatureViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SignatureRepository(File(application.filesDir,"signatures"))
    private val mutable = MutableStateFlow(SignatureState())
    val state = mutable.asStateFlow()
    init { reload() }

    fun reload() {
        viewModelScope.launch {
            try {
                val assets = withContext(Dispatchers.IO) {
                    BundledSignatures.ensureAdded(getApplication<Application>(),repository)
                    repository.list()
                }
                mutable.value = mutable.value.copy(assets=assets,loaded=true,loadFailed=false)
            } catch(error: CancellationException) { throw error
            } catch(error: Exception) {
                mutable.value = mutable.value.copy(loaded=true,loadFailed=true,message="读取签名库失败，请重试。")
            }
        }
    }
    fun consumeMessage() { mutable.value = mutable.value.copy(message=null) }
    fun setPenWidth(width: Float) { mutable.value = mutable.value.copy(penWidth=width.coerceIn(3f,24f)) }
    fun beginStroke(point: InkPoint) {
        if(mutable.value.busy) return
        mutable.value = mutable.value.copy(strokes=mutable.value.strokes + InkStroke(listOf(point),mutable.value.penWidth),redo=emptyList())
    }
    fun appendPoint(point: InkPoint) {
        val current = mutable.value
        val last = current.strokes.lastOrNull() ?: return
        if(current.busy || last.points.size >= 12000 || hypot(point.x-last.points.last().x,point.y-last.points.last().y) < .6f) return
        mutable.value = current.copy(strokes=current.strokes.dropLast(1)+last.copy(points=last.points+point))
    }
    fun undo() {
        val current=mutable.value
        if(current.busy || current.strokes.isEmpty()) return
        mutable.value=current.copy(strokes=current.strokes.dropLast(1),redo=current.redo+current.strokes.last())
    }
    fun redo() {
        val current=mutable.value
        if(current.busy || current.redo.isEmpty()) return
        mutable.value=current.copy(strokes=current.strokes+current.redo.last(),redo=current.redo.dropLast(1))
    }
    fun clearDraft() { mutable.value=mutable.value.copy(strokes=emptyList(),redo=emptyList()) }
    fun prepareHandwritten() {
        if(mutable.value.busy) return
        val strokes=mutable.value.strokes
        if(strokes.isEmpty()) return
        operation {
            val bitmap=withContext(Dispatchers.Default) { InkRenderer.export(strokes) }
            mutable.value=mutable.value.copy(pending=ImportedSignature(bitmap,true,false),pendingOrigin="手写")
        }
    }
    fun importPng(uri: Uri) {
        if(mutable.value.busy) return
        operation {
            val imported=withContext(Dispatchers.IO) { SignatureRepository.importPng(getApplication<Application>().contentResolver,uri) }
            mutable.value=mutable.value.copy(pending=imported,pendingOrigin="导入")
        }
    }
    fun dismissPending() { if(!mutable.value.busy) mutable.value=mutable.value.copy(pending=null) }
    fun prepareExtracted(bitmap: Bitmap,wasResized: Boolean) {
        if(mutable.value.busy) return
        mutable.value=mutable.value.copy(pending=ImportedSignature(bitmap,true,wasResized),pendingOrigin="提取")
    }

    fun savePending(name: String, onSaved: () -> Unit) {
        if(mutable.value.busy) return
        val pending=mutable.value.pending ?: return
        operation {
            val asset=withContext(Dispatchers.IO) { repository.save(name,pending.bitmap) }
            val assets=withContext(Dispatchers.IO) { repository.list() }
            mutable.value=mutable.value.copy(assets=assets,pending=null,selected=asset,selectedBitmap=pending.bitmap,
                strokes=if(mutable.value.pendingOrigin=="手写") emptyList() else mutable.value.strokes,
                redo=if(mutable.value.pendingOrigin=="手写") emptyList() else mutable.value.redo,message="签名已保存。")
            onSaved()
        }
    }
    fun select(asset: SignatureAsset, onSelected: () -> Unit) {
        if(mutable.value.busy) return
        operation {
            val bitmap=withContext(Dispatchers.IO) { repository.bitmap(asset.id) }
            mutable.value=mutable.value.copy(selected=asset,selectedBitmap=bitmap)
            onSelected()
        }
    }
    fun useDemo() { mutable.value=mutable.value.copy(selected=null,selectedBitmap=null) }
    fun rename(asset: SignatureAsset, name: String, onDone: () -> Unit) {
        operation {
            withContext(Dispatchers.IO) { repository.rename(asset.id,name) }
            val assets=withContext(Dispatchers.IO) { repository.list() }
            mutable.value=mutable.value.copy(assets=assets,selected=if(mutable.value.selected?.id==asset.id) asset.copy(name=name.trim()) else mutable.value.selected)
            onDone()
        }
    }
    fun delete(asset: SignatureAsset, onDone: () -> Unit) {
        operation {
            withContext(Dispatchers.IO) { repository.delete(asset.id) }
            val assets=withContext(Dispatchers.IO) { repository.list() }
            // Keep the already decoded editing copy even if the source asset is deleted.
            mutable.value=mutable.value.copy(assets=assets,message="签名已删除，本次编辑预览仍可使用。")
            onDone()
        }
    }
    suspend fun thumbnail(id: String): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { repository.bitmap(id,512) }.getOrNull()
    }
    private fun operation(block: suspend () -> Unit) {
        if(mutable.value.busy) return
        mutable.value=mutable.value.copy(busy=true)
        viewModelScope.launch {
            try { block() }
            catch(error: CancellationException) { throw error }
            catch(error: Exception) { mutable.value=mutable.value.copy(message=error.message ?: "操作失败，请重试。") }
            finally { mutable.value=mutable.value.copy(busy=false) }
        }
    }
}
