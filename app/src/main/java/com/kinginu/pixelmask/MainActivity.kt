package com.kinginu.pixelmask

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kinginu.pixelmask.Constants.FIELD_DOWNLOAD_URL
import com.kinginu.pixelmask.Constants.FIELD_LATEST_VERSION_CODE
import com.kinginu.pixelmask.Constants.LATEST_RELEASE_URL
import com.kinginu.pixelmask.Constants.UPDATE_INFO_URL
import com.kinginu.pixelmask.ui.screens.HomeScreen
import com.kinginu.pixelmask.ui.screens.SettingScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class MainActivity : ComponentActivity() {

    private val packageInfo: PackageInfo?
        get() = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
        } catch (_: Exception) { null }

    private val appVersion: String
        get() = packageInfo?.versionName ?: "Unknown"

    private val appVersionCode: Long
        get() = packageInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode
            else @Suppress("DEPRECATION") it.versionCode.toLong()
        } ?: -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val darkTheme = isSystemInDarkTheme()
            val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            val colorScheme = when {
                dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
                dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
                darkTheme -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppStructure(appVersion, appVersionCode)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainAppStructure(appVersion: String, appVersionCode: Long) {
        val navController = rememberNavController()
        val items = listOf(Screen.Home, Screen.Settings)
        val coroutineScope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

        var updateUrl by remember { mutableStateOf<String?>(null) }

        fun checkUpdate() {
            coroutineScope.launch {
                updateUrl = withContext(Dispatchers.IO) { checkUpdateAvailable(appVersionCode) }
            }
        }

        LaunchedEffect(Unit) { checkUpdate() }

        val rebootMessage = stringResource(R.string.please_force_stop_google_photos)
        fun notifySettingChanged() {
            coroutineScope.launch { snackbarHostState.showSnackbar(rebootMessage) }
        }

        val context = LocalContext.current
        // painterResource(R.mipmap.ic_launcher) crashes with "Only VectorDrawables and
        // rasterized asset types are supported" because the launcher icon is an
        // <adaptive-icon> XML which Compose's resource loader doesn't accept. Go through
        // PackageManager so Android's regular icon-rendering path produces a Bitmap we
        // can hand to Image — works for adaptive, themed, and legacy icons alike.
        val launcherIcon = remember(context) {
            context.packageManager.getApplicationIcon(context.packageName)
                .toBitmap()
                .asImageBitmap()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        // Putting the icon in the title slot (instead of navigationIcon)
                        // bypasses M3's fixed inset between navigationIcon and title, so
                        // the gap below is exactly Spacer's width.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                bitmap = launcherIcon,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(stringResource(screen.labelRes)) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        updateUrl = updateUrl,
                        appVersion = appVersion,
                        onOpenLink = { url -> openWebLink(url) },
                        onCheckForUpdate = { checkUpdate() }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingScreen(onSettingChanged = { notifySettingChanged() })
                }
            }
        }
    }

    private fun checkUpdateAvailable(currentVersionCode: Long): String? = try {
        val jsonString = URL(UPDATE_INFO_URL).readText()
        if (jsonString.isNotBlank()) {
            val json = JSONObject(jsonString)
            val remoteVersion = json.getInt(FIELD_LATEST_VERSION_CODE)
            if (currentVersionCode < remoteVersion) {
                // Workflow already wrote the exact APK URL into download_url; if it's
                // missing for any reason, fall back to /releases/latest so the user still
                // ends up somewhere useful.
                json.optString(FIELD_DOWNLOAD_URL).takeIf { it.isNotBlank() }
                    ?: LATEST_RELEASE_URL
            } else null
        } else null
    } catch (e: Exception) {
        android.util.Log.w("PixelMask", "update check failed", e)
        null
    }

    private fun openWebLink(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) })
    }

    sealed class Screen(val route: String, val labelRes: Int, val icon: ImageVector) {
        object Home : Screen("home", R.string.home, Icons.Default.Home)
        object Settings : Screen("settings", R.string.settings, Icons.Default.Settings)
    }
}
