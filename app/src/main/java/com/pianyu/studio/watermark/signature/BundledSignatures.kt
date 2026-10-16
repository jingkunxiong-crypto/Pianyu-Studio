package com.pianyu.studio.watermark.signature

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.Typeface

/** Generic typographic demo marks; no personal signature is distributed. */
object BundledSignatures {
    data class Entry(val key: String, val id: String, val name: String, val path: String)
    val entries = listOf(
        Entry("demo_white_v1","d0b0b120-ec32-4a01-8800-e2c9baac0021","Pianyu 演示 · 白色","white"),
        Entry("demo_black_v1","d0b0b120-ec32-4a01-8800-e2c9baac0022","Pianyu 演示 · 黑色","black")
    )
    @Synchronized fun ensureAdded(context: Context, repository: SignatureRepository,
                                  preferences: SharedPreferences = context.getSharedPreferences("bundled-signatures",Context.MODE_PRIVATE)) {
        entries.forEach {entry ->
            if(preferences.getBoolean(entry.key,false)) return@forEach
            // A stable ID prevents duplication if the app exits after saving but before marking the import complete.
            if(repository.list().none {it.id==entry.id}) {
                val source=Bitmap.createBitmap(900,300,Bitmap.Config.ARGB_8888)
                val canvas=Canvas(source)
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color=if(entry.path=="white") Color.WHITE else Color.rgb(38,52,55)
                    textSize=170f
                    typeface=Typeface.create("serif",Typeface.ITALIC)
                }
                canvas.drawText("Pianyu",70f,200f,paint)
                paint.strokeWidth=4f
                canvas.drawLine(65f,238f,720f,220f,paint)
                try {
                    val cropped=InkRenderer.cropTransparent(source)
                    try {repository.save(entry.name,cropped,entry.id)} finally {cropped.recycle()}
                } finally {source.recycle()}
            }
            check(preferences.edit().putBoolean(entry.key,true).commit()) { "签名导入记录保存失败，请重试。" }
        }
    }
}
