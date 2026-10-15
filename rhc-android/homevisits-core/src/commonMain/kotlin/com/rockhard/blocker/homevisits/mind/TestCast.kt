package com.rockhard.blocker.homevisits.mind

import com.rockhard.blocker.homevisits.mind.Trigger.Companion.ANYONE

/**
 * Rachel, Alfred, Maya and Ruth from HOMEVISITS_PLAN/cast.md, built in Kotlin until step 07 brings
 * the content format. Used by the tests and the soak.
 */
object TestCast {
    const val RACHEL = "rachel"
    const val ALFRED = "alfred"
    const val MAYA = "maya"
    const val RUTH = "ruth"

    val rachel = CharSheet(
        RACHEL, "Rachel",
        Personality(openness = 0.8f, conscientiousness = -0.2f, extraversion = 0.5f, agreeableness = 0.4f, neuroticism = 0.3f, pride = 0.5f, discretion = -0.1f),
        tastes = mapOf(Tag.PAINTING to 0.9f, Tag.ART to 0.9f, Tag.BRIGHT to 0.7f, Tag.HANDMADE to 0.6f, Tag.TRAVEL to 0.6f,
            Tag.MUSIC to 0.4f, Tag.PLANTS to 0.3f, Tag.RUGBY to -0.3f, Tag.TIDY to -0.2f),
        standards = mapOf(Standard.KINDNESS to 0.7f, Standard.LOYALTY to 0.6f, Standard.HONESTY to 0.5f, Standard.RELIABILITY to 0.2f),
        concerns = listOf(
            Concern("settling", 0.4f, helpedBy = listOf(Trigger(kind = EventKind.GIFT_ON_SHOW, strength = 1f), Trigger(kind = EventKind.SAW_ITEM, tag = Tag.PAINTING, strength = 0.3f))),
            Concern("exhibition", 0.8f,
                helpedBy = listOf(Trigger(Act.PRAISE, tag = Tag.PAINTING), Trigger(Act.PRAISE, tag = Tag.ART), Trigger(Act.COMFORT)),
                hurtBy = listOf(Trigger(Act.CRITICISE, tag = Tag.PAINTING), Trigger(Act.CRITICISE, tag = Tag.ART))),
            Concern("mural", 0.6f, helpedBy = listOf(Trigger(Act.CREDIT), Trigger(Act.DEFEND)))
        ),
        bonds = listOf(
            Bond(ALFRED, -40f, respect = -10f, familiarity = 60f, kind = BondKind.NEIGHBOURS,
                history = "She painted a mural on her side of the shared fence, and he complained to the council."),
            Bond(MAYA, 50f, familiarity = 50f, kind = BondKind.FRIENDS),
            Bond(RUTH, 15f, familiarity = 20f)
        )
    )

    val alfred = CharSheet(
        ALFRED, "Alfred",
        Personality(openness = -0.5f, conscientiousness = 0.8f, extraversion = -0.3f, agreeableness = -0.1f, neuroticism = 0.1f, pride = 0.6f, discretion = 0.7f),
        tastes = mapOf(Tag.GARDEN to 0.9f, Tag.PLANTS to 0.9f, Tag.TIDY to 0.8f, Tag.HANDMADE to 0.7f, Tag.VINTAGE to 0.6f, Tag.RUGBY to 0.6f,
            Tag.WEATHER to 0.5f, Tag.LANDSCAPE to 0.4f, Tag.ABSTRACT to -0.7f, Tag.MODERN to -0.5f, Tag.BRIGHT to -0.4f, Tag.ART to -0.3f),
        standards = mapOf(Standard.RELIABILITY to 0.9f, Standard.HONESTY to 0.8f, Standard.LOYALTY to 0.7f, Standard.TIDINESS to 0.7f,
            Standard.MODESTY to 0.6f, Standard.FAITH to 0.4f),
        concerns = listOf(
            Concern("shell", 0.5f, helpedBy = listOf(Trigger(Act.INVITE), Trigger(Act.THANK, strength = 0.3f), Trigger(Act.GREET, strength = 0.2f))),
            Concern("june", 0.9f, helpedBy = listOf(Trigger(Act.COMFORT), Trigger(Act.ASK_ABOUT, tag = Tag.FAMILY)), hurtBy = listOf(Trigger(Act.TEASE))),
            Concern("mural", 0.4f, helpedBy = listOf(Trigger(Act.CREDIT)))
        ),
        bonds = listOf(
            Bond(RACHEL, -35f, respect = -20f, familiarity = 60f, kind = BondKind.NEIGHBOURS,
                history = "The mural on the fence is an eyesore, and she's flighty."),
            Bond(RUTH, 60f, familiarity = 80f, kind = BondKind.OLD_FRIENDS, history = "Ruth knew June."),
            Bond(MAYA, -10f, familiarity = 15f, kind = BondKind.NEIGHBOURS)
        )
    )

    val maya = CharSheet(
        MAYA, "Maya",
        Personality(openness = 0.7f, conscientiousness = -0.4f, extraversion = 0.9f, agreeableness = 0.5f, neuroticism = 0.4f, pride = 0.2f, discretion = -0.7f),
        tastes = mapOf(Tag.PLANTS to 0.8f, Tag.MUSIC to 0.8f, Tag.COSY to 0.7f, Tag.CANDLES to 0.7f, Tag.VINTAGE to 0.6f, Tag.BRIGHT to 0.5f,
            Tag.TIDY to -0.3f, Tag.NEWS to -0.3f),
        standards = mapOf(Standard.KINDNESS to 0.6f, Standard.LOYALTY to 0.5f, Standard.HONESTY to 0.4f),
        concerns = listOf(
            Concern("exams", 0.7f, helpedBy = listOf(Trigger(Act.COMFORT))),
            Concern("homesick", 0.6f, helpedBy = listOf(Trigger(Act.INVITE), Trigger(Act.GIVE, tag = Tag.FOOD))),
            Concern("alfred", 0.3f, helpedBy = listOf(Trigger(Act.ASK_ABOUT, tag = Tag.PLANTS, target = ANYONE, strength = 0.3f)))
        ),
        bonds = listOf(
            Bond(RACHEL, 50f, familiarity = 50f, kind = BondKind.FRIENDS),
            Bond(ALFRED, -5f, familiarity = 15f, kind = BondKind.NEIGHBOURS),
            Bond(RUTH, 10f, familiarity = 5f)
        )
    )

    val ruth = CharSheet(
        RUTH, "Ruth",
        Personality(openness = 0.1f, conscientiousness = 0.6f, extraversion = 0.4f, agreeableness = 0.9f, neuroticism = -0.3f, pride = -0.2f, discretion = 0.9f),
        tastes = mapOf(Tag.BAKING to 0.9f, Tag.FAMILY to 0.8f, Tag.PHOTOS to 0.8f, Tag.FLOWERS to 0.7f, Tag.COSY to 0.7f, Tag.CHURCH to 0.7f, Tag.TIDY to 0.4f),
        standards = mapOf(Standard.KINDNESS to 0.9f, Standard.FAITH to 0.9f, Standard.HONESTY to 0.8f, Standard.LOYALTY to 0.6f),
        concerns = listOf(
            Concern("welcome", 0.4f, helpedBy = listOf(Trigger(Act.THANK), Trigger(Act.PRAISE, tag = Tag.BAKING))),
            Concern("alfred_alone", 0.6f, helpedBy = listOf(Trigger(Act.INVITE, target = ALFRED), Trigger(Act.COMFORT, target = ALFRED))),
            Concern("street", 0.5f, helpedBy = listOf(Trigger(Act.DEFEND, target = ANYONE), Trigger(Act.CREDIT, target = ANYONE)))
        ),
        bonds = listOf(
            Bond(ALFRED, 60f, familiarity = 80f, kind = BondKind.OLD_FRIENDS, history = "She knew June."),
            Bond(RACHEL, 20f, familiarity = 20f),
            Bond(MAYA, 10f, familiarity = 5f)
        ),
        toPlayer = Bond("@player", 10f, respect = 10f, trust = 10f, familiarity = 0f, kind = BondKind.NONE)
    )

    val cast = Cast(listOf(rachel, alfred, maya, ruth))
}
