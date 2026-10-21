package com.example.pager

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PagerCore.init(this)
        setContent {
            var debug by remember { mutableStateOf(false) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                PagerCore.log("POST_NOTIFICATIONS ${if (it) "GRANTED" else "DENIED"}")
            }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33 && !PagerCore.hasNotifPermission())
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            BackHandler(enabled = debug) { debug = false }
            if (debug) DebugScreen(
                onClose = { debug = false },
                requestPerm = { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            ) else PagerScreen(onDebug = { debug = true })
        }
    }
}
