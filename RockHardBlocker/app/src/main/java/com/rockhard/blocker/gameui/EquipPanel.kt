package com.rockhard.blocker

internal fun GameActivity.setupEquipPanel() {
    // 1. Inventory & Amulets (EquipItems.kt)
    setupItemEquipping()
    setupDistributeButton()
    
    // 2. Upgrades & Mutations (EquipUpgrades.kt)
    setupInfusionButton()
    setupCleanseButton()
    setupTeachMoveButton()
}