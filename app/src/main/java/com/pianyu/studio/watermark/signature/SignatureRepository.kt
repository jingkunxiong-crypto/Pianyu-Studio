package com.pianyu.studio.watermark.signature

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class SignatureAsset(val id: String, val name: String, val createdAt: Long)
data class ImportedSignature(val bitmap: Bitmap, val hasTransparency: Boolean, val wasResized: Boolean)

class SignatureRepository(private val directory: File) {
    private val index get() = AtomicFile(File(directory, "index.json"))
    private fun imageFile(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(directory, "$id.png")
    }

    @Synchronized fun list(): List<SignatureAsset> {
        if (!directory.exists()) return emptyList()
        if (!File(directory, "index.json").exists() && !File(directory, "index.json.bak").exists()) return emptyList()
        val array = JSONArray(String(index.readFully(), Charsets.UTF_8))
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            SignatureAsset(item.getString("id"), item.getString("name"), item.getLong("createdAt"))
        }.filter { imageFile(it.id).isFile }.sortedByDescending { it.createdAt }
    }

    private fun write(assets: List<SignatureAsset>) {
        check(directory.isDirectory || directory.mkdirs()) { "无法创建本地签名目录。" }
        val data = JSONArray().apply {
            assets.forEach { put(JSONObject().put("id",it.id).put("name",it.name).put("createdAt",it.createdAt)) }
        }.toString().toByteArray(Charsets.UTF_8)
        val stream = index.startWrite()
        try { stream.write(data); index.finishWrite(stream) }
        catch (error: Exception) { index.failWrite(stream); throw error }
    }

    @Synchronized fun save(name: String, bitmap: Bitmap, id: String = UUID.randomUUID().toString()): SignatureAsset {
        val clean = validateName(name)
        val existing = list()
        require(existing.none {it.id==id}) { "这份签名已存在。" }
        check(directory.isDirectory || directory.mkdirs()) { "无法创建本地签名目录。" }
        val asset = SignatureAsset(id, clean, System.currentTimeMillis())
        val destination = imageFile(asset.id)
        val temp = File(directory, "${asset.id}.tmp")
        try {
            FileOutputStream(temp).use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) { "签名图片保存失败。" }
                out.fd.sync()
            }
            check(temp.renameTo(destination)) { "签名文件保存失败。" }
            write(listOf(asset) + existing)
            return asset
        } catch (error: Exception) {
            temp.delete(); destination.delete(); throw error
        }
    }

    @Synchronized fun rename(id: String, name: String) {
        val clean = validateName(name)
        val existing = list()
        require(existing.any { it.id == id }) { "签名已不存在。" }
        write(existing.map { if (it.id == id) it.copy(name = clean) else it })
    }

    @Synchronized fun delete(id: String) {
        val existing = list()
        write(existing.filterNot { it.id == id })
        // Remove the index entry first: a failed file deletion leaves only an unused file.
        imageFile(id).delete()
    }

    fun bitmap(id: String, maximumEdge: Int = 2048): Bitmap {
        val file = imageFile(id)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true; inSampleSize = 1 }
        BitmapFactory.decodeFile(file.path, options)
        require(options.outWidth > 0 && options.outHeight > 0) { "无法读取这份签名，请重新导入。" }
        options.inJustDecodeBounds = false
        while (maxOf(options.outWidth,options.outHeight)/options.inSampleSize > maximumEdge) options.inSampleSize *= 2
        return requireNotNull(BitmapFactory.decodeFile(file.path, options)) { "签名图片已损坏。" }
    }

    companion object {
        fun validateName(name: String): String {
            val clean = name.trim()
            require(clean.isNotEmpty()) { "请输入签名名称。" }
            require(clean.length <= 40) { "名称最多 40 个字符。" }
            return clean
        }

        fun importPng(resolver: ContentResolver, uri: Uri): ImportedSignature {
            // Bound both input bytes and decoded pixels before allocating a bitmap.
            val bytes = requireNotNull(resolver.openInputStream(uri)) { "无法打开所选图片。" }.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16*1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size()+count <= 24*1024*1024) { "PNG 文件过大，请选择 24 MB 以内的签名图片。" }
                    output.write(buffer,0,count)
                }
                output.toByteArray()
            }
            val magic = byteArrayOf(-119,80,78,71,13,10,26,10)
            require(bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(magic)) { "请选择 PNG 格式图片。" }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true; inSampleSize = 1 }
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
            require(options.outWidth > 0 && options.outHeight > 0) { "PNG 图片已损坏，无法读取。" }
            require(options.outWidth.toLong()*options.outHeight <= 100_000_000L) { "图片尺寸过大，请先缩小签名图片。" }
            val resized = maxOf(options.outWidth,options.outHeight) > 2048
            while (maxOf(options.outWidth,options.outHeight)/options.inSampleSize > 2048) options.inSampleSize *= 2
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)) { "无法解码所选 PNG。" }
            return try {
                var transparent = false
                val row = IntArray(bitmap.width)
                for (y in 0 until bitmap.height) {
                    bitmap.getPixels(row,0,bitmap.width,0,y,bitmap.width,1)
                    if (row.any { (it ushr 24) < 255 }) transparent = true
                }
                ImportedSignature(InkRenderer.cropTransparent(bitmap),transparent,resized)
            } finally { bitmap.recycle() }
        }
    }
}
