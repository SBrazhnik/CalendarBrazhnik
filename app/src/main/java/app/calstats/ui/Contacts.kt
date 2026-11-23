package app.calstats.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.calstats.MainViewModel
import app.calstats.work.PALETTE
import app.calstats.work.Person
import app.calstats.work.WorkOps
import app.calstats.work.newId

/** Контакт из телефонной книги. Разрешение на чтение всех контактов не нужно — Android отдаёт только выбранный. */
data class PickedContact(val name: String, val phone: String)

private class PickPhone : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit) =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
    override fun parseResult(resultCode: Int, intent: Intent?) = if (resultCode == Activity.RESULT_OK) intent?.data else null
}

private fun read(ctx: Context, uri: Uri): PickedContact? = runCatching {
    ctx.contentResolver.query(
        uri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
        null, null, null,
    )?.use { c -> if (c.moveToFirst()) PickedContact(c.getString(0).orEmpty().trim(), c.getString(1).orEmpty().trim()) else null }
}.getOrNull()?.takeIf { it.name.isNotBlank() || it.phone.isNotBlank() }

/** Возвращает функцию, открывающую выбор контакта. */
@Composable
fun rememberContactPicker(onPick: (PickedContact) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val l = rememberLauncherForActivityResult(PickPhone()) { uri -> uri?.let { read(ctx, it) }?.let(onPick) }
    return { runCatching { l.launch(Unit) } }
}

@Composable
fun ContactChip(modifier: Modifier = Modifier, label: String = "📇 Из контактов", onPick: (PickedContact) -> Unit) {
    val pick = rememberContactPicker(onPick)
    AssistChip(onClick = pick, label = { Text(label) }, modifier = modifier)
}

private fun norm(p: String) = p.filter { it.isDigit() }.takeLast(10)

/** Находит человека по телефону или имени, иначе добавляет в «Люди». */
fun MainViewModel.personFromContact(c: PickedContact): Person {
    val w = work
    val found = w.people.firstOrNull { c.phone.isNotBlank() && it.phone.isNotBlank() && norm(it.phone) == norm(c.phone) }
        ?: w.people.firstOrNull { it.name.equals(c.name, true) }
    if (found != null) {
        if (found.phone.isBlank() && c.phone.isNotBlank()) updateWork { WorkOps.upsertPerson(it, found.copy(phone = c.phone)) }
        return found
    }
    val p = Person(newId(), c.name.ifBlank { c.phone }, PALETTE[(w.people.size + 1) % PALETTE.size], phone = c.phone)
    updateWork { WorkOps.upsertPerson(it, p) }
    return p
}
