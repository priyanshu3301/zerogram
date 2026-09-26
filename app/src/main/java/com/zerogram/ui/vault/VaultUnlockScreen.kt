package com.zerogram.ui.vault

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.hilt.navigation.compose.hiltViewModel

private val BackgroundColor = Color(0xFF000000)
private val SurfaceColor = Color(0xFF1E1E1E)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFA0A0A0)
private val PrimaryBlue = Color(0xFF2B65F6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultUnlockScreen(
    chatId: Long,
    onUnlockSuccess: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: VaultSelectionViewModel = hiltViewModel()
) {
    var key by remember { mutableStateOf("") }
    val actionState by viewModel.actionState.collectAsState()

    LaunchedEffect(actionState) {
        if (actionState is VaultActionState.VaultUnlocked) {
            onUnlockSuccess()
            viewModel.resetActionState()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(BackgroundColor),
        containerColor = BackgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Unlock Vault", color = TextPrimary, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundColor,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Enter your Vault Key",
                color = TextPrimary,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("Vault Key (Base64)", color = TextSecondary) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                isError = actionState is VaultActionState.UnlockFailed,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = TextSecondary,
                    cursorColor = PrimaryBlue
                ),
                singleLine = true
            )

            if (actionState is VaultActionState.UnlockFailed) {
                Text(
                    text = (actionState as VaultActionState.UnlockFailed).message,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { viewModel.unlockVault(chatId, key) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = key.isNotBlank() && actionState !is VaultActionState.Unlocking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue,
                    contentColor = TextPrimary,
                    disabledContainerColor = SurfaceColor,
                    disabledContentColor = TextSecondary
                )
            ) {
                if (actionState is VaultActionState.Unlocking) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = TextPrimary)
                } else {
                    Text("Unlock Vault", fontSize = 16.sp)
                }
            }
        }
    }
}
