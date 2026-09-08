package com.asok.medrecall.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Coral/red background sampled from the original iOS splash screen.
private val SplashBackground = Color(0xFFEB4C46)
private val SplashCircle = Color.White.copy(alpha = 0.15f)
private val SplashSubtitleColor = Color.White.copy(alpha = 0.92f)
private val SplashAttributionColor = Color.White.copy(alpha = 0.65f)

/**
 * First screen shown on app launch, matching the original iOS splash
 * (mic icon in a soft circle, "MedRecall" title, tagline, attribution)
 * on a coral background. Held on screen for 2.5s by the caller
 * (see the LaunchedEffect/delay in MedRecallApp.kt) before it's replaced
 * by the normal lock-check/home flow -- this composable itself has no
 * timing or navigation logic, it's just the visual.
 */
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground)
            .padding(PaddingValues(horizontal = 32.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .background(color = SplashCircle, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(height = 28.dp)

            Text(
                text = "MedRecall",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                textAlign = TextAlign.Center
            )

            Spacer(height = 20.dp)

            Text(
                text = "Record, transcribe, and organize your\ndoctor visits.",
                color = SplashSubtitleColor,
                fontSize = 17.sp,
                lineHeight = 23.sp,
                textAlign = TextAlign.Center
            )

            Spacer(height = 36.dp)

            Text(
                text = "Developed by Asok",
                color = SplashAttributionColor,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun Spacer(height: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(width = 0.dp, height = height))
}
