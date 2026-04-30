package com.iwatchme.android.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupAndCriticalJourneys() {
        baselineProfileRule.collect(
            packageName = PACKAGE_NAME,
        ) {
            // 1. 冷启动 → 首帧渲染 → 等待首屏数据加载
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.res("home_list")), 10_000)
            device.waitForIdle()

            // 2. 进入 Startup Inspector（最重型的 Compose 页面）
            val inspectorCard = device.wait(
                Until.findObject(By.res("demo_card_demo/startup-inspector")),
                5_000,
            )
            inspectorCard?.click()
            device.waitForIdle()
            // 等待页面内容渲染完成
            Thread.sleep(2_000)

            // 3. 滚动 Inspector 页面，覆盖 LazyColumn 中的更多 Composable
            val scrollable = device.findObject(By.scrollable(true))
            scrollable?.scroll(androidx.test.uiautomator.Direction.DOWN, 1.0f)
            device.waitForIdle()
            scrollable?.scroll(androidx.test.uiautomator.Direction.DOWN, 1.0f)
            device.waitForIdle()

            // 4. 返回首页
            device.pressBack()
            device.waitForIdle()

            // 5. 进入 CrashLib Demo 页面
            val crashCard = device.wait(
                Until.findObject(By.res("demo_card_demo/crashlib")),
                5_000,
            )
            crashCard?.click()
            device.waitForIdle()
            Thread.sleep(1_000)

            // 6. 返回首页
            device.pressBack()
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.iwatchme.android"
    }
}
