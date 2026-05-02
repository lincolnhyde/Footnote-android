package com.footnote.app.catalog.curated

import android.net.Uri
import com.footnote.app.catalog.Slot
import com.footnote.app.catalog.SlotAction
import com.footnote.app.catalog.SlotIcon

data class CuratedApp(
    val packageName: String,
    val branch: Slot.Branch
)

object CuratedCatalog {

    val Spotify = CuratedApp(
        packageName = "com.spotify.music",
        branch = Slot.Branch(
            id = "curated.spotify",
            label = "Spotify",
            icon = SlotIcon.AppIcon("com.spotify.music"),
            children = { _ ->
                listOf(
                    leaf("spotify.search", "Search", "spotify:search", "com.spotify.music"),
                    leaf("spotify.liked", "Liked", "spotify:collection:tracks", "com.spotify.music"),
                    leaf("spotify.playlists", "Playlists", "spotify:collection:playlists", "com.spotify.music"),
                    leaf("spotify.podcasts", "Podcasts", "spotify:collection:podcasts", "com.spotify.music"),
                    Slot.Leaf(
                        id = "spotify.open",
                        label = "Open",
                        icon = SlotIcon.AppIcon("com.spotify.music"),
                        action = SlotAction.LaunchApp("com.spotify.music")
                    )
                )
            }
        )
    )

    val Maps = CuratedApp(
        packageName = "com.google.android.apps.maps",
        branch = Slot.Branch(
            id = "curated.maps",
            label = "Maps",
            icon = SlotIcon.AppIcon("com.google.android.apps.maps"),
            children = { _ ->
                listOf(
                    leaf("maps.home", "Home", "google.navigation:q=home", "com.google.android.apps.maps"),
                    leaf("maps.work", "Work", "google.navigation:q=work", "com.google.android.apps.maps"),
                    leaf("maps.nearby", "Nearby", "geo:0,0?q=restaurants", "com.google.android.apps.maps"),
                    leaf("maps.search", "Search", "geo:0,0?q=", "com.google.android.apps.maps"),
                    Slot.Leaf(
                        id = "maps.open",
                        label = "Open",
                        icon = SlotIcon.AppIcon("com.google.android.apps.maps"),
                        action = SlotAction.LaunchApp("com.google.android.apps.maps")
                    )
                )
            }
        )
    )

    val YouTube = CuratedApp(
        packageName = "com.google.android.youtube",
        branch = Slot.Branch(
            id = "curated.youtube",
            label = "YouTube",
            icon = SlotIcon.AppIcon("com.google.android.youtube"),
            children = { _ ->
                listOf(
                    leaf("youtube.search", "Search", "vnd.youtube://results?search_query=", "com.google.android.youtube"),
                    leaf("youtube.subs", "Subs", "vnd.youtube://www.youtube.com/feed/subscriptions", "com.google.android.youtube"),
                    leaf("youtube.library", "Library", "vnd.youtube://www.youtube.com/feed/library", "com.google.android.youtube"),
                    Slot.Leaf(
                        id = "youtube.open",
                        label = "Open",
                        icon = SlotIcon.AppIcon("com.google.android.youtube"),
                        action = SlotAction.LaunchApp("com.google.android.youtube")
                    )
                )
            }
        )
    )

    val ALL: List<CuratedApp> = listOf(Spotify, Maps, YouTube)

    private fun leaf(id: String, label: String, uri: String, fallbackPkg: String): Slot.Leaf =
        Slot.Leaf(
            id = id,
            label = label,
            action = SlotAction.Deeplink(Uri.parse(uri), fallbackPackage = fallbackPkg)
        )
}
