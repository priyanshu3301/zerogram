import os
import re

path = r'd:\zerogram 3.0\app\src\main\java\com\zerogram\service\TransferService.kt'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix storageChatId
content = content.replace('val storageChatId = config.storageChatId\n', 'val storageChatId = config.storageChatId!!\n')

# Fix smart casts for telegramFileId
content = content.replace('telegramRepository.downloadDocument(fileEntity.telegramFileId, 0, 1)', 'telegramRepository.downloadDocument(fileEntity.telegramFileId!!, 0, 1)')
content = content.replace('val cleanupResult = telegramRepository.deleteLocalFile(fileEntity.telegramFileId)', 'val cleanupResult = telegramRepository.deleteLocalFile(fileEntity.telegramFileId!!)')

# Fix R.mipmap.ic_launcher
content = content.replace('R.mipmap.ic_launcher', 'com.zerogram.R.mipmap.ic_launcher')

# Fix setContentTitle and NotificationCompat
if 'import androidx.core.app.NotificationCompat' not in content:
    content = content.replace('import android.app.Notification', 'import android.app.Notification\nimport androidx.core.app.NotificationCompat')

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
