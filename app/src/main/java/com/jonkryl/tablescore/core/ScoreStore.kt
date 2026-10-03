package com.jonkryl.tablescore.core

import java.io.IOException
import java.util.UUID

interface ScorePersistence {
    @Throws(IOException::class)
    fun load(): ScoreSnapshot?

    @Throws(IOException::class)
    fun save(snapshot: ScoreSnapshot)
}

/** All changes are saved before they become visible. A failed write leaves both state and undo intact. */
class ScoreStore(
    private val persistence: ScorePersistence,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private var snapshot: ScoreSnapshot = persistence.load() ?: ScoreSnapshot()

    init {
        validateState(snapshot.state)
        snapshot.undoState?.let(::validateState)
    }

    val state: AppState get() = snapshot.state
    val activeGame: Game? get() = state.activeGame
    val canUndo: Boolean get() = snapshot.undoState != null

    fun newGame(names: List<String>, finishCurrent: Boolean = false): Game {
        require(names.size in 2..8) { "A game needs 2 to 8 players" }
        check(activeGame?.isFinished != false || finishCurrent) { "Finish the current game first" }
        val players = names.map { Player(newId(), checkedName(it)) }
        val startedAt = now()
        val game = Game(newId(), players, listOf(emptyRound(players, 1)), startedAt)
        val previousGames = state.games.map { previous ->
            if (finishCurrent && previous.id == state.activeGameId && !previous.isFinished)
                previous.copy(finishedAt = maxOf(startedAt, previous.startedAt))
            else previous
        }
        // Replacing a running game is one saved action, including its history and undo.
        commit(AppState(listOf(game) + previousGames, game.id))
        return game
    }

    fun addPoints(playerId: String, delta: Long) {
        val game = editableGame()
        require(game.players.any { it.id == playerId }) { "Unknown player" }
        if (delta == 0L) return
        val roundPoints = game.currentRound.points
        val next = try {
            // Check both the current round and complete game before any write.
            Math.addExact(game.total(playerId), delta)
            Math.addExact(roundPoints.getValue(playerId), delta)
        } catch (error: ArithmeticException) {
            throw IllegalArgumentException("Score is outside the supported range", error)
        }
        val round = game.currentRound.copy(points = roundPoints + (playerId to next))
        updateGame(game.copy(rounds = game.rounds.dropLast(1) + round))
    }

    fun renamePlayer(playerId: String, name: String) {
        val game = editableGame()
        val checkedName = checkedName(name)
        require(game.players.any { it.id == playerId }) { "Unknown player" }
        updateGame(game.copy(players = game.players.map { if (it.id == playerId) it.copy(name = checkedName) else it }))
    }

    fun nextRound() {
        val game = editableGame()
        updateGame(game.copy(rounds = game.rounds + emptyRound(game.players, game.rounds.size + 1)))
    }

    fun finishGame() {
        val game = editableGame()
        updateGame(game.copy(finishedAt = maxOf(now(), game.startedAt)))
    }

    fun resetGame() {
        val game = editableGame()
        updateGame(game.copy(rounds = listOf(emptyRound(game.players, 1))))
    }

    fun deleteGame(gameId: String) {
        require(state.games.any { it.id == gameId }) { "Unknown game" }
        commit(state.copy(
            games = state.games.filterNot { it.id == gameId },
            activeGameId = state.activeGameId.takeUnless { it == gameId },
        ))
    }

    fun undo(): Boolean {
        val previous = snapshot.undoState ?: return false
        val next = ScoreSnapshot(previous)
        persistence.save(next)
        snapshot = next
        return true
    }

    private fun commit(nextState: AppState) {
        if (nextState == state) return
        validateState(nextState)
        val next = ScoreSnapshot(nextState, state)
        persistence.save(next)
        snapshot = next
    }

    private fun updateGame(game: Game) {
        commit(state.copy(games = state.games.map { if (it.id == game.id) game else it }))
    }

    private fun editableGame(): Game {
        val game = checkNotNull(activeGame) { "Start a game first" }
        check(!game.isFinished) { "This game has finished" }
        return game
    }

    private fun emptyRound(players: List<Player>, number: Int): Round =
        Round(newId(), number, players.associate { it.id to 0L })

    private fun checkedName(name: String): String {
        val cleaned = name.trim()
        require(cleaned.isNotBlank() && cleaned.length <= 40) { "Names must contain 1 to 40 characters" }
        return cleaned
    }
}
