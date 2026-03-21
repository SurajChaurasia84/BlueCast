package com.bluecast.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bluecast.app.ui.theme.AquaGlow
import com.bluecast.app.ui.theme.DeepBlue
import com.bluecast.app.ui.theme.ElectricBlue
import com.bluecast.app.ui.theme.Midnight
import com.bluecast.app.ui.theme.SuccessGreen
import com.bluecast.app.ui.theme.VividPurple
import com.bluecast.app.ui.theme.WarningRed

@Composable
fun BlueCastApp(
    state: BlueCastUiState,
    onRefreshDevices: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onConnectDevice: () -> Unit,
    onDisconnectDevice: () -> Unit,
    onToggleStreaming: () -> Unit,
    onPushToTalkChange: (Boolean) -> Unit,
    onTalkPressedChange: (Boolean) -> Unit,
    onVolumeChanged: (Float) -> Unit,
    onDismissMessage: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            onDismissMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Midnight
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Midnight, DeepBlue, Color(0xFF12142A))
                    )
                )
                .padding(padding)
        ) {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "BlueCast",
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White
                    )
                    Text(
                        text = "Where Voice Meets Technology",
                        style = MaterialTheme.typography.bodyLarge,
                        color = AquaGlow
                    )
                }

                if (state.missingPermissions) {
                    item {
                        ActionCard(
                            title = "Permissions required",
                            subtitle = "BlueCast needs microphone and Bluetooth access before it can scan, connect, and stream.",
                            actionText = "Grant permissions",
                            onAction = onRequestPermissions
                        )
                    }
                }

                item {
                    StatusCard(state = state, onDisconnectDevice = onDisconnectDevice)
                }

                item {
                    DeviceListCard(
                        devices = state.devices,
                        selectedAddress = state.selectedDeviceAddress,
                        isScanning = state.isScanning,
                        onRefreshDevices = onRefreshDevices,
                        onSelectDevice = onSelectDevice
                    )
                }

                item {
                    StreamCard(
                        state = state,
                        onToggleStreaming = onToggleStreaming,
                        onPushToTalkChange = onPushToTalkChange,
                        onTalkPressedChange = onTalkPressedChange,
                        onVolumeChanged = onVolumeChanged
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    actionText: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x221E88E5)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = Color.White.copy(alpha = 0.8f))
            Button(onClick = onAction, shape = RoundedCornerShape(18.dp)) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun StatusCard(
    state: BlueCastUiState,
    onDisconnectDevice: () -> Unit
) {
    val isPositiveStatus = state.connectionStatus.startsWith("Connected to") ||
        state.connectionStatus.startsWith("Streaming to")
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x1AF5F7FF))
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(ElectricBlue, VividPurple))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.BluetoothAudio, contentDescription = null, tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "Bluetooth audio route", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = state.connectionStatus,
                        color = if (isPositiveStatus) SuccessGreen else WarningRed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Disconnect") },
                            onClick = {
                                menuExpanded = false
                                onDisconnectDevice()
                            },
                            enabled = state.isDeviceConnected
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceListCard(
    devices: List<BluetoothDeviceUi>,
    selectedAddress: String?,
    isScanning: Boolean,
    onRefreshDevices: () -> Unit,
    onSelectDevice: (String) -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refreshRotation"
    )

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x14182552))
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Available Devices", color = Color.White, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onRefreshDevices) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "Scan bluetooth devices",
                        tint = Color.White,
                        modifier = Modifier.graphicsLayer {
                            rotationZ = if (isScanning) rotation else 0f
                        }
                    )
                }
            }
            if (devices.isEmpty()) {
                Text(
                    text = if (isScanning) {
                        "Scanning for Bluetooth devices..."
                    } else {
                        "No available Bluetooth devices found. Make sure your speaker or headphones are powered on and tap Refresh."
                    },
                    color = Color.White.copy(alpha = 0.75f)
                )
            } else {
                devices.forEach { device ->
                    FilterChip(
                        selected = device.address == selectedAddress,
                        onClick = { onSelectDevice(device.address) },
                        label = {
                            Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                                Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = if (device.isAudioCapable) {
                                        if (device.isConnected) "Audio capable • Connected" else "Audio capable"
                                    } else {
                                        "Not supported"
                                    },
                                    color = Color.White.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.BluetoothAudio, contentDescription = null) },
                        shape = RoundedCornerShape(28.dp),
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color(0x1AFFFFFF),
                            selectedContainerColor = Color(0x334C7DFF),
                            labelColor = Color.White,
                            selectedLabelColor = Color.White,
                            iconColor = Color.White,
                            selectedLeadingIconColor = Color.White
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamCard(
    state: BlueCastUiState,
    onToggleStreaming: () -> Unit,
    onPushToTalkChange: (Boolean) -> Unit,
    onTalkPressedChange: (Boolean) -> Unit,
    onVolumeChanged: (Float) -> Unit
) {
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x16212967))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (state.isStreaming) listOf(VividPurple, ElectricBlue, DeepBlue) else listOf(DeepBlue, Midnight),
                            radius = 280f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onToggleStreaming,
                    modifier = Modifier.size(156.dp),
                    shape = CircleShape
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Mic, contentDescription = null)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(if (state.isStreaming) "Stop Streaming" else "Start Streaming", textAlign = TextAlign.Center)
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Mic level", color = Color.White)
                    Text(text = "${(state.micLevel * 100).toInt()}%", color = AquaGlow)
                }
                LinearProgressIndicator(progress = { state.micLevel }, modifier = Modifier.fillMaxWidth())
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Push-to-talk", color = Color.White)
                    Text(text = "Mute until you press and hold Talk", color = Color.White.copy(alpha = 0.7f))
                }
                Switch(checked = state.pushToTalkEnabled, onCheckedChange = onPushToTalkChange)
            }

            AnimatedVisibility(visible = state.pushToTalkEnabled && state.isStreaming) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (state.isTalkPressed) Color(0x3348FFB1) else Color(0x22FFFFFF))
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                onTalkPressedChange(true)
                                tryAwaitRelease()
                                onTalkPressedChange(false)
                            })
                        }
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (state.isTalkPressed) "Talking..." else "Hold to Talk", color = Color.White)
                }
            }

            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Output gain", color = Color.White)
                    Text(text = "${String.format("%.1f", state.outputGain)}x", color = AquaGlow)
                }
                Slider(value = state.outputGain, onValueChange = onVolumeChanged, valueRange = 0.5f..2f)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(onClick = {}, label = { Text(if (state.isStreaming) "Screen kept awake" else "Ready") }, leadingIcon = { Icon(Icons.Rounded.GraphicEq, null) })
            }
        }
    }
}

