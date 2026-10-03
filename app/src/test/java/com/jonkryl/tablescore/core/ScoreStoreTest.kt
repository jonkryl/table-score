package com.jonkryl.tablescore.core

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ScoreStoreTest {
    @Test
    fun signedRoundsKeepRoundPointsAndCumulativeTotalsDistinct() {
        val store = store()
        val players = store.newGame(listOf("Alice", "Bob")).players
        store.addPoints(players[0].id, 12)
        store.addPoints(players[1].id, -7)
        store.nextRound()
        assertEquals(0L, store.activeGame!!.currentRound.points[players[0].id])
        assertEquals(12L, store.activeGame!!.total(players[0].id))
        store.addPoints(players[0].id, -20)
        store.addPoints(players[1].id, 4)
        val game = store.activeGame!!
        assertEquals(-8L, game.total(players[0].id))
        assertEquals(-3L, game.total(players[1].id))
        assertEquals(12L, game.rounds[0].points[players[0].id])
        assertEquals(-20L, game.rounds[1].points[players[0].id])
        assertEquals(listOf(1, 2), game.rounds.map { it.number })
    }

    @Test
    fun undoSurvivesRestartAndRestoresExactRoundRatherThanOnlyTotal() {
        val persistence = MemoryPersistence()
        val store = store(persistence)
        val player = store.newGame(listOf("A", "B")).players.first()
        store.addPoints(player.id, -11)
        val before = store.state
        store.nextRound()
        val reopened = store(persistence)
        assertEquals(2, reopened.activeGame!!.rounds.size)
        assertTrue(reopened.canUndo)
        assertTrue(reopened.undo())
        assertEquals(before, reopened.state)
        assertFalse(reopened.canUndo)
        val reopenedAgain = store(persistence)
        assertEquals(before, reopenedAgain.state)
        assertFalse(reopenedAgain.undo())
    }

    @Test
    fun winnerWorksWhenEveryoneIsNegativeAndReportsTies() {
        val store = store()
        val players = store.newGame(listOf("A", "B", "C")).players
        store.addPoints(players[0].id, -20)
        store.addPoints(players[1].id, -5)
        store.addPoints(players[2].id, -5)
        assertTrue(store.activeGame!!.winnerIds.isEmpty())
        store.finishGame()
        assertEquals(listOf(players[1].id, players[2].id), store.activeGame!!.winnerIds)
        expectThrows<IllegalStateException> { store.addPoints(players[0].id, 1) }
        assertTrue(store.undo())
        assertFalse(store.activeGame!!.isFinished)
        assertTrue(store.activeGame!!.winnerIds.isEmpty())
    }

    @Test
    fun aNewGamePreservesFinishedHistoryAndUndoRestoresWinnerBoard() {
        val persistence = MemoryPersistence()
        val store = store(persistence)
        val first = store.newGame(listOf("A", "B"))
        store.addPoints(first.players[0].id, 31)
        store.finishGame()
        val finishedBoard = store.state
        val second = store.newGame(listOf("Team 1", "Team 2"))
        assertEquals(second.id, store.activeGame!!.id)
        assertEquals(2, store.state.games.size)
        assertEquals(31L, store.state.games.last().total(first.players[0].id))
        val reopened = store(persistence)
        assertTrue(reopened.undo())
        assertEquals(finishedBoard, reopened.state)
        assertTrue(reopened.activeGame!!.isFinished)
        assertEquals(listOf(first.players[0].id), reopened.activeGame!!.winnerIds)
    }

    @Test
    fun replacingRunningGameIsAtomicAndUndoRestoresUnfinishedBoard() {
        val persistence = MemoryPersistence()
        val store = store(persistence)
        val first = store.newGame(listOf("A", "B"))
        store.addPoints(first.players[0].id, -17)
        store.nextRound()
        val running = store.state
        persistence.failWrites = true
        expectThrows<IOException> { store.newGame(listOf("C", "D"), finishCurrent = true) }
        assertEquals(running, store.state)
        assertFalse(store(persistence).activeGame!!.isFinished)
        persistence.failWrites = false
        store.newGame(listOf("C", "D"), finishCurrent = true)
        assertTrue(store.state.games.last().isFinished)
        assertEquals(-17L, store.state.games.last().total(first.players[0].id))
        val reopened = store(persistence)
        assertTrue(reopened.undo())
        assertEquals(running, reopened.state)
        assertFalse(reopened.activeGame!!.isFinished)
    }

    @Test
    fun deletionAndResetCanRestoreAllRoundsAndNames() {
        val store = store()
        val game = store.newGame(listOf("A", "B"))
        store.renamePlayer(game.players[0].id, "  Team Red  ")
        store.addPoints(game.players[0].id, 10)
        store.nextRound()
        store.addPoints(game.players[0].id, -3)
        val before = store.state
        store.resetGame()
        assertEquals(1, store.activeGame!!.rounds.size)
        assertEquals(0L, store.activeGame!!.total(game.players[0].id))
        assertEquals("Team Red", store.activeGame!!.players[0].name)
        assertTrue(store.undo())
        assertEquals(before, store.state)
        store.deleteGame(game.id)
        assertNull(store.activeGame)
        assertTrue(store.state.games.isEmpty())
        assertTrue(store.undo())
        assertEquals(before, store.state)
    }

    @Test
    fun failedSaveDoesNotExposeUnsavedScoresOrReplaceUndo() {
        val persistence = MemoryPersistence()
        val store = store(persistence)
        val game = store.newGame(listOf("A", "B"))
        val beforeScore = store.state
        store.addPoints(game.players[0].id, -8)
        val durable = store.state
        persistence.failWrites = true
        expectThrows<IOException> { store.addPoints(game.players[0].id, 100) }
        assertEquals(durable, store.state)
        assertEquals(durable, store(persistence).state)
        expectThrows<IOException> { store.undo() }
        assertEquals(durable, store.state)
        assertTrue(store.canUndo)
        persistence.failWrites = false
        assertTrue(store.undo())
        assertEquals(beforeScore, store.state)
    }

    @Test
    fun emptyChangesDoNotConsumeTheLastUsefulUndo() {
        val store = store()
        val game = store.newGame(listOf("A", "B"))
        store.addPoints(game.players[0].id, 6)
        store.addPoints(game.players[0].id, 0)
        store.renamePlayer(game.players[0].id, " A ")
        assertTrue(store.undo())
        assertEquals(0L, store.activeGame!!.total(game.players[0].id))
    }

    @Test
    fun scoreOverflowIsRejectedWithoutChangingSavedGame() {
        val persistence = MemoryPersistence()
        val store = store(persistence)
        val player = store.newGame(listOf("A", "B")).players[0]
        store.addPoints(player.id, Long.MAX_VALUE)
        val maximum = store.state
        expectThrows<IllegalArgumentException> { store.addPoints(player.id, 1) }
        assertEquals(maximum, store.state)
        store.nextRound()
        val nextRound = store.state
        expectThrows<IllegalArgumentException> { store.addPoints(player.id, 1) }
        assertEquals(nextRound, store.state)
        assertEquals(nextRound, store(persistence).state)
        store.addPoints(player.id, -Long.MAX_VALUE)
        assertEquals(0L, store.activeGame!!.total(player.id))
    }

    @Test
    fun lowerBoundOverflowAlsoPreservesPriorUndo() {
        val store = store()
        val player = store.newGame(listOf("A", "B")).players[0]
        store.addPoints(player.id, Long.MIN_VALUE)
        val minimum = store.state
        expectThrows<IllegalArgumentException> { store.addPoints(player.id, -1) }
        assertEquals(minimum, store.state)
        assertTrue(store.undo())
        assertEquals(0L, store.activeGame!!.total(player.id))
    }

    @Test
    fun invalidInputsCannotDestroyTheCurrentGame() {
        val store = store()
        expectThrows<IllegalArgumentException> { store.newGame(listOf("One")) }
        expectThrows<IllegalArgumentException> { store.newGame(List(9) { "Player $it" }) }
        expectThrows<IllegalArgumentException> { store.newGame(listOf(" ", "B")) }
        val game = store.newGame(listOf("A", "B"))
        val before = store.state
        expectThrows<IllegalStateException> { store.newGame(listOf("C", "D")) }
        expectThrows<IllegalArgumentException> { store.addPoints("missing", 7) }
        expectThrows<IllegalArgumentException> { store.renamePlayer(game.players[0].id, " ") }
        expectThrows<IllegalArgumentException> { store.deleteGame("missing") }
        assertEquals(before, store.state)
    }

    @Test
    fun deletingAnOlderGameDoesNotChangeTheCurrentBoard() {
        val store = store()
        val first = store.newGame(listOf("A", "B"))
        store.finishGame()
        val current = store.newGame(List(8) { "Team ${it + 1}" })
        store.addPoints(current.players.last().id, -19)
        val board = store.activeGame
        store.deleteGame(first.id)
        assertEquals(board, store.activeGame)
        assertEquals(1, store.state.games.size)
        assertTrue(store.undo())
        assertEquals(board, store.activeGame)
        assertEquals(2, store.state.games.size)
    }

    private fun store(persistence: MemoryPersistence = MemoryPersistence()): ScoreStore {
        var sequence = 0
        return ScoreStore(persistence, now = { 1_000L }, newId = { "id-${++sequence}" })
    }

    private class MemoryPersistence : ScorePersistence {
        var saved: ScoreSnapshot? = null
        var failWrites = false
        override fun load(): ScoreSnapshot? = saved
        override fun save(snapshot: ScoreSnapshot) {
            if (failWrites) throw IOException("Disk is full")
            saved = snapshot
        }
    }
}

internal inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
    try {
        block()
    } catch (error: Throwable) {
        if (error is T) return error
        throw AssertionError("Expected ${T::class.java.simpleName}, got ${error::class.java.simpleName}", error)
    }
    throw AssertionError("Expected ${T::class.java.simpleName}")
}
