package com.zerogram.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineRule = BaselineProfileRule()

    @Test
    fun generate() = baselineRule.collect(
        packageName = "com.zerogram",
        profileBlock = {
            // 1. Cold start the app.
            startActivityAndWait()

            // 2. Wait for HomeScreen to be idle, scroll the Home list.
            device.waitForIdle()
            
            val homeScrollable = device.findObject(By.scrollable(true))
            if (homeScrollable != null) {
                homeScrollable.setGestureMargin(device.displayWidth / 5)
                homeScrollable.scroll(Direction.DOWN, 1f)
                homeScrollable.scroll(Direction.UP, 1f)
            }
            
            device.waitForIdle()

            // 3. Tap into "All files" (Folder screen) and scroll it.
            val allFiles = device.findObject(By.text("All files"))
            if (allFiles != null) {
                allFiles.click()
                device.waitForIdle()
                
                val folderScrollable = device.findObject(By.scrollable(true))
                if (folderScrollable != null) {
                    folderScrollable.setGestureMargin(device.displayWidth / 5)
                    folderScrollable.scroll(Direction.DOWN, 1f)
                    folderScrollable.scroll(Direction.UP, 1f)
                }
                
                device.pressBack()
                device.waitForIdle()
            }

            // 4. Tap into one category screen and back out.
            val imagesCategory = device.findObject(By.text("Images"))
            if (imagesCategory != null) {
                imagesCategory.click()
                device.waitForIdle()
                
                val categoryScrollable = device.findObject(By.scrollable(true))
                if (categoryScrollable != null) {
                    categoryScrollable.setGestureMargin(device.displayWidth / 5)
                    categoryScrollable.scroll(Direction.DOWN, 1f)
                }
                
                device.pressBack()
                device.waitForIdle()
            }
        }
    )
}
