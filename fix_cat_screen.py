import os
import re

path = r'd:\zerogram 3.0\feature-category\src\main\java\com\zerogram\feature\category\CategoryScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix FileItemData
content = content.replace('FileItemData', 'AppListItem')

# Fix itemToRename smart casts
replacement = '''val renameItem = itemToRename
    if (showRenameDialog && renameItem != null) {
        var newName by remember { mutableStateOf(renameItem.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { if (it.length <= 50) newName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Color(0xFF64B5F6),
                        unfocusedBorderColor = TextSecondary,
                        cursorColor = Color(0xFF64B5F6)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank() && newName != renameItem.name) {
                            viewModel.renameItem(renameItem.id, newName.trim())
                            isSelectionMode = false
                            viewModel.clearSelection()
                        }
                        showRenameDialog = false
                    }
                ) {
                    Text("Rename", color = Color(0xFF64B5F6))
                }
            },
            containerColor = SurfaceColor
        )
    }'''

content = re.sub(r'if \(showRenameDialog && itemToRename != null\) \{.*?containerColor = SurfaceColor\s*\)\s*\}', replacement, content, flags=re.DOTALL)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
