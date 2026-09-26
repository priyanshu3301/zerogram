import os
import shutil

# Move resources
app_res = r"d:\zerogram 3.0\app\src\main\res"
ui_res = r"d:\zerogram 3.0\core-ui\src\main\res"

os.makedirs(ui_res, exist_ok=True)

# Move values
if os.path.exists(os.path.join(app_res, 'values')):
    shutil.move(os.path.join(app_res, 'values'), os.path.join(ui_res, 'values'))

# Move drawables
for item in os.listdir(app_res):
    if item.startswith('drawable'):
        shutil.move(os.path.join(app_res, item), os.path.join(ui_res, item))

# Fix R imports and UI package imports
for root, dirs, files in os.walk(r"d:\zerogram 3.0"):
    for file in files:
        if file.endswith('.kt'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
            
            new_content = content.replace('import com.zerogram.R', 'import com.zerogram.core.ui.R')
            new_content = new_content.replace('com.zerogram.ui.', 'com.zerogram.feature.')
            
            if new_content != content:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(new_content)
