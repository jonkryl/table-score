package com.jonkryl.tablescore.core

data class Player(val id: String, val name: String)

data class Round(val id: String, val number: Int, val points: Map<String, Long>)

data class Game(
    val id: String,
    val players: List<Player>,
    val rounds: List<Round>,
    val startedAt: Long,
    val finishedAt: Long? = null,
) {
    val isFinished: Boolean get() = finishedAt != null
    val currentRound: Round get() = rounds.last()
    val totals: Map<String, Long> get() = players.associate { it.id to total(it.id) }

    fun total(playerId: String): Long {
        require(players.any { it.id == playerId }) { "Unknown player" }
        return rounds.fold(0L) { total, round -> Math.addExact(total, round.points[playerId] ?: 0L) }
    }

    val winnerIds: List<String>
        get() {
            if (!isFinished) return emptyList()
            val scores = totals
            val winningScore = scores.values.maxOrNull() ?: return emptyList()
            return players.filter { scores[it.id] == winningScore }.map { it.id }
        }
}

data class AppState(val games: List<Game> = emptyList(), val activeGameId: String? = null) {
    val activeGame: Game? get() = games.firstOrNull { it.id == activeGameId }
}

/** The previous complete state is deliberately persisted so undo survives a process restart. */
data class ScoreSnapshot(val state: AppState = AppState(), val undoState: AppState? = null)

internal fun validateState(state: AppState) {
    require(state.games.map { it.id }.distinct().size == state.games.size) { "Duplicate game IDs" }
    require(state.activeGameId == null || state.activeGame != null) { "Current game is missing" }
    val unfinished = state.games.filterNot { it.isFinished }
    require(unfinished.size <= 1) { "Only one unfinished game is allowed" }
    require(unfinished.isEmpty() || unfinished.single().id == state.activeGameId) { "Unfinished game must be current" }
    state.games.forEach { game ->
        require(game.id.isNotBlank()) { "Empty game ID" }
        require(game.players.size in 2..8) { "A game needs 2 to 8 players" }
        require(game.players.map { it.id }.distinct().size == game.players.size) { "Duplicate player IDs" }
        game.players.forEach { player ->
            require(player.id.isNotBlank()) { "Empty player ID" }
            require(player.name.isNotBlank() && player.name.length <= 40) { "Invalid player name" }
        }
        require(game.startedAt >= 0 && (game.finishedAt == null || game.finishedAt >= game.startedAt)) { "Invalid game time" }
        require(game.rounds.isNotEmpty()) { "A game needs a round" }
        require(game.rounds.map { it.id }.distinct().size == game.rounds.size) { "Duplicate round IDs" }
        val playerIds = game.players.map { it.id }.toSet()
        game.rounds.forEachIndexed { index, round ->
            require(round.id.isNotBlank() && round.number == index + 1) { "Invalid round" }
            require(round.points.keys == playerIds) { "Round players do not match the game" }
        }
        try {
            game.totals
        } catch (error: ArithmeticException) {
            throw IllegalArgumentException("Total is outside the supported score range", error)
        }
    }
}
