import os

for root, dirs, files in os.walk(r'd:\zerogram 3.0'):
    if 'build' in root or '.git' in root:
        continue
    for file in files:
        if file.endswith('.kt'):
            path = os.path.join(root, file)
            with open(path, 'r', encoding='utf-8') as f:
                content = f.read()

            if 'com.zerogram.core.utils.FileOpener' in content:
                content = content.replace('com.zerogram.core.utils.FileOpener', 'com.zerogram.core.ui.utils.FileOpener')
                with open(path, 'w', encoding='utf-8') as f:
                    f.write(content)
