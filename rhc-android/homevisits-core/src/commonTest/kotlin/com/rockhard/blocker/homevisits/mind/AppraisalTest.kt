package com.rockhard.blocker.homevisits.mind

import com.rockhard.blocker.homevisits.HomeClock
import com.rockhard.blocker.homevisits.house.RoomKind
import com.rockhard.blocker.homevisits.mind.TestCast.ALFRED
import com.rockhard.blocker.homevisits.mind.TestCast.MAYA
import com.rockhard.blocker.homevisits.mind.TestCast.RACHEL
import com.rockhard.blocker.homevisits.mind.TestCast.RUTH
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** One test per row of step 06's appraisal table. */
class AppraisalTest {
    private fun near(expected: Float, actual: Float, tol: Float = 0.02f) =
        assertTrue(kotlin.math.abs(expected - actual) <= tol, "expected $expected, got $actual")

    @Test
    fun seeingAnItemFollowsTasteNoveltyAndOpenness() {
        val m = mind()
        var first = 0f
        var again = 0f
        m.scene(1, RACHEL) {
            val moodBefore = mind.mood(RACHEL).p
            event(Event(EventKind.SAW_ITEM, at(), item = "plant", tags = listOf(Tag.PLANTS)))
            // plants 0.3 × new (1.0) × (1 + 0.5 × openness 0.8), lifted a little by a good mood
            first = mind.state.scene!!.pending.emotions.last { it.second.kind == EmotionKind.LIKING }.second.intensity
            near(0.3f * 1.4f * (1f + 0.25f * maxOf(0f, moodBefore)), first)
            event(Event(EventKind.SAW_ITEM, at(), item = "plant", tags = listOf(Tag.PLANTS)))
            again = mind.state.scene!!.pending.emotions.last { it.second.kind == EmotionKind.LIKING }.second.intensity
        }
        assertTrue(again < first * 0.3f, "seen before counts 0.3 and loses the openness boost: $first then $again")
        // Alfred (openness −0.5) gets less of a lift from something new than Rachel would.
        val s = mind().scene(1, ALFRED) { event(Event(EventKind.SAW_ITEM, at(), item = "abstract", tags = listOf(Tag.ABSTRACT))) }
        assertTrue(s.felt(ALFRED, EmotionKind.DISLIKING) > 0.4f, "Alfred dislikes abstract art")
    }

    @Test
    fun anItemChosenForThemAddsGratitude() {
        val plain = mind().scene(1, RACHEL) { event(Event(EventKind.SAW_ITEM, at(), item = "p", tags = listOf(Tag.PAINTING))) }
        val chosen = mind().scene(1, RACHEL) { event(Event(EventKind.SAW_ITEM, at(), item = "p", tags = listOf(Tag.PAINTING), chosenFor = RACHEL)) }
        assertEquals(0f, plain.felt(RACHEL, EmotionKind.GRATITUDE, you))
        assertTrue(chosen.felt(RACHEL, EmotionKind.GRATITUDE, you) > 0.4f)
    }

    @Test
    fun aGiftOnShowGivesGratitudeAndPrideMoreSoWherePeopleSeeIt() {
        fun shown(room: RoomKind) = mind().scene(1, RUTH) {
            event(Event(EventKind.GIFT_ON_SHOW, at(), item = "scone-tin", giftFrom = RUTH, room = room))
        }
        val hall = shown(RoomKind.HALL)
        val lounge = shown(RoomKind.LOUNGE)
        assertTrue(hall.felt(RUTH, EmotionKind.GRATITUDE, you) >= 0.55f)
        assertTrue(hall.felt(RUTH, EmotionKind.PRIDE, you) > 0f)
        assertTrue(lounge.felt(RUTH, EmotionKind.GRATITUDE, you) > hall.felt(RUTH, EmotionKind.GRATITUDE, you))
    }

    @Test
    fun praiseScalesWithRespectAndPrideButFlatteryFromTheDistrustedBackfires() {
        fun praise(r: Relationship, who: String) = mind().apply { set(who, you, r) }
            .scene(1, who) { by(you, SocialAction(Act.PRAISE, to = Who.Char(who))) }
        val respected = praise(Relationship(20f, 20f, 80f, 20f), ALFRED).felt(ALFRED, EmotionKind.GRATITUDE, you)
        val unknown = praise(Relationship(20f, 20f, 0f, 20f), ALFRED).felt(ALFRED, EmotionKind.GRATITUDE, you)
        assertTrue(respected > unknown, "praise from someone respected counts more: $respected vs $unknown")
        // Alfred (pride 0.6) feels praise more than Ruth (pride −0.2).
        val ruthG = praise(Relationship(20f, 20f, 0f, 20f), RUTH).felt(RUTH, EmotionKind.GRATITUDE, you)
        assertTrue(unknown > ruthG)
        val flattered = praise(Relationship(-20f, -50f, 0f, 20f), ALFRED)
        assertEquals(0f, flattered.felt(ALFRED, EmotionKind.GRATITUDE, you))
        assertTrue(flattered.felt(ALFRED, EmotionKind.REPROACH, you) > 0f)
    }

    @Test
    fun criticismBringsDistressAndAngerAtTheCritic() {
        val s = mind().scene(1, ALFRED) { by(you, SocialAction(Act.CRITICISE, to = alfred, tags = listOf(Tag.GARDEN))) }
        // (0.5 + 0.5 × pride 0.6) × (1 + 0.5 × neuroticism 0.1), before mood
        assertTrue(s.felt(ALFRED, EmotionKind.ANGER, you) >= 0.8f)
        assertTrue(s.felt(ALFRED, EmotionKind.DISTRESS) >= 0.8f)
    }

    @Test
    fun witnessesJudgeActionsByTheStandardsTheyHold() {
        val f = Fact("f1", by = you, about = alfred, sentiment = -0.8f)
        val s = mind().scene(1, RUTH, MAYA) { by(you, SocialAction(Act.TELL, to = maya, fact = f)) }
        // Ruth weighs kindness 0.9 and discretion 0.72; Maya kindness 0.6 and no discretion.
        assertTrue(s.felt(RUTH, EmotionKind.REPROACH, you) > s.felt(MAYA, EmotionKind.REPROACH, you))
        val kind = mind().scene(1, RUTH, ALFRED) { by(you, SocialAction(Act.DEFEND, to = ruth, about = alfred)) }
        assertTrue(kind.felt(RUTH, EmotionKind.ADMIRATION, you) > 0.5f)
        assertTrue(kind.felt(ALFRED, EmotionKind.GRATITUDE, you) > 0.5f, "Alfred heard you stick up for him")
    }

    @Test
    fun concernsHelpedOrHurtBringJoyAndGratitudeOrDistressAndAnger() {
        val helped = mind().scene(1, ALFRED) { by(you, SocialAction(Act.COMFORT, to = alfred)) }
        // June weighs 0.9 and comforting him helps it by 0.5: joy (for himself) and gratitude of 0.45
        val selfJoy = helped.emotions.getValue(ALFRED).filter { it.kind == EmotionKind.JOY && it.toward == null }.maxOf { it.intensity }
        near(0.45f, selfJoy, 0.05f)
        assertTrue(helped.emotions.getValue(ALFRED).count { it.kind == EmotionKind.GRATITUDE && it.toward == you } == 2, "comfort, and help with June")
        val m = mind()
        val hurt = m.scene(1, ALFRED) { by(you, SocialAction(Act.TEASE, to = alfred)) }
        assertTrue(hurt.felt(ALFRED, EmotionKind.DISTRESS) > 0.4f)
        assertTrue(hurt.felt(ALFRED, EmotionKind.ANGER, you) > 0.3f)
        val n = mind()
        n.scene(1, ALFRED) { by(you, SocialAction(Act.COMFORT, to = alfred)) }
        assertTrue(n.concernProgress(ALFRED, "june") > 0f)
    }

    @Test
    fun luckForOthersDependsOnLikingThem() {
        val good = Event(EventKind.LIFE_EVENT, HomeClock(1, 0), about = rachel, sentiment = 0.8f, concern = "exhibition")
        val s = mind().scene(1, RACHEL, MAYA, ALFRED) { event(good) }
        assertTrue(s.felt(RACHEL, EmotionKind.JOY) > 0.6f)
        assertTrue(s.felt(MAYA, EmotionKind.HAPPY_FOR, rachel) > 0.3f)
        assertTrue(s.felt(ALFRED, EmotionKind.RESENTMENT, rachel) > 0.2f)
        assertEquals(0f, s.felt(ALFRED, EmotionKind.HAPPY_FOR, rachel))
    }

    @Test
    fun keptAndBrokenPromisesWeighMoreForTheConscientious() {
        fun broken(who: String) = mind().scene(1, who) {
            by(you, SocialAction(Act.PROMISE, to = Who.Char(who), magnitude = 0.6f))
            by(you, SocialAction(Act.BREAK_PROMISE, to = Who.Char(who), magnitude = 0.6f))
        }
        val a = broken(ALFRED).felt(ALFRED, EmotionKind.DISAPPOINTMENT, you)
        val r = broken(RACHEL).felt(RACHEL, EmotionKind.DISAPPOINTMENT, you)
        assertTrue(a > 2f * r, "Alfred (conscientiousness 0.8) minds more than Rachel (−0.2): $a vs $r")
        val kept = mind().scene(1, ALFRED) { by(you, SocialAction(Act.KEEP_PROMISE, to = alfred, magnitude = 0.6f)) }
        assertTrue(kept.felt(ALFRED, EmotionKind.GRATITUDE, you) > 0.7f)
    }

    @Test
    fun arrivalsBringJoyOrDistressAndAGrudgeAddsAnger() {
        val s = mind().scene(1, RACHEL, ALFRED, MAYA)
        // Rachel's history with Alfred is a strong memory: a grudge.
        near(0.4f, s.felt(RACHEL, EmotionKind.DISTRESS, alfred), 0.15f)
        assertTrue(s.felt(RACHEL, EmotionKind.ANGER, alfred) > 0f)
        assertTrue(s.felt(RACHEL, EmotionKind.JOY, maya) >= 0.5f)
        assertEquals(0f, s.felt(MAYA, EmotionKind.ANGER, alfred), "Maya is only wary of him")
    }

    @Test
    fun expectingSomethingBringsHopeThenRelief() {
        val s = mind().scene(1, MAYA) {
            event(Event(EventKind.EXPECTED, at(), by = maya, concern = "exams", sentiment = -1f, magnitude = 0.8f))
            event(Event(EventKind.LIFE_EVENT, at(), about = maya, concern = "exams", sentiment = 0.7f))
        }
        assertTrue(s.felt(MAYA, EmotionKind.FEAR) > 0.4f)
        assertTrue(s.felt(MAYA, EmotionKind.RELIEF) > 0.3f, "feared exams went well: relief")
    }

    @Test
    fun jealousyIsResentmentWhenYouSpendTheVisitOnARival() {
        // Rachel (pride 0.5) watches you heap praise on Alfred, whom she dislikes.
        val s = mind().scene(1, RACHEL, ALFRED) {
            repeat(3) { by(you, SocialAction(Act.PRAISE, to = alfred)) }
        }
        assertTrue(s.felt(RACHEL, EmotionKind.RESENTMENT, alfred) > 0f)
        val fair = mind().scene(1, RACHEL, ALFRED) {
            by(you, SocialAction(Act.PRAISE, to = alfred)); by(you, SocialAction(Act.PRAISE, to = rachel))
        }
        assertEquals(0f, fair.felt(RACHEL, EmotionKind.RESENTMENT, alfred))
    }
}
