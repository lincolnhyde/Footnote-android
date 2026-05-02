package com.footnote.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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

private val Bg = Color(0xF20B0A09)
private val Accent = Color(0xFFE8B86E)
private val MutedText = Color(0xFF8A8580)
private val FaintText = Color(0xFF6E6A65)
private val Divider = Color(0x33E8B86E)

@Composable
fun LauncherScreen(
    onLaunch: (Slot.Leaf) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val catalog = remember { CatalogRoot(ctx.applicationContext) }

    var query by remember { mutableStateOf("") }
    var suggested by remember { mutableStateOf<List<Slot.Leaf>>(emptyList()) }
    var allApps by remember { mutableStateOf<List<Slot.Leaf>>(emptyList()) }
    var pool by remember { mutableStateOf<List<Slot.Leaf>>(emptyList()) }

    LaunchedEffect(Unit) {
        val ctxSnap = ContextSnapshot.now(triggerSource = "QS_TILE")
        launch {
            withContext(Dispatchers.IO) {
                runCatching { catalog.suggested(ctxSnap, limit = 6) }
                    .getOrDefault(emptyList())
            }.also { suggested = it }
        }
        launch {
            withContext(Dispatchers.IO) {
                runCatching { catalog.installedApps() }.getOrDefault(emptyList())
            }.also { allApps = it }
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

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp, start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            SearchField(
                value = query,
                onChange = { query = it },
                focusRequester = focusRequester,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (query.isBlank()) {
                    if (suggested.isNotEmpty()) {
                        item { SectionLabel("Suggested") }
                        item { SuggestedGrid(items = suggested, onTap = onLaunch) }
                        item { Spacer(Modifier.height(28.dp)) }
                    }
                    item { SectionLabel("All apps") }
                    items(allApps, key = { it.id }) { leaf ->
                        AppRow(leaf = leaf, onTap = onLaunch)
                    }
                    if (allApps.isEmpty()) {
                        item { LoadingHint("Loading apps…") }
                    }
                } else {
                    item { SectionLabel("Results") }
                    if (results.isEmpty()) {
                        item { LoadingHint("Nothing matches \"$query\".") }
                    } else {
                        items(results, key = { it.id }) { leaf ->
                            AppRow(leaf = leaf, onTap = onLaunch)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = {
            Text("Search apps & settings", color = FaintText, fontSize = 16.sp)
        },
        textStyle = TextStyle(
            color = Accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Light
        ),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF1A1816),
            unfocusedContainerColor = Color(0xFF1A1816),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Accent,
            focusedTextColor = Accent,
            unfocusedTextColor = Accent,
        ),
        modifier = modifier.focusRequester(focusRequester)
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = MutedText,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Light,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 12.dp)
    )
}

@Composable
private fun SuggestedGrid(items: List<Slot.Leaf>, onTap: (Slot.Leaf) -> Unit) {
    val rows = items.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { leaf ->
                    SuggestedTile(
                        leaf = leaf,
                        onTap = onTap,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SuggestedTile(
    leaf: Slot.Leaf,
    onTap: (Slot.Leaf) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1816))
            .border(1.dp, Divider, RoundedCornerShape(14.dp))
            .clickable { onTap(leaf) }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppGlyph(leaf = leaf, size = 36.dp)
        Text(
            text = leaf.label,
            color = Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Light,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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

@Composable
private fun LoadingHint(text: String) {
    Text(
        text = text,
        color = FaintText,
        fontSize = 13.sp,
        fontWeight = FontWeight.Light,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
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

