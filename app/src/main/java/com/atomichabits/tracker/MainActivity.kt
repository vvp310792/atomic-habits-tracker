package com.atomichabits.tracker

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.atomichabits.tracker.ui.navigation.AppNavigation
import com.atomichabits.tracker.ui.theme.AtomicHabitsTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val app = application as HabitTrackerApp

        setContent {
            AtomicHabitsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(app = app)
                }
            }
        }
    }
}
