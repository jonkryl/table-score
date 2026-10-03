package com.jonkryl.tablescore

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.jonkryl.tablescore.ads.BannerController
import com.jonkryl.tablescore.core.Game
import com.jonkryl.tablescore.core.Player
import com.jonkryl.tablescore.core.ScoreRepository
import java.io.IOException
import java.text.DateFormat
import java.util.Date

/** Native, two-column score board. The repository commits before any score is displayed. */
class MainActivity : Activity() {
    private lateinit var repository: ScoreRepository
    private lateinit var banner: BannerController
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var undoButton: Button
    private val scoreTexts = mutableMapOf<String, TextView>()
    private val roundTexts = mutableMapOf<String, TextView>()
    private val ink = Color.rgb(23, 61, 53)
    private val ivory = Color.rgb(247, 244, 235)
    private val mint = Color.rgb(217, 233, 207)
    private val coral = Color.rgb(237, 121, 93)
    private val muted = Color.rgb(93, 113, 107)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            repository = ScoreRepository(this)
        } catch (_: Exception) {
            val errorView = text(getString(R.string.load_failed), 18f, ink).apply {
                setPadding(dp(24), dp(40), dp(24), dp(24))
                setBackgroundColor(ivory)
            }
            setContentView(errorView)
            return
        }
        banner = BannerController(this)
        val root = column().apply { setBackgroundColor(ivory) }
        configureInsets(root)
        root.addView(header(), matchWrap())
        scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(dp(16), dp(8), dp(16), dp(20))
        }
        content = column()
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val privacyButton = button(getString(R.string.privacy), R.id.privacy_button, ivory, ink).apply {
            textSize = 12f
            setOnClickListener { banner.showPrivacyChoice() }
        }
        root.addView(privacyButton, matchWrap())
        val adHost = FrameLayout(this).apply {
            id = R.id.ad_container
            minimumHeight = dp(64)
            setBackgroundColor(ivory)
            contentDescription = getString(R.string.ad_area)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(adHost, matchWrap())
        setContentView(root)
        render()
        banner.attach(adHost)
    }

    override fun onDestroy() {
        if (::banner.isInitialized) banner.destroy()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun configureInsets(root: View) {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        root.requestApplyInsets()
    }

    private fun header(): View = column().apply {
        setPadding(dp(20), dp(16), dp(20), dp(6))
        addView(text(getString(R.string.app_name), 27f, ink, true))
        addView(text(getString(R.string.tagline), 13f, muted).apply { setPadding(0, dp(3), 0, dp(12)) })
        val actions = row()
        val history = button(getString(R.string.history), R.id.history_button, Color.WHITE, ink)
        history.setOnClickListener { showHistory() }
        actions.addView(history, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        undoButton = button(getString(R.string.undo), R.id.undo_button, mint, ink)
        undoButton.setOnClickListener { mutate { repository.undo() } }
        actions.addView(undoButton, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
        val more = button("⋯", R.id.more_button, Color.WHITE, ink).apply {
            textSize = 24f
            contentDescription = getString(R.string.more)
            setOnClickListener { showMore() }
        }
        actions.addView(more, LinearLayout.LayoutParams(dp(48), -2))
        addView(actions)
    }

    private fun render(resetScroll: Boolean = false) {
        val oldY = if (resetScroll) 0 else scroll.scrollY
        content.removeAllViews()
        scoreTexts.clear()
        roundTexts.clear()
        undoButton.isEnabled = repository.canUndo
        undoButton.alpha = if (repository.canUndo) 1f else 0.45f
        val game = repository.activeGame
        if (game == null) renderEmpty() else renderGame(game)
        scroll.post { scroll.scrollTo(0, oldY) }
    }

    private fun renderEmpty() {
        val illustration = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        content.addView(illustration, LinearLayout.LayoutParams(dp(104), dp(104)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(28)
            bottomMargin = dp(22)
        })
        content.addView(text(getString(R.string.empty_title), 28f, ink, true).apply { gravity = Gravity.CENTER })
        content.addView(text(getString(R.string.empty_body), 17f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(14), dp(8), dp(24))
            setLineSpacing(dp(3).toFloat(), 1f)
        })
        content.addView(button(getString(R.string.new_game), R.id.new_game_button, ink, Color.WHITE).apply {
            setOnClickListener { showNewGame() }
        }, matchWrap())
        content.addView(text(getString(R.string.empty_hint), 12f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
        })
    }

    private fun renderGame(game: Game) {
        val status = row().apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(2), dp(8), dp(2), dp(14)) }
        val roundTitle = if (game.isFinished) getString(R.string.finished)
            else getString(R.string.round_title, game.currentRound.number)
        status.addView(text(roundTitle, 20f, ink, true).apply {
            id = R.id.round_label
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val share = button(getString(R.string.share), R.id.share_button, Color.WHITE, ink).apply {
            textSize = 12f
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { shareGame(repository.activeGame ?: game) }
        }
        status.addView(share, LinearLayout.LayoutParams(-2, -2))
        content.addView(status)
        if (game.isFinished) {
            content.addView(text(winnerText(game), 20f, ink, true).apply {
                background = shape(mint)
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }, matchWrap().apply { bottomMargin = dp(12) })
        }
        game.players.chunked(2).forEachIndexed { rowIndex, players ->
            val scoreRow = row()
            players.forEachIndexed { column, player ->
                val index = rowIndex * 2 + column
                scoreRow.addView(playerCard(game, player, index), LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (column == 0) marginEnd = dp(10)
                })
            }
            if (players.size == 1) scoreRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            content.addView(scoreRow, matchWrap().apply { bottomMargin = dp(10) })
        }
        if (!game.isFinished) {
            content.addView(button(getString(R.string.next_round), R.id.round_button, ink, Color.WHITE).apply {
                setOnClickListener { mutate { repository.nextRound() } }
            }, matchWrap().apply { topMargin = dp(6) })
            content.addView(button(getString(R.string.finish_game), R.id.finish_game_button, mint, ink).apply {
                setOnClickListener {
                    confirm(R.string.finish_title, R.string.finish_body, R.string.finish_confirm) {
                        mutate { repository.finishGame() }
                    }
                }
            }, matchWrap().apply { topMargin = dp(8) })
        }
        content.addView(button(getString(R.string.new_game), R.id.new_game_button, Color.WHITE, ink).apply {
            setOnClickListener { showNewGame() }
        }, matchWrap().apply { topMargin = dp(8) })
        content.addView(button(getString(R.string.view_rounds), View.NO_ID, ivory, ink).apply {
            setOnClickListener { showGameDetails(repository.activeGame ?: game) }
        }, matchWrap().apply { topMargin = dp(2) })
    }

    private fun playerCard(game: Game, player: Player, index: Int): View = column().apply {
        background = shape(Color.WHITE, 22f)
        setPadding(dp(12), dp(10), dp(12), dp(12))
        val name = button(player.name, View.NO_ID, Color.WHITE, ink).apply {
            textSize = 17f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, 0)
            contentDescription = getString(R.string.edit_name, player.name)
            isEnabled = !game.isFinished
            setOnClickListener { rename(player, index) }
        }
        addView(name, matchWrap())
        addView(text(getString(R.string.total_score), 11f, muted))
        val value = text(game.total(player.id).toString(), 46f, ink, true).apply {
            id = scoreValueIds[index]
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(0, dp(2), 0, dp(2))
            if (Build.VERSION.SDK_INT >= 26) setAutoSizeTextTypeUniformWithConfiguration(12, 46, 1, 2)
            contentDescription = scoreDescription(game, player)
        }
        scoreTexts[player.id] = value
        addView(value, LinearLayout.LayoutParams(-1, dp(64)))
        fitLegacyScore(value)
        val roundValue = text(getString(R.string.round_points, (game.currentRound.points[player.id] ?: 0L).toString()), 12f, muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(10))
        }
        roundTexts[player.id] = roundValue
        addView(roundValue)
        if (!game.isFinished) {
            val points = row()
            points.addView(button("−", scoreMinusIds[index], ivory, ink).apply {
                textSize = 26f
                contentDescription = getString(R.string.subtract_one, player.name)
                setOnClickListener { mutate(fullRender = false) { repository.addPoints(player.id, -1L) } }
            }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(8) })
            points.addView(button("+", scorePlusIds[index], if (index % 2 == 0) mint else coral, ink).apply {
                textSize = 26f
                contentDescription = getString(R.string.add_one, player.name)
                setOnClickListener { mutate(fullRender = false) { repository.addPoints(player.id, 1L) } }
            }, LinearLayout.LayoutParams(0, dp(52), 1f))
            addView(points)
            addView(button(getString(R.string.exact_points), scoreExactIds[index], Color.WHITE, ink).apply {
                textSize = 12f
                setPadding(0, 0, 0, 0)
                setOnClickListener { enterPoints(player) }
            }, matchWrap().apply { topMargin = dp(4) })
        }
    }

    private fun refreshScores() {
        val game = repository.activeGame ?: return render()
        game.players.forEach { player ->
            scoreTexts[player.id]?.apply {
                text = game.total(player.id).toString()
                contentDescription = scoreDescription(game, player)
                fitLegacyScore(this)
            }
            roundTexts[player.id]?.text = getString(R.string.round_points,
                (game.currentRound.points[player.id] ?: 0L).toString())
        }
        undoButton.isEnabled = repository.canUndo
        undoButton.alpha = if (repository.canUndo) 1f else 0.45f
    }

    private fun showNewGame() {
        val form = column().apply { setPadding(dp(20), dp(12), dp(20), dp(10)) }
        form.addView(text(getString(R.string.players_count), 15f, ink, true))
        val count = input("2", R.id.player_count_input).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(1))
            setSelectAllOnFocus(true)
            contentDescription = getString(R.string.players_count)
        }
        form.addView(count, matchWrap())
        val names = (0 until 8).map { index ->
            input(getString(R.string.player_default, index + 1), playerNameIds[index]).apply {
                hint = getString(R.string.player_name, index + 1)
                contentDescription = hint
                setSelectAllOnFocus(true)
                filters = arrayOf(InputFilter.LengthFilter(40))
                visibility = if (index < 2) View.VISIBLE else View.GONE
            }.also { form.addView(it, matchWrap().apply { topMargin = dp(4) }) }
        }
        count.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val visibleCount = s?.toString()?.toIntOrNull()?.coerceIn(2, 8) ?: 2
                names.forEachIndexed { index, field -> field.visibility = if (index < visibleCount) View.VISIBLE else View.GONE }
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        val start = button(getString(R.string.start_game), R.id.start_game_button, ink, Color.WHITE)
        form.addView(start, matchWrap().apply { topMargin = dp(16) })
        val dialog = AlertDialog.Builder(this).setTitle(R.string.new_game)
            .setView(ScrollView(this).apply { addView(form) }).setNegativeButton(R.string.cancel, null).create()
        start.setOnClickListener {
            val playerCount = count.text.toString().toIntOrNull()
            if (playerCount == null || playerCount !in 2..8) {
                count.error = getString(R.string.invalid_count)
                return@setOnClickListener
            }
            val gameNames = names.take(playerCount).map { it.text.toString().trim() }
            val invalid = gameNames.indexOfFirst { it.isEmpty() || it.length > 40 }
            if (invalid >= 0) {
                names[invalid].error = getString(R.string.invalid_name)
                return@setOnClickListener
            }
            val createGame = {
                if (mutate(resetScroll = true) {
                    repository.newGame(gameNames, finishCurrent = repository.activeGame?.isFinished == false)
                }) {
                    hideKeyboard(start)
                    dialog.dismiss()
                }
            }
            if (repository.activeGame?.isFinished == false) {
                confirm(R.string.replace_title, R.string.replace_body, R.string.start_game) { createGame() }
            } else createGame()
        }
        dialog.show()
    }

    private fun enterPoints(player: Player) {
        val form = column().apply { setPadding(dp(20), dp(8), dp(20), dp(8)) }
        form.addView(text(getString(R.string.exact_help), 15f, muted))
        val delta = input("", R.id.exact_points_input).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            hint = getString(R.string.exact_hint)
            filters = arrayOf(InputFilter.LengthFilter(20))
        }
        form.addView(delta, matchWrap().apply { topMargin = dp(10) })
        val apply = button(getString(R.string.apply), R.id.exact_apply_button, ink, Color.WHITE)
        form.addView(apply, matchWrap().apply { topMargin = dp(14) })
        val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.exact_title, player.name))
            .setView(form).setNegativeButton(R.string.cancel, null).create()
        apply.setOnClickListener {
            val value = delta.text.toString().trim().toLongOrNull()
            if (value == null) {
                delta.error = getString(R.string.invalid_points)
            } else if (mutate(fullRender = false) { repository.addPoints(player.id, value) }) {
                hideKeyboard(delta)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun rename(player: Player, index: Int) {
        val name = input(player.name, playerNameIds[index]).apply {
            setSelection(text.length)
            filters = arrayOf(InputFilter.LengthFilter(40))
        }
        val form = column().apply { setPadding(dp(20), dp(8), dp(20), dp(8)); addView(name) }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.rename_title).setView(form)
            .setPositiveButton(R.string.save, null).setNegativeButton(R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = name.text.toString().trim()
                if (value.isEmpty()) name.error = getString(R.string.invalid_name)
                else if (mutate { repository.renamePlayer(player.id, value) }) dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showMore() {
        val canReset = repository.activeGame?.isFinished == false
        val options = if (canReset) arrayOf(getString(R.string.reset_scores), getString(R.string.privacy))
            else arrayOf(getString(R.string.privacy))
        AlertDialog.Builder(this).setTitle(R.string.more).setItems(options) { _, which ->
            if (canReset && which == 0) {
                confirm(R.string.reset_title, R.string.reset_body, R.string.reset_confirm) {
                    mutate(resetScroll = true) { repository.resetGame() }
                }
            } else banner.showPrivacyChoice()
        }.show()
    }

    private fun showHistory() {
        val games = repository.state.games.sortedByDescending { it.startedAt }
        if (games.isEmpty()) {
            AlertDialog.Builder(this).setTitle(R.string.history_title).setMessage(R.string.history_empty)
                .setPositiveButton(R.string.close, null).show()
            return
        }
        lateinit var historyDialog: AlertDialog
        val list = column().apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
        games.forEach { game ->
            val summary = buildString {
                append(getString(R.string.game_date, date(game.startedAt)))
                append('\n')
                append(if (game.isFinished) winnerText(game) else getString(R.string.live_results))
                append('\n')
                append(game.players.joinToString(" · ") { "${it.name}: ${game.total(it.id)}" })
            }
            list.addView(button(summary, View.NO_ID, Color.WHITE, ink).apply {
                textSize = 14f
                maxLines = 4
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setOnClickListener { historyDialog.dismiss(); showGameDetails(game) }
            }, matchWrap().apply { bottomMargin = dp(8) })
        }
        historyDialog = AlertDialog.Builder(this).setTitle(R.string.history_title)
            .setView(ScrollView(this).apply { addView(list) }).setPositiveButton(R.string.close, null).create()
        historyDialog.show()
    }

    private fun showGameDetails(game: Game) {
        val details = text(exportText(game), 16f, ink).apply {
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setTextIsSelectable(true)
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.game_date, date(game.startedAt)))
            .setView(ScrollView(this).apply { addView(details) })
            .setPositiveButton(R.string.share) { _, _ -> shareGame(game) }
            .setNegativeButton(R.string.close, null)
            .setNeutralButton(R.string.delete_game, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                confirm(R.string.delete_title, R.string.delete_body, R.string.delete_confirm) {
                    if (mutate { repository.deleteGame(game.id) }) dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun shareGame(game: Game) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
            putExtra(Intent.EXTRA_TEXT, exportText(game))
        }
        try {
            startActivity(Intent.createChooser(send, getString(R.string.share_chooser)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_share_app, Toast.LENGTH_LONG).show()
        }
    }

    private fun exportText(game: Game): String = buildString {
        append(getString(R.string.app_name)).append('\n')
        append(date(game.startedAt)).append("\n\n")
        append(if (game.isFinished) winnerText(game) else getString(R.string.live_results)).append('\n')
        game.players.forEach { append(it.name).append(": ").append(game.total(it.id)).append('\n') }
        append('\n').append(getString(R.string.round_history)).append('\n')
        game.rounds.forEach { round ->
            append(getString(R.string.round_title, round.number)).append(": ")
            append(game.players.joinToString("; ") { "${it.name}: ${round.points[it.id] ?: 0L}" })
            append('\n')
        }
    }

    private fun winnerText(game: Game): String {
        val names = game.players.filter { it.id in game.winnerIds }.joinToString(", ") { it.name }
        return getString(if (game.winnerIds.size == 1) R.string.winner else R.string.tie, names)
    }

    private fun scoreDescription(game: Game, player: Player) = getString(R.string.score_description,
        player.name, game.total(player.id).toString(), (game.currentRound.points[player.id] ?: 0L).toString())

    private fun mutate(fullRender: Boolean = true, resetScroll: Boolean = false, action: () -> Unit): Boolean {
        return try {
            action()
            if (fullRender) render(resetScroll) else refreshScores()
            true
        } catch (_: IOException) {
            AlertDialog.Builder(this).setTitle(R.string.operation_failed).setMessage(R.string.save_failed)
                .setPositiveButton(R.string.close, null).show()
            false
        } catch (error: IllegalArgumentException) {
            AlertDialog.Builder(this).setTitle(R.string.operation_failed)
                .setMessage(if (error.message?.contains("range", ignoreCase = true) == true)
                    getString(R.string.score_out_of_range) else getString(R.string.operation_failed))
                .setPositiveButton(R.string.close, null).show()
            false
        } catch (_: IllegalStateException) {
            AlertDialog.Builder(this).setTitle(R.string.operation_failed)
                .setMessage(R.string.operation_failed).setPositiveButton(R.string.close, null).show()
            render()
            false
        }
    }

    private fun confirm(title: Int, body: Int, positive: Int, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(body)
            .setPositiveButton(positive) { _, _ -> action() }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun date(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
    private fun fitLegacyScore(value: TextView) {
        if (Build.VERSION.SDK_INT >= 26) return
        value.post {
            val available = value.width - value.paddingLeft - value.paddingRight
            if (available <= 0) return@post
            val paint = android.graphics.Paint(value.paint)
            var size = 46f
            paint.textSize = size * resources.displayMetrics.scaledDensity
            while (size > 12f && paint.measureText(value.text.toString()) > available) {
                size -= 1f
                paint.textSize = size * resources.displayMetrics.scaledDensity
            }
            value.textSize = size
        }
    }
    private fun hideKeyboard(view: View) = (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
        .hideSoftInputFromWindow(view.windowToken, 0)
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
    private fun matchWrap() = LinearLayout.LayoutParams(-1, -2)
    private fun shape(color: Int, radius: Float = 14f) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius * resources.displayMetrics.density
    }
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun button(value: String, viewId: Int, color: Int, textColor: Int) = Button(this).apply {
        id = viewId
        text = value
        textSize = 14f
        isAllCaps = false
        setTextColor(textColor)
        setTypeface(typeface, Typeface.BOLD)
        background = shape(color)
        minHeight = dp(48)
        minimumHeight = dp(48)
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(10), dp(8), dp(10), dp(8))
        stateListAnimator = null
    }
    private fun input(value: String, viewId: Int) = EditText(this).apply {
        id = viewId
        setText(value)
        textSize = 17f
        setTextColor(ink)
        setHintTextColor(muted)
        minHeight = dp(52)
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
    }

    private val playerNameIds = intArrayOf(R.id.player_name_input_0, R.id.player_name_input_1, R.id.player_name_input_2,
        R.id.player_name_input_3, R.id.player_name_input_4, R.id.player_name_input_5, R.id.player_name_input_6, R.id.player_name_input_7)
    private val scorePlusIds = intArrayOf(R.id.score_plus_0, R.id.score_plus_1, R.id.score_plus_2, R.id.score_plus_3,
        R.id.score_plus_4, R.id.score_plus_5, R.id.score_plus_6, R.id.score_plus_7)
    private val scoreMinusIds = intArrayOf(R.id.score_minus_0, R.id.score_minus_1, R.id.score_minus_2, R.id.score_minus_3,
        R.id.score_minus_4, R.id.score_minus_5, R.id.score_minus_6, R.id.score_minus_7)
    private val scoreExactIds = intArrayOf(R.id.score_exact_0, R.id.score_exact_1, R.id.score_exact_2, R.id.score_exact_3,
        R.id.score_exact_4, R.id.score_exact_5, R.id.score_exact_6, R.id.score_exact_7)
    private val scoreValueIds = intArrayOf(R.id.score_value_0, R.id.score_value_1, R.id.score_value_2, R.id.score_value_3,
        R.id.score_value_4, R.id.score_value_5, R.id.score_value_6, R.id.score_value_7)
}
