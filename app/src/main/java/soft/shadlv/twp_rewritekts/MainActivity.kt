package soft.shadlv.twp_rewritekts

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import soft.shadlv.twp_rewritekts.domain.ProxyControlViewModel
import soft.shadlv.twp_rewritekts.domain.ProxyControlViewModelFactory
import soft.shadlv.twp_rewritekts.domain.ProxyViewModel
import soft.shadlv.twp_rewritekts.domain.ProxyViewModelFactory
import soft.shadlv.twp_rewritekts.ui.HomeScreen
import soft.shadlv.twp_rewritekts.ui.ProxyScreen
import soft.shadlv.twp_rewritekts.ui.theme.TGProxyTheme
import sv.lib.squircleshape.SquircleShape

class MainActivity : ComponentActivity() {

    private val proxyViewModel: ProxyViewModel by viewModels {
        ProxyViewModelFactory(application)
    }

    private val proxyControlViewModel: ProxyControlViewModel by viewModels {
        ProxyControlViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(
            ".MainActivity",
            "Proxy starting: Proxy Process PID: ${android.os.Process.myPid()}"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        checkExit()

        enableEdgeToEdge()
        setContent {
            TGProxyTheme {
                AppNavigation(proxyControlViewModel, proxyViewModel)
            }
        }
    }
}

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Settings :
        Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@Composable
fun AppNavigation(proxyControlViewModel: ProxyControlViewModel, proxyViewModel: ProxyViewModel) {
    val navController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Settings)

    Scaffold(
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                NavigationBar(
                    modifier = Modifier.clip(
                        SquircleShape(
                            radius = 100f,
                            smoothing = 50
                        )
                    ),
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                    tonalElevation = 8.dp
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    items.forEach { screen ->
                        val isSelected =
                            currentDestination?.hierarchy?.any { it.route == screen.route } == true

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = null,
                            alwaysShowLabel = false,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }

        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            composable(Screen.Home.route) { HomeScreen(proxyControlViewModel) }
            composable(Screen.Settings.route) { ProxyScreen(proxyViewModel) }
        }
    }
}

//@Preview(showBackground = true)
//@Composable
//fun Preview() {
//    TGProxyTheme {
//        AppNavigation()
//    }
//}