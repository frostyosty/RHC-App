package com.rockhard.blocker

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import kotlin.random.Random

class GameActivity : Activity() {

    private lateinit var tvConsole: TextView
    private lateinit var tvParty: TextView
    private lateinit var tvBag: TextView
    private lateinit var viewActivity: ScrollView
    private lateinit var viewParty: ScrollView
    private lateinit var viewBag: ScrollView

    private var isUnderAttack = false
    private var bossName = "The Dark One"
    private var battleOver = false
    
    private var sprays = 3
    private var potions = 5
    private var nets = 10
    private var party = mutableListOf("TechLine: Cacheon (Lvl 12)", "FitnessLine: Cardiol (Lvl 8)", "GamingLine: Skirmalot (Lvl 15)")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        tvConsole = findViewById(R.id.tvConsole)
        tvParty = findViewById(R.id.tvParty)
        tvBag = findViewById(R.id.tvBag)
        viewActivity = findViewById(R.id.viewActivity)
        viewParty = findViewById(R.id.viewParty)
        viewBag = findViewById(R.id.viewBag)

        isUnderAttack = intent.getBooleanExtra("UNDER_ATTACK", false)
        val flavor = getString(R.string.flavor_id)

        if (flavor == "rhc") {
            bossName = listOf("Pornosaur", "GoreIlla", "Fleshwire", "Lustrot").random()
        }

        setupTabs()
        checkWelfareBouncemons()
        updatePartyScreen()
        updateBagScreen()

        if (isUnderAttack) {
            printLog("⚠️ EMERGENCY: $bossName HAS INVADED THE SANCTUARY!")
            printLog("Your Bouncemons are terrified. You must DEFEND them!")
        } else {
            printLog("> INITIALIZING BOUNCEMON SAFARI...")
            printLog("> DETECTING LOCAL HUNTING ZONE...")
            printLog("> Location Lock: East Papamoa")
            printLog("> Bustlingness: HIGH | Rareness: 12% | Net Chance: 30%")
            printLog("-------------------------------------------------")
            printLog("Walking... No creatures currently found. Waiting for spawn...")
        }

        findViewById<Button>(R.id.btnExit).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnAttack).setOnClickListener {
            if (!isUnderAttack) { printLog("> Nothing to attack! Keep walking."); return@setOnClickListener }
            if (battleOver) return@setOnClickListener
            printLog("\n> You order your Bouncemon to ATTACK!")
            Handler(Looper.getMainLooper()).postDelayed({
                printLog("> $bossName EVADED your attack! It is too fast!")
                triggerBossCounterAttack()
            }, 1000)
        }

        findViewById<Button>(R.id.btnSpray).setOnClickListener {
            if (!isUnderAttack) { printLog("> You spray the air. It smells nice."); return@setOnClickListener }
            if (battleOver) return@setOnClickListener
            if (sprays <= 0) { printLog("> You are out of Sprays!"); return@setOnClickListener }
            sprays--
            updateBagScreen()
            printLog("\n> You used a Repel Spray! ($sprays left)")
            if (Random.nextInt(100) < 5) {
                printLog("> CRITICAL SUCCESS! The spray burned $bossName! It flees into the shadows.")
                battleOver = true
            } else {
                printLog("> It had no effect... $bossName is enraged!")
                triggerBossCounterAttack()
            }
        }
        
        findViewById<Button>(R.id.btnPotion).setOnClickListener {
            if (potions > 0) { potions--; updateBagScreen(); printLog("\n> You used a Potion. Attack power increased! (But it won't help against $bossName...)") } 
            else printLog("\n> Out of potions!")
        }
        
        findViewById<Button>(R.id.btnNet).setOnClickListener {
            if (isUnderAttack) printLog("\n> You can't catch $bossName! Are you crazy?!")
            else if (nets > 0) { nets--; updateBagScreen(); printLog("\n> You throw a net at nothing. Good job. Lost 1 Net.") } 
            else printLog("\n> You are out of Nets!")
        }
    }

    private fun checkWelfareBouncemons() {
        if (party.size < 3) {
            printLog("📥 INBOX ALERT: The Safari Reserve noticed you were running low. They sent a care package!")
            party.add("WelfareLine: PityPet (Lvl 5)")
        }
    }

    private fun triggerBossCounterAttack() {
        if (party.isEmpty()) {
            printLog("> All your Bouncemons are already dead...")
            battleOver = true
            return
        }
        Handler(Looper.getMainLooper()).postDelayed({
            val victim1 = party.random()
            party.remove(victim1)
            updatePartyScreen()
            printLog("🩸 $bossName strikes with devastating force! $victim1 was KILLED!")
            if (Random.nextBoolean() && party.isNotEmpty()) {
                Handler(Looper.getMainLooper()).postDelayed({
                    val victim2 = party.random()
                    party.remove(victim2)
                    updatePartyScreen()
                    printLog("🩸 $bossName follows up! $victim2 was KILLED!")
                    bossFlees()
                }, 1500)
            } else {
                bossFlees()
            }
        }, 1500)
    }

    private fun bossFlees() {
        Handler(Looper.getMainLooper()).postDelayed({
            printLog("\n> Satisfied with the destruction, $bossName fades into the shadows...")
            printLog("> You survived... but at what cost?")
            battleOver = true
        }, 2000)
    }

    private fun printLog(msg: String) {
        val currentText = tvConsole.text.toString()
        tvConsole.text = "$currentText\n$msg"
    }

    private fun updatePartyScreen() {
        var pText = "=== YOUR BOUNCEMONS ===\n\n"
        if (party.isEmpty()) pText += "Your party is empty. You are defenseless.\n"
        for (p in party) pText += "[Sprite Placeholder] $p\n- Health: 100/100\n- Status: OK\n\n"
        tvParty.text = pText
    }

    private fun updateBagScreen() {
        tvBag.text = "=== INVENTORY ===\n\n🕸️ Capture Nets: $nets\n🧪 Healing Potions: $potions\n💨 Repel Sprays: $sprays\n\n(Keep browsing safely to earn Focus Coins to buy more!)"
    }

    private fun setupTabs() {
        val btnAct = findViewById<Button>(R.id.tabActivity)
        val btnPar = findViewById<Button>(R.id.tabParty)
        val btnBag = findViewById<Button>(R.id.tabBag)

        btnAct.setOnClickListener { switchTab(0, btnAct, btnPar, btnBag) }
        btnPar.setOnClickListener { switchTab(1, btnAct, btnPar, btnBag) }
        btnBag.setOnClickListener { switchTab(2, btnAct, btnPar, btnBag) }
    }

    private fun switchTab(index: Int, b1: Button, b2: Button, b3: Button) {
        viewActivity.visibility = if (index == 0) View.VISIBLE else View.GONE
        viewParty.visibility = if (index == 1) View.VISIBLE else View.GONE
        viewBag.visibility = if (index == 2) View.VISIBLE else View.GONE
        b1.setBackgroundColor(if (index == 0) Color.parseColor("#333333") else Color.parseColor("#111111"))
        b2.setBackgroundColor(if (index == 1) Color.parseColor("#333333") else Color.parseColor("#111111"))
        b3.setBackgroundColor(if (index == 2) Color.parseColor("#333333") else Color.parseColor("#111111"))
        b1.setTextColor(if (index == 0) Color.parseColor("#00FF00") else Color.parseColor("#888888"))
        b2.setTextColor(if (index == 1) Color.parseColor("#00BCD4") else Color.parseColor("#888888"))
        b3.setTextColor(if (index == 2) Color.parseColor("#FFCA28") else Color.parseColor("#888888"))
    }
}
