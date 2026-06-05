package com.magics.pool8

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PoolSplashScreen(onTimeout: () -> Unit) {
    // 2-second timeout trigger
    LaunchedEffect(Unit) {
        delay(2000L)
        onTimeout()
    }

    // Animation timer goes from 0.0 to 1.0 over 2 seconds
    val animTime = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        animTime.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2000, easing = LinearEasing)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)), // slate-900 / premium dark mode background
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.weight(1f))
            
            // Big title "POOL 8"
            Text(
                text = "POOL 8",
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF22D3EE), // Cyan-400
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Online Multiplayer",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(60.dp))

            // Canvas for animating pool balls
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val centerX = width / 2
                    val centerY = height / 2
                    val r = 40f // Increased ball radius for prominent visual style

                    // Break shot time calculations:
                    // t goes from 0.0 to 1.0 over 2 seconds
                    val t = animTime.value
                    
                    // Ball positions
                    val cueX: Float
                    val cueY: Float
                    
                    val ball8X: Float
                    val ball8Y: Float
                    val ball1X: Float
                    val ball1Y: Float
                    val ball9X: Float
                    val ball9Y: Float
                    val ball3X: Float
                    val ball3Y: Float

                    val hitT = 0.35f
                    if (t < hitT) {
                        // Before collision
                        val progress = t / hitT
                        cueX = centerX
                        cueY = height - (height - centerY - r * 2) * progress // moving up

                        // Object balls are sitting in a small rack
                        ball1X = centerX // Apex
                        ball1Y = centerY - r * 1.5f
                        
                        ball8X = centerX - r // Left
                        ball8Y = centerY
                        
                        ball9X = centerX + r // Right
                        ball9Y = centerY
                        
                        ball3X = centerX // Center back
                        ball3Y = centerY + r * 1.5f
                    } else {
                        // After collision (scatter phase)
                        val progress = (t - hitT) / (1f - hitT)
                        
                        // Cue ball rebounds/slows down
                        cueX = centerX
                        cueY = centerY + r * 2 + (r * 1.5f) * progress // slow rebound down
                        
                        // Object balls scatter outwards with deceleration (ease-out feel)
                        // Using quadratic ease-out: 1 - (1-p)^2
                        val easeOut = 1f - (1f - progress) * (1f - progress)
                        
                        // Ball 1 (Apex): scatters straight up (scaled up)
                        ball1X = centerX
                        ball1Y = (centerY - r * 1.5f) - 240f * easeOut
                        
                        // Ball 8: scatters bottom-left (angle 210 deg, scaled up)
                        val rad8 = Math.toRadians(210.0)
                        ball8X = (centerX - r) + (220f * cos(rad8) * easeOut).toFloat()
                        ball8Y = centerY + (220f * sin(rad8) * easeOut).toFloat()
                        
                        // Ball 9: scatters bottom-right (angle 330 deg, scaled up)
                        val rad9 = Math.toRadians(330.0)
                        ball9X = (centerX + r) + (220f * cos(rad9) * easeOut).toFloat()
                        ball9Y = centerY + (220f * sin(rad9) * easeOut).toFloat()
                        
                        // Ball 3: scatters straight down/left slightly (scaled up)
                        val rad3 = Math.toRadians(95.0)
                        ball3X = centerX + (160f * cos(rad3) * easeOut).toFloat()
                        ball3Y = (centerY + r * 1.5f) + (160f * sin(rad3) * easeOut).toFloat()
                    }

                    // Helper to draw a shaded pool ball
                    fun DrawScope.drawSplashBall(x: Float, y: Float, color: Color, number: String?, isStripe: Boolean = false) {
                        // Drop shadow
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.35f),
                            radius = r,
                            center = Offset(x + r * 0.12f, y + r * 0.16f)
                        )

                        if (isStripe) {
                            // Base white ball
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.White, Color(0xFFE2E8F0), Color.Black.copy(alpha = 0.7f)),
                                    center = Offset(x - r * 0.35f, y - r * 0.35f),
                                    radius = r * 1.35f
                                ),
                                radius = r,
                                center = Offset(x, y)
                            )
                            // Stripe band
                            drawCircle(
                                color = color,
                                radius = r * 0.72f,
                                center = Offset(x, y)
                            )
                        } else {
                            // Solid ball
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.White, color, color.copy(alpha = 0.75f), Color.Black.copy(alpha = 0.9f)),
                                    center = Offset(x - r * 0.35f, y - r * 0.35f),
                                    radius = r * 1.35f
                                ),
                                radius = r,
                                center = Offset(x, y)
                            )
                        }

                        // White center circle for number
                        if (number != null) {
                            drawCircle(
                                color = Color.White,
                                radius = r * 0.38f,
                                center = Offset(x, y)
                            )
                            // Draw text
                            drawContext.canvas.nativeCanvas.apply {
                                val textPaint = android.graphics.Paint().apply {
                                    setColor(android.graphics.Color.BLACK)
                                    textSize = r * 0.45f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    typeface = android.graphics.Typeface.create(
                                        android.graphics.Typeface.DEFAULT,
                                        android.graphics.Typeface.BOLD
                                    )
                                }
                                val textY = y - (textPaint.descent() + textPaint.ascent()) / 2
                                drawText(number, x, textY, textPaint)
                            }
                        }

                        // Glare highlight
                        drawCircle(
                            color = Color.White.copy(alpha = 0.3f),
                            radius = r * 0.18f,
                            center = Offset(x - r * 0.42f, y - r * 0.42f)
                        )
                    }

                    // Draw the balls
                    // Cue Ball (White)
                    drawSplashBall(cueX, cueY, Color.White, null)
                    
                    // Ball 1 (Solid Yellow)
                    drawSplashBall(ball1X, ball1Y, Color(0xFFEAB308), "1")
                    
                    // Ball 8 (Solid Black)
                    drawSplashBall(ball8X, ball8Y, Color(0xFF000000), "8")
                    
                    // Ball 9 (Stripe Yellow)
                    drawSplashBall(ball9X, ball9Y, Color(0xFFEAB308), "9", isStripe = true)
                    
                    // Ball 3 (Solid Red)
                    drawSplashBall(ball3X, ball3Y, Color(0xFFEF4444), "3")
                }
            }

            Spacer(modifier = Modifier.weight(1.2f))
        }
    }
}
