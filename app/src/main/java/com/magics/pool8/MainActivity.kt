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
import androidx.lifecycle.lifecycleScope
import com.magics.pool8.ui.theme.Pool8Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Force portrait orientation programmatically (covers tablets)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        
        enableEdgeToEdge()

        // Initialize Monetization & Ads Manager
        val monetizationManager = MonetizationManager(applicationContext)

        // Initialize the Magics Physics Engine
        val engine = GameEngine()

        // Interstitial Ads loop (triggers every 5 minutes if not premium)
        lifecycleScope.launch {
            while (true) {
                delay(300000L) // 5 minutes
                if (!monetizationManager.isPremium) {
                    // Wait for physics simulation to settle so we don't disrupt active gameplay shots
                    while (engine.isSimulationRunning) {
                        delay(1000L)
                    }
                    monetizationManager.showInterstitialAd(this@MainActivity)
                }
            }
        }

        setContent {
            Pool8Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showSplash by remember { mutableStateOf(true) }
                    if (showSplash) {
                        PoolSplashScreen(onTimeout = { 
                            showSplash = false 
                            // Show App Open Ad when the splash screen ends (giving it 2 seconds to load)
                            monetizationManager.showAppOpenAd(this@MainActivity)
                        })
                    } else {
                        PoolGameScreen(
                            engine = engine,
                            monetizationManager = monetizationManager
                        )
                    }
                }
            }
        }
    }
}