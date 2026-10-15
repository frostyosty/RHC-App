package com.rockhard.blocker.homevisits.mind

import com.rockhard.blocker.homevisits.HomeClock
import com.rockhard.blocker.homevisits.mind.TestCast.ALFRED
import com.rockhard.blocker.homevisits.mind.TestCast.RACHEL
import com.rockhard.blocker.homevisits.mind.TestCast.RUTH
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The scenarios from step 06's "Done when", written from the user's example. */
class ScenarioTest {
    @Test
    fun rachelSeesANewPaintingAndWarmsToYouByFiveToFifteen() {
        val m = mind()
        val before = m.aff(RACHEL, you)
        m.scene(1, RACHEL) { event(Event(EventKind.SAW_ITEM, at(), item = "painting", tags = listOf(Tag.PAINTING, Tag.LANDSCAPE))) }
        val rise = m.aff(RACHEL, you) - before
        assertTrue(rise in 5f..15f, "rose by $rise")
    }

    @Test
    fun rachelAndAlfredLeftAloneStillDontGetOnAfterThirtyDays() {
        val m = mind()
        m.advanceTo(HomeClock(30, 0))
        assertEquals(Label.DONT_GET_ON, m.label(RACHEL, alfred))
        assertEquals(Label.DONT_GET_ON, m.label(ALFRED, rachel))
    }

    /** Rachel complains about Alfred, you stick up for him, and Ruth puts in a good word. */
    private fun defendScenes(withRuth: Boolean): Mind {
        val m = mind()
        m.set(RACHEL, you, Relationship(50f, 60f, 30f, 40f)) // a Rachel who trusts you
        for (day in listOf(1, 8, 15)) {
            val who = if (withRuth) arrayOf(RACHEL, RUTH) else arrayOf(RACHEL)
            m.scene(day, *who) {
                by(rachel, SocialAction(Act.CRITICISE, to = you, about = alfred))
                by(you, SocialAction(Act.DEFEND, to = rachel, about = alfred))
                if (withRuth) by(ruth, SocialAction(Act.CREDIT, to = rachel, about = alfred))
            }
        }
        m.advanceTo(HomeClock(21, 0))
        return m
    }

    @Test
    fun defendingAlfredThreeTimesWithRuthThereBringsRachelRoundWithinThreeWeeks() {
        val alone = mind().also { it.advanceTo(HomeClock(21, 0)) }.aff(RACHEL, alfred)
        val once = mind().apply {
            set(RACHEL, you, Relationship(50f, 60f, 30f, 40f))
            scene(1, RACHEL) { by(you, SocialAction(Act.DEFEND, to = rachel, about = alfred)) }
        }.aff(RACHEL, alfred)
        assertTrue(once > -40f, "one defence softens her view a little: $once")
        val withoutRuth = defendScenes(false).aff(RACHEL, alfred)
        val m = defendScenes(true)
        assertTrue(withoutRuth > alone, "defending him helps even without Ruth")
        assertTrue(m.aff(RACHEL, alfred) > withoutRuth, "Ruth being there helps more")
        assertTrue(Labels.warm(m.label(RACHEL, alfred)), "Rachel→Alfred is ${m.label(RACHEL, alfred)} (${m.aff(RACHEL, alfred)})")
    }

    @Test
    fun oneRudeAnswerDropsACloseFriendByAtMostFifteenAndItMendsWithinAWeek() {
        val m = mind()
        m.set(RACHEL, you, Relationship(75f, 60f, 50f, 60f))
        assertEquals(Label.CLOSE_FRIENDS, m.label(RACHEL, you))
        val before = m.aff(RACHEL, you)
        m.scene(1, RACHEL) { by(you, SocialAction(Act.CRITICISE, to = rachel, tags = listOf(Tag.PAINTING))) }
        val drop = before - m.aff(RACHEL, you)
        assertTrue(drop > 0f && drop <= 15f, "dropped $drop")
        assertEquals(Label.CLOSE_FRIENDS, m.label(RACHEL, you), "one bad answer can't wreck a friendship")
        m.scene(4, RACHEL) {
            by(you, SocialAction(Act.APOLOGISE, to = rachel))
            by(you, SocialAction(Act.PRAISE, to = rachel, tags = listOf(Tag.PAINTING)))
        }
        m.advanceTo(HomeClock(8, 0))
        assertEquals(Label.CLOSE_FRIENDS, m.label(RACHEL, you))
        assertTrue(m.aff(RACHEL, you) >= before - 3f, "back to ${m.aff(RACHEL, you)} from $before")
    }
}
