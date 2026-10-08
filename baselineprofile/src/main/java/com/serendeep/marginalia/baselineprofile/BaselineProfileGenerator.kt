package com.serendeep.marginalia.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "com.serendeep.marginalia"
private const val WAIT_MS = 5_000L

/** Cold start, Today, Library, the first notebook, then a scroll through its pages. */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.wait(Until.hasObject(By.text("Library")), WAIT_MS)
        device.findObject(By.text("Library"))?.click()
        device.wait(Until.hasObject(By.textContains("PAGES")), WAIT_MS)
        device.findObject(By.textContains("PAGES"))?.click()
        device.waitForIdle()
        Thread.sleep(2_000)
        repeat(3) {
            device.swipe(device.displayWidth / 4, device.displayHeight * 3 / 4, device.displayWidth / 4, device.displayHeight / 4, 20)
            device.waitForIdle()
        }
    }
}
