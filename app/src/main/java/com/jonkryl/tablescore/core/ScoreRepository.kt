package com.jonkryl.tablescore.core

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** App-private storage requires no Android permission. AtomicFile retains the prior file on a failed write. */
class AtomicJsonScorePersistence(private val file: AtomicFile) : ScorePersistence {
    override fun load(): ScoreSnapshot? {
        val bytes = try {
            file.readFully()
        } catch (_: FileNotFoundException) {
            return null
        }
        return ScoreJson.decode(bytes.toString(Charsets.UTF_8))
    }

    override fun save(snapshot: ScoreSnapshot) {
        val bytes = ScoreJson.encode(snapshot).toByteArray(Charsets.UTF_8)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw IOException("Scores could not be saved", error)
        }
    }
}

class ScoreRepository(context: Context) {
    private val store = ScoreStore(AtomicJsonScorePersistence(AtomicFile(File(context.applicationContext.filesDir, FILE_NAME))))

    val state: AppState get() = store.state
    val activeGame: Game? get() = store.activeGame
    val canUndo: Boolean get() = store.canUndo

    fun newGame(names: List<String>, finishCurrent: Boolean = false): Game = store.newGame(names, finishCurrent)
    fun addPoints(playerId: String, delta: Long) = store.addPoints(playerId, delta)
    fun renamePlayer(playerId: String, name: String) = store.renamePlayer(playerId, name)
    fun nextRound() = store.nextRound()
    fun finishGame() = store.finishGame()
    fun resetGame() = store.resetGame()
    fun deleteGame(gameId: String) = store.deleteGame(gameId)
    fun undo(): Boolean = store.undo()

    companion object {
        const val FILE_NAME = "table-score-state-v1.json"
    }
}
