/*
 * AutoTV – tegeloverzicht (GPL-3.0, bovenop AABrowser).
 *
 * Startscherm met een tegel per dienst (met logo), een zoektegel (Google)
 * en een plustegel om zelf een dienst toe te voegen. Lang drukken op een
 * tegel = verwijderen. Tijdens het kijken staat rechtsboven een kruis dat
 * terugbrengt naar dit overzicht.
 */

package com.kododake.aabrowser.autotv

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kododake.aabrowser.data.SiteIconCache
import org.json.JSONArray
import org.json.JSONObject

data class AutoTvTile(val name: String, val url: String)

object AutoTvTileStore {
    private const val PREFS = "autotv_tiles"
    private const val KEY = "tiles"

    const val SEARCH_URL = "https://www.google.com"

    val DEFAULT_TILES = listOf(
        AutoTvTile("YouTube", "https://m.youtube.com"),
        AutoTvTile("Netflix", "https://www.netflix.com/browse"),
        AutoTvTile("NPO Start", "https://npo.nl/start"),
        AutoTvTile("Videoland", "https://v2.videoland.com"),
        AutoTvTile("Prime Video", "https://www.primevideo.com"),
        AutoTvTile("Disney+", "https://www.disneyplus.com"),
    )

    fun load(context: Context): List<AutoTvTile> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?: return DEFAULT_TILES
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map {
                val o = array.getJSONObject(it)
                AutoTvTile(o.getString("name"), o.getString("url"))
            }
        }.getOrDefault(DEFAULT_TILES)
    }

    fun save(context: Context, tiles: List<AutoTvTile>) {
        val array = JSONArray()
        tiles.forEach { array.put(JSONObject().put("name", it.name).put("url", it.url)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }
}

private val HomeBackground = Color(0xFF101114)
private val TileColor = Color(0xFF1E2026)
private val TileTextColor = Color(0xFFECECEC)
private val AccentColor = Color(0xFF3D7BFF)

@Composable
fun AutoTvHomeScreen(onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val tiles = remember { mutableStateListOf<AutoTvTile>().apply { addAll(AutoTvTileStore.load(context)) } }
    var showAddDialog by remember { mutableStateOf(false) }
    var tileToDelete by remember { mutableStateOf<AutoTvTile?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeBackground)
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 170.dp),
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(tiles, key = { it.url + it.name }) { tile ->
                ServiceTile(
                    tile = tile,
                    onClick = { onOpen(tile.url) },
                    onLongClick = { tileToDelete = tile }
                )
            }
            item(key = "__search") {
                IconTile(icon = Icons.Rounded.Search, label = "Zoeken", onClick = { onOpen(AutoTvTileStore.SEARCH_URL) })
            }
            item(key = "__add") {
                IconTile(icon = Icons.Rounded.Add, label = "Toevoegen", onClick = { showAddDialog = true })
            }
        }
    }

    if (showAddDialog) {
        AddTileDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, url ->
                tiles.add(AutoTvTile(name, url))
                AutoTvTileStore.save(context, tiles)
                SiteIconCache.prefetchIconIfNeeded(context, url)
                showAddDialog = false
            }
        )
    }

    tileToDelete?.let { tile ->
        AlertDialog(
            onDismissRequest = { tileToDelete = null },
            title = { Text("${tile.name} verwijderen?") },
            text = { Text("De tegel verdwijnt van het startscherm. Je inloggegevens blijven bewaard.") },
            confirmButton = {
                TextButton(onClick = {
                    tiles.remove(tile)
                    AutoTvTileStore.save(context, tiles)
                    tileToDelete = null
                }) { Text("Verwijderen") }
            },
            dismissButton = { TextButton(onClick = { tileToDelete = null }) { Text("Annuleren") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileFrame(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.15f)
            .clip(RoundedCornerShape(22.dp))
            .background(TileColor)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun ServiceTile(tile: AutoTvTile, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    var logo by remember(tile.url) { mutableStateOf<Bitmap?>(SiteIconCache.getCachedIcon(context, tile.url)) }
    LaunchedEffect(tile.url) {
        if (logo == null) {
            SiteIconCache.prefetchIconIfNeeded(context, tile.url) { logo = it }
        }
    }

    TileFrame(onClick = onClick, onLongClick = onLongClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                val bmp = logo
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = tile.name,
                        contentScale = ContentScale.Fit,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    )
                } else {
                    Icon(Icons.Rounded.Public, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = tile.name,
                color = TileTextColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp)
            )
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    TileFrame(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(AccentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = AccentColor, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(text = label, color = TileTextColor, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AddTileDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val normalized = AutoTvTileStore.normalizeUrl(url)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dienst toevoegen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Naam (bijv. Ziggo GO)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Adres (bijv. ziggogo.tv)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = normalized.isNotEmpty(),
                onClick = {
                    val label = name.trim().ifEmpty { SiteIconCache.extractHost(normalized) ?: normalized }
                    onAdd(label, normalized)
                }
            ) { Text("Toevoegen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuleren") } }
    )
}

@Composable
fun AutoTvCloseButton(onClose: () -> Unit) {
    Surface(
        onClick = onClose,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.6f),
        modifier = Modifier.size(56.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, contentDescription = "Terug naar overzicht", tint = Color.White, modifier = Modifier.size(34.dp))
        }
    }
}
