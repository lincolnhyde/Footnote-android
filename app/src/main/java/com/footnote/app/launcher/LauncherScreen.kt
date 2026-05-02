package com.footnote.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.footnote.app.catalog.CatalogRoot
import com.footnote.app.catalog.SearchIndex
import com.footnote.app.catalog.Slot
import com.footnote.app.catalog.SlotIcon
import com.footnote.app.ranking.ContextSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Bg = Color(0xFF0B0A09)
private val SurfaceBg = Color(0xFF1A1816)
private val Accent = Color(0xFFE8B86E)
private val FaintText = Color(0xFF6E6A65)
private val Divider = Color(0x33E8B86E)

@Composable
fun LauncherScreen(
    triggerSource: String,
    onLaunch: (Slot.Leaf) -> Unit
) {
    val ctx = LocalContext.current
    val catalog = remember { CatalogRoot(ctx.applicationContext) }

    var query by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var suggested by remember { mutableStateOf<List<Slot.Leaf>>(emptyList()) }
    var pool by remember { mutableStateOf<List<Slot.Leaf>>(emptyList()) }

    LaunchedEffect(triggerSource) {
        val ctxSnap = ContextSnapshot.now(triggerSource = triggerSource)
        launch {
            withContext(Dispatchers.IO) {
                runCatching { catalog.suggested(ctxSnap, limit = 7) }
                    .getOrDefault(emptyList())
            }.also { suggested = it }
        }
        launch {
            withContext(Dispatchers.IO) {
                runCatching { catalog.allSearchable(ctxSnap) }.getOrDefault(emptyList())
            }.also { pool = it }
        }
    }

    val results: List<Slot.Leaf> = remember(query, pool) {
        if (query.isBlank()) emptyList() else SearchIndex.search(query, pool, limit = 30)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(top = 32.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (searchActive || query.isNotBlank()) {
                SearchResults(
                    query = query,
                    results = results,
                    onTap = onLaunch,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                )
            } else {
                TetrisPredictionGrid(
                    items = suggested,
                    onTap = onLaunch,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
        BottomSearchStrip(
            value = query,
            onChange = { query = it },
            onFocusChange = { focused ->
                if (focused) searchActive = true
                else if (query.isBlank()) searchActive = false
            }
        )
    }
}

@Composable
private fun TetrisPredictionGrid(
    items: List<Slot.Leaf>,
    onTap: (Slot.Leaf) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.getOrNull(0)?.let { leaf ->
            Tile(
                leaf = leaf,
                onTap = onTap,
                size = TileSize.Huge,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f)
            )
        }
        if (items.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items.getOrNull(1)?.let {
                    Tile(it, onTap, TileSize.Wide, Modifier.weight(1f).fillMaxHeight())
                }
                items.getOrNull(2)?.let {
                    Tile(it, onTap, TileSize.Wide, Modifier.weight(1f).fillMaxHeight())
                } ?: Spacer(Modifier.weight(1f))
            }
        }
        if (items.size > 3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (i in 3..6) {
                    val leaf = items.getOrNull(i)
                    if (leaf != null) {
                        Tile(leaf, onTap, TileSize.Small, Modifier.weight(1f).fillMaxHeight())
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private enum class TileSize { Huge, Wide, Small }

private data class TileMetrics(val iconSize: Dp, val fontSize: TextUnit, val padding: Dp)

@Composable
private fun Tile(
    leaf: Slot.Leaf,
    onTap: (Slot.Leaf) -> Unit,
    size: TileSize,
    modifier: Modifier = Modifier
) {
    val m = when (size) {
        TileSize.Huge -> TileMetrics(56.dp, 20.sp, 22.dp)
        TileSize.Wide -> TileMetrics(40.dp, 14.sp, 16.dp)
        TileSize.Small -> TileMetrics(32.dp, 11.sp, 10.dp)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceBg)
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .clickable { onTap(leaf) }
            .padding(m.padding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppGlyph(leaf, m.iconSize)
        Text(
            text = leaf.label,
            color = Accent,
            fontSize = m.fontSize,
            fontWeight = FontWeight.Light,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchResults(
    query: String,
    results: List<Slot.Leaf>,
    onTap: (Slot.Leaf) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when {
            query.isBlank() -> Text(
                "Type to search apps & settings.",
                color = FaintText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 16.dp)
            )
            results.isEmpty() -> Text(
                "Nothing matches \"$query\".",
                color = FaintText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(top = 16.dp)
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { leaf ->
                    AppRow(leaf = leaf, onTap = onTap)
                }
            }
        }
    }
}

@Composable
private fun AppRow(leaf: Slot.Leaf, onTap: (Slot.Leaf) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onTap(leaf) }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppGlyph(leaf = leaf, size = 36.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            text = leaf.label,
            color = Accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BottomSearchStrip(
    value: String,
    onChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = {
            Text("Search apps & settings", color = FaintText, fontSize = 14.sp)
        },
        textStyle = TextStyle(
            color = Accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.Light
        ),
        singleLine = true,
        shape = RoundedCornerShape(0.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = SurfaceBg,
            unfocusedContainerColor = SurfaceBg,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Accent,
            focusedTextColor = Accent,
            unfocusedTextColor = Accent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { onFocusChange(it.isFocused) }
    )
}

@Composable
private fun AppGlyph(leaf: Slot.Leaf, size: Dp) {
    val ctx = LocalContext.current
    val painter: Painter? = when (val ic = leaf.icon) {
        is SlotIcon.AppIcon -> remember(ic.packageName) {
            runCatching {
                val drawable = ctx.packageManager.getApplicationIcon(ic.packageName)
                BitmapPainter(drawable.toBitmap(96, 96).asImageBitmap())
            }.getOrNull()
        }
        else -> null
    }
    if (painter != null) {
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp))
        )
    } else {
        val ch = (leaf.icon as? SlotIcon.Letter)?.char
            ?: leaf.label.firstOrNull()
            ?: '·'
        Box(
            modifier = Modifier
                .size(size)
                .background(Color(0xFF2A2825), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ch.uppercaseChar().toString(),
                color = Accent,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Light
            )
        }
    }
}
