package com.footnote.app.overlay

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.footnote.app.catalog.Slot

/**
 * Single-View overlay host. Renders the right-edge strip when closed, expands
 * into a vertical panel when the user pulls inward. Owns its own touch loop;
 * coordinates the state machine and asks the host service to resize the
 * WindowManager window between strip and fullscreen.
 *
 * Plain Views (no Compose) keep this file livable inside a Service without the
 * lifecycle/view-tree-owner ceremony Compose needs there. UI fidelity is
 * deliberately rough for Slice 1 — the test is whether the trigger surface
 * works, not whether it's beautiful.
 */
class OverlayHostView(context: Context) : FrameLayout(context) {

    interface Callbacks {
        /** Ask host to resize WindowManager window to fullscreen. */
        fun onActivate()
        /** Ask host to resize WindowManager window back to edge strip. */
        fun onDismiss()
        /** Fire a leaf via IntentLauncher + log the selection. */
        fun onLeafTap(leaf: Slot.Leaf)
        /** Synchronous in-memory search against the prefetched pool. */
        fun onSearch(query: String): List<Slot.Leaf>
    }

    var callbacks: Callbacks? = null

    private val accent = Color.parseColor("#E8B86E")
    private val muted = Color.parseColor("#7A7570")
    private val panelBg = Color.parseColor("#E61A1816")
    private val stripTint = Color.parseColor("#33E8B86E")

    private val stripIndicator: View
    private val panelContainer: LinearLayout
    private val searchInput: EditText
    private val rowsScroll: ScrollView
    private val rowsContainer: LinearLayout
    private val headerLabel: TextView

    private enum class Mode { Closed, Gesturing, SettledOpen }

    private var mode: Mode = Mode.Closed
    private var currentDepth: PullDepth = PullDepth.Closed
    private var pullFraction: Float = 0f

    private var allSuggested: List<Slot.Leaf> = emptyList()

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        stripIndicator = View(context).apply {
            setBackgroundColor(stripTint)
            layoutParams = LayoutParams(dp(4), LayoutParams.MATCH_PARENT, Gravity.END)
        }
        addView(stripIndicator)

        panelContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panelBg)
            visibility = View.GONE
            layoutParams = LayoutParams(dp(280), LayoutParams.MATCH_PARENT, Gravity.END)
            setPadding(dp(20), dp(56), dp(20), dp(40))
        }

        headerLabel = TextView(context).apply {
            text = "Suggested"
            setTextColor(muted)
            textSize = 11f
            letterSpacing = 0.2f
            setPadding(dp(12), 0, dp(12), dp(12))
        }
        panelContainer.addView(headerLabel)

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
        panelContainer.addView(searchInput)

        rowsContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
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
        panelContainer.addView(
            rowsScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        addView(panelContainer)
    }

    fun setSuggested(items: List<Slot.Leaf>) {
        allSuggested = items
        if (mode == Mode.SettledOpen && currentDepth != PullDepth.Search) {
            renderForDepth(currentDepth)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                return when (mode) {
                    Mode.Closed -> {
                        mode = Mode.Gesturing
                        callbacks?.onActivate()
                        pullFraction = 0f
                        panelContainer.visibility = View.VISIBLE
                        renderForDepth(PullDepth.Immediate)
                        true
                    }
                    Mode.SettledOpen -> {
                        if (!isInPanel(event.rawX)) {
                            dismiss()
                            true
                        } else {
                            // Let children (row TextViews) handle the click.
                            false
                        }
                    }
                    Mode.Gesturing -> false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode != Mode.Gesturing) return false
                val sw = resources.displayMetrics.widthPixels.toFloat()
                pullFraction = ((sw - event.rawX) / sw).coerceIn(0f, 1f)
                val live = depthForFraction(pullFraction, snap = false)
                if (live != currentDepth) renderForDepth(live)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mode != Mode.Gesturing) return false
                val snapped = depthForFraction(pullFraction, snap = true)
                if (snapped == PullDepth.Closed) {
                    dismiss()
                } else {
                    mode = Mode.SettledOpen
                    renderForDepth(snapped)
                    if (snapped == PullDepth.Search) {
                        searchInput.requestFocus()
                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun renderForDepth(depth: PullDepth) {
        currentDepth = depth
        when (depth) {
            PullDepth.Closed -> { /* dismiss handles teardown */ }
            PullDepth.Immediate -> {
                searchInput.visibility = View.GONE
                headerLabel.text = "Suggested"
                renderRows(allSuggested.take(IMMEDIATE_COUNT))
            }
            PullDepth.Stack -> {
                searchInput.visibility = View.GONE
                headerLabel.text = "Recent & frequent"
                renderRows(allSuggested.take(STACK_COUNT))
            }
            PullDepth.Search -> {
                searchInput.visibility = View.VISIBLE
                headerLabel.text = "Search"
                renderSearchResults(searchInput.text?.toString().orEmpty())
            }
        }
    }

    private fun renderSearchResults(query: String) {
        val results = if (query.isBlank()) allSuggested.take(STACK_COUNT)
        else callbacks?.onSearch(query).orEmpty()
        renderRows(results)
    }

    private fun renderRows(items: List<Slot.Leaf>) {
        rowsContainer.removeAllViews()
        if (items.isEmpty()) {
            rowsContainer.addView(TextView(context).apply {
                text = "No suggestions yet — open an app to start training."
                setTextColor(muted)
                textSize = 12f
                setPadding(dp(12), dp(24), dp(12), dp(24))
            })
            return
        }
        for (leaf in items) {
            val row = TextView(context).apply {
                text = leaf.label
                setTextColor(accent)
                textSize = 16f
                isClickable = true
                isFocusable = true
                setPadding(dp(12), dp(16), dp(12), dp(16))
                setOnClickListener {
                    callbacks?.onLeafTap(leaf)
                    dismiss()
                }
            }
            rowsContainer.addView(row)
        }
    }

    private fun dismiss() {
        mode = Mode.Closed
        currentDepth = PullDepth.Closed
        pullFraction = 0f
        panelContainer.visibility = View.GONE
        searchInput.setText("")
        searchInput.clearFocus()
        runCatching {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(windowToken, 0)
        }
        callbacks?.onDismiss()
    }

    private fun isInPanel(rawX: Float): Boolean {
        val sw = resources.displayMetrics.widthPixels.toFloat()
        return rawX > (sw - dp(280).toFloat())
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
