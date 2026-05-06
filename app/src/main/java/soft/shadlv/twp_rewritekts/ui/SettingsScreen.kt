package soft.shadlv.twp_rewritekts.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import soft.shadlv.twp_rewritekts.domain.ProxyViewModel
import sv.lib.squircleshape.SquircleShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyScreen(viewModel: ProxyViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProxyScreenContent(
        state = state,
        onIntent = { viewModel.onIntent(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyScreenContent(
    state: ProxyViewModel.ProxyUiState,
    onIntent: (ProxyViewModel.ProxyIntent) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(),
        topBar = { CenterAlignedTopAppBar(title = { Text("TG Proxy Control Settings") }) },
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
                    GlassSaveButton(
                        onClick = onIntent,
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ProxyInputFields(state, onValueChange = onIntent)
            }
        }
    }
}

@Composable
fun ProxyInputFields(
    state: ProxyViewModel.ProxyUiState,
    onValueChange: (ProxyViewModel.ProxyIntent) -> Unit
) {
    Card(elevation = CardDefaults.cardElevation(4.dp), modifier = Modifier.clip(
            SquircleShape(
                radius = 100f,
                smoothing = 50
            )
            )) {
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
fun GlassSaveButton(
    modifier: Modifier = Modifier,
    onClick: (ProxyViewModel.ProxyIntent) -> Unit,
) {

    val targetBackgroundColor = Color.White.copy(alpha = 0.08f)
    val backgroundColor by animateColorAsState(targetBackgroundColor, label = "color")

    val targetBorderAlpha =  0.5f
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
            .clickableDebounced { onClick(ProxyViewModel.ProxyIntent.SaveConfig) }
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
            Text(
                text = "Сохранить настройки",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProxyScreenPreview() {
    val fakeState = ProxyViewModel.ProxyUiState(
        host = "127.0.0.1",
        port = 8080,
        dcip = "1.1.1.1",
        secret = "random_secret_string"
    )

    MaterialTheme {
        ProxyScreenContent(
            state = fakeState,
            onIntent = {}
        )
    }
}

