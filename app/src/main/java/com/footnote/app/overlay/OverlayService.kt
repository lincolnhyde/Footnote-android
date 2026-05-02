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
 * Foreground service that owns a single WindowManager overlay window. Resizes
 * the window between an edge strip (resting) and fullscreen (panel open) in
 * response to gestures from [OverlayHostView]. Touch sequences survive the
 * resize because we use [android.view.MotionEvent.getRawX], not window-local
 * coords, for pull math.
 */
class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private var hostView: OverlayHostView? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var catalog: CatalogRoot

    @Volatile
    private var searchablePool: List<Slot.Leaf> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        catalog = CatalogRoot(applicationContext)
        startInForeground()
        showOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (hostView == null) showOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        hostView?.let { runCatching { wm.removeView(it) } }
        hostView = null
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
        } else if (Build.VERSION.SDK_INT >= 29) {
            @Suppress("DEPRECATION")
            startForeground(NOTIF_ID, notif)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        if (hostView != null) return
        val host = OverlayHostView(this)
        host.callbacks = object : OverlayHostView.Callbacks {
            override fun onActivate() {
                runCatching { wm.updateViewLayout(host, fullscreenParams()) }
            }

            override fun onDismiss() {
                runCatching { wm.updateViewLayout(host, stripParams()) }
            }

            override fun onLeafTap(leaf: Slot.Leaf) {
                IntentLauncher.launch(applicationContext, leaf.action)
                SelectionLogger.log(
                    applicationContext,
                    leaf.id,
                    ContextSnapshot.now(triggerSource = "EDGE_SWIPE")
                )
                refreshSuggestions(host)
                refreshSearchablePool()
            }

            override fun onSearch(query: String): List<Slot.Leaf> =
                SearchIndex.search(query, searchablePool, limit = 12)
        }
        runCatching { wm.addView(host, stripParams()) }
        hostView = host
        refreshSuggestions(host)
        refreshSearchablePool()
    }

    private fun refreshSuggestions(host: OverlayHostView) {
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                runCatching {
                    catalog.suggested(
                        ContextSnapshot.now(triggerSource = "EDGE_SWIPE"),
                        limit = STACK_COUNT
                    )
                }.getOrDefault(emptyList())
            }
            host.setSuggested(items)
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
            (24 * density).toInt(),
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
    }

    private fun fullscreenParams(): WindowManager.LayoutParams {
        // Focusable when fullscreen so the search EditText can receive key events.
        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    companion object {
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "footnote-overlay"

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
