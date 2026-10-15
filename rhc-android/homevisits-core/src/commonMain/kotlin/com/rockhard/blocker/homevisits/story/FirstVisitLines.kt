package com.rockhard.blocker.homevisits.story

/** Everything the first visit says, in one place until step 07 moves it into content files. NZ English. */
object FirstVisitLines {
    const val RACHEL: CharId = "rachel"
    const val PARCEL = "parcel"
    const val PAINTING = "painting_harbour_dusk"
    const val PAINTING_TITLE = "Harbour at dusk"

    // 1. The doorbell (home screen)
    const val BELL = "Ding-dong! Sounds like a parcel. Shall we go and see?"
    const val BELL_GO = "Go and see"
    const val NOT_NOW = "Not now"
    const val CAMERA_ASK = "We'll look through your camera. Nothing it sees is saved as a photo or leaves your phone."
    const val CAMERA_OK = "OK"
    const val NO_AR = "No worries, we'll do this another time."
    const val CAMERA_SETTINGS = "Your camera's switched off for this app. Turn it on under Permissions → Camera, then come back. We'll keep the Guardian out of the way for a minute and a half."
    const val CAMERA_SETTINGS_GO = "Open settings"
    const val BELL_AGAIN = "Ding-dong! The parcel's still waiting."

    // 2. To the front door
    const val TO_DOOR = "Head to your front door. 📦"
    const val TIP_HOLD_UP = "Hold your phone up like you're filming. Watch your step."
    const val AT_DOOR = "🚪 I'm at the door"

    // 3. The parcel
    const val OPEN_DOOR = "Open the door and have a look."
    const val FOUND_IT = "There it is!"
    const val LOOK_DOWN = "Look down at the doorstep."
    const val JUST_INSIDE = "Oh, they left it just inside!"
    const val DARK_INSIDE = "It's dark out, so they left it just inside."
    const val RATHER_NOT = "I'd rather not open the door"

    // 4. To the bench
    const val TO_BENCH = "Heavy! Let's open it on the kitchen bench."
    const val TAP_BENCH = "Tap the bench to put it down."
    const val STEP_BACK = "Step back a little so I can see the top."
    const val ANY_TABLE = "Any table's fine."
    const val TAP_ANYWHERE = "Just tap where you'd like to put it."

    // 5. Unwrapping
    const val CARD = "Welcome to the street! Something for your walls. I'll pop round and see where you put it. — Rachel, next door"

    // 6. Hanging it
    const val HANG_ASK = "It'd look lovely in your bedroom. Hang it there?"
    const val BEDROOM = "Bedroom"
    const val ELSEWHERE = "Somewhere else"
    const val WHICH_ROOM = "Where shall we hang it?"
    val ROOMS = listOf("Lounge", "Hallway", "Kitchen", "Other")
    fun takeItTo(room: String) = "Take it to $room."
    const val TAP_WALL = "Tap the wall where you want it."
    const val STEP_FROM_WALL = "Stand about a step back from the wall, facing it."
    const val LOOKS_GOOD = "Looks good"

    // 7. A moment
    const val PERFECT = "Perfect."

    // 8. The knock
    const val KNOCK = "Someone's at the door!"
    const val OPEN = "Open the door"
    const val FIND_DOOR = "Someone's at the door! Tap your front door when you get there."
    const val FIND_PAINTING = "Help me find the painting: tap where it's hanging."

    // 9. Rachel
    const val HI = "Hi! I'm Rachel, from next door. Did my parcel turn up?"
    val HI_ANSWERS = listOf("It did, thank you!", "Oh, that was you?")
    const val YOUR_NAME = "And you are…?"
    const val JUST_NEIGHBOUR = "Just call me neighbour"
    fun nice(name: String?) = if (name == null) "Neighbour it is!" else "Lovely to meet you, $name!"
    const val CAN_I_SEE = "Can I see where you put it?"
    const val COME_IN = "Come in!"

    // 10. The tour
    const val MEET_YOU = "I'll meet you there!"
    const val LEAD_ON = "Lead the way!"
    const val THERE = "We're here"
    const val NICE_KITCHEN = "Ooh, I like your kitchen."
    const val NICE_SPOT = "Ooh, nice spot."
    fun hungIn(roomName: String, bedroom: Boolean) =
        if (bedroom) "You hung it in your bedroom! I'm honoured." else "You hung it in $roomName! It looks right at home."
    val PAINTING_ANSWERS = listOf("I love it", "It's perfect there", "Honestly? I wasn't sure where to put it")
    val PAINTING_REPLIES = listOf(
        "I'm so glad. I nearly kept it for myself!",
        "It really is. The light's just right.",
        "Ha! Well, I think you chose well.",
    )
    const val OK = "😊"

    // 11. Goodbye
    const val GOODBYE = "I'd better get back. I'll come round again tomorrow!"
    const val BYE = "Bye, Rachel!"
    const val AFTER_CARD = "Rachel's coming back tomorrow. She loves paintings. 🎨\nEarn Momentum by resisting distractions, then treat your home."
    const val LATER_CARD = "The parcel's still waiting. Come back to it any time."
}
