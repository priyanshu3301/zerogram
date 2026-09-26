package com.zerogram.feature.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.hilt.navigation.compose.hiltViewModel
import com.zerogram.domain.repository.TelegramChannel
import androidx.compose.material.icons.automirrored.filled.ArrowBack

private val BackgroundColor = Color(0xFF000000)
private val SurfaceColor = Color(0xFF1E1E1E)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFA0A0A0)
private val PrimaryBlue = Color(0xFF2B65F6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSelectionScreen(
    onNavigateToUnlock: (Long) -> Unit,
    onNavigateToCreate: () -> Unit,
    onVaultCreated: (String) -> Unit,
    onUnlockSuccess: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: VaultSelectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionState by viewModel.actionState.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(actionState) {
        when (actionState) {
            is VaultActionState.NavigateToUnlock -> {
                onNavigateToUnlock((actionState as VaultActionState.NavigateToUnlock).chatId)
                viewModel.resetActionState()
            }
            is VaultActionState.VaultCreated -> {
                onVaultCreated((actionState as VaultActionState.VaultCreated).newKeyBase64)
                viewModel.resetActionState()
            }
            is VaultActionState.VaultUnlocked -> {
                onUnlockSuccess()
                viewModel.resetActionState()
            }
            is VaultActionState.Error -> {
                android.widget.Toast.makeText(context, (actionState as VaultActionState.Error).message, android.widget.Toast.LENGTH_LONG).show()
                viewModel.resetActionState()
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(BackgroundColor),
        containerColor = BackgroundColor,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Select Vault",
                        fontWeight = FontWeight.Normal, 
                        color = TextPrimary,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadChannels() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundColor,
                    titleContentColor = TextPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToCreate,
                icon = { Icon(Icons.Default.Add, contentDescription = "Create New Vault", tint = TextPrimary) },
                text = { Text("Create New Vault", color = TextPrimary) },
                containerColor = PrimaryBlue,
                contentColor = TextPrimary
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is VaultSelectionState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is VaultSelectionState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadChannels() },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Text("Retry", color = TextPrimary)
                        }
                    }
                }
                is VaultSelectionState.Success -> {
                    if (state.channels.isEmpty()) {
                        Text(
                            text = "No existing vaults found.",
                            modifier = Modifier.align(Alignment.Center),
                            color = TextSecondary,
                            fontSize = 16.sp
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(state.channels) { channel ->
                                ChannelItem(channel = channel, onClick = { viewModel.selectChannel(channel) })
                            }
                        }
                    }
                }
            }

            if (actionState is VaultActionState.Checking || actionState is VaultActionState.Creating) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = false) {}
                ) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
fun ChannelItem(channel: TelegramChannel, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, contentDescription = "Vault", tint = PrimaryBlue, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = channel.title,
                color = TextPrimary,
                fontSize = 18.sp
            )
        }
    }
}
