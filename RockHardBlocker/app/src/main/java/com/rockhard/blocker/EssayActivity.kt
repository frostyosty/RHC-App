package com.rockhard.blocker

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class EssayActivity : Activity() {

    private val prompts = listOf(
        "I am a grown man with a fully functioning prefrontal cortex and millions of years of evolutionary survival behind me. Yet, right now, I am losing a psychological battle against a glowing glass rectangle. Instead of doing a single pushup or doing literally anything productive with my fleeting time on this earth, my brain has decided the highest peak of my existence is this momentary urge. I acknowledge that I am acting ridiculously, and I am typing this to prove my stubbornness.",
        "And here we are at the bottom of the ninth. He had a great streak going, ladies and gentlemen, but it looks like he is about to throw it all away for some pixels. It is a tragic display of weak-willed behavior. The crowd is silent. His future self is face-palming. He has the option to be a legend today, to be a rock-hard man of focus and sheer will, but instead, he is typing this humiliating paragraph because he could not control a momentary spike in his dopamine receptors."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_essay)

        // Lock them in fullscreen
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN)

        val targetText = prompts.random()
        findViewById<TextView>(R.id.tvPrompt).text = targetText
        
        val etInput = findViewById<EditText>(R.id.etInput)
        val background = findViewById<View>(R.id.essayBackground)

        etInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val typed = s.toString()
                
                if (typed.isEmpty()) return

                // Check if they typed correctly so far
                if (targetText.startsWith(typed)) {
                    // SUCCESS: They finished the whole thing perfectly!
                    if (typed == targetText) {
                        Toast.makeText(this@EssayActivity, "BLOCKER PAUSED FOR 5 MINUTES.", Toast.LENGTH_LONG).show()
                        // Tell the Guardian to sleep for 5 minutes
                        GuardianService.pauseUntil = System.currentTimeMillis() + (5 * 60 * 1000)
                        finish()
                    }
                } else {
                    // TYPO DETECTED! PUNISH THEM.
                    background.setBackgroundColor(Color.parseColor("#B71C1C")) // Flash Red
                    etInput.setText("") // Clear the box entirely
                    Toast.makeText(this@EssayActivity, "TYPO DETECTED. START OVER.", Toast.LENGTH_SHORT).show()
                    
                    // Reset background color after 300ms
                    Handler(Looper.getMainLooper()).postDelayed({
                        background.setBackgroundColor(Color.parseColor("#121212"))
                    }, 300)
                }
            }
        })
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Trapped! Can't back out unless they type it or hit home.
        Toast.makeText(this, "Type the essay or go Home.", Toast.LENGTH_SHORT).show()
    }
}
