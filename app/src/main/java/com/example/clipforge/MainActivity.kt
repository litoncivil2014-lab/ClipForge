package com.example.clipforge

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.*
import androidx.media3.ui.PlayerView
import java.io.File

val Accent = Color(0xFF25F4EE)
val Panel = Color(0xFF161616)

data class Clip(val uri: Uri, val durationMs: Long, val startMs: Long = 0, val endMs: Long = durationMs)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Color.Black, surface = Panel)) {
                Surface(Modifier.fillMaxSize(), color = Color.Black) { App() }
            }
        }
    }
}

fun durationOf(ctx: Context, uri: Uri): Long = try {
    MediaMetadataRetriever().run {
        setDataSource(ctx, uri)
        val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        release(); d
    }
} catch (e: Exception) { 0L }

@Composable
fun App() {
    val ctx = LocalContext.current
    val clips = remember { mutableStateListOf<Clip>() }
    var inEditor by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) {
            clips.clear()
            uris.forEach { u -> val d = durationOf(ctx, u); if (d > 0) clips.add(Clip(u, d)) }
            inEditor = clips.isNotEmpty()
        }
    }
    if (inEditor) EditorScreen(clips) { inEditor = false }
    else HomeScreen { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }
}

@Composable
fun HomeScreen(onNew: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Text("ClipForge", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(32.dp))
        Box(
            Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp)).background(Accent).clickable { onNew() },
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("New project", color = Color.Black, style = MaterialTheme.typography.titleLarge)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Projects", Modifier.align(Alignment.Start), color = Color.Gray)
    }
}

@Composable
fun EditorScreen(clips: SnapshotStateList<Clip>, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var sel by remember { mutableIntStateOf(0) }
    var exporting by remember { mutableStateOf(false) }
    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }

    LaunchedEffect(clips.toList()) {
        player.setMediaItems(clips.map { toMediaItem(it) })
        player.prepare()
    }
    val c = clips[sel.coerceIn(0, clips.lastIndex)]

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Spacer(Modifier.weight(1f))
            Button(
                enabled = !exporting,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                onClick = { exporting = true; export(ctx, clips) { exporting = false } }
            ) { Text(if (exporting) "Exporting…" else "Export") }
        }
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player; useController = true } },
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        // Timeline
        Column(Modifier.background(Panel).padding(12.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(clips) { i, clip ->
                    val w = ((clip.endMs - clip.startMs) / 100).coerceIn(60, 400).dp
                    Box(
                        Modifier.width(w).height(54.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF2E2E2E))
                            .then(if (i == sel) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp)) else Modifier)
                            .clickable { sel = i; player.seekTo(i, 0) },
                        contentAlignment = Alignment.Center
                    ) { Text("${(clip.endMs - clip.startMs) / 1000}s", color = Color.White) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Trim clip ${sel + 1}", color = Color.Gray, style = MaterialTheme.typography.labelMedium)
            RangeSlider(
                value = c.startMs.toFloat()..c.endMs.toFloat(),
                valueRange = 0f..c.durationMs.toFloat(),
                onValueChange = { r ->
                    if (r.endInclusive - r.start >= 500f)
                        clips[sel] = c.copy(startMs = r.start.toLong(), endMs = r.endInclusive.toLong())
                }
            )
        }
        // Bottom toolbar
        Row(Modifier.fillMaxWidth().background(Color.Black).padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToolItem(Icons.Default.ContentCut, "Split") {
                val pos = player.currentPosition
                val s = c.startMs + pos
                if (s > c.startMs + 300 && s < c.endMs - 300) {
                    clips[sel] = c.copy(endMs = s)
                    clips.add(sel + 1, c.copy(startMs = s))
                }
            }
            ToolItem(Icons.Default.Delete, "Delete") { if (clips.size > 1) { clips.removeAt(sel); sel = 0 } }
            ToolItem(Icons.Default.MusicNote, "Audio") { soon(ctx) }
            ToolItem(Icons.Default.TextFields, "Text") { soon(ctx) }
            ToolItem(Icons.Default.Face, "Stickers") { soon(ctx) }
            ToolItem(Icons.Default.AutoAwesome, "Effects") { soon(ctx) }
            ToolItem(Icons.Default.Tune, "Filters") { soon(ctx) }
        }
    }
}

@Composable
fun ToolItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }.padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label, tint = Color.White)
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

fun soon(ctx: Context) = Toast.makeText(ctx, "Coming soon", Toast.LENGTH_SHORT).show()

fun toMediaItem(c: Clip): MediaItem = MediaItem.Builder().setUri(c.uri)
    .setClippingConfiguration(
        MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(c.endMs).build()
    ).build()

fun export(ctx: Context, clips: List<Clip>, done: () -> Unit) {
    val out = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "clipforge_${System.currentTimeMillis()}.mp4")
    val seq = EditedMediaItemSequence(clips.map { EditedMediaItem.Builder(toMediaItem(it)).build() })
    val comp = Composition.Builder(seq).build()
    Transformer.Builder(ctx).addListener(object : Transformer.Listener {
        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
            Toast.makeText(ctx, "Saved: ${out.absolutePath}", Toast.LENGTH_LONG).show(); done()
        }
        override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
            Toast.makeText(ctx, "Export failed: ${exportException.message}", Toast.LENGTH_LONG).show(); done()
        }
    }).build().start(comp, out.absolutePath)
}
