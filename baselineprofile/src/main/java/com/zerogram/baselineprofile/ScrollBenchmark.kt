package com.zerogram.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollHome() = benchmarkRule.measureRepeated(
        packageName = "com.zerogram",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        }
    ) {
        device.waitForIdle()
        val scrollable = device.findObject(By.scrollable(true))
        if (scrollable != null) {
            scrollable.setGestureMargin(device.displayWidth / 5)
            scrollable.scroll(Direction.DOWN, 1f)
            scrollable.scroll(Direction.UP, 1f)
        }
    }

    @Test
    fun scrollFolder() = benchmarkRule.measureRepeated(
        packageName = "com.zerogram",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        }
    ) {
        device.waitForIdle()
        val allFiles = device.findObject(By.text("All files"))
        if (allFiles != null) {
            allFiles.click()
            device.waitForIdle()
            
            val scrollable = device.findObject(By.scrollable(true))
            if (scrollable != null) {
                scrollable.setGestureMargin(device.displayWidth / 5)
                scrollable.scroll(Direction.DOWN, 1f)
                scrollable.scroll(Direction.UP, 1f)
            }
            
            device.pressBack()
        }
    }

    @Test
    fun scrollCategory() = benchmarkRule.measureRepeated(
        packageName = "com.zerogram",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        }
    ) {
        device.waitForIdle()
        val imagesCategory = device.findObject(By.text("Images"))
        if (imagesCategory != null) {
            imagesCategory.click()
            device.waitForIdle()
            
            val scrollable = device.findObject(By.scrollable(true))
            if (scrollable != null) {
                scrollable.setGestureMargin(device.displayWidth / 5)
                scrollable.scroll(Direction.DOWN, 1f)
                scrollable.scroll(Direction.UP, 1f)
            }
            
            device.pressBack()
        }
    }

    @Test
    fun scrollTransfers() = benchmarkRule.measureRepeated(
        packageName = "com.zerogram",
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.DEFAULT,
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        }
    ) {
        device.waitForIdle()
        val transfersCategory = device.findObject(By.text("Transfers"))
        if (transfersCategory != null) {
            transfersCategory.click()
            device.waitForIdle()
            
            val scrollable = device.findObject(By.scrollable(true))
            if (scrollable != null) {
                scrollable.setGestureMargin(device.displayWidth / 5)
                scrollable.scroll(Direction.DOWN, 1f)
                scrollable.scroll(Direction.UP, 1f)
            }
            
            device.pressBack()
        }
    }
}
