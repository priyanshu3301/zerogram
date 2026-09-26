import os
import re

viewmodels = []
for root, dirs, files in os.walk(r'd:\zerogram 3.0'):
    if 'build' in root or '.git' in root:
        continue
    for file in files:
        if file.endswith('ViewModel.kt') or file == 'TransfersViewModel.kt' or file == 'SearchViewModel.kt' or file == 'CategoryViewModel.kt':
            viewmodels.append(os.path.join(root, file))

replacement = '''val intent = android.content.Intent().apply { setClassName(context.packageName, "com.zerogram.service.TransferService") }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }'''

for path in viewmodels:
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content
    content = re.sub(r'import com\.zerogram\.service\.TransferService\n?', '', content)
    content = re.sub(r'import com\.example\.zerogram\.service\.TransferService\n?', '', content)
    content = re.sub(r'TransferService\.startService\(context\)', replacement, content)

    if original != content:
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content)
