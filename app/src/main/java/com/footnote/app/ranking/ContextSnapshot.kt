package com.footnote.app.ranking

import java.util.Calendar

data class ContextSnapshot(
    val foregroundPkg: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val hourOfDay: Int = nowHour(),
    val dayOfWeek: Int = nowDow(),
    val triggerSource: String = "IN_APP"
) {
    companion object {
        fun now(foregroundPkg: String? = null, triggerSource: String = "IN_APP"): ContextSnapshot =
            ContextSnapshot(
                foregroundPkg = foregroundPkg,
                timestamp = System.currentTimeMillis(),
                hourOfDay = nowHour(),
                dayOfWeek = nowDow(),
                triggerSource = triggerSource
            )

        private fun nowHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        private fun nowDow(): Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    }
}

enum class TriggerSource {
    IN_APP, NOTIFICATION, EDGE_SWIPE, VOLUME_COMBO, QS_TILE
}
