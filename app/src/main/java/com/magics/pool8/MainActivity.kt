package com.magics.pool8

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.magics.pool8.ui.theme.Pool8Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force portrait orientation programmatically (covers tablets)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        
        enableEdgeToEdge()

        // Initialize the Magics Physics Engine
        val engine = GameEngine()

        setContent {
            Pool8Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showSplash by remember { mutableStateOf(true) }
                    if (showSplash) {
                        PoolSplashScreen(onTimeout = { showSplash = false })
                    } else {
                        PoolGameScreen(engine = engine)
                    }
                }
            }
        }
    }
}