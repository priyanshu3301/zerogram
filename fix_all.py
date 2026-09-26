import os
import re

# 1. AppModule.kt
path = r'd:\zerogram 3.0\app\src\main\java\com\zerogram\di\AppModule.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()
content = content.replace('import com.zerogram.telegram.TDLibClient', 'import com.zerogram.core.tdlib.TDLibClient')
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

# 2. SearchViewModel.kt (Remove duplicate SortOrder)
path = r'd:\zerogram 3.0\feature-search\src\main\java\com\zerogram\feature\search\SearchViewModel.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()
content = re.sub(r'enum class SortOrder \{.*?\}', '', content, flags=re.DOTALL)
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)

# 3. CategoryScreen.kt (Add missing imports)
path = r'd:\zerogram 3.0\feature-category\src\main\java\com\zerogram\feature\category\CategoryScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()
if 'import com.zerogram.core.ui.components.AppListItem' not in content:
    content = content.replace('import androidx.compose.ui.Modifier', 'import androidx.compose.ui.Modifier\nimport com.zerogram.core.ui.components.AppListItem\nimport com.zerogram.core.ui.navigation.LocalSharedTransitionScope\nimport com.zerogram.core.ui.navigation.LocalAnimatedVisibilityScope')
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
