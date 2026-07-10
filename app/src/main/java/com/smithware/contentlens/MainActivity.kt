package com.smithware.contentlens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.smithware.contentlens.ui.ContentLensApp
import com.smithware.contentlens.ui.ContentLensViewModel
import com.smithware.contentlens.ui.ContentLensViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: ContentLensViewModel = viewModel(
                factory = ContentLensViewModelFactory(application)
            )
            ContentLensApp(vm)
        }
    }
}
