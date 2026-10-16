package com.pianyu.studio.watermark.photo

import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class WatermarkPreset(val id: String, val name: String, val signatureId: String?,
    val pose: WatermarkPose, val opacity: Float, val ink: Int, val format: PhotoFormat, val quality: Int)

class PresetRepository(directory: File) {
    private val storage = AtomicFile(File(directory.apply { mkdirs() }, "presets.json"))
    fun list(): List<WatermarkPreset> {
        if (!storage.baseFile.exists() && !File(storage.baseFile.path+".bak").exists()) return emptyList()
        val array = JSONArray(storage.openRead().bufferedReader().use { it.readText() })
        return (0 until array.length()).map { i ->
            val o=array.getJSONObject(i)
            validate(WatermarkPreset(o.getString("id"),o.getString("name"),o.optString("signatureId").takeIf { it.isNotEmpty() },
                WatermarkPose(o.getDouble("x").toFloat(),o.getDouble("y").toFloat(),o.getDouble("width").toFloat()),
                o.getDouble("opacity").toFloat(),o.getInt("ink"),PhotoFormat.valueOf(o.getString("format")),o.getInt("quality")))
        }.also { require(it.map { p -> p.id }.distinct().size==it.size) }
    }
    fun save(preset: WatermarkPreset) {
        val p=validate(preset.copy(name=preset.name.trim()))
        val all=list(); require(all.size<100 || all.any {it.id==p.id}) {"最多保存 100 个预设。"}
        write(listOf(p)+all.filterNot {it.id==p.id})
    }
    fun delete(id: String) {write(list().filterNot {it.id==id})}
    private fun validate(p: WatermarkPreset): WatermarkPreset {
        UUID.fromString(p.id); p.signatureId?.let {UUID.fromString(it)}
        require(p.name.isNotBlank() && p.name.length<=40) {"名称请输入 1–40 个字符。"}
        require(p.pose.x in 0f..1f && p.pose.y in 0f..1f && p.pose.width in .08f.. .65f && p.opacity in 0f..1f && p.ink in 0..2 && p.quality in 70..100)
        return p
    }
    private fun write(all: List<WatermarkPreset>) {
        val a=JSONArray()
        all.forEach { p -> a.put(JSONObject().put("id",p.id).put("name",p.name).put("signatureId",p.signatureId ?: "")
            .put("x",p.pose.x).put("y",p.pose.y).put("width",p.pose.width).put("opacity",p.opacity)
            .put("ink",p.ink).put("format",p.format.name).put("quality",p.quality)) }
        val stream=storage.startWrite()
        try {stream.write(a.toString().toByteArray(Charsets.UTF_8));storage.finishWrite(stream)}
        catch(error: Throwable) {storage.failWrite(stream);throw error}
    }
}
