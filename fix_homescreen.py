import os

path = r'd:\zerogram 3.0\feature-home\src\main\java\com\zerogram\feature\home\HomeScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('import com.zerogram.LocalSharedTransitionScope', 'import com.zerogram.core.ui.navigation.LocalSharedTransitionScope')
content = content.replace('import com.zerogram.LocalAnimatedVisibilityScope', 'import com.zerogram.core.ui.navigation.LocalAnimatedVisibilityScope')
content = content.replace('import com.zerogram.SharedBoundsAnimSpec', 'import com.zerogram.core.ui.navigation.SharedBoundsAnimSpec')
content = content.replace('com.zerogram.feature.search.SortOrder', 'SortOrder')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
