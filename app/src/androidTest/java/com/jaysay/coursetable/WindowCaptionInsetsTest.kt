package com.jaysay.coursetable

import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WindowCaptionInsetsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun captionResizeAndReturnToFullscreenKeepActionsOutsideSystemControls() {
        rule.waitUntil(20_000) {
            rule.onAllNodesWithTag("add-course-button").fetchSemanticsNodes().isNotEmpty()
        }
        // Dispatch real platform insets to the Compose owner: freeform has a caption,
        // but no status bar. This is the case omitted by statusBarsPadding alone.
        for (caption in listOf(0, 96, 144, 0)) {
            rule.runOnIdle {
                val owner = findComposeOwner(rule.activity.window.decorView)
                    ?: error("Compose owner missing")
                ViewCompat.dispatchApplyWindowInsets(owner, WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.NONE)
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.NONE)
                    .setInsets(WindowInsetsCompat.Type.captionBar(), Insets.of(0, caption, 0, 0))
                    .setVisible(WindowInsetsCompat.Type.captionBar(), caption > 0)
                    .build())
            }
            rule.waitForIdle()
            val top = rule.onNodeWithTag("add-course-button").fetchSemanticsNode().boundsInRoot.top
            assertTrue("caption=$caption actionTop=$top", top >= caption)
            if (caption == 0) assertTrue("Fullscreen retains no caption gap: $top", top < 96)
        }
    }

    private fun findComposeOwner(view: View): View? {
        if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findComposeOwner(view.getChildAt(i))?.let { return it }
        }
        return null
    }
}
