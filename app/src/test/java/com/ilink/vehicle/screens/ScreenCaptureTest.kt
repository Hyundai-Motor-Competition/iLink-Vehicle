package com.ilink.vehicle.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.ilink.vehicle.ui.preview.ScreenCatalog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.util.TimeZone

/*
 * 에뮬레이터 없이 V00~V07을 PNG로 저장한다 (Robolectric 네이티브 그래픽).
 *   scripts/capture-screens.sh
 * 크기: Landscape 1280x720dp(창 2개), Portrait 720x1100dp(창 1개), mdpi(1dp = 1px). 라이트/다크 둘 다.
 * design/ 폴더는 디자인 PNG의 앱 창 크기(1305x908)로 그린 V00이라 assets 디자인과 나란히 비교할 수 있다.
 * Pleos 실제 해상도가 정해지면 qualifiers만 바꾸면 된다.
 */

/** 캡처 시각 고정: 2026-11-10 18:00 KST */
private val NOW: Instant = Instant.parse("2026-11-10T09:00:00Z")

private fun entries(only: Regex? = null): List<Array<Any>> {
    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"))
    return ScreenCatalog.entries(NOW).filter { only == null || only.containsMatchIn(it.name) }.map { arrayOf(it) }
}

/** assets에 디자인이 있는 화면 */
private val DESIGNED = Regex("^V0[0-3]_")

/** 상태 하나를 그려서 저장. 파라미터 테스트 클래스들이 공유한다 */
abstract class CaptureBase(private val e: ScreenCatalog.Entry, private val folder: String) {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun capture() {
        compose.setContent { ScreenCatalog.Render(e.state) }
        save(folder, e.name, compose.snapshot())
    }
}

/**
 * captureToImage는 Robolectric에서 프레임 콜백을 못 받아 멈추므로, 화면 루트 뷰를 직접 비트맵에 그린다.
 */
private fun AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>.snapshot(): Bitmap {
    waitForIdle()
    val view = activity.window.decorView
    return Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
}

private fun save(folder: String, name: String, bitmap: Bitmap) {
    val dir = File(System.getProperty("screens.out") ?: "build/screenshots", folder).apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}

// ILinkApplication은 Pleos SDK를 만들기 때문에 빈 Application으로 띄운다
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h720dp-land-notnight-mdpi")
class LandscapeCaptureTest(e: ScreenCatalog.Entry) : CaptureBase(e, "landscape") {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = entries()
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w1280dp-h720dp-land-night-mdpi")
class LandscapeDarkCaptureTest(e: ScreenCatalog.Entry) : CaptureBase(e, "landscape-dark") {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = entries()
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w720dp-h1100dp-port-notnight-mdpi")
class PortraitCaptureTest(e: ScreenCatalog.Entry) : CaptureBase(e, "portrait") {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = entries()
    }
}

/** 디자인 PNG의 앱 창 크기(1305x908). 디자인이 있는 V00~V03만 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w1305dp-h908dp-land-notnight-mdpi")
class DesignLightCaptureTest(e: ScreenCatalog.Entry) : CaptureBase(e, "design-light") {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = entries(DESIGNED)
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w1305dp-h908dp-land-night-mdpi")
class DesignDarkCaptureTest(e: ScreenCatalog.Entry) : CaptureBase(e, "design-dark") {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = entries(DESIGNED)
    }
}
