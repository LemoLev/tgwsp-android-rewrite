package soft.shadlv.twp_rewritekts.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import soft.shadlv.twp_rewritekts.R
import soft.shadlv.twp_rewritekts.domain.ProxyControlViewModel
import sv.lib.squircleshape.SquircleShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: ProxyControlViewModel) {
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()

    HomeScreenContent(
        isRun = isRunning,
        onToggleClick = {
            viewModel.onIntent(ProxyControlViewModel.ProxyControlIntent.ToggleProxy)
        },
        onOpenTelegram = {
            viewModel.onIntent(ProxyControlViewModel.ProxyControlIntent.OpenTelegram)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    isRun: Boolean,
    onToggleClick: () -> Unit,
    onOpenTelegram: () -> Unit
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        topBar = { CenterAlignedTopAppBar(title = { Text("TG Proxy Control") }) },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars),
                color = Color.Transparent
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    GlassToggleButton(
                        isProxyActive = isRun,
                        onClick = onToggleClick,
                        activeIcon = Icons.Rounded.Done,
                        inactiveIcon = Icons.Rounded.PlayArrow
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onOpenTelegram) {
                Icon(
                    painter = painterResource(id = R.mipmap.telegram),
                    contentDescription = "Open Telegram",
                    modifier = Modifier.size(100.dp),
                    tint = Color.Unspecified
                )
            }
        }
    }
}

@Composable
fun GlassToggleButton(
    isProxyActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    activeText: String = "Stop Proxy",
    inactiveText: String = "Start Proxy",
    activeIcon: ImageVector,
    inactiveIcon: ImageVector
) {

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
            .clip(
                SquircleShape(
                    radius = 100f,
                    smoothing = 50
                )
            )
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
                shape = SquircleShape(
                    radius = 100f,
                    smoothing = 50
                )
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

@Preview(
    showBackground = true,
    backgroundColor = 0xFF000000
) // Тёмный фон для "стеклянного" эффекта
@Composable
fun HomeScreenPreview() {
    MaterialTheme {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // Состояние: Остановлено
            HomeScreenContent(
                isRun = false,
                onToggleClick = {},
                onOpenTelegram = {}
            )

            // Состояние: Запущено
            HomeScreenContent(
                isRun = true,
                onToggleClick = {},
                onOpenTelegram = {}
            )
        }
    }
}