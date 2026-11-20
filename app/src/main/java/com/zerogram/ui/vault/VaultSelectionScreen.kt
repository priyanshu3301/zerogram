package com.zerogram.feature.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import com.zerogram.core.ui.components.TelegramChannelAvatar
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.rememberLazyListState
import com.zerogram.core.ui.scroll.isScrollingUp
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
    val currentVaultChatId by viewModel.currentVaultChatId.collectAsState()
    val listState = rememberLazyListState()
    val isScrollingUp = listState.isScrollingUp()
    val fabScale by animateFloatAsState(
        targetValue = if (isScrollingUp) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.6f, 
            stiffness = Spring.StiffnessMedium
        )
    )
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
                    val screenTitle = if (currentVaultChatId != null) "Switch Vault" else "Select Vault"
                    Text(
                        screenTitle,
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
                modifier = Modifier
                    .scale(fabScale)
                    .alpha(fabScale.coerceIn(0f, 1f)),
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
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            items(state.channels, key = { it.chatId }) { channel ->
                                val isCurrentVault = channel.chatId == currentVaultChatId
                                ChannelItem(
                                    channel = channel,
                                    isCurrentVault = isCurrentVault,
                                    onClick = { viewModel.selectChannel(channel) }
                                )
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
fun ChannelItem(
    channel: TelegramChannel,
    isCurrentVault: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceColor),
        border = if (isCurrentVault) BorderStroke(1.5.dp, PrimaryBlue.copy(alpha = 0.7f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Telegram-style channel profile picture or gradient initials avatar
            TelegramChannelAvatar(
                title = channel.title,
                chatId = channel.chatId,
                photoPath = channel.photoPath,
                size = 50.dp
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.title,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                if (isCurrentVault) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(PrimaryBlue, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Current Vault",
                            color = PrimaryBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    val subtitle = if (channel.memberCount > 1) {
                        "${channel.memberCount} subscribers"
                    } else {
                        "Private Channel"
                    }
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (isCurrentVault) {
                // Telegram-style checkmark badge indicating active/current vault
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(PrimaryBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Current Vault",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
