package com.bluecast.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bluecast.app.ui.BlueCastApp
import com.bluecast.app.ui.theme.BlueCastTheme
import com.bluecast.app.viewmodel.BlueCastViewModel

class MainActivity : ComponentActivity() {
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        viewModel.handlePermissionResult(granted)
    }

    private val viewModel: BlueCastViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as BlueCastApplication
                BlueCastViewModel(
                    application = app,
                    bluetoothDeviceManager = app.container.bluetoothDeviceManager,
                    streamingController = app.container.streamingController
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { viewModel.uiState.value.showSplash }

        setContent {
            val state = viewModel.uiState.collectAsStateWithLifecycle().value

            if (state.isStreaming) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            BlueCastTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BlueCastApp(
                        state = state,
                        onRefreshDevices = viewModel::refreshDevices,
                        onSelectDevice = viewModel::selectDevice,
                        onConnectDevice = viewModel::connectSelectedDevice,
                        onDisconnectDevice = viewModel::disconnectSelectedDevice,
                        onToggleStreaming = viewModel::toggleStreaming,
                        onPushToTalkChange = viewModel::setPushToTalkEnabled,
                        onTalkPressedChange = viewModel::setTalkPressed,
                        onVolumeChanged = viewModel::setOutputGain,
                        onDismissMessage = viewModel::clearMessage,
                        onRequestPermissions = { requestRuntimePermissions() }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onScreenVisible()
    }

    private fun requestRuntimePermissions() {
        permissionsLauncher.launch(buildPermissions())
    }

    private fun buildPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }
        return permissions.toTypedArray()
    }
}

