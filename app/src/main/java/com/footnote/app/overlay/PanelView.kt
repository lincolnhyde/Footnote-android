package com.footnote.app.overlay

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.footnote.app.catalog.Slot

/**
 * The panel UI rendered into a fixed-width window on the right edge. Receives
 * depth + content from [OverlayController]; doesn't own gesture logic. Its
 * window starts as NOT_TOUCHABLE so the strip can capture the gesture; the
 * controller flips it to touchable on settle so users can tap rows / search.
 */
class PanelView(context: Context) : LinearLayout(context) {

    interface Callbacks {
        fun onLeafTap(leaf: Slot.Leaf)
        fun onSearch(query: String): List<Slot.Leaf>
        fun onCloseTap()
    }

    var callbacks: Callbacks? = null
    private var allSuggested: List<Slot.Leaf> = emptyList()
    private var currentDepth: PullDepth = PullDepth.Closed

    private val accent = Color.parseColor("#E8B86E")
    private val muted = Color.parseColor("#7A7570")
    private val panelBg = Color.parseColor("#F21A1816")

    private val headerLabel: TextView
    private val closeButton: TextView
    private val searchInput: EditText
    private val rowsScroll: ScrollView
    private val rowsContainer: LinearLayout

    init {
        orientation = VERTICAL
        setBackgroundColor(panelBg)
        setPadding(dp(20), dp(56), dp(20), dp(40))
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Header row: title + close button
        val headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        headerLabel = TextView(context).apply {
            text = "Suggested"
            setTextColor(muted)
            textSize = 11f
            letterSpacing = 0.2f
            setPadding(dp(12), 0, 0, 0)
        }
        closeButton = TextView(context).apply {
            text = "✕"
            setTextColor(muted)
            textSize = 18f
            isClickable = true
            isFocusable = true
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { callbacks?.onCloseTap() }
        }
        headerRow.addView(
            headerLabel,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        headerRow.addView(closeButton)
        addView(
            headerRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        )

        searchInput = EditText(context).apply {
            hint = "Search apps & settings"
            setHintTextColor(muted)
            setTextColor(accent)
            textSize = 14f
            background = null
            visibility = View.GONE
            setPadding(dp(8), dp(12), dp(8), dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (currentDepth == PullDepth.Search) renderSearchResults(s?.toString().orEmpty())
                }
            })
        }
        addView(searchInput)

        rowsContainer = LinearLayout(context).apply { orientation = VERTICAL }
        rowsScroll = ScrollView(context).apply {
            isFillViewport = false
            addView(
                rowsContainer,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        addView(
            rowsScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            callbacks?.onCloseTap()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    fun setSuggested(items: List<Slot.Leaf>) {
        allSuggested = items
        if (currentDepth != PullDepth.Search && currentDepth != PullDepth.Closed) {
            renderForDepth(currentDepth)
        }
    }

    fun renderForDepth(depth: PullDepth) {
        currentDepth = depth
        when (depth) {
            PullDepth.Closed -> { /* no-op; controller handles visibility */ }
            PullDepth.Immediate -> {
                searchInput.visibility = View.GONE
                headerLabel.text = "Suggested"
                renderRows(allSuggested.take(IMMEDIATE_COUNT), showDeeperHints = true)
            }
            PullDepth.Stack -> {
                searchInput.visibility = View.GONE
                headerLabel.text = "Recent & frequent"
                renderRows(allSuggested.take(STACK_COUNT), showDeeperHints = true)
            }
            PullDepth.Search -> {
                searchInput.visibility = View.VISIBLE
                headerLabel.text = "Search"
                renderSearchResults(searchInput.text?.toString().orEmpty())
            }
        }
    }

    fun focusSearchInput() {
        if (currentDepth != PullDepth.Search) return
        searchInput.requestFocus()
        runCatching {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun resetSearch() {
        searchInput.setText("")
        searchInput.clearFocus()
        runCatching {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(windowToken, 0)
        }
    }

    private fun renderSearchResults(query: String) {
        val results = if (query.isBlank()) allSuggested.take(STACK_COUNT)
        else callbacks?.onSearch(query).orEmpty()
        renderRows(results, showDeeperHints = false)
    }

    private fun renderRows(items: List<Slot.Leaf>, showDeeperHints: Boolean) {
        rowsContainer.removeAllViews()
        if (items.isEmpty()) {
            rowsContainer.addView(TextView(context).apply {
                text = "No suggestions yet — open an app to start training."
                setTextColor(muted)
                textSize = 12f
                setPadding(dp(12), dp(24), dp(12), dp(24))
            })
        } else {
            for (leaf in items) {
                val row = TextView(context).apply {
                    text = leaf.label
                    setTextColor(accent)
                    textSize = 16f
                    isClickable = true
                    isFocusable = true
                    setPadding(dp(12), dp(16), dp(12), dp(16))
                    setOnClickListener { callbacks?.onLeafTap(leaf) }
                }
                rowsContainer.addView(row)
            }
        }
        if (!showDeeperHints) return
        when (currentDepth) {
            PullDepth.Immediate -> addHintRow("Show more ↓") {
                renderForDepth(PullDepth.Stack)
            }
            PullDepth.Stack -> addHintRow("Search…") {
                renderForDepth(PullDepth.Search)
                focusSearchInput()
            }
            else -> Unit
        }
    }

    private fun addHintRow(label: String, onClick: () -> Unit) {
        val row = TextView(context).apply {
            text = label
            setTextColor(muted)
            textSize = 13f
            isClickable = true
            isFocusable = true
            setPadding(dp(12), dp(16), dp(12), dp(16))
            setOnClickListener { onClick() }
        }
        rowsContainer.addView(row)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
