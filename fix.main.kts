import java.io.File

fun main() {
    val searchVm = File("d:/zerogram 3.0/feature-search/src/main/java/com/zerogram/feature/search/SearchViewModel.kt")
    var content = searchVm.readText()
    
    content = content.replace("import com.zerogram.feature.folder.SelectionDetails", "import com.zerogram.core.ui.components.SelectionDetails")
    
    // Fix properties on AppListItem
    content = content.replace("item.isFolder", "(item is AppListItem.Folder)")
    content = content.replace("it.isFolder", "(it is AppListItem.Folder)")
    content = content.replace("item.date", "(if (item is AppListItem.File) item.dateText else (item as? AppListItem.Folder)?.dateText ?: \"\")")
    content = content.replace("item.size", "(if (item is AppListItem.File) item.sizeText else \"\")")
    content = content.replace("item.sizeBytes", "(if (item is AppListItem.File) item.sizeBytes else 0L)")
    content = content.replace("file.sizeBytes", "(if (file is AppListItem.File) file.sizeBytes else 0L)")
    
    searchVm.writeText(content)
    
    val catVm = File("d:/zerogram 3.0/feature-category/src/main/java/com/zerogram/feature/category/CategoryViewModel.kt")
    var catContent = catVm.readText()
    catContent = catContent.replace("item.isFolder", "(item is AppListItem.Folder)")
    catContent = catContent.replace("it.isFolder", "(it is AppListItem.Folder)")
    catContent = catContent.replace("file.isFolder", "(file is AppListItem.Folder)")
    catVm.writeText(catContent)
    
    val homeVm = File("d:/zerogram 3.0/feature-home/src/main/java/com/zerogram/feature/home/HomeViewModel.kt")
    if (homeVm.exists()) {
        var homeContent = homeVm.readText()
        homeContent = homeContent.replace("import com.zerogram.feature.folder.FileItemData", "import com.zerogram.core.ui.components.AppListItem")
        homeContent = homeContent.replace("FileItemData", "AppListItem")
        homeVm.writeText(homeContent)
    }
    
    // update imports in screens
    val files = listOf(
        "d:/zerogram 3.0/feature-folder/src/main/java/com/zerogram/feature/folder/FolderScreen.kt",
        "d:/zerogram 3.0/feature-folder/src/main/java/com/zerogram/feature/folder/RecentlyDeletedScreen.kt",
        "d:/zerogram 3.0/feature-search/src/main/java/com/zerogram/feature/search/SearchScreen.kt"
    )
    for (f in files) {
        val file = File(f)
        var c = file.readText()
        c = c.replace("import com.zerogram.feature.folder.SelectionDetails", "import com.zerogram.core.ui.components.SelectionDetails")
        file.writeText(c)
    }
}
