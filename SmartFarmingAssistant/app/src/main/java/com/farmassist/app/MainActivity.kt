package com.farmassist.app

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.farmassist.app.ui.AppViewModel
import com.farmassist.app.ui.components.pressScale
import com.farmassist.app.ui.screens.*
import com.farmassist.app.ui.theme.FarmAssistTheme
import com.farmassist.app.ui.theme.LeafGreen

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Capture : Screen("crop_select", "Scan", Icons.Default.CameraAlt)
    object Sensor : Screen("sensor", "Sensors", Icons.Default.Sensors)
    object History : Screen("history", "History", Icons.Default.History)
    object Profile : Screen("profile", "Profile", Icons.Default.Person)
}

private const val ROUTE_CAMERA = "camera"
private const val ROUTE_CONFIRM = "confirm"
private const val ROUTE_ACREAGE = "acreage"
private const val ROUTE_RESULT = "result"

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FarmAssistTheme {
                FarmAssistApp(viewModel)
            }
        }
    }
}

@Composable
fun FarmAssistApp(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val bottomTabs = listOf(Screen.Home, Screen.Capture, Screen.Sensor, Screen.History, Screen.Profile)

    // Holds the captured bitmap across the capture -> confirm -> acreage steps.
    var pendingBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // ── Virtual field mode: auto-feed images from the hardware agent ─────
    val virtualModeEnabled by viewModel.virtualFieldModeEnabled.collectAsStateWithLifecycle()
    val virtualBitmap by viewModel.virtualCaptureManager.latestBitmap.collectAsStateWithLifecycle()
    val virtualCropHint by viewModel.virtualCaptureManager.cropHint.collectAsStateWithLifecycle()

    // When a new virtual image arrives and virtual mode is on, feed it into the
    // same inference pipeline — skipping the manual camera step entirely.
    LaunchedEffect(virtualBitmap, virtualModeEnabled) {
        if (virtualModeEnabled && virtualBitmap != null) {
            pendingBitmap = virtualBitmap
            // Auto-set the crop from the camera module's crop_hint if available
            if (!virtualCropHint.isNullOrBlank()) {
                viewModel.selectedCrop = virtualCropHint
            }
            // Navigate to confirm screen so the user can see & approve the image
            // before inference runs (same UX as manual capture, just auto-sourced)
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            if (currentRoute != ROUTE_CONFIRM && currentRoute != ROUTE_RESULT) {
                navController.navigate(ROUTE_CONFIRM) {
                    launchSingleTop = true
                }
            }
        }
    }
    // ─────────────────────────────────────────────────────────────────────

    Scaffold(
        bottomBar = { FloatingPillNavBar(navController, bottomTabs) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) {
                val connectionState by viewModel.bleConnectionState.collectAsStateWithLifecycle()
                val sensorData by viewModel.sensorData.collectAsStateWithLifecycle()
                HomeScreen(
                    sensorData = sensorData,
                    connectionState = connectionState,
                    onScanCrop = { navController.navigate(Screen.Capture.route) },
                    onOpenSensors = { navController.navigate(Screen.Sensor.route) },
                    onOpenHistory = { navController.navigate(Screen.History.route) },
                    onOpenProfile = { navController.navigate(Screen.Profile.route) }
                )
            }

            // Step 1: pick the crop
            composable(Screen.Capture.route) {
                CropSelectionScreen(
                    cropNames = viewModel.cropMapping.cropNames(),
                    displayName = { viewModel.cropMapping.displayName(it) },
                    onBack = { navController.popBackStack() }
                ) { crop ->
                    viewModel.selectedCrop = crop
                    navController.navigate(ROUTE_CAMERA)
                }
            }

            // Step 2: capture the photo
            composable(ROUTE_CAMERA) {
                CaptureScreen(onBack = { navController.popBackStack() }) { bitmap ->
                    pendingBitmap = bitmap
                    navController.navigate(ROUTE_CONFIRM)
                }
            }

            // Step 3: confirm the photo before it's sent to the model
            composable(ROUTE_CONFIRM) {
                val bitmap = pendingBitmap
                if (bitmap == null) {
                    navController.popBackStack(Screen.Capture.route, inclusive = false)
                } else {
                    ConfirmPhotoScreen(
                        bitmap = bitmap,
                        onRetake = { pendingBitmap = null; navController.popBackStack() },
                        onConfirm = { navController.navigate(ROUTE_ACREAGE) }
                    )
                }
            }

            // Step 4: how many acres — used to size the treatment dose (optional)
            composable(ROUTE_ACREAGE) {
                AcreageInputScreen(
                    onBack = { navController.popBackStack() },
                    onSubmit = { acres ->
                        val bitmap = pendingBitmap
                        if (bitmap != null) {
                            viewModel.runInference(bitmap, viewModel.selectedCrop, acres)
                            navController.navigate(ROUTE_RESULT)
                        }
                    },
                    onSkip = {
                        val bitmap = pendingBitmap
                        if (bitmap != null) {
                            viewModel.runInference(bitmap, viewModel.selectedCrop, null)
                            navController.navigate(ROUTE_RESULT)
                        }
                    }
                )
            }

            // Step 5: result (shows a processing state while isRunning)
            composable(ROUTE_RESULT) {
                val state by viewModel.predictionState.collectAsStateWithLifecycle()
                if (state.isRunning) {
                    ProcessingScreen()
                } else {
                    ResultScreen(state, viewModel.currentAcres, state.diseaseInfo) {
                        pendingBitmap = null
                        navController.popBackStack(Screen.Capture.route, inclusive = false)
                    }
                }
            }

            composable(Screen.Sensor.route) {
                val connectionState by viewModel.bleConnectionState.collectAsStateWithLifecycle()
                val sensorData by viewModel.sensorData.collectAsStateWithLifecycle()

                LaunchedEffect(sensorData) {
                    if (sensorData.connected) viewModel.recordSensorReading(sensorData)
                }

                SensorScreen(
                    connectionState = connectionState,
                    sensorData = sensorData,
                    onStartScan = { viewModel.bleManager.startScan() },
                    onStopScan = { viewModel.bleManager.stopScan() }
                )
            }

            composable(Screen.History.route) {
                val predictions by viewModel.allPredictions.collectAsStateWithLifecycle(initialValue = emptyList())
                val readings by viewModel.recentSensorReadings.collectAsStateWithLifecycle(initialValue = emptyList())
                HistoryScreen(predictions, readings) { viewModel.clearAllHistory() }
            }

            composable(Screen.Profile.route) {
                val vfmEnabled by viewModel.virtualFieldModeEnabled.collectAsStateWithLifecycle()
                val nodeIp by viewModel.fieldNodeIpAddress.collectAsStateWithLifecycle()

                ProfileScreen(
                    currentNumber = viewModel.farmerPhoneNumber,
                    onNumberSaved = { number ->
                        viewModel.farmerPhoneNumber = number
                        if (viewModel.smsManager.hasSmsPermission()) {
                            viewModel.smsManager.sendAlert(
                                number,
                                "FarmAssist: Your number has been registered for crop alerts."
                            )
                        }
                    },
                    virtualFieldModeEnabled = vfmEnabled,
                    fieldNodeIp = nodeIp,
                    onVirtualFieldModeToggled = { viewModel.setVirtualFieldMode(it) },
                    onFieldNodeIpSaved = { viewModel.setFieldNodeIp(it) }
                )
            }
        }
    }
}

/** Floating rounded-pill bottom nav bar — dark background, icon-only tabs,
 *  selected tab gets a filled circular highlight. Matches the reference style. */
@Composable
private fun FloatingPillNavBar(navController: NavHostController, tabs: List<Screen>) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF14231A))
                .padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { screen ->
                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                Box(
                    Modifier
                        .size(if (selected) 46.dp else 40.dp)
                        .clip(CircleShape)
                        .background(if (selected) LeafGreen else Color.Transparent)
                        .pressScale {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        screen.icon,
                        contentDescription = screen.label,
                        tint = if (selected) Color.White else Color.White.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}