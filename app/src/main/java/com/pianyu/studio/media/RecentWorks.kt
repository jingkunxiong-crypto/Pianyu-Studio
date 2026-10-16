package com.pianyu.studio.media

import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class RecentWork(val uri: String, val name: String, val kind: String)

object RecentWorks {
    @Synchronized fun list(context: Context): List<RecentWork> = runCatching {
        val array = JSONArray(context.getSharedPreferences("studio_works", 0).getString("items", "[]"))
        List(array.length()) { i -> array.getJSONObject(i).let {
            RecentWork(it.getString("uri"), it.getString("name"), it.getString("kind"))
        } }
    }.getOrDefault(emptyList())

    @Synchronized fun add(context: Context, uri: Uri, name: String, kind: String) {
        val items = (listOf(RecentWork(uri.toString(), name, kind)) + list(context))
            .distinctBy { it.uri }.take(30)
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("uri", it.uri).put("name", it.name).put("kind", it.kind)) }
        context.getSharedPreferences("studio_works", 0).edit().putString("items", array.toString()).apply()
    }

    fun share(context: Context, uri: Uri) {
        val type = context.contentResolver.getType(uri) ?: "image/*"
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            this.type = type
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Pianyu Studio", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "分享作品"))
    }
}
