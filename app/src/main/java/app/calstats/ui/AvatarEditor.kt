@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package app.calstats.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.text.TextPaint
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

/** Файлы аватара: итоговый круг и исходное фото (чтобы можно было поправить позже). */
object AvatarFiles {
    fun out(ctx: Context) = File(ctx.filesDir, "avatar.jpg")
    fun src(ctx: Context) = File(ctx.filesDir, "avatar_src.jpg")

    /** Загружает фото (уменьшая до 1600 px) и сохраняет как исходник. */
    fun importPhoto(ctx: Context, uri: Uri): Bitmap? = runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / sample > 1600) sample *= 2
        val b = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        src(ctx).outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        b
    }.getOrNull()

    fun loadSrc(ctx: Context): Bitmap? = src(ctx).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    fun save(ctx: Context, b: Bitmap) {
        out(ctx).outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    }

    fun delete(ctx: Context) {
        out(ctx).delete()
        src(ctx).delete()
    }
}

/** Положение фото в круге: поворот, отражение, масштаб и сдвиг (в долях стороны круга). */
data class AvatarTransform(val rotation: Int = 0, val flip: Boolean = false, val scale: Float = 1f, val dx: Float = 0f, val dy: Float = 0f)

/** Одна матрица и для показа, и для сохранения — что видно, то и сохранится. */
fun avatarMatrix(b: Bitmap, t: AvatarTransform, side: Float): Matrix {
    val base = side / minOf(b.width, b.height)
    return Matrix().apply {
        postTranslate(-b.width / 2f, -b.height / 2f)
        if (t.flip) postScale(-1f, 1f)
        postRotate(t.rotation.toFloat())
        postScale(base * t.scale, base * t.scale)
        postTranslate(side / 2f + t.dx * side, side / 2f + t.dy * side)
    }
}

fun renderAvatar(b: Bitmap, t: AvatarTransform, size: Int = 512): Bitmap {
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawColor(android.graphics.Color.WHITE)
    c.drawBitmap(b, avatarMatrix(b, t, size.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    return out
}

/** Редактор: двумя пальцами — увеличить, одним — сдвинуть; кнопки — повернуть и отразить. */
@Composable
fun AvatarEditorDialog(bitmap: Bitmap, onDismiss: () -> Unit, onSave: (Bitmap) -> Unit) {
    var t by remember { mutableStateOf(AvatarTransform()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Отмена", color = Color.White) }
                Spacer(Modifier.weight(1f))
                Text("Фото профиля", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onSave(renderAvatar(bitmap, t)) }) { Text("Готово", color = Color(0xFFC9B8FF)) }
            }
            Spacer(Modifier.weight(1f))
            ComposeCanvas(
                Modifier.fillMaxWidth().aspectSquare().pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val s = size.width.toFloat()
                        t = t.copy(
                            scale = (t.scale * zoom).coerceIn(1f, 6f),
                            dx = (t.dx + pan.x / s).coerceIn(-1.5f, 1.5f),
                            dy = (t.dy + pan.y / s).coerceIn(-1.5f, 1.5f),
                        )
                    }
                },
            ) {
                drawIntoNative { it.drawBitmap(bitmap, avatarMatrix(bitmap, t, size.width), Paint(Paint.FILTER_BITMAP_FLAG)) }
                // Затемнение вокруг круга
                val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset.Zero, size)) }
                clipPath(circle, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.6f)) }
                drawCircle(Color.White.copy(alpha = 0.8f), radius = size.width / 2, style = Stroke(2f))
            }
            Spacer(Modifier.height(20.dp))
            Text("Масштаб", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium)
            Slider(value = t.scale, onValueChange = { t = t.copy(scale = it) }, valueRange = 1f..6f, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EditorButton("⟲ Повернуть") { t = t.copy(rotation = (t.rotation + 270) % 360) }
                EditorButton("⟳") { t = t.copy(rotation = (t.rotation + 90) % 360) }
                EditorButton("⇋ Отразить") { t = t.copy(flip = !t.flip) }
                EditorButton("Сброс") { t = AvatarTransform() }
            }
            Spacer(Modifier.weight(1f))
            Text("Двумя пальцами — увеличить, одним — сдвинуть", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun Modifier.aspectSquare() = this.aspectRatio(1f)

@Composable
private fun EditorButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(text, color = Color.White) }
}

// ---------- Аватар из эмодзи, как в Telegram ----------

val AVATAR_EMOJI = listOf(
    "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐸", "🐵", "🐶", "🐱", "🐰", "🦉", "🐧", "🦄", "🐙", "🐳",
    "🌿", "🌸", "🌻", "🍀", "🌙", "⭐", "🔥", "⚡", "🌈", "❄", "🍊", "🍓", "🥑", "☕", "🎧", "🎨",
    "⚽", "🏀", "🎾", "🚴", "🧘", "💃", "🕺", "🚗", "✈", "🏔", "🏝", "📚", "💡", "🎯", "🚀", "👑",
)

val AVATAR_BACKGROUNDS: List<Pair<Long, Long>> = listOf(
    0xFF2F5D46 to 0xFF6E8B3D, // лес
    0xFF1F3A2B to 0xFFD4A960, // лес и латунь
    0xFF3F3561 to 0xFFC9B8FF, // лаванда
    0xFF121721 to 0xFF4A6A8A, // ночь
    0xFFE0954F to 0xFFF4A259, // абрикос
    0xFFB0656B to 0xFFF2B5A0, // роза
    0xFF3F7C9A to 0xFF8FD3E8, // море
    0xFF6C4F7A to 0xFFE07AA6, // закат
    0xFF5A6B7D to 0xFFB8C4D0, // графит
    0xFF9A7B3F to 0xFFEAD7A4, // песок
)

fun renderEmojiAvatar(emoji: String, bg: Pair<Long, Long>, size: Int = 512): Bitmap {
    val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), bg.first.toInt(), bg.second.toInt(), Shader.TileMode.CLAMP)
    }
    c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
    val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size * 0.52f; textAlign = Paint.Align.CENTER }
    val y = size / 2f - (tp.descent() + tp.ascent()) / 2
    c.drawText(emoji, size / 2f, y, tp)
    return out
}

@Composable
fun EmojiAvatarSheet(onDismiss: () -> Unit, onSave: (Bitmap) -> Unit) {
    var emoji by remember { mutableStateOf(AVATAR_EMOJI.first()) }
    var bg by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Аватар из эмодзи", style = MaterialTheme.typography.titleLarge)
            val (a, b) = AVATAR_BACKGROUNDS[bg]
            Box(
                Modifier.align(Alignment.CenterHorizontally).size(120.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(a), Color(b)))),
                contentAlignment = Alignment.Center,
            ) { Text(emoji, fontSize = 60.sp) }
            Text("Фон", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AVATAR_BACKGROUNDS.forEachIndexed { i, (x, y) ->
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(x), Color(y))))
                            .then(if (i == bg) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { bg = i },
                    )
                }
            }
            Text("Эмодзи", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AVATAR_EMOJI.forEach { e ->
                    Box(
                        Modifier.size(42.dp).clip(CircleShape)
                            .background(if (e == emoji) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable { emoji = e },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 24.sp) }
                }
            }
            Button(onClick = { onSave(renderEmojiAvatar(emoji, AVATAR_BACKGROUNDS[bg])) }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntoNative(block: (android.graphics.Canvas) -> Unit) {
    drawContext.canvas.nativeCanvas.let(block)
}

@Suppress("unused")
private fun colorInt(c: Color) = c.toArgb()
