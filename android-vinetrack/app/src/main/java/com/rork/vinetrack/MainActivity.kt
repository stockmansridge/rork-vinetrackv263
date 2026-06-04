package com.rork.vinetrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rork.vinetrack.data.AppConfig
import com.rork.vinetrack.ui.RootScreen
import com.rork.vinetrack.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppConfig.logDiagnostics()
        enableEdgeToEdge()
        setContent {
            AppTheme {
                RootScreen()
            }
        }
    }
}
