package com.footnote.app.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.footnote.app.MainActivity
import com.footnote.app.catalog.CatalogRoot
import com.footnote.app.catalog.SearchIndex
import com.footnote.app.catalog.Slot
import com.footnote.app.core.IntentLauncher
import com.footnote.app.ranking.ContextSnapshot
import com.footnote.app.ranking.SelectionLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that owns two WindowManager overlay windows:
 *
 *   StripWindow — 24dp wide, always alive at the right edge. Captures the
 *     pull gesture. Once it owns ACTION_DOWN, the InputDispatcher routes all
 *     subsequent MOVE events here even if the finger leaves the window's
 *     bounds — so we get the full-screen pull without resizing.
 *
 *   PanelWindow — fixed-width (PANEL_WIDTH_DP) on the right edge. Visible
 *     during gesture and after settle. Starts NOT_TOUCHABLE so the strip
 *     keeps gesture ownership; flips to touchable on settle so the user can
 *     tap rows. Becomes focusable when entering Search depth so the EditText
 *     can receive input.
 *
 * The two windows do not overlap touchably: the strip is always touchable on
 * its 24dp slice; the panel becomes touchable on the 280dp slab that does
 * not include the rightmost 24dp (we offset its right edge by the strip
 * width so the strip stays clickable for dismiss-on-tap).
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var catalog: CatalogRoot

    private var stripView: StripView? = null
    private var panelView: PanelView? = null

    @Volatile private var searchablePool: List<Slot.Leaf> = emptyList()

    private enum class Mode { Closed, Gesturing, SettledOpen }
    private var mode: Mode = Mode.Closed

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        catalog = CatalogRoot(applicationContext)
        startInForeground()
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (stripView == null) showOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        stripView?.let { runCatching { wm.removeView(it) } }
        panelView?.let { runCatching { wm.removeView(it) } }
        stripView = null
        panelView = null
        super.onDestroy()
    }

    private fun startInForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Footnote launcher",
                NotificationManager.IMPORTANCE_MIN
            ).apply { setShowBadge(false) }
            nm.createNotificationChannel(ch)
        }
        val tap = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Footnote launcher")
            .setContentText("Pull from right edge to open")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(tap)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        if (stripView != null) return

        val strip = StripView(this)
        val panel = PanelView(this)

        strip.callbacks = object : StripView.Callbacks {
            override fun onPressStart() {
                mode = Mode.Gesturing
                panel.visibility = View.VISIBLE
                panel.renderForDepth(PullDepth.Immediate)
                // Stay non-touchable during gesture — strip owns the touch sequence.
                applyPanelFlags(touchable = false, focusable = false)
            }

            override fun onPullChange(pullFraction: Float) {
                if (mode != Mode.Gesturing) return
                val live = depthForFraction(pullFraction, snap = false)
                panel.renderForDepth(live)
            }

            override fun onPressEnd(pullFraction: Float) {
                if (mode != Mode.Gesturing) return
                val snapped = depthForFraction(pullFraction, snap = true)
                if (snapped == PullDepth.Closed) {
                    dismiss()
                } else {
                    mode = Mode.SettledOpen
                    panel.renderForDepth(snapped)
                    applyPanelFlags(touchable = true, focusable = snapped == PullDepth.Search)
                    if (snapped == PullDepth.Search) panel.focusSearchInput()
                    strip.dismissOnTap = true
                }
            }

            override fun onDismissRequest() {
                dismiss()
            }
        }

        panel.callbacks = object : PanelView.Callbacks {
            override fun onLeafTap(leaf: Slot.Leaf) {
                IntentLauncher.launch(applicationContext, leaf.action)
                SelectionLogger.log(
                    applicationContext,
                    leaf.id,
                    ContextSnapshot.now(triggerSource = "EDGE_SWIPE")
                )
                dismiss()
                refreshSuggestions()
                refreshSearchablePool()
            }

            override fun onSearch(query: String): List<Slot.Leaf> =
                SearchIndex.search(query, searchablePool, limit = 12)

            override fun onCloseTap() {
                dismiss()
            }
        }

        runCatching { wm.addView(strip, stripParams()) }
        panel.visibility = View.GONE
        runCatching { wm.addView(panel, panelParams(touchable = false, focusable = false)) }

        stripView = strip
        panelView = panel
        refreshSuggestions()
        refreshSearchablePool()
    }

    private fun dismiss() {
        mode = Mode.Closed
        val panel = panelView ?: return
        panel.resetSearch()
        panel.visibility = View.GONE
        applyPanelFlags(touchable = false, focusable = false)
        stripView?.dismissOnTap = false
    }

    private fun applyPanelFlags(touchable: Boolean, focusable: Boolean) {
        val panel = panelView ?: return
        runCatching { wm.updateViewLayout(panel, panelParams(touchable, focusable)) }
    }

    private fun refreshSuggestions() {
        val panel = panelView ?: return
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                runCatching {
                    catalog.suggested(
                        ContextSnapshot.now(triggerSource = "EDGE_SWIPE"),
                        limit = STACK_COUNT
                    )
                }.getOrDefault(emptyList())
            }
            panel.setSuggested(items)
        }
    }

    private fun refreshSearchablePool() {
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                runCatching {
                    catalog.allSearchable(ContextSnapshot.now(triggerSource = "EDGE_SWIPE"))
                }.getOrDefault(emptyList())
            }
            searchablePool = items
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun stripParams(): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        val density = resources.displayMetrics.density
        return WindowManager.LayoutParams(
            (STRIP_WIDTH_DP * density).toInt(),
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
    }

    private fun panelParams(touchable: Boolean, focusable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        val density = resources.displayMetrics.density
        return WindowManager.LayoutParams(
            (PANEL_WIDTH_DP * density).toInt(),
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Anchor right edge — but offset by the strip width so the strip
            // window stays clickable for dismiss-on-tap.
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = (STRIP_WIDTH_DP * density).toInt()
        }
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "footnote-overlay"
        private const val STRIP_WIDTH_DP = 24
        private const val PANEL_WIDTH_DP = 300

        fun start(ctx: Context) {
            val intent = Intent(ctx, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(intent)
            else ctx.startService(intent)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }
    }
}
