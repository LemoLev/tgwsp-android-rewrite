package soft.shadlv.twp_rewritekts

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import soft.shadlv.twp_rewritekts.ui.theme.TGProxyTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ProxyViewModel by viewModels {
        ProxyViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(
            "TGProxyService.MainActivity",
            "Proxy starting: Proxy Process PID: ${android.os.Process.myPid()}"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        enableEdgeToEdge()
        setContent {
            TGProxyTheme {
                ProxyScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyScreen(viewModel: ProxyViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = { CenterAlignedTopAppBar(title = { Text("TG Proxy Control") }) },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars), // Учитываем системную полоску навигации
                color = Color.Transparent, // Прозрачный фон для бара
                tonalElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    GlassToggleButton(
                        viewModel,
                        onClick = { viewModel.onIntent(ProxyViewModel.ProxyIntent.ToggleProxy) },
                        activeIcon = Icons.Rounded.Done,
                        inactiveIcon = Icons.Rounded.PlayArrow
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ProxyInputFields(state, onValueChange = { viewModel.onIntent(it) })
            }
        }
    }
}

@Composable
fun ProxyInputFields(
    state: ProxyViewModel.ProxyUiState,
    onValueChange: (ProxyViewModel.ProxyIntent) -> Unit
) {
    Card(elevation = CardDefaults.cardElevation(4.dp)) {
        Column(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            OutlinedTextField(
                value = state.host,
                onValueChange = { onValueChange(ProxyViewModel.ProxyIntent.UpdateHost(it)) },
                label = { Text("Server Host") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.port.toString(),
                onValueChange = { onValueChange(ProxyViewModel.ProxyIntent.UpdatePort(it)) },
                label = { Text("Server Port") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.dcip,
                onValueChange = { onValueChange(ProxyViewModel.ProxyIntent.UpdateDcip(it)) },
                label = { Text("DCIP") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.secret,
                onValueChange = { },
                label = { Text("Secret") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        onValueChange(ProxyViewModel.ProxyIntent.RegenerateSecret)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate secret"
                        )
                    }
                }
            )
        }
    }
}

@Composable
fun GlassToggleButton(
    viewModel: ProxyViewModel,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    activeText: String = "Stop Proxy",
    inactiveText: String = "Start Proxy",
    activeIcon: ImageVector,
    inactiveIcon: ImageVector
) {
    val isProxyActive by viewModel.isRunning.collectAsStateWithLifecycle()

    val targetBackgroundColor = if (isProxyActive) {
        Color.Red.copy(alpha = 0.15f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }

    val backgroundColor by animateColorAsState(targetBackgroundColor, label = "color")

    val targetBorderAlpha = if (isProxyActive) 0.5f else 0.2f
    val borderAlpha by animateFloatAsState(targetBorderAlpha, label = "alpha")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickableDebounced { onClick() }
            .background(backgroundColor)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = borderAlpha + 0.1f),
                        Color.White.copy(alpha = borderAlpha)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = if (isProxyActive) activeIcon else inactiveIcon,
                contentDescription = null,
                tint = if (isProxyActive) Color.Red.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isProxyActive) activeText else inactiveText,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

fun Modifier.clickableDebounced(
    enabled: Boolean = true,
    delayMillis: Long = 1000L,
    onClick: () -> Unit
): Modifier = composed {
    var lastClickTime by remember { mutableLongStateOf(0L) }

    clickable(enabled = enabled) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > delayMillis) {
            lastClickTime = currentTime
            onClick()
        }
    }
}

//@Preview(showBackground = true)
//@Composable
//fun GreetingPreview() {
//    TGProxyTheme {
//        ProxyScreen(viewModel = ProxyViewModel)
//    }
//}