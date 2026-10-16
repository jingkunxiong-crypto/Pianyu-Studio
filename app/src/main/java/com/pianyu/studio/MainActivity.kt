package com.pianyu.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.lifecycle.ViewModelProvider
import com.pianyu.studio.watermark.signature.SignatureViewModel
import com.pianyu.studio.watermark.signature.ExtractionViewModel
import com.pianyu.studio.watermark.photo.PhotoViewModel
import com.pianyu.studio.watermark.photo.BatchStudioViewModel
import com.pianyu.studio.watermark.ui.theme.PianyuStudioTheme
import com.pianyu.studio.photoedit.PhotoEditViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        val provider = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))
        val signatures = provider[SignatureViewModel::class.java]
        val photos = provider[PhotoViewModel::class.java]
        val extraction = provider[ExtractionViewModel::class.java]
        val batch = provider[BatchStudioViewModel::class.java]
        val photoEditor = provider[PhotoEditViewModel::class.java]
        setContent { PianyuStudioTheme { PianyuStudioApp(signatures, photos, extraction, batch, photoEditor) } }
    }
}
