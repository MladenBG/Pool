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
                            title = "Play Online",
                            description = "Play 1v1 multiplayer online with other players",
                            onClick = { engine.resetGame(GameMode.ONLINE_MULTIPLAYER) }
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

                                            val maxTableWidth = size.width - 260f
                                            val maxTableHeight = size.height - 560f
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

                            val maxTableWidth = canvasWidth - 260f
                            val maxTableHeight = canvasHeight - 560f

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

                            val drawCueStick: DrawScope.(Float, Float, Float, Float, Float) -> Unit = { csX, csY, dx, dy, recoil ->
                                val u = Offset(dx, dy)
                                val v = Offset(-dy, dx)
                                val cueStyle = engine.selectedCueStyle
                                val scale = tableWidth / 1000f

                                val stickLength = when (cueStyle) {
                                     CueStyle.CLASSIC_MAHOGANY -> 550f * scale
                                     CueStyle.GOLDEN_DRAGON -> 620f * scale
                                     CueStyle.STEALTH_CARBON -> 480f * scale
                                }

                                // Define segment points along direction u (all scaled proportionally to tableWidth)
                                val p0 = Offset(csX, csY) + u * (visualBallRadius + recoil)
                                val p1 = p0 + u * 8f * scale   // blue tip
                                val p2 = p1 + u * 14f * scale  // ferrule
                                val p3 = p2 + u * (stickLength * 0.45f) // shaft
                                val p4 = p3 + u * 10f * scale  // joint
                                val p5 = p4 + u * (stickLength * 0.2f)  // forearm
                                val p6 = p5 + u * (stickLength * 0.28f) // grip
                                val p7 = p6 + u * 13f * scale  // butt cap

                                // Define half-widths at each point (proportional to table width for responsiveness)
                                val w0 = 3f * scale
                                val w1 = 3.2f * scale
                                val w2 = 3.6f * scale
                                val w3 = 6.5f * scale
                                val w4 = 7.0f * scale
                                val w5 = 8.5f * scale
                                val w6 = 9.8f * scale
                                val w7 = 10.5f * scale

                                // Helper to draw a tapered segment
                                fun DrawScope.drawSegment(ptA: Offset, ptB: Offset, widthA: Float, widthB: Float, brush: Brush) {
                                    val path = Path().apply {
                                        moveTo(ptA.x - v.x * widthA, ptA.y - v.y * widthA)
                                        lineTo(ptB.x - v.x * widthB, ptB.y - v.y * widthB)
                                        lineTo(ptB.x + v.x * widthB, ptB.y + v.y * widthB)
                                        lineTo(ptA.x + v.x * widthA, ptA.y + v.y * widthA)
                                        close()
                                    }
                                    drawPath(path = path, brush = brush)
                                }

                                // 1. Draw Drop Shadow first (shifted by offset)
                                val shadowOffset = Offset(6f * scale, 10f * scale)
                                val shadowBrush = SolidColor(Color.Black.copy(alpha = 0.35f))
                                drawSegment(p0 + shadowOffset, p1 + shadowOffset, w0, w1, shadowBrush)
                                drawSegment(p1 + shadowOffset, p2 + shadowOffset, w1, w2, shadowBrush)
                                drawSegment(p2 + shadowOffset, p3 + shadowOffset, w2, w3, shadowBrush)
                                drawSegment(p3 + shadowOffset, p4 + shadowOffset, w3, w4, shadowBrush)
                                drawSegment(p4 + shadowOffset, p5 + shadowOffset, w4, w5, shadowBrush)
                                drawSegment(p5 + shadowOffset, p6 + shadowOffset, w5, w6, shadowBrush)
                                drawSegment(p6 + shadowOffset, p7 + shadowOffset, w6, w7, shadowBrush)

                                // 2. Define colors & gradients based on style
                                val tipBrush: Brush
                                val ferruleBrush: Brush
                                val shaftBrush: Brush
                                val jointBrush: Brush
                                val forearmBrush: Brush
                                val gripBrush: Brush
                                val buttBrush: Brush

                                when (cueStyle) {
                                    CueStyle.CLASSIC_MAHOGANY -> {
                                        tipBrush = SolidColor(Color(0xFF3B82F6)) // blue chalk
                                        ferruleBrush = SolidColor(Color(0xFFF1F5F9)) // ivory white
                                        shaftBrush = Brush.linearGradient(
                                            colors = listOf(Color(0xFFFEF08A), Color(0xFFFDE047), Color(0xFFFEF08A)),
                                            start = p1, end = p3
                                        ) // maple wood look
                                        jointBrush = Brush.linearGradient(
                                            colors = listOf(Color(0xFFB45309), Color(0xFFFDE047), Color(0xFFB45309)),
                                            start = p3, end = p4
                                        ) // brass joint
                                        forearmBrush = SolidColor(Color(0xFF78350F)) // mahogany wood
                                        gripBrush = SolidColor(Color(0xFF1E293B)) // slate wrap
                                        buttBrush = SolidColor(Color(0xFFF1F5F9)) // ivory cap
                                    }
                                    CueStyle.GOLDEN_DRAGON -> {
                                        tipBrush = SolidColor(Color(0xFF1E3A8A)) // dark royal blue
                                        ferruleBrush = SolidColor(Color(0xFFF8FAFC)) // premium ivory
                                        shaftBrush = Brush.linearGradient(
                                            colors = listOf(Color(0xFFFBBF24), Color(0xFFD97706), Color(0xFFFBBF24)),
                                            start = p1, end = p3
                                        ) // golden maple
                                        jointBrush = Brush.linearGradient(
                                            colors = listOf(Color(0xFFFEF08A), Color(0xFFCA8A04), Color(0xFFFEF08A)),
                                            start = p3, end = p4
                                        ) // shiny polished gold Joint
                                        forearmBrush = Brush.linearGradient(
                                            colors = listOf(Color(0xFF991B1B), Color(0xFF7F1D1D), Color(0xFF991B1B)),
                                            start = p4, end = p5
                                        ) // ruby red forearm
                                        gripBrush = Brush.linearGradient(
                                            colors = listOf(Color(0xFFD97706), Color(0xFF991B1B), Color(0xFFD97706)),
                                            start = p5, end = p6
                                        ) // gold/red luxury wrap
                                        buttBrush = Brush.linearGradient(
                                            colors = listOf(Color(0xFFFEF08A), Color(0xFFCA8A04), Color(0xFFFEF08A)),
                                            start = p6, end = p7
                                        ) // gold butt
                                    }
                                    CueStyle.STEALTH_CARBON -> {
                                        tipBrush = SolidColor(Color(0xFF22D3EE)) // glowing cyan tip
                                        ferruleBrush = SolidColor(Color(0xFF0F172A)) // carbon ferrule
                                        shaftBrush = Brush.linearGradient(
                                            colors = listOf(Color(0xFF334155), Color(0xFF1E293B), Color(0xFF334155)),
                                            start = p1, end = p3
                                        ) // matte dark carbon
                                        jointBrush = SolidColor(Color(0xFF06B6D4)) // electric cyan joint ring
                                        forearmBrush = SolidColor(Color(0xFF1E293B)) // dark forearm
                                        gripBrush = Brush.linearGradient(
                                            colors = listOf(Color(0xFF06B6D4), Color(0xFF0891B2), Color(0xFF06B6D4)),
                                            start = p5, end = p6
                                        ) // cyan grip wrap
                                        buttBrush = SolidColor(Color(0xFF0F172A)) // dark butt cap
                                    }
                                }

                                // 3. Draw actual segments
                                drawSegment(p0, p1, w0, w1, tipBrush)
                                drawSegment(p1, p2, w1, w2, ferruleBrush)
                                drawSegment(p2, p3, w2, w3, shaftBrush)
                                drawSegment(p3, p4, w3, w4, jointBrush)
                                drawSegment(p4, p5, w4, w5, forearmBrush)
                                drawSegment(p5, p6, w5, w6, gripBrush)
                                drawSegment(p6, p7, w6, w7, buttBrush)
                            }

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
                                val apexY = tableTop + (580f / 2000f) * tableHeight
                                
                                val bottomLeftX = tableLeft + (320f / 1000f) * tableWidth
                                val bottomLeftY = tableTop + (220f / 2000f) * tableHeight
                                
                                val bottomRightX = tableLeft + (680f / 1000f) * tableWidth
                                val bottomRightY = tableTop + (220f / 2000f) * tableHeight

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
                                if (ball.id in 9..15) {
                                    // Stripe Ball base: White shaded sphere
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color.White,
                                                Color(0xFFE2E8F0),
                                                Color(0xFFCBD5E1),
                                                Color.Black.copy(alpha = 0.8f)
                                            ),
                                            center = Offset(screenX - visualBallRadius * 0.35f, screenY - visualBallRadius * 0.35f),
                                            radius = visualBallRadius * 1.35f
                                        ),
                                        radius = visualBallRadius,
                                        center = Offset(screenX, screenY)
                                    )
                                    
                                    // Draw color stripe band using a clipPath to keep it circular
                                    val stripePath = Path().apply {
                                        addOval(androidx.compose.ui.geometry.Rect(screenX - visualBallRadius, screenY - visualBallRadius, screenX + visualBallRadius, screenY + visualBallRadius))
                                    }
                                    drawContext.canvas.save()
                                    drawContext.canvas.clipPath(stripePath)
                                    val stripeHeight = visualBallRadius * 1.1f
                                    val stripeGradient = Brush.radialGradient(
                                        colors = listOf(
                                            Color(ball.color),
                                            Color(ball.color).copy(alpha = 0.75f),
                                            Color.Black.copy(alpha = 0.9f)
                                        ),
                                        center = Offset(screenX - visualBallRadius * 0.35f, screenY - visualBallRadius * 0.35f),
                                        radius = visualBallRadius * 1.35f
                                    )
                                    drawRect(
                                        brush = stripeGradient,
                                        topLeft = Offset(screenX - visualBallRadius, screenY - stripeHeight / 2),
                                        size = Size(visualBallRadius * 2, stripeHeight)
                                    )
                                    drawContext.canvas.restore()
                                } else {
                                    // Solid / Cue / Black Ball: draw standard shaded sphere
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
                                }

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

                            // 10. LAYER 10: AIMING MECHANICS (Styled cue stick)
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

                                        val stickRecoil = 15f + dragLength * 0.22f
                                        drawCueStick(cueScreenX, cueScreenY, nx, ny, stickRecoil)
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

                                    val stickRecoil = engine.botCueRecoil
                                    drawCueStick(cueScreenX, cueScreenY, nx, ny, stickRecoil)
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
    val remainingObjects = engine.balls.count { !it.isCueBall && it.id != 8 }
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

                    if (engine.currentMode == GameMode.ONLINE_MULTIPLAYER) {
                        if (engine.onlineStatus != null && !engine.onlineStatus!!.startsWith("Matched")) {
                            turnText = engine.onlineStatus!!.uppercase()
                            turnColor = Color(0xFFEAB308)
                        } else {
                            if (engine.isPlayerTurn) {
                                turnText = "YOUR TURN"
                                turnColor = Color(0xFF22D3EE)
                            } else {
                                turnText = "OPPONENT'S TURN"
                                turnColor = Color(0xFFEF4444)
                            }
                        }
                    } else if (engine.currentMode == GameMode.PRACTICE) {
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

                val has8Ball = engine.balls.any { it.id == 8 }
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

            if (engine.currentMode != GameMode.PRACTICE) {
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val p1Label = if (engine.currentMode == GameMode.PLAYER_VS_BOT) {
                        "You"
                    } else if (engine.currentMode == GameMode.ONLINE_MULTIPLAYER) {
                        if (engine.myRole == "P1") "You (P1)" else "Opponent (P1)"
                    } else {
                        "Player 1"
                    }
                    val p2Label = if (engine.currentMode == GameMode.PLAYER_VS_BOT) {
                        "Bot"
                    } else if (engine.currentMode == GameMode.ONLINE_MULTIPLAYER) {
                        if (engine.myRole == "P2") "You (P2)" else "Opponent (P2)"
                    } else {
                        "Player 2"
                    }
                    
                    if (engine.player1Group != null) {
                        Text(
                            text = "$p1Label: ${engine.player1Group.toString().lowercase().replaceFirstChar { it.uppercase() }}s",
                            fontSize = 11.sp,
                            color = Color(0xFF22D3EE),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$p2Label: ${engine.player2Group.toString().lowercase().replaceFirstChar { it.uppercase() }}s",
                            fontSize = 11.sp,
                            color = Color(0xFFA855F7),
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Table is Open",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.4f),
                                fontWeight = FontWeight.Medium,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
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
