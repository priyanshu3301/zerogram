import os
import re

path = r'd:\zerogram 3.0\feature-category\src\main\java\com\zerogram\feature\category\CategoryViewModel.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

replacement = '''val itemDateText = if (item is AppListItem.File) item.dateText else (item as AppListItem.Folder).dateText
            val itemSizeText = if (item is AppListItem.File) item.sizeText else ""
            val formattedDate = fileEntity?.let { dateFormat.format(java.util.Date(it.createdAt)).lowercase(java.util.Locale.getDefault()) } ?: itemDateText
            val formattedSize = fileEntity?.sizeBytes?.let { com.zerogram.core.utils.FormatUtils.formatSize(it) } ?: itemSizeText'''

content = re.sub(
    r'val formattedDate = fileEntity\?\.let \{ dateFormat\.format\(Date\(it\.createdAt\)\)\.lowercase\(Locale\.getDefault\(\)\) \} \?\: item\.dateText\s*val formattedSize = fileEntity\?\.sizeBytes\?\.let \{ FormatUtils\.formatSize\(it\) \} \?\: item\.sizeText',
    replacement,
    content
)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
