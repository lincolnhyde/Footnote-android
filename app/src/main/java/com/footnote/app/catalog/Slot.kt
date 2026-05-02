package com.footnote.app.catalog

import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import com.footnote.app.ranking.ContextSnapshot

sealed interface Slot {
    val id: String
    val label: String
    val icon: SlotIcon?
    val keywords: List<String>

    data class Leaf(
        override val id: String,
        override val label: String,
        override val icon: SlotIcon? = null,
        override val keywords: List<String> = emptyList(),
        val action: SlotAction
    ) : Slot

    data class Branch(
        override val id: String,
        override val label: String,
        override val icon: SlotIcon? = null,
        override val keywords: List<String> = emptyList(),
        val children: suspend (ContextSnapshot) -> List<Slot>
    ) : Slot
}

sealed interface SlotAction {
    data class LaunchIntent(val intent: Intent) : SlotAction
    data class LaunchApp(val packageName: String) : SlotAction
    data class Deeplink(val uri: Uri, val fallbackPackage: String? = null) : SlotAction
    data class SettingsPanel(val action: String) : SlotAction
    data object Pop : SlotAction
}

sealed interface SlotIcon {
    data class Vector(@DrawableRes val res: Int) : SlotIcon
    data class AppIcon(val packageName: String) : SlotIcon
    data class Letter(val char: Char) : SlotIcon
}
