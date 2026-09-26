import os
path = r'd:\zerogram 3.0\app\build.gradle.kts'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

deps = '''
    implementation(project(":feature-category"))
    implementation(project(":feature-search"))
    implementation(project(":feature-transfers"))
    implementation("androidx.metrics:metrics-performance:1.0.0-beta01")
    implementation(libs.tink.android)
    implementation(libs.androidx.security.crypto)
'''

content = content.replace('implementation(project(":feature-folder"))', 'implementation(project(":feature-folder"))' + deps)

with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
