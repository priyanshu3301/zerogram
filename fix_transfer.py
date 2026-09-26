import os
import re

paths = [
    r'd:\zerogram 3.0\feature-folder\src\main\java\com\zerogram\feature\folder\FolderUploadViewModel.kt',
    r'd:\zerogram 3.0\feature-folder\src\main\java\com\zerogram\feature\folder\FolderViewModel.kt'
]

replacement = '''val intent = android.content.Intent().apply { setClassName(context.packageName, "com.zerogram.service.TransferService") }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }'''

for path in paths:
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    content = re.sub(r'import com\.zerogram\.service\.TransferService\n?', '', content)
    content = re.sub(r'TransferService\.startService\(context\)', replacement, content)

    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
