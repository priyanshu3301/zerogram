package com.zerogram.core.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
sealed interface AppListItem {
    val id: String
    val name: String
    val iconRes: Int
    val timestamp: Long
    val sizeBytes: Long

    @Immutable
    data class File(
        override val id: String,
        override val name: String,
        override val iconRes: Int,
        val sizeText: String,
        val dateText: String,
        val extraInfo: String? = null,
        override val timestamp: Long = 0L,
        override val sizeBytes: Long = 0L
    ) : AppListItem
    
    @Immutable
    data class Folder(
        override val id: String,
        override val name: String,
        override val iconRes: Int,
        val dateText: String,
        val extraInfo: String? = null,
        override val timestamp: Long = 0L,
        override val sizeBytes: Long = 0L
    ) : AppListItem
}

/**
 * A stable wrapper for lists to ensure Compose treats them as immutable.
 */
@Immutable
data class ImmutableListWrapper<T>(val items: List<T>)

@Composable
fun AppList(
    items: ImmutableListWrapper<AppListItem>,
    selectedItems: Set<String>,
    isSelectionMode: Boolean,
    onItemClick: (AppListItem) -> Unit,
    onItemLongClick: (AppListItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) {
        items(
            items = items.items,
            key = { it.id },
            contentType = { it::class.simpleName }
        ) { item ->
            val isSelected = selectedItems.contains(item.id)
            AppListItemRow(
                item = item,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                onClick = { onItemClick(item) },
                onLongClick = { onItemLongClick(item) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppListItemRow(
    item: AppListItem,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    val backgroundColor = if (isSelected) Color(0xFF1A1A1A) else Color.Transparent
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                indication = ripple(color = Color(0xFFA0A0A0)),
                interactionSource = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Icon(
                painter = painterResource(id = item.iconRes),
                contentDescription = item.name,
                tint = Color.Unspecified,
                modifier = Modifier.size(44.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = item.name, 
                    color = Color.White, 
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(2.dp))
                
                val subtitleText = when (item) {
                    is AppListItem.File -> {
                        if (item.extraInfo != null) "${item.extraInfo} | ${item.sizeText} | ${item.dateText}"
                        else "${item.sizeText}  •  ${item.dateText}"
                    }
                    is AppListItem.Folder -> {
                        if (item.extraInfo != null) "${item.extraInfo} | ${item.dateText}"
                        else item.dateText
                    }
                }
                
                Text(
                    text = subtitleText, 
                    color = Color(0xFFA0A0A0), 
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Trailing action / checkbox
            Box(
                modifier = Modifier.width(32.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF1E88E5),
                            uncheckedColor = Color(0xFFA0A0A0),
                            checkmarkColor = Color.White
                        ),
                        modifier = Modifier.size(24.dp)
                    )
                } else if (item is AppListItem.Folder) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFFA0A0A0),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Divider
        HorizontalDivider(
            modifier = Modifier.padding(start = 80.dp),
            thickness = 1.dp,
            color = Color(0xFF333333)
        )
    }
}
