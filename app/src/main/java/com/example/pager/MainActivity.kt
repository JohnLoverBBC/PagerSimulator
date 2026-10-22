package com.example.pager

import android.Manifest
import android.content.pm.ActivityInfo
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
            var func by remember { mutableStateOf(false) }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                PagerCore.log("POST_NOTIFICATIONS ${if (it) "GRANTED" else "DENIED"}")
            }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33 && !PagerCore.hasNotifPermission())
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            LaunchedEffect(PagerCore.orient) {
                requestedOrientation = when (PagerCore.orient) {
                    1 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    2 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                }
            }
            BackHandler(enabled = func) { func = false }
            if (func) FuncScreen(
                onClose = { func = false },
                requestPerm = { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            ) else PagerScreen(onFunc = { func = true })
        }
    }

    override fun onResume() {
        super.onResume()
        PagerCore.checkDaily()
    }
}
