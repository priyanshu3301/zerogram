package com.zerogram.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.File
import kotlin.math.abs

/**
 * Telegram-style channel avatar component:
 * - If a channel photo exists locally, loads and displays it.
 * - Otherwise (or on load error), falls back to Telegram's 7 signature gradient colors
 *   with deterministic color hashing based on [chatId] and uppercase initials.
 */
@Composable
fun TelegramChannelAvatar(
    title: String,
    chatId: Long,
    photoPath: String?,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp
) {
    val hasValidPhoto = remember(photoPath) {
        !photoPath.isNullOrEmpty() && File(photoPath).exists()
    }

    var isImageError by remember(photoPath) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (hasValidPhoto && !isImageError) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(photoPath!!))
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    isImageError = true
                }
            )
        } else {
            TelegramInitialsAvatar(
                title = title,
                chatId = chatId,
                size = size
            )
        }
    }
}

/**
 * Renders Telegram's signature gradient avatar with channel/chat initials.
 */
@Composable
fun TelegramInitialsAvatar(
    title: String,
    chatId: Long,
    modifier: Modifier = Modifier,
    size: Dp = 50.dp
) {
    val telegramGradients = remember {
        listOf(
            listOf(Color(0xFFFF516A), Color(0xFFFF885E)), // Red
            listOf(Color(0xFFFFA85C), Color(0xFFFFCD6A)), // Orange
            listOf(Color(0xFF665CAC), Color(0xFF8C7BE2)), // Violet
            listOf(Color(0xFF54CB68), Color(0xFFA0DE7E)), // Green
            listOf(Color(0xFF2A9EF5), Color(0xFF53D4FB)), // Cyan
            listOf(Color(0xFF2A7BF5), Color(0xFF5DAEFB)), // Blue
            listOf(Color(0xFFE25383), Color(0xFFF184A9))  // Pink
        )
    }

    val gradientColors = remember(chatId, title) {
        val hash = if (chatId != 0L) abs(chatId) else abs(title.hashCode().toLong())
        val index = (hash % telegramGradients.size).toInt()
        telegramGradients[index]
    }

    val initials = remember(title) {
        extractTelegramInitials(title)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                brush = Brush.linearGradient(
                    colors = gradientColors,
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (initials.isNotEmpty()) {
            Text(
                text = initials,
                color = Color.White,
                fontSize = (size.value * 0.38f).sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        } else {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }
}

/**
 * Extracts 1-2 character initials formatted like Telegram.
 * E.g., "My Vault" -> "MV", "Personal" -> "P".
 */
fun extractTelegramInitials(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return ""
    val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> {
            val first = words[0].firstOrNull()?.uppercase() ?: ""
            val second = words[1].firstOrNull()?.uppercase() ?: ""
            "$first$second"
        }
        words.size == 1 -> {
            words[0].firstOrNull()?.uppercase() ?: ""
        }
        else -> ""
    }
}
