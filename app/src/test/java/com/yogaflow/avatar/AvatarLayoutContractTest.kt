package com.yogaflow.avatar

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AvatarLayoutContractTest {

    @Test
    fun virtualCoachView_isCornerPipNotFullscreen() {
        val layout = File("src/main/res/layout/activity_main.xml").readText()
        val virtualCoachBlock = Regex(
            """<androidx\.fragment\.app\.FragmentContainerView[\s\S]*?android:id="@\+id/virtualCoachView"[\s\S]*?/>"""
        ).find(layout)?.value ?: error("virtualCoachView not found")

        assertTrue(virtualCoachBlock.contains("""android:layout_width="110dp""""))
        assertTrue(virtualCoachBlock.contains("""android:layout_height="196dp""""))
        assertTrue(virtualCoachBlock.contains("""android:layout_gravity="bottom|end""""))
        assertTrue(!virtualCoachBlock.contains("""android:layout_width="match_parent""""))
        assertTrue(!virtualCoachBlock.contains("""android:layout_height="match_parent""""))
    }
}
