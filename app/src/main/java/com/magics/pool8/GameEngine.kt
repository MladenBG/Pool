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

enum class GameMode {
    PLAYER_VS_BOT,
    PLAYER_VS_PLAYER,
    PRACTICE
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

    var currentMode by mutableStateOf(GameMode.PLAYER_VS_BOT)
    var gameState by mutableStateOf(GameState.MENU)
    var isPlayerTurn by mutableStateOf(true)
    var winnerMessage by mutableStateOf<String?>(null)
    var isSimulationRunning by mutableStateOf(false)
    var selectedCueStyle by mutableStateOf(CueStyle.CLASSIC_MAHOGANY)
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
        resetGame(GameMode.PLAYER_VS_BOT)
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
        eventLogs.clear()
        addLog("Match Started: ${mode.name.replace("_", " ")}")
        resetBotAnimationState()
        setupRack()
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

        // 8-Ball Rack (Simplified for demonstration)
        balls.add(PoolBall(1, 500f, 500f, 0f, 0f, 0xFFEAB308)) // Yellow
        balls.add(PoolBall(2, 470f, 440f, 0f, 0f, 0xFFEF4444)) // Red
        balls.add(PoolBall(3, 530f, 440f, 0f, 0f, 0xFF000000)) // 8-Ball Black
        balls.add(PoolBall(4, 440f, 380f, 0f, 0f, 0xFF3B82F6)) // Blue
        balls.add(PoolBall(5, 500f, 380f, 0f, 0f, 0xFFF97316)) // Orange
        balls.add(PoolBall(6, 560f, 380f, 0f, 0f, 0xFF22C55E)) // Green
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
                        } else if (originalBall.id == 3) {
                            is8BallPocketed = true
                        } else {
                            objectBallsPocketed++
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
                        addLog("Pocketed $objectBallsPocketed object ball(s)!")
                    }
                    if (is8BallPocketed) {
                        is8BallPocketedThisTurn = true
                        addLog("Black 8-Ball Pocketed!")
                    }

                    if (isSimulationRunning && !ballsMoving) {
                        isSimulationRunning = false
                        handleTurnTransition()
                    } else if (ballsMoving) {
                        isSimulationRunning = true
                    }
                }

                delay(16L) // ~60fps
            }
        }
    }

    private fun handleTurnTransition() {
        val remainingObjectBalls = balls.count { !it.isCueBall && it.id != 3 }

        if (is8BallPocketedThisTurn) {
            gameState = GameState.GAME_OVER
            if (remainingObjectBalls == 0 && !wasCueBallPocketedThisTurn) {
                winnerMessage = when (currentMode) {
                    GameMode.PRACTICE -> "Practice Completed! You Pocketed all balls."
                    GameMode.PLAYER_VS_BOT -> if (isPlayerTurn) "Congratulations! You Win!" else "Bot Wins! Better luck next time."
                    GameMode.PLAYER_VS_PLAYER -> if (isPlayerTurn) "Player 1 Wins!" else "Player 2 Wins!"
                }
            } else {
                winnerMessage = when (currentMode) {
                    GameMode.PRACTICE -> "Game Over: 8-Ball Pocketed early!"
                    GameMode.PLAYER_VS_BOT -> if (isPlayerTurn) "Game Over: You pocketed 8-Ball early! Bot Wins!" else "Game Over: Bot pocketed 8-Ball early! You Win!"
                    GameMode.PLAYER_VS_PLAYER -> if (isPlayerTurn) "Game Over: Player 1 pocketed 8-Ball early! Player 2 Wins!" else "Game Over: Player 2 pocketed 8-Ball early! Player 1 Wins!"
                }
            }
            return
        }

        if (remainingObjectBalls == 0) {
            addLog("All object balls pocketed! Target the 8-Ball.")
        }

        if (currentMode == GameMode.PRACTICE) {
            wasCueBallPocketedThisTurn = false
            objectBallsPocketedThisTurn = 0
            isPlayerTurn = true
            return
        }

        val keepTurn = objectBallsPocketedThisTurn > 0 && !wasCueBallPocketedThisTurn

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
            addLog(if (currentMode == GameMode.PLAYER_VS_PLAYER) {
                if (isPlayerTurn) "Player 1 strikes!" else "Player 2 strikes!"
            } else {
                "Player strikes!"
            })
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

        val targets = balls.filter { !it.isCueBall && it.id != 3 }
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
