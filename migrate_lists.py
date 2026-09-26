import os
import re

# We will read each file, modify it in memory, and then write it back.

def process_view_model(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Replace import
    content = re.sub(r'import com\.zerogram\.feature\.folder\.FileItemData', 'import com.zerogram.core.ui.components.AppListItem', content)
    content = re.sub(r'import com\.zerogram\.feature\.folder\.DeletedItemData', 'import com.zerogram.core.ui.components.AppListItem', content)

    # Replace StateFlow<List<FileItemData>> -> StateFlow<List<AppListItem>>
    content = content.replace('StateFlow<List<FileItemData>>', 'StateFlow<List<AppListItem>>')
    content = content.replace('StateFlow<List<DeletedItemData>>', 'StateFlow<List<AppListItem>>')
    
    # Replace FileItemData() with AppListItem.File() or Folder()
    # We must be careful here. In FolderViewModel:
    # FileItemData(id=folder.id, name=folder.name, date=..., size=..., iconRes=..., isFolder=true, timestamp=..., sizeBytes=0L)
    # becomes AppListItem.Folder(id=folder.id, name=folder.name, dateText=..., iconRes=..., timestamp=...)
    
    # Let's just do text replacements for the known constructors
    
    # In FolderViewModel and SearchViewModel for Folders:
    content = re.sub(
        r'FileItemData\(\s*id\s*=\s*(.*?),\s*name\s*=\s*(.*?),\s*date\s*=\s*(.*?),\s*size\s*=\s*(.*?),\s*iconRes\s*=\s*(.*?),\s*isFolder\s*=\s*true,\s*timestamp\s*=\s*(.*?),\s*sizeBytes\s*=\s*0L\s*\)',
        r'AppListItem.Folder(id = \1, name = \2, dateText = \3, extraInfo = \4, iconRes = \5, timestamp = \6)',
        content,
        flags=re.DOTALL
    )

    # In FolderViewModel, SearchViewModel, CategoryViewModel for Files:
    content = re.sub(
        r'FileItemData\(\s*id\s*=\s*(.*?),\s*name\s*=\s*(.*?),\s*date\s*=\s*(.*?),\s*size\s*=\s*(.*?),\s*iconRes\s*=\s*(.*?),\s*isFolder\s*=\s*false,\s*timestamp\s*=\s*(.*?),\s*sizeBytes\s*=\s*(.*?)\s*\)',
        r'AppListItem.File(id = \1, name = \2, dateText = \3, sizeText = \4, iconRes = \5, timestamp = \6, sizeBytes = \7)',
        content,
        flags=re.DOTALL
    )

    # In RecentlyDeletedViewModel for Folders:
    content = re.sub(
        r'DeletedItemData\(\s*id\s*=\s*(.*?),\s*name\s*=\s*(.*?),\s*deletedAt\s*=\s*(.*?),\s*sizeText\s*=\s*(.*?),\s*dateText\s*=\s*(.*?),\s*iconRes\s*=\s*(.*?),\s*isFolder\s*=\s*true\s*\)',
        r'AppListItem.Folder(id = \1, name = \2, timestamp = \3, dateText = \5, iconRes = \6, extraInfo = "${maxOf(0L, (30L * 24 * 60 * 60 * 1000 - (System.currentTimeMillis() - \3)) / (24 * 60 * 60 * 1000))} days remaining")',
        content,
        flags=re.DOTALL
    )
    
    # In RecentlyDeletedViewModel for Files:
    content = re.sub(
        r'DeletedItemData\(\s*id\s*=\s*(.*?),\s*name\s*=\s*(.*?),\s*deletedAt\s*=\s*(.*?),\s*sizeText\s*=\s*(.*?),\s*dateText\s*=\s*(.*?),\s*iconRes\s*=\s*(.*?),\s*isFolder\s*=\s*false\s*\)',
        r'AppListItem.File(id = \1, name = \2, timestamp = \3, sizeText = \4, dateText = \5, iconRes = \6, extraInfo = "${maxOf(0L, (30L * 24 * 60 * 60 * 1000 - (System.currentTimeMillis() - \3)) / (24 * 60 * 60 * 1000))} days remaining")',
        content,
        flags=re.DOTALL
    )

    # Sorting logic in ViewModels: compareByDescending<FileItemData> { !it.isFolder }
    content = content.replace('compareByDescending<FileItemData> { !it.isFolder }', 'compareByDescending<AppListItem> { it is AppListItem.Folder }')
    content = content.replace('compareByDescending<DeletedItemData> { !it.isFolder }', 'compareByDescending<AppListItem> { it is AppListItem.Folder }')
    content = content.replace('compareByDescending<FileItemData> { it.isFolder }', 'compareByDescending<AppListItem> { it is AppListItem.Folder }')
    content = content.replace('<FileItemData>', '<AppListItem>')
    content = content.replace('<DeletedItemData>', '<AppListItem>')
    content = content.replace('mutableListOf<FileItemData>()', 'mutableListOf<AppListItem>()')
    content = content.replace('mutableListOf<DeletedItemData>()', 'mutableListOf<AppListItem>()')
    content = content.replace('it.sizeBytes', '(it as? AppListItem.File)?.sizeBytes ?: 0L')

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)


def process_screen(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Import
    content = re.sub(r'import com\.zerogram\.feature\.folder\.FileItemData\n', '', content)
    content = re.sub(r'import com\.zerogram\.feature\.folder\.FileListItem\n', '', content)
    content = re.sub(r'import com\.zerogram\.feature\.folder\.DeletedItemData\n', '', content)
    content = re.sub(r'import com\.zerogram\.feature\.folder\.DeletedItemRow\n', '', content)
    
    if 'import com.zerogram.core.ui.components.AppList' not in content:
        content = content.replace('import androidx.compose.runtime.Composable', 'import androidx.compose.runtime.Composable\nimport com.zerogram.core.ui.components.AppList\nimport com.zerogram.core.ui.components.AppListItem\nimport com.zerogram.core.ui.components.ImmutableListWrapper')

    # Replaces LazyColumn block with AppList for filesAndFolders
    # FolderScreen, CategoryScreen, SearchScreen use `val displayItems = ...` and then LazyColumn
    # This requires careful regex or parsing.
    
    # FolderScreen LazyColumn:
    lazy_column_folder_pattern = r'LazyColumn\(\s*modifier = Modifier\s*\.fillMaxSize\(\)\s*\.then\(listModifier\)\s*\)\s*\{.*?items\(\s*items = displayItems,\s*key = \{ it\.id \},\s*contentType = \{ if \(it\.isFolder\) "folder" else "file" \}\s*\)\s*\{ file ->.*?FileListItem\(\s*file = file,\s*modifier = Modifier\.animateItem\(\),\s*isSelected = selectedItems\.contains\(file\.id\),\s*isSelectionMode = isSelectionMode,\s*onClick = \{(.*?)\},\s*onLongClick = \{(.*?)\}\s*\)\s*\}\s*\}'
    
    lazy_column_folder_repl = r'''AppList(
            items = ImmutableListWrapper(displayItems),
            selectedItems = selectedItems,
            isSelectionMode = isSelectionMode,
            onItemClick = { file -> \1 },
            onItemLongClick = { file -> \2 },
            modifier = Modifier.then(listModifier)
        )'''
        
    content = re.sub(lazy_column_folder_pattern, lazy_column_folder_repl, content, flags=re.DOTALL)
    
    # CategoryScreen LazyColumn:
    lazy_column_cat_pattern = r'LazyColumn\(modifier = Modifier\.fillMaxSize\(\)\) \{\s*items\(\s*items = filesAndFolders,\s*key = \{ it\.id \},\s*contentType = \{ if \(it\.isFolder\) "folder" else "file" \}\s*\) \{ file ->\s*FileListItem\(\s*file = file,\s*isSelected = selectedItems\.contains\(file\.id\),\s*isSelectionMode = isSelectionMode,\s*onClick = \{(.*?)\},\s*onLongClick = \{(.*?)\}\s*\)\s*\}\s*\}'
    
    lazy_column_cat_repl = r'''AppList(
            items = ImmutableListWrapper(filesAndFolders),
            selectedItems = selectedItems,
            isSelectionMode = isSelectionMode,
            onItemClick = { file -> \1 },
            onItemLongClick = { file -> \2 }
        )'''
    content = re.sub(lazy_column_cat_pattern, lazy_column_cat_repl, content, flags=re.DOTALL)

    # SearchScreen LazyColumn:
    lazy_column_search_pattern = r'LazyColumn\(modifier = Modifier\.fillMaxSize\(\)\) \{\s*items\(\s*items = filesAndFolders,\s*key = \{ it\.id \},\s*contentType = \{ if \(it\.isFolder\) "folder" else "file" \}\s*\) \{ file ->\s*FileListItem\(\s*file = file,\s*isSelected = selectedItems\.contains\(file\.id\),\s*isSelectionMode = isSelectionMode,\s*onClick = \{(.*?)\},\s*onLongClick = \{(.*?)\}\s*\)\s*\}\s*\}'
    
    lazy_column_search_repl = r'''AppList(
            items = ImmutableListWrapper(filesAndFolders),
            selectedItems = selectedItems,
            isSelectionMode = isSelectionMode,
            onItemClick = { file -> \1 },
            onItemLongClick = { file -> \2 }
        )'''
    content = re.sub(lazy_column_search_pattern, lazy_column_search_repl, content, flags=re.DOTALL)

    # RecentlyDeleted LazyColumn:
    lazy_column_del_pattern = r'LazyColumn\(modifier = Modifier\.fillMaxSize\(\)\) \{\s*items\(\s*items = deletedItems,\s*key = \{ it\.id \},\s*contentType = \{ if \(it\.isFolder\) "folder" else "file" \}\s*\) \{ item ->\s*DeletedItemRow\(\s*item = item,\s*dividerColor = DividerColor,\s*isSelected = selectedItems\.contains\(item\.id\),\s*isSelectionMode = isSelectionMode,\s*onClick = \{(.*?)\},\s*onLongClick = \{(.*?)\}\s*\)\s*\}\s*\}'
    
    lazy_column_del_repl = r'''AppList(
            items = ImmutableListWrapper(deletedItems),
            selectedItems = selectedItems,
            isSelectionMode = isSelectionMode,
            onItemClick = { item -> \1 },
            onItemLongClick = { item -> \2 }
        )'''
    content = re.sub(lazy_column_del_pattern, lazy_column_del_repl, content, flags=re.DOTALL)

    # Clean up FileItemData definition if it exists in FolderScreen
    content = re.sub(r'@Immutable\s*data class FileItemData\(.*?\)\s*// dummyFiles removed', '', content, flags=re.DOTALL)
    content = re.sub(r'@Immutable\s*data class FileItemData\(.*?\)', '', content, flags=re.DOTALL)
    content = re.sub(r'data class FileItemData\(.*?\)', '', content, flags=re.DOTALL)
    
    # Clean up FileListItem function
    content = re.sub(r'@OptIn\(ExperimentalFoundationApi::class\)\s*@Composable\s*fun FileListItem\(.*?\)\s*\{.*?// Divider\s*HorizontalDivider\(.*?\}\s*\}', '', content, flags=re.DOTALL)
    
    # Clean up DeletedItemData and DeletedItemRow in RecentlyDeletedScreen
    content = re.sub(r'data class DeletedItemData\(.*?\)\s*', '', content, flags=re.DOTALL)
    content = re.sub(r'@OptIn\(androidx\.compose\.foundation\.ExperimentalFoundationApi::class\)\s*@Composable\s*fun DeletedItemRow\(.*?\)\s*\{.*?// Divider\s*HorizontalDivider\(.*?\}\s*\}', '', content, flags=re.DOTALL)

    # In screens, replace file.isFolder with (file is AppListItem.Folder)
    content = content.replace('file.isFolder', '(file is AppListItem.Folder)')
    content = content.replace('item.isFolder', '(item is AppListItem.Folder)')
    
    # itemToRename: FileItemData? -> AppListItem?
    content = content.replace('var itemToRename by remember { mutableStateOf<FileItemData?>(null) }', 'var itemToRename by remember { mutableStateOf<AppListItem?>(null) }')
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

base_dir = r"d:\zerogram 3.0"
viewmodels = [
    r"feature-folder\src\main\java\com\zerogram\feature\folder\FolderViewModel.kt",
    r"feature-folder\src\main\java\com\zerogram\feature\folder\RecentlyDeletedViewModel.kt",
    r"feature-search\src\main\java\com\zerogram\feature\search\SearchViewModel.kt",
    r"feature-category\src\main\java\com\zerogram\feature\category\CategoryViewModel.kt"
]
screens = [
    r"feature-folder\src\main\java\com\zerogram\feature\folder\FolderScreen.kt",
    r"feature-folder\src\main\java\com\zerogram\feature\folder\RecentlyDeletedScreen.kt",
    r"feature-search\src\main\java\com\zerogram\feature\search\SearchScreen.kt",
    r"feature-category\src\main\java\com\zerogram\feature\category\CategoryScreen.kt"
]

for vm in viewmodels:
    path = os.path.join(base_dir, vm)
    if os.path.exists(path):
        process_view_model(path)

for sc in screens:
    path = os.path.join(base_dir, sc)
    if os.path.exists(path):
        process_screen(path)

print("Done")
