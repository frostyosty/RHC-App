package com.rockhard.blocker

internal fun GameActivity.evolveBeast(petIndex: Int) {
    val pet = party[petIndex]
    val baseName = pet.name.split(" ").last()
    val currentDef = GameData.beasts.find { it.name == baseName }

    if (currentDef != null && currentDef.evoStage < 3) {
        val nextEvo = GameData.beasts.find { it.type == currentDef.type && it.evoStage == currentDef.evoStage + 1 }
        if (nextEvo != null) {
            printLog("\n> 🌟 WHAT IS HAPPENING?!")
            printLog("> ${pet.name} is glowing... It evolved into ${nextEvo.name}!")

            pet.name = pet.name.replace(baseName, nextEvo.name)
            pet.maxHp = (pet.maxHp * 1.5).toInt()
            pet.hp = pet.maxHp

            val newMoves = SkillEngine.generateMoves(pet.type, nextEvo.evoStage)
            if (nextEvo.evoStage == 2) pet.move2 = newMoves.second
            if (nextEvo.evoStage == 3) pet.move3 = newMoves.third

            party.forEach { it.expedsDone = 0 } // Reset all beasts!
            saveParty()
            updatePartyScreen()
        }
    }
}