package com.magics.pool8

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

enum class GameMode {
    PLAYER_VS_BOT,
    PLAYER_VS_PLAYER,
    PRACTICE,
    ONLINE_MULTIPLAYER
}

enum class GameState {
    MENU,
    PLAYING,
    GAME_OVER
}

enum class CueStyle {
    CLASSIC_MAHOGANY,
    GOLDEN_DRAGON,
    STEALTH_CARBON
}

enum class BallGroup {
    SOLID,
    STRIPE,
    BLACK,
    CUE
}

data class PoolBall(
    var id: Int,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var color: Long,
    var isCueBall: Boolean = false
)

// PROCEDURAL AUDIO SYNTHESIZER
object SoundManager {
    fun playCollisionSound(intensity: Float) {
        val sampleRate = 22050
        val durationMs = 35
        val numSamples = (sampleRate * (durationMs / 1000f)).toInt()
        val buffer = ShortArray(numSamples)

        val volume = Math.min(1.0f, Math.max(0.12f, intensity))

        for (i in 0 until numSamples) {
            val t = i.toFloat() / sampleRate
            val frequency = 1750f
            // Exponential decay to simulate solid phenolic billiard ball collision
            val decay = Math.exp(-t * 220.0) 
            val sine = Math.sin(2.0 * Math.PI * frequency * t)
            val noise = (Math.random() * 2.0 - 1.0) * 0.08 // Transient hit click noise

            val sampleVal = ((sine + noise) * decay * 32767.0 * volume).toInt()
            buffer[i] = Math.min(32767, Math.max(-32768, sampleVal)).toShort()
        }

        // Run procedurally synthesized audio inside static buffer track
        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()
            
            // Release the track after playing
            CoroutineScope(Dispatchers.IO).launch {
                delay(100L)
                try {
                    audioTrack.stop()
                    audioTrack.release()
                } catch (ignored: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

class GameEngine {

    val balls = mutableStateListOf<PoolBall>()
    val eventLogs = mutableStateListOf<String>()

    // Solids vs Stripes properties
    var player1Group by mutableStateOf<BallGroup?>(null)
    var player2Group by mutableStateOf<BallGroup?>(null)
    val pocketedGroupsThisTurn = mutableStateListOf<BallGroup>()

    // Online Multiplayer properties
    var onlineStatus by mutableStateOf<String?>(null)
    var myRole by mutableStateOf<String?>(null) // "P1" or "P2"
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private var wasMyShot = false

    fun getBallGroup(ballId: Int): BallGroup {
        return when (ballId) {
            0 -> BallGroup.CUE
            8 -> BallGroup.BLACK
            in 1..7 -> BallGroup.SOLID
            in 9..15 -> BallGroup.STRIPE
            else -> BallGroup.SOLID
        }
    }

    var currentMode by mutableStateOf(GameMode.PLAYER_VS_BOT)
    var gameState by mutableStateOf(GameState.MENU)
    var isPlayerTurn by mutableStateOf(true)
    var winnerMessage by mutableStateOf<String?>(null)
    var isSimulationRunning by mutableStateOf(false)
    var selectedCueStyle by mutableStateOf(CueStyle.CLASSIC_MAHOGANY)
    var showAimHelper by mutableStateOf(true)
    var isBreakShot by mutableStateOf(true)

    // Bot Strike Animation properties
    var botShowCue by mutableStateOf(false)
    var botAimDx by mutableStateOf(0f)
    var botAimDy by mutableStateOf(0f)
    var botCueRecoil by mutableStateOf(15f)
    var botAimLength by mutableStateOf(0f)

    private var wasCueBallPocketedThisTurn = false
    private var objectBallsPocketedThisTurn = 0
    private var is8BallPocketedThisTurn = false

    private var physicsJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    init {
        System.loadLibrary("magicspool")
        startPhysicsLoop()
    }

    fun resetGame(mode: GameMode) {
        currentMode = mode
        gameState = GameState.PLAYING
        isPlayerTurn = true
        winnerMessage = null
        isSimulationRunning = false
        isBreakShot = true
        wasCueBallPocketedThisTurn = false
        objectBallsPocketedThisTurn = 0
        is8BallPocketedThisTurn = false
        player1Group = null
        player2Group = null
        pocketedGroupsThisTurn.clear()
        eventLogs.clear()
        addLog("Match Started: ${mode.name.replace("_", " ")}")
        resetBotAnimationState()
        
        disconnectWebSocket()
        if (mode == GameMode.ONLINE_MULTIPLAYER) {
            connectWebSocket()
        } else {
            setupRack()
        }
    }

    fun exitToMenu() {
        gameState = GameState.MENU
        disconnectWebSocket()
        balls.clear()
        eventLogs.clear()
        onlineStatus = null
        myRole = null
        winnerMessage = null
        isSimulationRunning = false
        isBreakShot = true
        player1Group = null
        player2Group = null
        pocketedGroupsThisTurn.clear()
        resetBotAnimationState()
    }

    private fun connectWebSocket() {
        onlineStatus = "Connecting to matchmaking server..."
        val request = Request.Builder()
            .url("ws://10.0.2.2:8080") // Local server IP
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                scope.launch(Dispatchers.Main) {
                    onlineStatus = "Connected! Waiting for opponent..."
                    addLog("Connected to server. Matching...")
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch(Dispatchers.Main) {
                    try {
                        val json = JSONObject(text)
                        val type = json.optString("type")
                        when (type) {
                            "waiting" -> {
                                onlineStatus = "Waiting for opponent..."
                            }
                            "start" -> {
                                myRole = json.getString("role")
                                isPlayerTurn = json.getBoolean("turn")
                                onlineStatus = "Matched! You are $myRole"
                                addLog("Matched! You are $myRole. ${if (isPlayerTurn) "Your turn." else "Opponent's turn."}")
                                setupRack()
                            }
                            "strike" -> {
                                val vx = json.getDouble("vx").toFloat()
                                val vy = json.getDouble("vy").toFloat()
                                applyReceivedStrike(vx, vy)
                            }
                            "sync" -> {
                                val isOpponentNextTurn = json.getBoolean("isPlayerTurn")
                                val p1GroupStr = json.optString("player1Group", "null")
                                val p2GroupStr = json.optString("player2Group", "null")
                                
                                player1Group = if (p1GroupStr == "null" || p1GroupStr.isEmpty()) null else BallGroup.valueOf(p1GroupStr)
                                player2Group = if (p2GroupStr == "null" || p2GroupStr.isEmpty()) null else BallGroup.valueOf(p2GroupStr)

                                val gameStateStr = json.optString("gameState", "PLAYING")
                                gameState = GameState.valueOf(gameStateStr)
                                val winnerMsgStr = json.optString("winnerMessage", "null")
                                winnerMessage = if (winnerMsgStr == "null") null else winnerMsgStr

                                val ballsArray = json.getJSONArray("balls")
                                val updatedBalls = mutableListOf<PoolBall>()
                                for (i in 0 until ballsArray.length()) {
                                    val bJson = ballsArray.getJSONObject(i)
                                    val id = bJson.getInt("id")
                                    val x = bJson.optDouble("x", 500.0).toFloat()
                                    val y = bJson.optDouble("y", 1500.0).toFloat()
                                    val vx = bJson.optDouble("vx", 0.0).toFloat()
                                    val vy = bJson.optDouble("vy", 0.0).toFloat()
                                    val color = bJson.getLong("color")
                                    val isCueBall = bJson.getBoolean("isCueBall")
                                    updatedBalls.add(PoolBall(id, x, y, vx, vy, color, isCueBall))
                                }

                                balls.clear()
                                balls.addAll(updatedBalls)

                                isPlayerTurn = if (myRole == "P1") isOpponentNextTurn else !isOpponentNextTurn
                                addLog("Opponent settled turn. Your turn!")
                                isSimulationRunning = false
                            }
                            "game_canceled" -> {
                                winnerMessage = "Game Canceled: Opponent left!"
                                gameState = GameState.GAME_OVER
                                addLog("Game Canceled: Opponent left!")
                                disconnectWebSocket()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch(Dispatchers.Main) {
                    onlineStatus = "Connection Failed!"
                    addLog("Network Error: Could not connect to server.")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                scope.launch(Dispatchers.Main) {
                    addLog("Connection closed.")
                }
            }
        })
    }

    private fun disconnectWebSocket() {
        webSocket?.close(1000, "Goodbye")
        webSocket = null
        myRole = null
        onlineStatus = null
    }

    private fun applyReceivedStrike(vx: Float, vy: Float) {
        val cueBallIndex = balls.indexOfFirst { it.isCueBall }
        if (cueBallIndex != -1) {
            pocketedGroupsThisTurn.clear()
            addLog("Opponent strikes the cue ball!")
            balls[cueBallIndex] = balls[cueBallIndex].copy(
                vx = vx,
                vy = vy
            )
            isSimulationRunning = true
            isBreakShot = false
            wasMyShot = false
            wasCueBallPocketedThisTurn = false
            objectBallsPocketedThisTurn = 0
            is8BallPocketedThisTurn = false
            SoundManager.playCollisionSound(0.7f)
        }
    }

    private fun addLog(msg: String) {
        eventLogs.add(0, msg)
        if (eventLogs.size > 8) {
            eventLogs.removeLast()
        }
    }

    private fun setupRack() {
        balls.clear()
        // White Cue Ball
        balls.add(PoolBall(0, 500f, 1500f, 0f, 0f, 0xFFFFFFFF, true))

        // Standard 15-Ball Triangle Rack
        // Row 1 (Apex):
        balls.add(PoolBall(1, 500f, 500f, 0f, 0f, 0xFFEAB308)) // Solid Yellow
        
        // Row 2:
        balls.add(PoolBall(2, 470f, 440f, 0f, 0f, 0xFF3B82F6)) // Solid Blue
        balls.add(PoolBall(9, 530f, 440f, 0f, 0f, 0xFFEAB308)) // Stripe Yellow
        
        // Row 3:
        balls.add(PoolBall(10, 440f, 380f, 0f, 0f, 0xFF3B82F6)) // Stripe Blue
        balls.add(PoolBall(8, 500f, 380f, 0f, 0f, 0xFF000000)) // Black 8-Ball
        balls.add(PoolBall(3, 560f, 380f, 0f, 0f, 0xFFEF4444)) // Solid Red
        
        // Row 4:
        balls.add(PoolBall(11, 410f, 320f, 0f, 0f, 0xFFEF4444)) // Stripe Red
        balls.add(PoolBall(4, 470f, 320f, 0f, 0f, 0xFF8B5CF6)) // Solid Purple
        balls.add(PoolBall(12, 530f, 320f, 0f, 0f, 0xFF8B5CF6)) // Stripe Purple
        balls.add(PoolBall(5, 590f, 320f, 0f, 0f, 0xFFF97316)) // Solid Orange
        
        // Row 5:
        balls.add(PoolBall(6, 380f, 260f, 0f, 0f, 0xFF22C55E)) // Solid Green
        balls.add(PoolBall(13, 440f, 260f, 0f, 0f, 0xFFF97316)) // Stripe Orange
        balls.add(PoolBall(7, 500f, 260f, 0f, 0f, 0xFFB45309)) // Solid Maroon/Brown
        balls.add(PoolBall(14, 560f, 260f, 0f, 0f, 0xFF22C55E)) // Stripe Green
        balls.add(PoolBall(15, 620f, 260f, 0f, 0f, 0xFFB45309)) // Stripe Maroon/Brown
    }

    private fun startPhysicsLoop() {
        physicsJob = scope.launch {
            while (isActive) {
                if (!isSimulationRunning) {
                    delay(16L)
                    continue
                }
                val size = balls.size
                if (size == 0) {
                    delay(16L)
                    continue
                }

                val xArray = FloatArray(size) { balls[it].x }
                val yArray = FloatArray(size) { balls[it].y }
                val vxArray = FloatArray(size) { balls[it].vx }
                val vyArray = FloatArray(size) { balls[it].vy }

                val oldVxArray = vxArray.clone()
                val oldVyArray = vyArray.clone()

                stepPhysics(xArray, yArray, vxArray, vyArray, size, 1.0f)

                // Detect velocity changes to synthesize click sounds
                var playedSound = false
                for (i in 0 until size) {
                    val dvx = vxArray[i] - oldVxArray[i]
                    val dvy = vyArray[i] - oldVyArray[i]
                    val change = Math.sqrt((dvx * dvx + dvy * dvy).toDouble()).toFloat()
                    if (change > 2.0f && !playedSound) {
                        SoundManager.playCollisionSound(change / 25f)
                        playedSound = true
                    }
                }

                var ballsMoving = false
                val updatedBalls = mutableListOf<PoolBall>()
                var cueBallScratched = false
                var objectBallsPocketed = 0
                var is8BallPocketed = false
                val localPocketedGroups = mutableListOf<BallGroup>()

                val pockets = listOf(
                    Pair(0f, 0f), Pair(1000f, 0f), // Corners top
                    Pair(0f, 1000f), Pair(1000f, 1000f), // Sides middle
                    Pair(0f, 2000f), Pair(1000f, 2000f) // Corners bottom
                )
                val pocketRadius = 55f

                for (i in 0 until size) {
                    val originalBall = balls.getOrNull(i) ?: continue
                    var vx = vxArray[i]
                    var vy = vyArray[i]
                    var x = xArray[i]
                    var y = yArray[i]

                    // Dynamic threshold to stop slow sliding
                    if (Math.abs(vx) < 0.15f) vx = 0f
                    if (Math.abs(vy) < 0.15f) vy = 0f

                    if (vx != 0f || vy != 0f) {
                        ballsMoving = true
                    }

                    // Pocket calculations
                    var isPocketed = false
                    for (pocket in pockets) {
                        val dx = x - pocket.first
                        val dy = y - pocket.second
                        if (dx * dx + dy * dy < pocketRadius * pocketRadius) {
                            isPocketed = true
                            break
                        }
                    }

                    if (isPocketed) {
                        if (originalBall.isCueBall) {
                            cueBallScratched = true
                        } else if (originalBall.id == 8) {
                            is8BallPocketed = true
                        } else {
                            objectBallsPocketed++
                            localPocketedGroups.add(getBallGroup(originalBall.id))
                        }
                    } else {
                        updatedBalls.add(originalBall.copy(x = x, y = y, vx = vx, vy = vy))
                    }
                }

                if (cueBallScratched) {
                    val cueBall = balls.find { it.isCueBall } ?: PoolBall(0, 500f, 1500f, 0f, 0f, 0xFFFFFFFF, true)
                    updatedBalls.add(cueBall.copy(x = 500f, y = 1500f, vx = 0f, vy = 0f))
                }

                withContext(Dispatchers.Main) {
                    balls.clear()
                    balls.addAll(updatedBalls)

                    if (cueBallScratched) {
                        wasCueBallPocketedThisTurn = true
                        addLog("Foul: Cue Ball Scratched!")
                    }
                    if (objectBallsPocketed > 0) {
                        objectBallsPocketedThisTurn += objectBallsPocketed
                        pocketedGroupsThisTurn.addAll(localPocketedGroups)
                        addLog("Pocketed $objectBallsPocketed object ball(s)!")
                    }
                    if (is8BallPocketed) {
                        is8BallPocketedThisTurn = true
                        addLog("Black 8-Ball Pocketed!")
                    }

                    if (isSimulationRunning && !ballsMoving) {
                        isSimulationRunning = false
                        if (currentMode == GameMode.ONLINE_MULTIPLAYER) {
                            if (wasMyShot) {
                                handleTurnTransition()
                                // Send synchronization packet to opponent
                                try {
                                    val syncObj = JSONObject()
                                    syncObj.put("type", "sync")
                                    syncObj.put("player1Group", player1Group?.name ?: "null")
                                    syncObj.put("player2Group", player2Group?.name ?: "null")
                                    syncObj.put("gameState", gameState.name)
                                    syncObj.put("winnerMessage", winnerMessage ?: "null")

                                    val nextP1Turn = if (myRole == "P1") isPlayerTurn else !isPlayerTurn
                                    syncObj.put("isPlayerTurn", nextP1Turn)

                                    val ballsArray = JSONArray()
                                    for (ball in balls) {
                                        val bJson = JSONObject()
                                        bJson.put("id", ball.id)
                                        bJson.put("x", ball.x)
                                        bJson.put("y", ball.y)
                                        bJson.put("vx", ball.vx)
                                        bJson.put("vy", ball.vy)
                                        bJson.put("color", ball.color)
                                        bJson.put("isCueBall", ball.isCueBall)
                                        ballsArray.put(bJson)
                                    }
                                    syncObj.put("balls", ballsArray)
                                    webSocket?.send(syncObj.toString())
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } else {
                                // We didn't shoot, so just stop simulation and wait for the sync packet to correct ball positions
                            }
                        } else {
                            handleTurnTransition()
                        }
                    } else if (ballsMoving) {
                        isSimulationRunning = true
                    }
                }

                delay(16L) // ~60fps
            }
        }
    }

    private fun handleTurnTransition() {
        val remainingObjectBalls = balls.count { !it.isCueBall && it.id != 8 }

        // 1. Assign groups if table was open and a group ball was pocketed
        if (currentMode != GameMode.PRACTICE && player1Group == null) {
            val firstPocketed = pocketedGroupsThisTurn.firstOrNull { it == BallGroup.SOLID || it == BallGroup.STRIPE }
            if (firstPocketed != null) {
                if (isPlayerTurn) {
                    player1Group = firstPocketed
                    player2Group = if (firstPocketed == BallGroup.SOLID) BallGroup.STRIPE else BallGroup.SOLID
                } else {
                    player2Group = firstPocketed
                    player1Group = if (firstPocketed == BallGroup.SOLID) BallGroup.STRIPE else BallGroup.SOLID
                }
                addLog("Groups: ${if (isPlayerTurn) "Player 1" else "Bot"} is ${firstPocketed.name}s!")
            }
        }

        val updatedCurrentGroup = if (isPlayerTurn) player1Group else player2Group
        val opponentGroup = if (isPlayerTurn) {
            if (player1Group == BallGroup.SOLID) BallGroup.STRIPE else BallGroup.SOLID
        } else {
            if (player2Group == BallGroup.SOLID) BallGroup.STRIPE else BallGroup.SOLID
        }

        if (is8BallPocketedThisTurn) {
            gameState = GameState.GAME_OVER
            val remainingOwnBalls = if (updatedCurrentGroup != null) {
                balls.count { !it.isCueBall && getBallGroup(it.id) == updatedCurrentGroup }
            } else {
                1 // table was open -> illegal 8-ball pocketing
            }

            if (remainingOwnBalls == 0 && !wasCueBallPocketedThisTurn) {
                winnerMessage = when (currentMode) {
                    GameMode.PRACTICE -> "Practice Completed! You Pocketed all balls."
                    GameMode.PLAYER_VS_BOT -> if (isPlayerTurn) "Congratulations! You Win!" else "Bot Wins! Better luck next time."
                    GameMode.PLAYER_VS_PLAYER -> if (isPlayerTurn) "Player 1 Wins!" else "Player 2 Wins!"
                    GameMode.ONLINE_MULTIPLAYER -> if (isPlayerTurn) "Congratulations! You Win!" else "Opponent Wins! Better luck next time."
                }
            } else {
                winnerMessage = when (currentMode) {
                    GameMode.PRACTICE -> "Game Over: 8-Ball Pocketed early!"
                    GameMode.PLAYER_VS_BOT -> if (isPlayerTurn) "Game Over: You pocketed 8-Ball early! Bot Wins!" else "Game Over: Bot pocketed 8-Ball early! You Win!"
                    GameMode.PLAYER_VS_PLAYER -> if (isPlayerTurn) "Game Over: Player 1 pocketed 8-Ball early! Player 2 Wins!" else "Game Over: Player 2 pocketed 8-Ball early! Player 1 Wins!"
                    GameMode.ONLINE_MULTIPLAYER -> if (isPlayerTurn) "Game Over: You pocketed 8-Ball early! Opponent Wins!" else "Game Over: Opponent pocketed 8-Ball early! You Win!"
                }
            }
            return
        }

        val remainingOwnBallsCount = if (updatedCurrentGroup != null) {
            balls.count { !it.isCueBall && getBallGroup(it.id) == updatedCurrentGroup }
        } else {
            remainingObjectBalls
        }

        if (remainingOwnBallsCount == 0 && updatedCurrentGroup != null) {
            addLog("Group cleared! Target the 8-Ball.")
        }

        if (currentMode == GameMode.PRACTICE) {
            wasCueBallPocketedThisTurn = false
            objectBallsPocketedThisTurn = 0
            isPlayerTurn = true
            return
        }

        val pocketedOwn = updatedCurrentGroup != null && pocketedGroupsThisTurn.contains(updatedCurrentGroup)
        val pocketedOpponent = updatedCurrentGroup != null && pocketedGroupsThisTurn.contains(opponentGroup)

        val keepTurn = if (updatedCurrentGroup != null) {
            // Standard rule: must pocket own ball, and NOT pocket opponent's ball, and not scratch
            pocketedOwn && !pocketedOpponent && !wasCueBallPocketedThisTurn
        } else {
            objectBallsPocketedThisTurn > 0 && !wasCueBallPocketedThisTurn
        }

        if (updatedCurrentGroup != null && pocketedOpponent && !wasCueBallPocketedThisTurn) {
            addLog("Opponent's ball pocketed! Turn passes.")
        }

        if (keepTurn) {
            addLog(if (currentMode == GameMode.PLAYER_VS_BOT && !isPlayerTurn) "Bot keeps turn!" else if (isPlayerTurn) "Player 1 keeps turn!" else "Player 2 keeps turn!")
        } else {
            isPlayerTurn = !isPlayerTurn
            addLog(if (currentMode == GameMode.PLAYER_VS_BOT && !isPlayerTurn) "Bot's Turn" else if (isPlayerTurn) "Player 1's Turn" else "Player 2's Turn")
        }

        wasCueBallPocketedThisTurn = false
        objectBallsPocketedThisTurn = 0

        if (!isPlayerTurn && currentMode == GameMode.PLAYER_VS_BOT) {
            scope.launch {
                delay(800L) // Think time delay before aiming visual starts
                withContext(Dispatchers.Main) {
                    startBotAimingSequence()
                }
            }
        }
    }

    fun applyStrike(vx: Float, vy: Float) {
        if (gameState != GameState.PLAYING || isSimulationRunning) return
        val cueBallIndex = balls.indexOfFirst { it.isCueBall }
        if (cueBallIndex != -1 && (isPlayerTurn || currentMode == GameMode.PRACTICE)) {
            pocketedGroupsThisTurn.clear()
            addLog(if (currentMode == GameMode.PLAYER_VS_PLAYER) {
                if (isPlayerTurn) "Player 1 strikes!" else "Player 2 strikes!"
            } else {
                "Player strikes!"
            })

            wasMyShot = true
            if (currentMode == GameMode.ONLINE_MULTIPLAYER) {
                try {
                    val json = JSONObject()
                    json.put("type", "strike")
                    json.put("vx", vx)
                    json.put("vy", vy)
                    webSocket?.send(json.toString())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            balls[cueBallIndex] = balls[cueBallIndex].copy(
                vx = vx,
                vy = vy
            )
            isSimulationRunning = true
            isBreakShot = false
            // Play a synthetic cue strike audio sound!
            SoundManager.playCollisionSound(0.75f)
        }
    }

    private fun startBotAimingSequence() {
        if (gameState != GameState.PLAYING) return
        val cueBall = balls.find { it.isCueBall } ?: return

        val botGroup = player2Group
        val targets = if (botGroup != null) {
            val ownBalls = balls.filter { !it.isCueBall && getBallGroup(it.id) == botGroup }
            if (ownBalls.isNotEmpty()) ownBalls else balls.filter { !it.isCueBall && it.id == 8 }
        } else {
            val openTargets = balls.filter { !it.isCueBall && it.id != 8 }
            if (openTargets.isNotEmpty()) openTargets else balls.filter { !it.isCueBall }
        }
        val finalTargets = if (targets.isNotEmpty()) targets else balls.filter { !it.isCueBall }
        if (finalTargets.isEmpty()) return

        val targetXArray = FloatArray(finalTargets.size) { finalTargets[it].x }
        val targetYArray = FloatArray(finalTargets.size) { finalTargets[it].y }

        val botForce = calculateBotMove(cueBall.x, cueBall.y, targetXArray, targetYArray, finalTargets.size)
        val vx = botForce[0]
        val vy = botForce[1]
        val speed = Math.sqrt((vx * vx + vy * vy).toDouble()).toFloat()
        if (speed < 0.01f) {
            applyBotStrikeDirect(vx, vy)
            return
        }

        val ux = vx / speed
        val uy = vy / speed

        // Invert for cue stick position (cue stick is behind the cue ball, pointing along the strike direction)
        val nx = -ux
        val ny = -uy

        botAimDx = nx
        botAimDy = ny
        botShowCue = true
        botAimLength = 0f
        botCueRecoil = 15f

        scope.launch {
            // Step 1: Laser line extends (aiming phase)
            val stepsAim = 40
            val maxAimLength = 450f
            for (step in 1..stepsAim) {
                if (gameState != GameState.PLAYING || isPlayerTurn) {
                    resetBotAnimationState()
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    botAimLength = (step.toFloat() / stepsAim) * maxAimLength
                }
                delay(16L)
            }

            // Short pause at full aim
            delay(200L)

            // Step 2: Cue stick pullback (recoil phase) - MUCH larger swing/zamah
            val maxRecoil = 25f + speed * 2.2f // Proportional to speed, ranging from ~108f to ~130f
            val stepsPullback = 30
            for (step in 1..stepsPullback) {
                if (gameState != GameState.PLAYING || isPlayerTurn) {
                    resetBotAnimationState()
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    botCueRecoil = 15f + (step.toFloat() / stepsPullback) * (maxRecoil - 15f)
                }
                delay(16L)
            }

            // Short pause at full pullback
            delay(150L)

            // Step 3: Forward strike (snap forward to follow-through)
            val stepsStrike = 6
            val startRecoil = maxRecoil
            val endRecoil = -12f // Penetrates cue ball slightly for realistic contact follow-through
            for (step in 1..stepsStrike) {
                if (gameState != GameState.PLAYING || isPlayerTurn) {
                    resetBotAnimationState()
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    botCueRecoil = startRecoil - (step.toFloat() / stepsStrike) * (startRecoil - endRecoil)
                }
                delay(16L)
            }

            // Step 4: Apply physical hit on main thread at point of visual impact
            withContext(Dispatchers.Main) {
                if (gameState == GameState.PLAYING && !isPlayerTurn) {
                    applyBotStrikeDirect(vx, vy)
                }
            }

            // Step 5: Brief follow-through hold so the player clearly sees the cue contact
            delay(120L)

            // Step 6: Hide cue stick and reset state
            withContext(Dispatchers.Main) {
                resetBotAnimationState()
            }
        }
    }

    private fun applyBotStrikeDirect(vx: Float, vy: Float) {
        val cueBallIndex = balls.indexOfFirst { it.isCueBall }
        if (cueBallIndex != -1) {
            pocketedGroupsThisTurn.clear()
            addLog("Bot strikes the cue ball!")
            balls[cueBallIndex] = balls[cueBallIndex].copy(
                vx = vx * 0.75f,
                vy = vy * 0.75f
            )
            isSimulationRunning = true
            isBreakShot = false
            SoundManager.playCollisionSound(0.7f)
        }
    }

    private fun resetBotAnimationState() {
        botShowCue = false
        botAimDx = 0f
        botAimDy = 0f
        botCueRecoil = 15f
        botAimLength = 0f
    }

    private external fun stepPhysics(
        ballX: FloatArray, ballY: FloatArray,
        ballVX: FloatArray, ballVY: FloatArray,
        count: Int, deltaTime: Float
    )

    private external fun calculateBotMove(
        cueX: Float, cueY: Float,
        targetX: FloatArray, targetY: FloatArray,
        targetCount: Int
    ): FloatArray
}
