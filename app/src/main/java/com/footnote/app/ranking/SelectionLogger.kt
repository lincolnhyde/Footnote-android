package com.footnote.app.ranking

import android.content.Context
import com.footnote.app.data.FootnoteDb
import com.footnote.app.data.SelectionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object SelectionLogger {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun log(ctx: Context, slotId: String, snapshot: ContextSnapshot) {
        val appCtx = ctx.applicationContext
        scope.launch {
            FootnoteDb.get(appCtx).selections().insert(
                SelectionEntity(
                    slotId = slotId,
                    foregroundPkg = snapshot.foregroundPkg,
                    triggerSource = snapshot.triggerSource,
                    hourOfDay = snapshot.hourOfDay,
                    dayOfWeek = snapshot.dayOfWeek,
                    ts = snapshot.timestamp
                )
            )
        }
    }
}
