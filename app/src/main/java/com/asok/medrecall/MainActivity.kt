package com.asok.medrecall

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.asok.medrecall.ui.theme.MedRecallTheme

// FragmentActivity (not plain ComponentActivity) because androidx.biometric's
// BiometricPrompt needs a FragmentActivity/Fragment host to attach its
// dialog to -- see ui/lock/LockScreen.kt. FragmentActivity is itself a
// ComponentActivity, so enableEdgeToEdge()/setContent below are unaffected.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MedRecallTheme {
                MedRecallApp()
            }
        }
    }
}
