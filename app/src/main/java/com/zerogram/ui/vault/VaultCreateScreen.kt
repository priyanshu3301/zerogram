package com.zerogram.feature.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
fun VaultCreateScreen(
    generatedKeyBase64: String?,
    onVaultCreated: (String) -> Unit,
    onContinue: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: VaultSelectionViewModel = hiltViewModel()
) {
    var vaultName by remember { mutableStateOf("My Vault") }
    val actionState by viewModel.actionState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(actionState) {
        if (actionState is VaultActionState.VaultCreated) {
            onVaultCreated((actionState as VaultActionState.VaultCreated).newKeyBase64)
            viewModel.resetActionState()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().background(BackgroundColor),
        containerColor = BackgroundColor,
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (generatedKeyBase64 == null) "Create New Vault" else "Save Vault Key",
                        color = TextPrimary,
                        fontSize = 20.sp
                    ) 
                },
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
            if (generatedKeyBase64 == null) {
                // Step 1: Name input and Create Button
                OutlinedTextField(
                    value = vaultName,
                    onValueChange = { vaultName = it },
                    label = { Text("Vault Name", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = actionState is VaultActionState.Error,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = TextSecondary,
                        cursorColor = PrimaryBlue
                    ),
                    singleLine = true
                )

                if (actionState is VaultActionState.Error) {
                    Text(
                        text = (actionState as VaultActionState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { viewModel.createNewVault(vaultName.ifBlank { "My Vault" }) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = actionState !is VaultActionState.Creating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = TextPrimary
                    )
                ) {
                    if (actionState is VaultActionState.Creating) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = TextPrimary)
                    } else {
                        Text("Create Vault", fontSize = 16.sp)
                    }
                }
            } else {
                // Step 2: Show generated key
                Text(
                    text = "Please copy and securely store your vault key.\nYou will need this key to unlock your vault in the future. It cannot be recovered if lost.",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.error
                )
                
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = generatedKeyBase64,
                    onValueChange = { },
                    readOnly = true,
                    label = { Text("Your Vault Key", color = TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = TextSecondary,
                        unfocusedBorderColor = TextSecondary
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Vault Key", generatedKeyBase64)
                        clipboard.setPrimaryClip(clip)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SurfaceColor,
                        contentColor = TextPrimary
                    )
                ) {
                    Text("Copy Key", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = TextPrimary
                    )
                ) {
                    Text("I have saved my key. Continue", fontSize = 16.sp)
                }
            }
        }
    }
}
