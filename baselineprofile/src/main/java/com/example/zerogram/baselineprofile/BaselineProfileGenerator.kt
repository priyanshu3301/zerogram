package com.example.zerogram.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineRule = BaselineProfileRule()

    @Test
    fun generate() = baselineRule.collect(
        packageName = "com.example.zerogram",
        profileBlock = {
            // 1. Cold start the app.
            startActivityAndWait()

            // 2. Wait for HomeScreen to be idle, scroll the Home list.
            // Home list typically has a list or we can just scroll the main view.
            device.waitForIdle()
            
            // Look for a scrollable element on Home screen
            val homeScrollable = device.findObject(By.scrollable(true))
            if (homeScrollable != null) {
                homeScrollable.setGestureMargin(device.displayWidth / 5)
                homeScrollable.scroll(Direction.DOWN, 1f)
                homeScrollable.scroll(Direction.UP, 1f)
            }
            
            device.waitForIdle()

            // 3. Tap into "All files" (Folder screen) and scroll it.
            // Find the "All files" card/text
            val allFiles = device.findObject(By.text("All files"))
            if (allFiles != null) {
                allFiles.click()
                device.waitForIdle()
                
                // Scroll in Folder screen
                val folderScrollable = device.findObject(By.scrollable(true))
                if (folderScrollable != null) {
                    folderScrollable.setGestureMargin(device.displayWidth / 5)
                    folderScrollable.scroll(Direction.DOWN, 1f)
                    folderScrollable.scroll(Direction.UP, 1f)
                }
                
                // Back out of Folder screen
                device.pressBack()
                device.waitForIdle()
            }

            // 4. Tap into one category screen and back out.
            // Find "Images" or "Videos" category
            val imagesCategory = device.findObject(By.text("Images"))
            if (imagesCategory != null) {
                imagesCategory.click()
                device.waitForIdle()
                
                // Scroll in Category screen
                val categoryScrollable = device.findObject(By.scrollable(true))
                if (categoryScrollable != null) {
                    categoryScrollable.setGestureMargin(device.displayWidth / 5)
                    categoryScrollable.scroll(Direction.DOWN, 1f)
                }
                
                // Back out of Category screen
                device.pressBack()
                device.waitForIdle()
            }
        }
    )
}
