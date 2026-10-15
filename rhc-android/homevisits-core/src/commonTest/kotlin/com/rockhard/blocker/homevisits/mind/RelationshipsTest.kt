package com.rockhard.blocker.homevisits.mind

import com.rockhard.blocker.homevisits.HomeClock
import com.rockhard.blocker.homevisits.mind.TestCast.ALFRED
import com.rockhard.blocker.homevisits.mind.TestCast.MAYA
import com.rockhard.blocker.homevisits.mind.TestCast.RACHEL
import com.rockhard.blocker.homevisits.mind.TestCast.RUTH
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How emotions settle into relationships, drift, labels, memory, knowledge, gossip and choices. */
class RelationshipsTest {
    private fun near(expected: Float, actual: Float, tol: Float = 0.01f) =
        assertTrue(abs(expected - actual) <= tol, "expected $expected, got $actual")

    private fun settleOne(m: Mind, who: String, kind: EmotionKind, i: Float, n: Int = 1) {
        val p = Pending()
        repeat(n) { p.emotions += who to Emotion(kind, i, you, null, 99L) }
        m.settle(p, null)
    }

    @Test
    fun gratitudeSettlesAsAffinityAndTrustScaledByStabilityAndAgreeableness() {
        val m = mind()
        m.set(RACHEL, you, Relationship(0f, 0f, 0f, 0f))
        settleOne(m, RACHEL, EmotionKind.GRATITUDE, 0.5f)
        // +8i and +3i, × stability 1 × (1 + 0.3 × agreeableness 0.4)
        near(4f * 1.12f, m.relationship(RACHEL, you).affinity)
        near(1.5f * 1.12f, m.relationship(RACHEL, you).trust)
    }

    @Test
    fun oldFriendsSwingLessAndNegativesWeighByNeuroticism() {
        val a = mind().apply { set(RACHEL, you, Relationship(0f, 0f, 0f, 100f)); settleOne(this, RACHEL, EmotionKind.ANGER, 0.5f) }
        val b = mind().apply { set(RACHEL, you, Relationship(0f, 0f, 0f, 0f)); settleOne(this, RACHEL, EmotionKind.ANGER, 0.5f) }
        // −10i × stability (0.5 at familiarity 100) × (1 + 0.3 × neuroticism 0.3)
        near(-5f * 0.5f * 1.09f, a.relationship(RACHEL, you).affinity)
        near(-5f * 1.09f, b.relationship(RACHEL, you).affinity)
    }

    @Test
    fun noNumberMovesMoreThanFifteenInOneScene() {
        val m = mind()
        m.set(RACHEL, you, Relationship(0f, 0f, 0f, 0f))
        settleOne(m, RACHEL, EmotionKind.GRATITUDE, 1f, n = 6)
        assertEquals(15f, m.relationship(RACHEL, you).affinity)
    }

    @Test
    fun familiarityGrowsWithTimeTogether() {
        val m = mind()
        val before = m.relationship(RUTH, you).familiarity
        m.scene(1, RUTH, minutes = 40)
        near(before + 0.3f * 40f, m.relationship(RUTH, you).familiarity, 0.5f)
    }

    @Test
    fun dailyDriftReturnsToTheBondsRestingPointFasterForTheForgiving() {
        val m = mind()
        m.set(RACHEL, maya, Relationship(80f, 0f, 0f, 50f))
        m.set(RACHEL, alfred, Relationship(-40f, 0f, 0f, 60f))
        m.set(ALFRED, rachel, Relationship(-40f, 0f, 0f, 60f))
        m.dailyDrift(1)
        near(80f + (30f - 80f) * 0.01f, m.relationship(RACHEL, maya).affinity) // friends rest at 30
        near(-40f + 50f * 0.018f, m.relationship(RACHEL, alfred).affinity)     // neighbours rest at 10; 1% + 2% × 0.4
        near(-40f + 50f * 0.008f, m.relationship(ALFRED, rachel).affinity)     // 1% + 2% × −0.1
    }

    @Test
    fun familiarityFadesAfterTwoWeeksApart() {
        val m = mind()
        val f = m.relationship(RACHEL, maya).familiarity
        m.dailyDrift(10)
        assertEquals(f, m.relationship(RACHEL, maya).familiarity)
        m.dailyDrift(20)
        near(f - 0.2f, m.relationship(RACHEL, maya).familiarity)
    }

    @Test
    fun moodGetsHalfwayBackToRestInEightHoursTimesOnePlusNeuroticism() {
        val m = mind()
        val rest = TestCast.rachel.personality.restingMood()
        m.state.mood[RACHEL] = Pad(-1f, 0f, 0f)
        m.advanceTo(HomeClock(0, (8f * 1.3f * 60f).toInt()))
        near((-1f + rest.p) / 2f, m.mood(RACHEL).p, 0.01f)
    }

    @Test
    fun labelsComeFromTheNumbers() {
        assertEquals(Label.DONT_GET_ON, mind().label(RACHEL, alfred))
        assertEquals(Label.FRIENDS, mind().label(RACHEL, maya))
        assertEquals(Label.FRIENDS, mind().label(RUTH, alfred))
        assertEquals(Label.RIVALS, Labels.of(Relationship(-30f, 0f, 40f, 50f), BondKind.NONE))
        assertEquals(Label.CANT_STAND, Labels.of(Relationship(-70f, 0f, 40f, 50f), BondKind.NONE))
        assertEquals(Label.CLOSE_FRIENDS, Labels.of(Relationship(70f, 0f, 0f, 60f), BondKind.NONE))
        assertEquals(Label.FRIENDS, Labels.of(Relationship(70f, 0f, 0f, 10f), BondKind.NONE))
        assertEquals(Label.FAMILY, Labels.of(Relationship(-10f, 0f, 0f, 60f), BondKind.FAMILY))
        assertEquals(Label.LOOKS_UP_TO, Labels.of(Relationship(25f, 0f, 80f, 30f), BondKind.NONE))
    }

    @Test
    fun whyPointsAtTheEventsBehindAView() {
        val m = mind()
        assertTrue(m.why(RACHEL, alfred).first().event.note.contains("council"))
        m.scene(1, ALFRED) { by(you, SocialAction(Act.COMFORT, to = alfred)) }
        val top = m.why(ALFRED, you).first().event
        assertEquals(Act.COMFORT, top.act)
    }

    @Test
    fun charactersOnlyKnowWhatTheySawOrWereTold() {
        val m = mind()
        m.scene(1, RACHEL) { by(you, SocialAction(Act.DEFEND, to = rachel, about = alfred)) }
        val fact = m.state.facts.keys.single()
        assertTrue(m.knows(rachel, fact))
        assertTrue(m.knows(you, fact))
        assertTrue(!m.knows(alfred, fact))
        assertTrue(m.state.stanceKnown.contains("$RACHEL>$ALFRED"))
        assertTrue((m.state.playerStance[ALFRED] ?: 0f) > 0f)
    }

    @Test
    fun thePlayerLearnsTastesByAskingOrSeeingTwoStrongReactions() {
        val m = mind()
        assertEquals(null, m.tasteKnownToPlayer(RACHEL, Tag.PAINTING))
        m.scene(1, RACHEL) { by(you, SocialAction(Act.ASK_ABOUT, to = rachel, tags = listOf(Tag.TRAVEL))) }
        assertEquals(0.6f, m.tasteKnownToPlayer(RACHEL, Tag.TRAVEL))
        m.scene(2, RACHEL) { event(Event(EventKind.SAW_ITEM, at(), item = "a", tags = listOf(Tag.PAINTING))) }
        assertEquals(null, m.tasteKnownToPlayer(RACHEL, Tag.PAINTING))
        m.scene(3, RACHEL) { event(Event(EventKind.SAW_ITEM, at(), item = "b", tags = listOf(Tag.PAINTING))) }
        assertEquals(0.9f, m.tasteKnownToPlayer(RACHEL, Tag.PAINTING))
    }

    @Test
    fun thePlayerOnlySeesViewsThatWereShown() {
        val m = mind()
        assertTrue(!m.playerKnowsView(RACHEL, alfred))
        m.scene(1, RACHEL, ALFRED)
        assertTrue(m.playerKnowsView(RACHEL, alfred))
    }

    @Test
    fun hearingGossipMovesTheListenersViews() {
        // Maya hears from Rachel that Alfred called the mural an eyesore.
        val m = mind()
        val before = m.aff(MAYA, alfred)
        val f = Fact("eyesore", by = alfred, about = rachel, sentiment = -0.6f)
        m.perceive(SocialAction(Act.TELL, to = maya, fact = f).event(rachel, HomeClock(0, 10), witnesses = listOf(MAYA)))
        assertTrue(m.aff(MAYA, alfred) < before, "Maya likes Rachel, so she cools on Alfred")
        // And Rachel hearing it about herself, from Maya, cools on Alfred.
        val n = mind()
        val r0 = n.aff(RACHEL, alfred)
        n.perceive(SocialAction(Act.TELL, to = rachel, fact = f).event(maya, HomeClock(0, 10), witnesses = listOf(RACHEL)))
        assertTrue(n.aff(RACHEL, alfred) < r0 - 3f)
    }

    @Test
    fun mayaSpreadsNewsAndRuthKeepsIt() {
        fun spread(teller: String): Int {
            var total = 0
            for (seed in 1L..10L) {
                val m = mind(seed)
                val f = Fact("eyesore", by = alfred, about = rachel, sentiment = -0.6f)
                m.state.facts[f.id] = f
                m.know(Who.Char(teller), f, null, 0)
                m.advanceTo(HomeClock(2, 0))
                total += TestCast.cast.ids.count { it != teller && m.knows(Who.Char(it), f.id) }
            }
            return total
        }
        val maya = spread(MAYA)
        val ruth = spread(RUTH)
        assertTrue(maya >= 5, "in ten tries over two days, Maya's news should reach someone most times: $maya")
        assertEquals(0, ruth)
    }

    @Test
    fun balanceWarmsYouToYourFriendsFriends() {
        // Rachel likes you and knows you like Alfred: she slowly warms to him.
        val m = mind()
        m.set(RACHEL, you, Relationship(80f, 60f, 40f, 60f))
        m.state.playerStance[ALFRED] = 80f
        m.state.stanceKnown += "$RACHEL>$ALFRED"
        val before = m.aff(RACHEL, alfred)
        m.balance()
        assertTrue(m.aff(RACHEL, alfred) > before)
        assertTrue(m.aff(RACHEL, alfred) - before <= 0.5f)
    }

    @Test
    fun choicesFollowRelationshipsAndPersonality() {
        val m = mind()
        val critRachel = m.score(ALFRED, SocialAction(Act.CRITICISE, to = you, about = rachel))
        val critRuth = m.score(ALFRED, SocialAction(Act.CRITICISE, to = you, about = ruth))
        assertTrue(critRachel > critRuth, "criticising scores higher the less he likes them")
        val alfredLeaves = m.score(ALFRED, SocialAction(Act.LEAVE_EARLY))
        m.advanceTo(HomeClock(1, 900))
        m.startScene(listOf(ALFRED, RACHEL), com.rockhard.blocker.homevisits.house.RoomKind.LOUNGE)
        assertTrue(m.score(ALFRED, SocialAction(Act.LEAVE_EARLY)) > alfredLeaves, "more likely to leave with Rachel there")
        assertTrue(m.score(ALFRED, SocialAction(Act.LEAVE_EARLY)) > m.score(RACHEL, SocialAction(Act.LEAVE_EARLY)) - 1f)
        m.endScene()
        val a = mind(7).choose(MAYA, listOf(SocialAction(Act.JOKE), SocialAction(Act.KEEP_SECRET)))
        val b = mind(7).choose(MAYA, listOf(SocialAction(Act.JOKE), SocialAction(Act.KEEP_SECRET)))
        assertEquals(a, b)
        assertEquals(Act.JOKE, a.act, "Maya jokes rather than keeps quiet")
    }

    @Test
    fun theFaceIsTheStrongestEmotionOrElseTheMood() {
        val m = mind()
        m.advanceTo(HomeClock(1, 900))
        m.startScene(listOf(ALFRED), com.rockhard.blocker.homevisits.house.RoomKind.LOUNGE)
        m.perceive(SocialAction(Act.COMFORT, to = alfred).event(you, HomeClock(1, 901)))
        assertEquals(Expression.TOUCHED, m.expression(ALFRED))
        assertEquals(Emote.HEART, m.emote(ALFRED))
        m.perceive(SocialAction(Act.CRITICISE, to = alfred).event(you, HomeClock(1, 902)))
        assertEquals(Expression.ANGRY, m.expression(ALFRED))
        m.endScene()
        val calm = mind()
        assertEquals(Expression.NEUTRAL, calm.expression(ALFRED))
        calm.state.mood[ALFRED] = Pad(0.6f, 0f, 0f)
        assertEquals(Expression.HAPPY, calm.expression(ALFRED))
    }

    @Test
    fun wantingToVisitNeverDropsToZero() {
        val m = mind()
        m.set(ALFRED, you, Relationship(-100f, -100f, -100f, 0f))
        m.state.mood[ALFRED] = Pad(-1f, 0f, 0f)
        assertTrue(m.wantsToVisit(ALFRED, 5) >= 0.05f)
        m.set(RACHEL, you, Relationship(80f, 60f, 40f, 60f))
        assertTrue(m.wantsToVisit(RACHEL, 5) > m.wantsToVisit(ALFRED, 5))
    }
}
