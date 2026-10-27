package app.calstats.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.calstats.work.IssueType
import app.calstats.work.Person
import app.calstats.work.Priority
import app.calstats.work.Status

/** Кружок с первой буквой имени — как аватар в Jira. */
@Composable
fun Avatar(p: Person?, size: Dp = 24.dp) {
    if (p == null) {
        Box(
            Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, "Не назначено", Modifier.size(size * 0.7f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Box(Modifier.size(size).clip(CircleShape).background(Color(p.color)), contentAlignment = Alignment.Center) {
        Text(
            p.name.trim().take(1).uppercase().ifEmpty { "?" },
            color = Color.White,
            fontSize = (size.value * 0.45f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Значок типа задачи: цветной квадратик с буквой. */
@Composable
fun TypeIcon(t: IssueType, size: Dp = 16.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(3.dp)).background(Color(t.color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when (t) {
                IssueType.TASK -> "✓"
                IssueType.STORY -> "◆"
                IssueType.BUG -> "●"
                IssueType.EPIC -> "⚡"
                IssueType.SUBTASK -> "↳"
            },
            color = Color.White,
            fontSize = (size.value * 0.62f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun PriorityIcon(p: Priority) {
    Text(p.arrow, color = Color(p.color), fontWeight = FontWeight.Bold, fontSize = 14.sp)
}

fun statusColor(s: Status): Color = when (s) {
    Status.TODO -> Color(0xFF6B778C)
    Status.IN_PROGRESS -> Color(0xFF0C66E4)
    Status.REVIEW -> Color(0xFF8270DB)
    Status.DONE -> Color(0xFF22A06B)
}

/** Плашка статуса в стиле Jira. */
@Composable
fun StatusPill(s: Status, onClick: (() -> Unit)? = null) {
    val c = statusColor(s)
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.copy(alpha = 0.16f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(s.title.uppercase(), color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Круглая отметка выполнения. */
@Composable
fun TaskCheck(done: Boolean, color: Int?, onToggle: () -> Unit) {
    val accent = color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val fill by animateColorAsState(if (done) accent else Color.Transparent, label = "check")
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, accent, CircleShape)
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = done) {
            Icon(Icons.Filled.Check, "Выполнено", tint = Color.White, modifier = Modifier.size(15.dp))
        }
    }
}

/** Выпадающий список людей. */
@Composable
fun PersonMenu(
    expanded: Boolean,
    people: List<Person>,
    allowNone: Boolean,
    onDismiss: () -> Unit,
    onPick: (Person?) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (allowNone) {
            DropdownMenuItem(text = { Text("Не назначено") }, leadingIcon = { Avatar(null) }, onClick = { onPick(null) })
        }
        people.forEach { p ->
            DropdownMenuItem(text = { Text(p.name) }, leadingIcon = { Avatar(p) }, onClick = { onPick(p) })
        }
    }
}

@Composable
fun PersonLine(p: Person?, emptyText: String = "Не назначено") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(p, 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(p?.name ?: emptyText, style = MaterialTheme.typography.bodyLarge)
    }
}

object Durations {
    /** «1ч 30м», «1.5ч», «90м», «2d», «3» (= часы) → минуты. День = 8 часов. */
    fun parse(text: String): Int? {
        val t = text.lowercase().replace(",", ".").trim()
        if (t.isEmpty()) return null
        t.toDoubleOrNull()?.let { return (it * 60).toInt().takeIf { m -> m > 0 } }
        val re = Regex("""(\d+(?:\.\d+)?)\s*(д|d|ч|h|м|m|мин)""")
        var total = 0.0
        var found = false
        re.findAll(t).forEach { m ->
            found = true
            val v = m.groupValues[1].toDouble()
            total += when (m.groupValues[2]) {
                "д", "d" -> v * 8 * 60
                "ч", "h" -> v * 60
                else -> v
            }
        }
        return if (found && total > 0) total.toInt() else null
    }

    fun format(min: Int): String {
        val h = min / 60
        val m = min % 60
        return when {
            h == 0 -> "${m}м"
            m == 0 -> "${h}ч"
            else -> "${h}ч ${m}м"
        }
    }
}
