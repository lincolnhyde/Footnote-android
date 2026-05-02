package com.footnote.app.catalog.providers

import android.provider.Settings
import com.footnote.app.catalog.Slot
import com.footnote.app.catalog.SlotAction
import com.footnote.app.catalog.SlotProvider
import com.footnote.app.ranking.ContextSnapshot

object SystemSettingsProvider : SlotProvider {
    override val id: String = "provider.settings"

    private val SETTINGS: List<Slot.Leaf> = listOf(
        leaf("settings.wifi", "Wi-Fi", Settings.ACTION_WIFI_SETTINGS),
        leaf("settings.bluetooth", "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS),
        leaf("settings.airplane", "Airplane", Settings.ACTION_AIRPLANE_MODE_SETTINGS),
        leaf("settings.display", "Display", Settings.ACTION_DISPLAY_SETTINGS),
        leaf("settings.sound", "Sound", Settings.ACTION_SOUND_SETTINGS),
        leaf("settings.battery", "Battery", Settings.ACTION_BATTERY_SAVER_SETTINGS),
        leaf("settings.apps", "Apps", Settings.ACTION_APPLICATION_SETTINGS),
        leaf("settings.storage", "Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
        leaf("settings.location", "Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS),
        leaf("settings.security", "Security", Settings.ACTION_SECURITY_SETTINGS),
        leaf("settings.privacy", "Privacy", Settings.ACTION_PRIVACY_SETTINGS),
        leaf("settings.network", "Network", Settings.ACTION_WIRELESS_SETTINGS),
        leaf("settings.data", "Data", Settings.ACTION_DATA_ROAMING_SETTINGS),
        leaf("settings.date", "Date & Time", Settings.ACTION_DATE_SETTINGS),
        leaf("settings.locale", "Language", Settings.ACTION_LOCALE_SETTINGS),
        leaf("settings.input", "Keyboard", Settings.ACTION_INPUT_METHOD_SETTINGS),
        leaf("settings.accessibility", "Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS),
        leaf("settings.notifications", "Notifications", "android.settings.NOTIFICATION_SETTINGS"),
        leaf("settings.dnd", "Do Not Disturb", "android.settings.ZEN_MODE_SETTINGS"),
        leaf("settings.about", "About", Settings.ACTION_DEVICE_INFO_SETTINGS),
        leaf("settings.developer", "Developer", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
        leaf("settings.home", "Home Settings", Settings.ACTION_HOME_SETTINGS),
        leaf("settings.search", "Search", Settings.ACTION_SETTINGS),
        leaf("settings.nfc", "NFC", Settings.ACTION_NFC_SETTINGS),
        leaf("settings.cast", "Cast", "android.settings.CAST_SETTINGS")
    )

    val branch: Slot.Branch = Slot.Branch(
        id = "settings.root",
        label = "Settings",
        children = { SETTINGS }
    )

    override suspend fun slots(ctx: ContextSnapshot): List<Slot> = listOf(branch)

    private fun leaf(id: String, label: String, action: String): Slot.Leaf =
        Slot.Leaf(id = id, label = label, action = SlotAction.SettingsPanel(action))
}
