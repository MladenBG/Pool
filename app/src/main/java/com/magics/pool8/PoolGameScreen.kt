package com.magics.pool8

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PoolGameScreen(engine: GameEngine) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragEnd by remember { mutableStateOf<Offset?>(null) }
    var isCueSelectorVisible by remember { mutableStateOf(true) }

    val tableFeltColor = Color(0xFF0F766E) // teal-700 / modern pool table felt
    val tableRailColor = Color(0xFF4E2712) // mahogany wood
    val pocketColor = Color(0xFF090D16) // deep slate black hole
    val pocketRimColor = Color(0xFFEAB308) // gold pocket rim

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // slate-900 / premium dark mode background
    ) {
        when (engine.gameState) {
            GameState.MENU -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.9f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                            .padding(32.dp)
                    ) {
                        Text(
                            text = "8-BALL BILLIARDS",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF22D3EE), // cyan-400
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Magics Physics Engine v1.0",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
                        )

                        GameModeButton(
                            title = "Vs Computer (AI)",
                            description = "Play against a smart synthetic bot player",
                            onClick = { engine.resetGame(GameMode.PLAYER_VS_BOT) }
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        GameModeButton(
                            title = "Pass & Play",
                            description = "Play locally with a friend on the same screen",
                            onClick = { engine.resetGame(GameMode.PLAYER_VS_PLAYER) }
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        GameModeButton(
                            title = "Practice Mode",
                            description = "Hit balls freely with no turn rules or fouls",
                            onClick = { engine.resetGame(GameMode.PRACTICE) }
                        )
                    }
                }
            }

            GameState.PLAYING, GameState.GAME_OVER -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    GameHeaderPanel(
                        engine = engine,
                        isCueSelectorVisible = isCueSelectorVisible,
                        onToggleCueSelector = { isCueSelectorVisible = !isCueSelectorVisible }
                    )

                    // CUE STICK SELECTOR
                    if (isCueSelectorVisible) {
                        CueSelectorPanel(
                            engine = engine,
                            onSelected = { isCueSelectorVisible = false }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(engine.isPlayerTurn, engine.gameState) {
                                if (engine.gameState != GameState.PLAYING || !engine.isPlayerTurn) return@pointerInput
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        dragStart = offset
                                        dragEnd = offset
                                    },
                                    onDrag = { change, _ ->
                                        dragEnd = change.position
                                    },
                                    onDragEnd = {
                                        if (dragStart != null && dragEnd != null) {
                                            val dx = dragStart!!.x - dragEnd!!.x
                                            val dy = dragStart!!.y - dragEnd!!.y

                                            val canvasWidth = size.width
                                            val canvasHeight = size.height
                                            val maxTableWidth = canvasWidth - 80f
                                            val maxTableHeight = canvasHeight - 320f
                                            val tableWidth = if (maxTableHeight > maxTableWidth * 2) {
                                                maxTableWidth
                                            } else {
                                                maxTableHeight / 2
                                            }
                                            val tableHeight = tableWidth * 2

                                            val physicsDx = (dx / tableWidth) * 1000f
                                            val physicsDy = (dy / tableHeight) * 2000f

                                            engine.applyStrike(physicsDx * 0.15f, physicsDy * 0.15f)
                                        }
                                        dragStart = null
                                        dragEnd = null
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val canvasWidth = size.width
                            val canvasHeight = size.height

                            val maxTableWidth = canvasWidth - 80f
                            val maxTableHeight = canvasHeight - 320f

                            val tableWidth: Float
                            val tableHeight: Float

                            if (maxTableHeight > maxTableWidth * 2) {
                                tableWidth = maxTableWidth
                                tableHeight = maxTableWidth * 2
                            } else {
                                tableHeight = maxTableHeight
                                tableWidth = maxTableHeight / 2
                            }

                            val tableLeft = (canvasWidth - tableWidth) / 2
                            val tableTop = (canvasHeight - tableHeight) / 2

                            val railThickness = 32f * (tableWidth / 1000f) // Sights/diamonds rail width
                            val cushionThickness = 12f * (tableWidth / 1000f)
                            val visualBallRadius = 30f * (tableWidth / 1000f)

                            // 1. LAYER 1: Wood Rail (Rounded casing + outer rail)
                            // Table Outer Casing
                            drawRoundRect(
                                color = Color(0xFF1E0E06), // Dark brown casing outline
                                topLeft = Offset(tableLeft - railThickness - 4f, tableTop - railThickness - 4f),
                                size = Size(tableWidth + (railThickness + 4f) * 2, tableHeight + (railThickness + 4f) * 2),
                                cornerRadius = CornerRadius(24f, 24f)
                            )
                            // Wooden Rails
                            drawRoundRect(
                                color = tableRailColor, 
                                topLeft = Offset(tableLeft - railThickness, tableTop - railThickness),
                                size = Size(tableWidth + railThickness * 2, tableHeight + railThickness * 2),
                                cornerRadius = CornerRadius(20f, 20f)
                            )

                            // 2. LAYER 2: Cushions (Felt borders)
                            // Draw 6 separate cushions leaving gaps for side pockets in the middle of the left and right rails
                            val c = cushionThickness
                            val r = visualBallRadius * 1.5f
                            val cushionColor = Color(0xFF0F5A55) // Dark teal cushion
                            val noseHighlightColor = Color(0xFF14B8A6) // Bright teal cushion nose
                            val noseThickness = 2.5f * (tableWidth / 1000f)

                            // Top Cushion (solid across top rail)
                            val topCush = Path().apply {
                                moveTo(tableLeft + r, tableTop)
                                lineTo(tableLeft + r + c, tableTop - c)
                                lineTo(tableLeft + tableWidth - r - c, tableTop - c)
                                lineTo(tableLeft + tableWidth - r, tableTop)
                                close()
                            }
                            drawPath(path = topCush, color = cushionColor)
                            drawLine(
                                color = noseHighlightColor,
                                start = Offset(tableLeft + r, tableTop),
                                end = Offset(tableLeft + tableWidth - r, tableTop),
                                strokeWidth = noseThickness
                            )

                            // Bottom Cushion (solid across bottom rail)
                            val bottomCush = Path().apply {
                                moveTo(tableLeft + r, tableTop + tableHeight)
                                lineTo(tableLeft + r + c, tableTop + tableHeight + c)
                                lineTo(tableLeft + tableWidth - r - c, tableTop + tableHeight + c)
                                lineTo(tableLeft + tableWidth - r, tableTop + tableHeight)
                                close()
                            }
                            drawPath(path = bottomCush, color = cushionColor)
                            drawLine(
                                color = noseHighlightColor,
                                start = Offset(tableLeft + r, tableTop + tableHeight),
                                end = Offset(tableLeft + tableWidth - r, tableTop + tableHeight),
                                strokeWidth = noseThickness
                            )

                            // Left Cushion - Top Part (from top-left corner to side pocket)
                            val leftCushTop = Path().apply {
                                moveTo(tableLeft, tableTop + r)
                                lineTo(tableLeft - c, tableTop + r + c)
                                lineTo(tableLeft - c, tableTop + tableHeight / 2 - r / 2 - c)
                                lineTo(tableLeft, tableTop + tableHeight / 2 - r / 2)
                                close()
                            }
                            drawPath(path = leftCushTop, color = cushionColor)
                            drawLine(
                                color = noseHighlightColor,
                                start = Offset(tableLeft, tableTop + r),
                                end = Offset(tableLeft, tableTop + tableHeight / 2 - r / 2),
                                strokeWidth = noseThickness
                            )

                            // Left Cushion - Bottom Part (from side pocket to bottom-left corner)
                            val leftCushBottom = Path().apply {
                                moveTo(tableLeft, tableTop + tableHeight / 2 + r / 2)
                                lineTo(tableLeft - c, tableTop + tableHeight / 2 + r / 2 + c)
                                lineTo(tableLeft - c, tableTop + tableHeight - r - c)
                                lineTo(tableLeft, tableTop + tableHeight - r)
                                close()
                            }
                            drawPath(path = leftCushBottom, color = cushionColor)
                            drawLine(
                                color = noseHighlightColor,
                                start = Offset(tableLeft, tableTop + tableHeight / 2 + r / 2),
                                end = Offset(tableLeft, tableTop + tableHeight - r),
                                strokeWidth = noseThickness
                            )

                            // Right Cushion - Top Part (from top-right corner to side pocket)
                            val rightCushTop = Path().apply {
                                moveTo(tableLeft + tableWidth, tableTop + r)
                                lineTo(tableLeft + tableWidth + c, tableTop + r + c)
                                lineTo(tableLeft + tableWidth + c, tableTop + tableHeight / 2 - r / 2 - c)
                                lineTo(tableLeft + tableWidth, tableTop + tableHeight / 2 - r / 2)
                                close()
                            }
                            drawPath(path = rightCushTop, color = cushionColor)
                            drawLine(
                                color = noseHighlightColor,
                                start = Offset(tableLeft + tableWidth, tableTop + r),
                                end = Offset(tableLeft + tableWidth, tableTop + tableHeight / 2 - r / 2),
                                strokeWidth = noseThickness
                            )

                            // Right Cushion - Bottom Part (from side pocket to bottom-right corner)
                            val rightCushBottom = Path().apply {
                                moveTo(tableLeft + tableWidth, tableTop + tableHeight / 2 + r / 2)
                                lineTo(tableLeft + tableWidth + c, tableTop + tableHeight / 2 + r / 2 + c)
                                lineTo(tableLeft + tableWidth + c, tableTop + tableHeight - r - c)
                                lineTo(tableLeft + tableWidth, tableTop + tableHeight - r)
                                close()
                            }
                            drawPath(path = rightCushBottom, color = cushionColor)
                            drawLine(
                                color = noseHighlightColor,
                                start = Offset(tableLeft + tableWidth, tableTop + tableHeight / 2 + r / 2),
                                end = Offset(tableLeft + tableWidth, tableTop + tableHeight - r),
                                strokeWidth = noseThickness
                            )

                            // 3. LAYER 3: Felt Center Playing Surface
                            drawRect(
                                color = tableFeltColor,
                                topLeft = Offset(tableLeft, tableTop),
                                size = Size(tableWidth, tableHeight)
                            )



                            // 5. LAYER 5: Inner shadow overlay inside cushions for 3D depth
                            drawRect(
                                color = Color.Black.copy(alpha = 0.25f),
                                topLeft = Offset(tableLeft, tableTop),
                                size = Size(tableWidth, tableHeight),
                                style = Stroke(width = 4f)
                            )

                            // 6. LAYER 6: Table sights (diamonds) on the rails (18 total)
                            val sightRadius = 4f * (tableWidth / 1000f)
                            val sightColor = Color(0xFFE2E8F0)
                            val sightOffset = railThickness / 2f

                            // Long rails
                            val verticalSightFracs = listOf(1/8f, 2/8f, 3/8f, 5/8f, 6/8f, 7/8f)
                            verticalSightFracs.forEach { frac ->
                                drawCircle(
                                    color = sightColor,
                                    radius = sightRadius,
                                    center = Offset(tableLeft - sightOffset, tableTop + tableHeight * frac)
                                )
                                drawCircle(
                                    color = sightColor,
                                    radius = sightRadius,
                                    center = Offset(tableLeft + tableWidth + sightOffset, tableTop + tableHeight * frac)
                                )
                            }
                            // Short rails
                            val horizontalSightFracs = listOf(1/4f, 2/4f, 3/4f)
                            horizontalSightFracs.forEach { frac ->
                                drawCircle(
                                    color = sightColor,
                                    radius = sightRadius,
                                    center = Offset(tableLeft + tableWidth * frac, tableTop - sightOffset)
                                )
                                drawCircle(
                                    color = sightColor,
                                    radius = sightRadius,
                                    center = Offset(tableLeft + tableWidth * frac, tableTop + tableHeight + sightOffset)
                                )
                            }

                            // 7. LAYER 7: Pockets (aligned with corner cuts, gold rims, inner shadow)
                            val visualPocketRadius = 45f * (tableWidth / 1000f)
                            
                            data class PocketInfo(
                                val center: Offset,
                                val offset: Offset, // depth drop direction
                                val type: String
                            )

                            val pocketInfos = listOf(
                                PocketInfo(
                                    center = Offset(tableLeft, tableTop),
                                    offset = Offset(-visualPocketRadius * 0.22f, -visualPocketRadius * 0.22f),
                                    type = "corner_tl"
                                ),
                                PocketInfo(
                                    center = Offset(tableLeft + tableWidth, tableTop),
                                    offset = Offset(visualPocketRadius * 0.22f, -visualPocketRadius * 0.22f),
                                    type = "corner_tr"
                                ),
                                PocketInfo(
                                    center = Offset(tableLeft, tableTop + tableHeight / 2),
                                    offset = Offset(-visualPocketRadius * 0.28f, 0f),
                                    type = "side_l"
                                ),
                                PocketInfo(
                                    center = Offset(tableLeft + tableWidth, tableTop + tableHeight / 2),
                                    offset = Offset(visualPocketRadius * 0.28f, 0f),
                                    type = "side_r"
                                ),
                                PocketInfo(
                                    center = Offset(tableLeft, tableTop + tableHeight),
                                    offset = Offset(-visualPocketRadius * 0.22f, visualPocketRadius * 0.22f),
                                    type = "corner_bl"
                                ),
                                PocketInfo(
                                    center = Offset(tableLeft + tableWidth, tableTop + tableHeight),
                                    offset = Offset(visualPocketRadius * 0.22f, visualPocketRadius * 0.22f),
                                    type = "corner_br"
                                )
                            )

                            // Helper function to draw a screw head
                            val screwRadius = 3f * (tableWidth / 1000f)
                            val drawScrew: DrawScope.(Offset) -> Unit = { center ->
                                // Outer border shadow
                                drawCircle(
                                    color = Color(0xFF0F172A),
                                    radius = screwRadius + 1f,
                                    center = center
                                )
                                // Silver metal body
                                drawCircle(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFFE2E8F0), Color(0xFF94A3B8), Color(0xFFCBD5E1)),
                                        start = center - Offset(screwRadius, screwRadius),
                                        end = center + Offset(screwRadius, screwRadius)
                                    ),
                                    radius = screwRadius,
                                    center = center
                                )
                                // Screw slot line (45 degrees)
                                drawLine(
                                    color = Color(0xFF334155),
                                    start = center - Offset(screwRadius * 0.6f, screwRadius * 0.6f),
                                    end = center + Offset(screwRadius * 0.6f, screwRadius * 0.6f),
                                    strokeWidth = 1.2f
                                )
                            }

                            pocketInfos.forEach { pocket ->
                                val pos = pocket.center
                                
                                // 7a. Draw the outer pocket rubber liner sleeve / jaws
                                drawCircle(
                                    color = Color(0xFF1E293B), // slate-800 rubber texture
                                    radius = visualPocketRadius * 1.16f,
                                    center = pos
                                )
                                drawCircle(
                                    color = Color(0xFF0F172A), // slate-900 inner lining rim
                                    radius = visualPocketRadius * 1.02f,
                                    center = pos
                                )

                                // 7b. Draw the 3D depth drop chute (offset gradient)
                                val dropCenter = pos + pocket.offset
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFF1F2937), Color(0xFF030712), Color.Black),
                                        center = dropCenter,
                                        radius = visualPocketRadius * 0.92f
                                    ),
                                    radius = visualPocketRadius * 0.92f,
                                    center = dropCenter
                                )

                                // 7c. Deep black vertical hole bottom (pure void)
                                val voidCenter = pos + pocket.offset * 1.35f
                                drawCircle(
                                    color = Color.Black,
                                    radius = visualPocketRadius * 0.68f,
                                    center = voidCenter
                                )

                                // 7d. Draw pocket liner ribbed ridges (leather folds)
                                val baseAngle = when (pocket.type) {
                                    "corner_tl" -> 225f
                                    "corner_tr" -> 315f
                                    "corner_bl" -> 135f
                                    "corner_br" -> 45f
                                    "side_l" -> 180f
                                    else -> 0f
                                }
                                for (angleOffset in listOf(-35f, 0f, 35f)) {
                                    val rad = Math.toRadians((baseAngle + angleOffset).toDouble())
                                    val startRadius = visualPocketRadius * 0.65f
                                    val endRadius = visualPocketRadius * 1.0f
                                    val startPt = dropCenter + Offset(
                                        (cos(rad) * startRadius).toFloat(),
                                        (sin(rad) * startRadius).toFloat()
                                    )
                                    val endPt = dropCenter + Offset(
                                        (cos(rad) * endRadius).toFloat(),
                                        (sin(rad) * endRadius).toFloat()
                                    )
                                    drawLine(
                                        color = Color.Black.copy(alpha = 0.5f),
                                        start = startPt,
                                        end = endPt,
                                        strokeWidth = 2.5f * (tableWidth / 1000f)
                                    )
                                }
                            }

                            // 7e. Draw metallic pocket hardware brackets (irons) on top of the rails
                            val ironThickness = 7f * (tableWidth / 1000f)
                            val goldGradient = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFB45309), // Amber-800 / dark gold
                                    Color(0xFFFDE047), // Yellow-300 / light gold
                                    Color(0xFFCA8A04), // Yellow-600 / golden brass
                                    Color(0xFFFEF08A), // Yellow-100 / bright reflection
                                    Color(0xFFB45309)  // Amber-800
                                )
                            )

                            pocketInfos.forEach { pocket ->
                                val pos = pocket.center
                                when (pocket.type) {
                                    "corner_tl" -> {
                                        drawArc(
                                            brush = goldGradient,
                                            startAngle = 180f,
                                            sweepAngle = 90f,
                                            useCenter = false,
                                            topLeft = Offset(pos.x - visualPocketRadius, pos.y - visualPocketRadius),
                                            size = Size(visualPocketRadius * 2, visualPocketRadius * 2),
                                            style = Stroke(width = ironThickness, cap = StrokeCap.Round)
                                        )
                                        drawArc(
                                            color = Color.White.copy(alpha = 0.35f), // reflection highlight
                                            startAngle = 185f,
                                            sweepAngle = 80f,
                                            useCenter = false,
                                            topLeft = Offset(pos.x - visualPocketRadius + 1.5f, pos.y - visualPocketRadius + 1.5f),
                                            size = Size((visualPocketRadius - 1.5f) * 2, (visualPocketRadius - 1.5f) * 2),
                                            style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                                        )
                                        // Screws on rail endpoints
                                        drawScrew(pos + Offset(-visualPocketRadius, 0f))
                                        drawScrew(pos + Offset(0f, -visualPocketRadius))
                                    }
                                    "corner_tr" -> {
                                        drawArc(
                                            brush = goldGradient,
                                            startAngle = 270f,
                                            sweepAngle = 90f,
                                            useCenter = false,
                                            topLeft = Offset(pos.x - visualPocketRadius, pos.y - visualPocketRadius),
                                            size = Size(visualPocketRadius * 2, visualPocketRadius * 2),
                                            style = Stroke(width = ironThickness, cap = StrokeCap.Round)
                                        )
                                        drawArc(
                                            color = Color.White.copy(alpha = 0.35f),
                                            startAngle = 275f,
                                            sweepAngle = 80f,
                                            useCenter = false,
                                            topLeft = Offset(pos.x - visualPocketRadius + 1.5f, pos.y - visualPocketRadius + 1.5f),
                                            size = Size((visualPocketRadius - 1.5f) * 2, (visualPocketRadius - 1.5f) * 2),
                                            style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                                        )
                                        drawScrew(pos + Offset(0f, -visualPocketRadius))
                                        drawScrew(pos + Offset(visualPocketRadius, 0f))
                                    }
                                    "corner_bl" -> {
                                        drawArc(
                                            brush = goldGradient,
                                            startAngle = 90f,
                                            sweepAngle = 90f,
                                            useCenter = false,
                                            topLeft = Offset(pos.x - visualPocketRadius, pos.y - visualPocketRadius),
                                            size = Size(visualPocketRadius * 2, visualPocketRadius * 2),
                                            style = Stroke(width = ironThickness, cap = StrokeCap.Round)
                                        )
                                        drawArc(
                                            color = Color.White.copy(alpha = 0.35f),
                                            startAngle = 95f,
                                            sweepAngle = 80f,
                                            useCenter = false,
                                            topLeft = Offset(pos.x - visualPocketRadius + 1.5f, pos.y - visualPocketRadius + 1.5f),
                                            size = Size((visualPocketRadius - 1.5f) * 2, (visualPocketRadius - 1.5f) * 2),
                                            style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                                        )
                                        drawScrew(pos + Offset(-visualPocketRadius, 0f))
                                        drawScrew(pos + Offset(0f, visualPocketRadius))
                                    }
                                    "corner_br" -> {
                                        drawArc(
                                            brush = goldGradient,
                                            startAngle = 0f,
                                            sweepAngle = 90f,
                                            useCenter = false,
                                            topLeft = Offset(pos.x - visualPocketRadius, pos.y - visualPocketRadius),
                                            size = Size(visualPocketRadius * 2, visualPocketRadius * 2),
                                            style = Stroke(width = ironThickness, cap = StrokeCap.Round)
                                        )
                                        drawArc(
                                            color = Color.White.copy(alpha = 0.35f),
                                            startAngle = 5f,
                                            sweepAngle = 80f,
                                            useCenter = false,
                                            topLeft = Offset(pos.x - visualPocketRadius + 1.5f, pos.y - visualPocketRadius + 1.5f),
                                            size = Size((visualPocketRadius - 1.5f) * 2, (visualPocketRadius - 1.5f) * 2),
                                            style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                                        )
                                        drawScrew(pos + Offset(visualPocketRadius, 0f))
                                        drawScrew(pos + Offset(0f, visualPocketRadius))
                                    }
                                    "side_l" -> {
                                        // Semicircular gold bracket cap for left side pocket (goes around the pocket like corners)
                                        drawArc(
                                            brush = goldGradient,
                                            startAngle = 90f,
                                            sweepAngle = 180f,
                                            useCenter = false,
                                            topLeft = Offset(pos.x - visualPocketRadius, pos.y - visualPocketRadius),
                                            size = Size(visualPocketRadius * 2, visualPocketRadius * 2),
                                            style = Stroke(width = ironThickness, cap = StrokeCap.Round)
                                        )
                                        drawArc(
                                            color = Color.White.copy(alpha = 0.35f), // reflection highlight
                                            startAngle = 95f,
                                            sweepAngle = 170f,
                                            useCenter = false,
                                            topLeft = Offset(pos.x - visualPocketRadius + 1.5f, pos.y - visualPocketRadius + 1.5f),
                                            size = Size((visualPocketRadius - 1.5f) * 2, (visualPocketRadius - 1.5f) * 2),
                                            style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                                        )
                                        // Screws on top and bottom rail endings
                                        drawScrew(pos + Offset(0f, -visualPocketRadius))
                                        drawScrew(pos + Offset(0f, visualPocketRadius))
                                    }
                                    "side_r" -> {
                                        // Semicircular gold bracket cap for right side pocket (goes around the pocket like corners)
                                        drawArc(
                                            brush = goldGradient,
                                            startAngle = 270f,
                                            sweepAngle = 180f,
                                            useCenter = false,
                                            topLeft = Offset(pos.x - visualPocketRadius, pos.y - visualPocketRadius),
                                            size = Size(visualPocketRadius * 2, visualPocketRadius * 2),
                                            style = Stroke(width = ironThickness, cap = StrokeCap.Round)
                                        )
                                        drawArc(
                                            color = Color.White.copy(alpha = 0.35f), // reflection highlight
                                            startAngle = 275f,
                                            sweepAngle = 170f,
                                            useCenter = false,
                                            topLeft = Offset(pos.x - visualPocketRadius + 1.5f, pos.y - visualPocketRadius + 1.5f),
                                            size = Size((visualPocketRadius - 1.5f) * 2, (visualPocketRadius - 1.5f) * 2),
                                            style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                                        )
                                        // Screws on top and bottom rail endings
                                        drawScrew(pos + Offset(0f, -visualPocketRadius))
                                        drawScrew(pos + Offset(0f, visualPocketRadius))
                                    }
                                }
                            }

                            // 8. LAYER 8: Billiard Triangle Rack (Drawn before the break shot)
                            if (engine.isBreakShot) {
                                val apexX = tableLeft + (500f / 1000f) * tableWidth
                                val apexY = tableTop + (565f / 2000f) * tableHeight
                                
                                val bottomLeftX = tableLeft + (380f / 1000f) * tableWidth
                                val bottomLeftY = tableTop + (330f / 2000f) * tableHeight
                                
                                val bottomRightX = tableLeft + (620f / 1000f) * tableWidth
                                val bottomRightY = tableTop + (330f / 2000f) * tableHeight

                                val trianglePath = Path().apply {
                                    moveTo(apexX, apexY)
                                    lineTo(bottomRightX, bottomRightY)
                                    lineTo(bottomLeftX, bottomLeftY)
                                    close()
                                }

                                // A. Solid black drop shadow to ensure it looks fully opaque and 3D
                                drawPath(
                                    path = trianglePath,
                                    color = Color.Black.copy(alpha = 0.5f),
                                    style = Stroke(width = 26f * (tableWidth / 1000f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                                // B. Thick crimson red wooden/plastic rack body (completely opaque)
                                drawPath(
                                    path = trianglePath,
                                    color = Color(0xFFDC2626), // Opaque Crimson Red
                                    style = Stroke(width = 20f * (tableWidth / 1000f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                                // C. Bright highlight border for premium bevel look
                                drawPath(
                                    path = trianglePath,
                                    color = Color(0xFFFCA5A5), // light pink/red highlight
                                    style = Stroke(width = 5f * (tableWidth / 1000f), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }

                            // 9. LAYER 9: Professional Billiard Balls with Numbers and Shines
                            engine.balls.forEach { ball ->
                                val screenX = tableLeft + (ball.x / 1000f) * tableWidth
                                val screenY = tableTop + (ball.y / 2000f) * tableHeight

                                // Drop Shadow
                                drawCircle(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    radius = visualBallRadius,
                                    center = Offset(screenX + 5f, screenY + 7f)
                                )

                                // Ball Sphere Body
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White,
                                            Color(ball.color),
                                            Color(ball.color).copy(alpha = 0.75f),
                                            Color.Black.copy(alpha = 0.9f)
                                        ),
                                        center = Offset(screenX - visualBallRadius * 0.35f, screenY - visualBallRadius * 0.35f),
                                        radius = visualBallRadius * 1.35f
                                    ),
                                    radius = visualBallRadius,
                                    center = Offset(screenX, screenY)
                                )

                                // White center circle for object balls (IDs 1-6 and 8-ball)
                                if (!ball.isCueBall) {
                                    drawCircle(
                                        color = Color.White,
                                        radius = visualBallRadius * 0.38f,
                                        center = Offset(screenX, screenY)
                                    )

                                    // Draw number text inside white circle
                                    drawContext.canvas.nativeCanvas.apply {
                                        val textPaint = android.graphics.Paint().apply {
                                            color = android.graphics.Color.BLACK
                                            textSize = (visualBallRadius * 0.45f).coerceAtLeast(10f)
                                            textAlign = android.graphics.Paint.Align.CENTER
                                            typeface = android.graphics.Typeface.create(
                                                android.graphics.Typeface.DEFAULT,
                                                android.graphics.Typeface.BOLD
                                            )
                                        }
                                        val textY = screenY - (textPaint.descent() + textPaint.ascent()) / 2
                                        drawText(
                                            ball.id.toString(),
                                            screenX,
                                            textY,
                                            textPaint
                                        )
                                    }
                                }

                                // Glare highlights
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.35f),
                                    radius = visualBallRadius * 0.18f,
                                    center = Offset(screenX - visualBallRadius * 0.42f, screenY - visualBallRadius * 0.42f)
                                )
                            }

                            // 10. LAYER 10: AIMING MECHANICS (Laser dashed lines and styled cue stick)
                            if (dragStart != null && dragEnd != null && engine.isPlayerTurn && engine.gameState == GameState.PLAYING) {
                                val cueBall = engine.balls.find { it.isCueBall }
                                if (cueBall != null) {
                                    val cueScreenX = tableLeft + (cueBall.x / 1000f) * tableWidth
                                    val cueScreenY = tableTop + (cueBall.y / 2000f) * tableHeight

                                    val dragDx = dragEnd!!.x - dragStart!!.x
                                    val dragDy = dragEnd!!.y - dragStart!!.y
                                    val dragLength = Math.sqrt((dragDx * dragDx + dragDy * dragDy).toDouble()).toFloat()

                                    if (dragLength > 10f) {
                                        val nx = dragDx / dragLength
                                        val ny = dragDy / dragLength

                                        // Cyan laser aiming pointer vector
                                        val aimLength = Math.min(dragLength * 2.2f, 450f)
                                        val aimStart = Offset(cueScreenX - nx * visualBallRadius, cueScreenY - ny * visualBallRadius)
                                        val aimEnd = Offset(cueScreenX - nx * (visualBallRadius + aimLength), cueScreenY - ny * (visualBallRadius + aimLength))

                                        drawLine(
                                            color = Color(0xBB06B6D4), 
                                            start = aimStart,
                                            end = aimEnd,
                                            strokeWidth = 5f,
                                            cap = StrokeCap.Round,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                                        )

                                        drawCircle(
                                            color = Color(0xBB06B6D4),
                                            radius = 12f,
                                            center = aimEnd,
                                            style = Stroke(width = 3f)
                                        )

                                        // CUSTOM SELECTED CUE STICK DESIGN
                                        val cueStyle = engine.selectedCueStyle
                                        val stickRecoil = 15f + dragLength * 0.22f

                                        val stickLength: Float
                                        val stickThickness: Float
                                        val shaftColor: Color
                                        val gripColor: Color
                                        val tipColor: Color

                                        when (cueStyle) {
                                            CueStyle.CLASSIC_MAHOGANY -> {
                                                stickLength = 360f
                                                stickThickness = 14f
                                                shaftColor = Color(0xFF78350F) // mahogany brown
                                                gripColor = Color(0xFF1E293B)  // slate gray wrap
                                                tipColor = Color(0xFFF1F5F9)   // ivory white
                                            }
                                            CueStyle.GOLDEN_DRAGON -> {
                                                stickLength = 440f // Longer
                                                stickThickness = 18f // Thicker
                                                shaftColor = Color(0xFFD97706) // gold amber
                                                gripColor = Color(0xFF991B1B)  // ruby red wrap
                                                tipColor = Color(0xFFFEF08A)   // gold cap tip
                                            }
                                            CueStyle.STEALTH_CARBON -> {
                                                stickLength = 290f // Shorter
                                                stickThickness = 16f // Thicker
                                                shaftColor = Color(0xFF334155) // carbon slate
                                                gripColor = Color(0xFF22D3EE)  // cyan wrap
                                                tipColor = Color(0xFF06B6D4)   // cyan glowing tip
                                            }
                                        }

                                        val stickStart = Offset(cueScreenX + nx * (visualBallRadius + stickRecoil), cueScreenY + ny * (visualBallRadius + stickRecoil))
                                        val stickEnd = Offset(cueScreenX + nx * (visualBallRadius + stickRecoil + stickLength), cueScreenY + ny * (visualBallRadius + stickRecoil + stickLength))

                                        // Draw Shaft
                                        drawLine(
                                            color = shaftColor,
                                            start = stickStart,
                                            end = stickEnd,
                                            strokeWidth = stickThickness,
                                            cap = StrokeCap.Round
                                        )
                                        // Draw Grip
                                        val gripStart = Offset(cueScreenX + nx * (visualBallRadius + stickRecoil + stickLength * 0.4f), cueScreenY + ny * (visualBallRadius + stickRecoil + stickLength * 0.4f))
                                        drawLine(
                                            color = gripColor,
                                            start = gripStart,
                                            end = stickEnd,
                                            strokeWidth = stickThickness + 1.5f,
                                            cap = StrokeCap.Round
                                        )
                                        // Draw Ivory Joint Tip
                                        val tipEnd = Offset(cueScreenX + nx * (visualBallRadius + stickRecoil + 15f), cueScreenY + ny * (visualBallRadius + stickRecoil + 15f))
                                        drawLine(
                                            color = tipColor,
                                            start = stickStart,
                                            end = tipEnd,
                                            strokeWidth = stickThickness - 1f,
                                            cap = StrokeCap.Round
                                        )

                                        // Ornament details for Golden Dragon pro cue
                                        if (cueStyle == CueStyle.GOLDEN_DRAGON) {
                                            val bandStart = Offset(cueScreenX + nx * (visualBallRadius + stickRecoil + 60f), cueScreenY + ny * (visualBallRadius + stickRecoil + 60f))
                                            val bandEnd = Offset(cueScreenX + nx * (visualBallRadius + stickRecoil + 68f), cueScreenY + ny * (visualBallRadius + stickRecoil + 68f))
                                            drawLine(
                                                color = Color(0xFFFBBF24),
                                                start = bandStart,
                                                end = bandEnd,
                                                strokeWidth = stickThickness + 0.5f
                                            )
                                        }
                                    }
                                }
                            }

                            // 11. LAYER 11: BOT AIMING MECHANICS (Dashed lines and styled cue stick for the bot)
                            if (!engine.isPlayerTurn && engine.botShowCue && engine.gameState == GameState.PLAYING) {
                                val cueBall = engine.balls.find { it.isCueBall }
                                if (cueBall != null) {
                                    val cueScreenX = tableLeft + (cueBall.x / 1000f) * tableWidth
                                    val cueScreenY = tableTop + (cueBall.y / 2000f) * tableHeight

                                    val nx = engine.botAimDx
                                    val ny = engine.botAimDy
                                    val aimLength = engine.botAimLength

                                    if (aimLength > 0f) {
                                        val aimStart = Offset(cueScreenX - nx * visualBallRadius, cueScreenY - ny * visualBallRadius)
                                        val aimEnd = Offset(cueScreenX - nx * (visualBallRadius + aimLength), cueScreenY - ny * (visualBallRadius + aimLength))

                                        drawLine(
                                            color = Color(0xBB06B6D4), 
                                            start = aimStart,
                                            end = aimEnd,
                                            strokeWidth = 5f,
                                            cap = StrokeCap.Round,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                                        )

                                        drawCircle(
                                            color = Color(0xBB06B6D4),
                                            radius = 12f,
                                            center = aimEnd,
                                            style = Stroke(width = 3f)
                                        )
                                    }

                                    // CUSTOM SELECTED CUE STICK DESIGN FOR BOT
                                    val cueStyle = engine.selectedCueStyle
                                    val stickRecoil = engine.botCueRecoil

                                    val stickLength: Float
                                    val stickThickness: Float
                                    val shaftColor: Color
                                    val gripColor: Color
                                    val tipColor: Color

                                    when (cueStyle) {
                                        CueStyle.CLASSIC_MAHOGANY -> {
                                            stickLength = 360f
                                            stickThickness = 14f
                                            shaftColor = Color(0xFF78350F) // mahogany brown
                                            gripColor = Color(0xFF1E293B)  // slate gray wrap
                                            tipColor = Color(0xFFF1F5F9)   // ivory white
                                        }
                                        CueStyle.GOLDEN_DRAGON -> {
                                            stickLength = 440f // Longer
                                            stickThickness = 18f // Thicker
                                            shaftColor = Color(0xFFD97706) // gold amber
                                            gripColor = Color(0xFF991B1B)  // ruby red wrap
                                            tipColor = Color(0xFFFEF08A)   // gold cap tip
                                        }
                                        CueStyle.STEALTH_CARBON -> {
                                            stickLength = 290f // Shorter
                                            stickThickness = 16f // Thicker
                                            shaftColor = Color(0xFF334155) // carbon slate
                                            gripColor = Color(0xFF22D3EE)  // cyan wrap
                                            tipColor = Color(0xFF06B6D4)   // cyan glowing tip
                                        }
                                    }

                                    val stickStart = Offset(cueScreenX + nx * (visualBallRadius + stickRecoil), cueScreenY + ny * (visualBallRadius + stickRecoil))
                                    val stickEnd = Offset(cueScreenX + nx * (visualBallRadius + stickRecoil + stickLength), cueScreenY + ny * (visualBallRadius + stickRecoil + stickLength))

                                    // Draw Shaft
                                    drawLine(
                                        color = shaftColor,
                                        start = stickStart,
                                        end = stickEnd,
                                        strokeWidth = stickThickness,
                                        cap = StrokeCap.Round
                                    )
                                    // Draw Grip
                                    val gripStart = Offset(cueScreenX + nx * (visualBallRadius + stickRecoil + stickLength * 0.4f), cueScreenY + ny * (visualBallRadius + stickRecoil + stickLength * 0.4f))
                                    drawLine(
                                        color = gripColor,
                                        start = gripStart,
                                        end = stickEnd,
                                        strokeWidth = stickThickness + 1.5f,
                                        cap = StrokeCap.Round
                                    )
                                    // Draw Ivory Joint Tip
                                    val tipEnd = Offset(cueScreenX + nx * (visualBallRadius + stickRecoil + 15f), cueScreenY + ny * (visualBallRadius + stickRecoil + 15f))
                                    drawLine(
                                        color = tipColor,
                                        start = stickStart,
                                        end = tipEnd,
                                        strokeWidth = stickThickness - 1f,
                                        cap = StrokeCap.Round
                                    )

                                    // Ornament details for Golden Dragon pro cue
                                    if (cueStyle == CueStyle.GOLDEN_DRAGON) {
                                        val bandStart = Offset(cueScreenX + nx * (visualBallRadius + stickRecoil + 60f), cueScreenY + ny * (visualBallRadius + stickRecoil + 60f))
                                        val bandEnd = Offset(cueScreenX + nx * (visualBallRadius + stickRecoil + 68f), cueScreenY + ny * (visualBallRadius + stickRecoil + 68f))
                                        drawLine(
                                            color = Color(0xFFFBBF24),
                                            start = bandStart,
                                            end = bandEnd,
                                            strokeWidth = stickThickness + 0.5f
                                        )
                                    }
                                }
                            }
                        }
                    }

                    EventLogTicker(engine = engine)
                }

                if (engine.gameState == GameState.GAME_OVER) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.75f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(320.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFF1E293B))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                                .padding(28.dp)
                        ) {
                            Text(
                                text = "GAME OVER",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF4444)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = engine.winnerMessage ?: "Game finished.",
                                fontSize = 16.sp,
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(28.dp))
                            Button(
                                onClick = { engine.gameState = GameState.MENU },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF06B6D4)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Return to Menu", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameModeButton(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = Color(0xFF334155),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF22D3EE).copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF22D3EE)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun GameHeaderPanel(
    engine: GameEngine,
    isCueSelectorVisible: Boolean,
    onToggleCueSelector: () -> Unit
) {
    val remainingObjects = engine.balls.count { !it.isCueBall && it.id != 3 }
    val isSimulationRunning = engine.isSimulationRunning

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .shadow(4.dp),
        color = Color(0xFF1E293B),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { engine.gameState = GameState.MENU },
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color(0xFF334155), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Menu",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val turnText: String
                    val turnColor: Color

                    if (engine.currentMode == GameMode.PRACTICE) {
                        turnText = "PRACTICE MODE"
                        turnColor = Color(0xFFEAB308)
                    } else if (engine.currentMode == GameMode.PLAYER_VS_BOT) {
                        if (engine.isPlayerTurn) {
                            turnText = "YOUR TURN"
                            turnColor = Color(0xFF22D3EE)
                        } else {
                            turnText = "BOT THINKING..."
                            turnColor = Color(0xFFEF4444)
                        }
                    } else {
                        if (engine.isPlayerTurn) {
                            turnText = "PLAYER 1 TURN"
                            turnColor = Color(0xFF22D3EE)
                        } else {
                            turnText = "PLAYER 2 TURN"
                            turnColor = Color(0xFFA855F7)
                        }
                    }

                    Text(
                        text = turnText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = turnColor
                    )

                    Text(
                        text = if (isSimulationRunning) "Balls rolling..." else "Drag screen to shoot",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onToggleCueSelector,
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (isCueSelectorVisible) Color(0xFF22D3EE).copy(alpha = 0.2f) else Color(0xFF334155),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Customize Cue",
                            tint = if (isCueSelectorVisible) Color(0xFF22D3EE) else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { engine.resetGame(engine.currentMode) },
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFF334155), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restart Game",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEAB308))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Object Left: $remainingObjects",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                val has8Ball = engine.balls.any { it.id == 3 }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (has8Ball) Color.Black else Color.Black.copy(alpha = 0.2f))
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (has8Ball) "8-Ball: Active" else "8-Ball: Pocketed",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun CueSelectorPanel(engine: GameEngine, onSelected: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "SELECT CUE STICK STYLE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CueStyleOption(
                    name = "Classic",
                    style = CueStyle.CLASSIC_MAHOGANY,
                    isSelected = engine.selectedCueStyle == CueStyle.CLASSIC_MAHOGANY,
                    onClick = { 
                        engine.selectedCueStyle = CueStyle.CLASSIC_MAHOGANY 
                        onSelected()
                    },
                    color = Color(0xFF78350F),
                    modifier = Modifier.weight(1f)
                )
                CueStyleOption(
                    name = "Golden Pro",
                    style = CueStyle.GOLDEN_DRAGON,
                    isSelected = engine.selectedCueStyle == CueStyle.GOLDEN_DRAGON,
                    onClick = { 
                        engine.selectedCueStyle = CueStyle.GOLDEN_DRAGON 
                        onSelected()
                    },
                    color = Color(0xFFD97706),
                    modifier = Modifier.weight(1f)
                )
                CueStyleOption(
                    name = "Carbon Stealth",
                    style = CueStyle.STEALTH_CARBON,
                    isSelected = engine.selectedCueStyle == CueStyle.STEALTH_CARBON,
                    color = Color(0xFF334155),
                    onClick = { 
                        engine.selectedCueStyle = CueStyle.STEALTH_CARBON 
                        onSelected()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun CueStyleOption(
    name: String,
    style: CueStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        color = if (isSelected) Color(0xFF0F172A) else Color(0xFF334155),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color(0xFF22D3EE) else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = name,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) Color(0xFF22D3EE) else Color.White
            )
        }
    }
}

@Composable
fun EventLogTicker(engine: GameEngine) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .shadow(6.dp),
        color = Color(0xFF0F172A),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = "MATCH LOGS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(bottom = 6.dp)
            )

            if (engine.eventLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Logs will appear here once the match starts",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.25f),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(engine.eventLogs) { log ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "•",
                                color = Color(0xFF22D3EE),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = log,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
