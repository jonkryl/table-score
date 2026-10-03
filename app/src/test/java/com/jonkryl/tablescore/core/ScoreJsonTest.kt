package com.jonkryl.tablescore.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ScoreJsonTest {
    @Test
    fun encodedSaveRetainsUnicodeSigned64BitScoresHistoryAndUndo() {
        var sequence = 0
        val persistence = JsonPersistence()
        val store = ScoreStore(persistence, now = { 8_888L }, newId = { "id-${++sequence}" })
        val first = store.newGame(listOf("Таня 🎲", "Team \"Blue\"\n& friends"))
        store.addPoints(first.players[0].id, Long.MIN_VALUE)
        store.addPoints(first.players[1].id, Long.MAX_VALUE)
        store.finishGame()
        val finished = store.state
        val second = store.newGame(listOf("Север", "South"))
        store.addPoints(second.players[0].id, 9_007_199_254_740_993L)
        val reopened = ScoreStore(persistence)
        assertEquals(store.state, reopened.state)
        assertEquals(9_007_199_254_740_993L, reopened.activeGame!!.total(second.players[0].id))
        assertEquals(Long.MIN_VALUE, reopened.state.games.last().total(first.players[0].id))
        assertEquals(Long.MAX_VALUE, reopened.state.games.last().total(first.players[1].id))
        assertTrue(reopened.undo())
        assertEquals(0L, reopened.activeGame!!.total(second.players[0].id))
        assertEquals(finished.games.single(), reopened.state.games.last())
        assertFalse(ScoreStore(persistence).canUndo)
    }

    @Test
    fun malformedOrFutureFilesAreReportedWithoutInventingAnEmptySave() {
        expectThrows<IOException> { ScoreJson.decode("{not json}") }
        expectThrows<IOException> { ScoreJson.decode("{\"version\":2,\"state\":{\"games\":[],\"activeGameId\":null},\"undo\":null}") }
        expectThrows<IOException> { ScoreJson.decode("{\"version\":1,\"state\":{\"games\":[],\"activeGameId\":\"missing\"},\"undo\":null}") }
    }

    @Test
    fun aSaveWithMissingPlayersOrOverflowCannotLoadAsAValidGame() {
        var sequence = 0
        val persistence = JsonPersistence()
        val store = ScoreStore(persistence, now = { 5_000L }, newId = { "id-${++sequence}" })
        val game = store.newGame(listOf("A", "B"))
        store.addPoints(game.players[0].id, Long.MAX_VALUE)
        store.nextRound()
        val root = JSONObject(persistence.json!!)
        val rounds = root.getJSONObject("state").getJSONArray("games").getJSONObject(0).getJSONArray("rounds")
        rounds.getJSONObject(1).getJSONObject("points").put(game.players[0].id, "1")
        expectThrows<IOException> { ScoreJson.decode(root.toString()) }
        rounds.getJSONObject(1).getJSONObject("points").put(game.players[0].id, "0")
        rounds.getJSONObject(1).getJSONObject("points").remove(game.players[1].id)
        expectThrows<IOException> { ScoreJson.decode(root.toString()) }
    }

    private class JsonPersistence : ScorePersistence {
        var json: String? = null
        override fun load(): ScoreSnapshot? = json?.let(ScoreJson::decode)
        override fun save(snapshot: ScoreSnapshot) { json = ScoreJson.encode(snapshot) }
    }
}
