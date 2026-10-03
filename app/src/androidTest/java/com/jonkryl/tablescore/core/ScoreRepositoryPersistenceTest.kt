package com.jonkryl.tablescore.core

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ScoreRepositoryPersistenceTest {
    private lateinit var directory: File
    private lateinit var context: Context

    @Before
    fun setUp() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(base.cacheDir, "score-test-${UUID.randomUUID()}")
        check(directory.mkdirs())
        context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
        }
    }

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun actualFileRestoresNegativeRoundsNamesWinnerAndDurableUndo() {
        val repository = ScoreRepository(context)
        val game = repository.newGame(listOf("Анна", "B"))
        val first = game.players[0].id
        val second = game.players[1].id
        repository.addPoints(first, -14)
        repository.addPoints(second, -9)
        repository.nextRound()
        repository.addPoints(first, 7)
        repository.renamePlayer(second, "Blue Team")

        var reopened = ScoreRepository(context)
        assertEquals(repository.state, reopened.state)
        assertEquals(-7L, reopened.activeGame!!.total(first))
        assertEquals(-9L, reopened.activeGame!!.total(second))
        assertEquals(2, reopened.activeGame!!.rounds.size)
        assertEquals("Blue Team", reopened.activeGame!!.players[1].name)
        reopened.finishGame()
        reopened = ScoreRepository(context)
        assertTrue(reopened.activeGame!!.isFinished)
        assertEquals(listOf(first), reopened.activeGame!!.winnerIds)
        assertTrue(reopened.undo())
        reopened = ScoreRepository(context)
        assertFalse(reopened.activeGame!!.isFinished)
        assertFalse(reopened.canUndo)
        assertEquals(-7L, reopened.activeGame!!.total(first))

        reopened.deleteGame(game.id)
        assertNull(ScoreRepository(context).activeGame)
        reopened = ScoreRepository(context)
        assertTrue(reopened.undo())
        assertEquals(-7L, ScoreRepository(context).activeGame!!.total(first))
    }
}
