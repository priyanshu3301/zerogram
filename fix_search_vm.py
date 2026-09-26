import os
import re

path = r'd:\zerogram 3.0\feature-search\src\main\java\com\zerogram\feature\search\SearchViewModel.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

replacement = '''val itemDateText = if (item is AppListItem.File) item.dateText else (item as AppListItem.Folder).dateText
            val itemSizeText = if (item is AppListItem.File) item.sizeText else ""
            val formattedDate = fileEntity?.let { FormatUtils.formatDate(it.createdAt) } ?: itemDateText
            val formattedSize = fileEntity?.sizeBytes?.let { FormatUtils.formatSize(it) } ?: itemSizeText'''

content = re.sub(
    r'val formattedDate = fileEntity\?\.let \{ FormatUtils\.formatDate\(it\.createdAt\) \} \?\: item\.dateText\s*val formattedSize = fileEntity\?\.sizeBytes\?\.let \{ FormatUtils\.formatSize\(it\) \} \?\: item\.sizeText',
    replacement,
    content
)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
