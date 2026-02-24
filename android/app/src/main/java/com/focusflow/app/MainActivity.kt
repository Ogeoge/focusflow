package com.focusflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusflow.app.data.prefs.SettingsDataStore
import com.focusflow.app.ui.navigation.AppNav
import com.focusflow.app.ui.theme.FocusFlowTheme
import com.focusflow.app.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val settings = AppServices.settings
            MainRoot(settings = settings)
        }
    }
}

@Composable
private fun MainRoot(settings: SettingsDataStore) {
    val themeMode by settings.themeModeFlow.collectAsState(initial = null)

    FocusFlowTheme(themeMode = themeMode ?: ThemeMode.SYSTEM) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (themeMode == null) {
                LoadingScreen()
            } else {
                AppNav()
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
