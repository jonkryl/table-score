package com.jonkryl.tablescore.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** Versioned JSON uses decimal strings for scores, preserving every signed 64-bit value. */
object ScoreJson {
    fun encode(snapshot: ScoreSnapshot): String {
        validateState(snapshot.state)
        snapshot.undoState?.let(::validateState)
        return JSONObject().apply {
            put("version", 1)
            put("state", encodeState(snapshot.state))
            put("undo", snapshot.undoState?.let(::encodeState) ?: JSONObject.NULL)
        }.toString()
    }

    @Throws(IOException::class)
    fun decode(json: String): ScoreSnapshot {
        try {
            val root = JSONObject(json)
            require(root.getInt("version") == 1) { "Unsupported save version" }
            val state = decodeState(root.getJSONObject("state"))
            val undo = if (root.isNull("undo")) null else decodeState(root.getJSONObject("undo"))
            validateState(state)
            undo?.let(::validateState)
            return ScoreSnapshot(state, undo)
        } catch (error: Exception) {
            throw IOException("Saved scores could not be read", error)
        }
    }

    private fun encodeState(state: AppState): JSONObject = JSONObject().apply {
        put("activeGameId", state.activeGameId ?: JSONObject.NULL)
        put("games", JSONArray().apply {
            state.games.forEach { game ->
                put(JSONObject().apply {
                    put("id", game.id)
                    put("startedAt", game.startedAt.toString())
                    put("finishedAt", game.finishedAt?.toString() ?: JSONObject.NULL)
                    put("players", JSONArray().apply {
                        game.players.forEach { player ->
                            put(JSONObject().put("id", player.id).put("name", player.name))
                        }
                    })
                    put("rounds", JSONArray().apply {
                        game.rounds.forEach { round ->
                            put(JSONObject().apply {
                                put("id", round.id)
                                put("number", round.number)
                                put("points", JSONObject().apply {
                                    round.points.forEach { (id, points) -> put(id, points.toString()) }
                                })
                            })
                        }
                    })
                })
            }
        })
    }

    private fun decodeState(json: JSONObject): AppState {
        val games = json.getJSONArray("games")
        return AppState(
            games = (0 until games.length()).map { index ->
                val game = games.getJSONObject(index)
                val players = game.getJSONArray("players")
                val rounds = game.getJSONArray("rounds")
                Game(
                    id = game.getString("id"),
                    players = (0 until players.length()).map {
                        val player = players.getJSONObject(it)
                        Player(player.getString("id"), player.getString("name"))
                    },
                    rounds = (0 until rounds.length()).map {
                        val round = rounds.getJSONObject(it)
                        val points = round.getJSONObject("points")
                        Round(round.getString("id"), round.getInt("number"), points.keys().asSequence().associateWith { id -> points.getString(id).toLong() })
                    },
                    startedAt = game.getString("startedAt").toLong(),
                    finishedAt = if (game.isNull("finishedAt")) null else game.getString("finishedAt").toLong(),
                )
            },
            activeGameId = if (json.isNull("activeGameId")) null else json.getString("activeGameId"),
        )
    }
}
