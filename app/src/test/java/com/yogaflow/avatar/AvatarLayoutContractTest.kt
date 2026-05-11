package com.yogaflow.avatar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AvatarLayoutContractTest {

    @Test
    fun virtualCoachView_isFullScreenTransparentOverlay() {
        val layout = File("src/main/res/layout/activity_main.xml").readText()
        val virtualCoachBlock = Regex(
            """<androidx\.fragment\.app\.FragmentContainerView[\s\S]*?android:id="@\+id/virtualCoachView"[\s\S]*?/>"""
        ).find(layout)?.value ?: error("virtualCoachView not found")

        assertTrue(
            "virtualCoachView must use match_parent width — the Godot overlay is full-screen (see docs/avatar.md §3).",
            virtualCoachBlock.contains("""android:layout_width="match_parent"""")
        )
        assertTrue(
            "virtualCoachView must use match_parent height — the Godot overlay is full-screen (see docs/avatar.md §3).",
            virtualCoachBlock.contains("""android:layout_height="match_parent"""")
        )
        assertFalse(
            "virtualCoachView must not pin to a corner — avatar movement happens inside Godot, not by translating the Android view.",
            virtualCoachBlock.contains("""android:layout_gravity="bottom|end"""")
        )
        assertFalse(
            "virtualCoachView must not declare any fixed dp width — full-screen overlay.",
            Regex("""android:layout_width="\d+dp"""").containsMatchIn(virtualCoachBlock)
        )
        assertFalse(
            "virtualCoachView must not declare any fixed dp height — full-screen overlay.",
            Regex("""android:layout_height="\d+dp"""").containsMatchIn(virtualCoachBlock)
        )
        assertTrue(
            "virtualCoachView starts invisible; code toggles visibility once a session is running.",
            virtualCoachBlock.contains("""android:visibility="invisible"""")
        )
    }
}
