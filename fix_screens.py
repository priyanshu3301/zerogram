import os
import re

paths = [
    r'd:\zerogram 3.0\feature-search\src\main\java\com\zerogram\feature\search\SearchScreen.kt',
    r'd:\zerogram 3.0\feature-category\src\main\java\com\zerogram\feature\category\CategoryScreen.kt'
]

def trailing_icon_repl(match):
    condition = match.group(1)
    icon_code = match.group(2)
    return f'trailingIcon = if ({condition}) {{ @androidx.compose.runtime.Composable {icon_code} }} else null'

for path in paths:
    if not os.path.exists(path):
        continue
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    content = re.sub(r'trailingIcon\s*=\s*if\s*\((.*?)\)\s*\{\s*(\{.*?\})\s*\}\s*else\s*null', trailing_icon_repl, content)

    # Also add import for SelectionDetails if not there
    if 'import com.zerogram.core.ui.components.SelectionDetails' not in content:
        content = content.replace('import com.zerogram.core.ui.components.SortOrder', 'import com.zerogram.core.ui.components.SortOrder\nimport com.zerogram.core.ui.components.SelectionDetails')
        
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
