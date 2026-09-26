import os

for root, dirs, files in os.walk(r"d:\zerogram 3.0"):
    for file in files:
        if file.endswith('.kt'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r', encoding='utf-8') as f:
                content = f.read()
            
            new_content = content.replace('import com.zerogram.LocalSharedTransitionScope', 'import com.zerogram.core.ui.navigation.LocalSharedTransitionScope')
            new_content = new_content.replace('import com.zerogram.LocalAnimatedVisibilityScope', 'import com.zerogram.core.ui.navigation.LocalAnimatedVisibilityScope')
            new_content = new_content.replace('import com.zerogram.SharedBoundsAnimSpec', 'import com.zerogram.core.ui.navigation.SharedBoundsAnimSpec')
            
            if new_content != content:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(new_content)
