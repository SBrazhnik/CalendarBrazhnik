@file:OptIn(ExperimentalMaterial3Api::class)

package app.calstats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.calstats.MainViewModel
import app.calstats.R
import app.calstats.SharedText
import app.calstats.Tab

/** Цвет модуля — для плиток и значков. */
fun moduleColor(t: Tab): Color = when (t) {
    // Приглушённые «интерьерные» тона: читаются и на льне, и на графите, и на тёмно-зелёном
    Tab.HOME -> Color(0xFF1F3A2B)
    Tab.CALENDAR -> Color(0xFF4A6A8A)
    Tab.TASKS -> Color(0xFF2F5D46)
    Tab.FINANCE -> Color(0xFFB08A4E)
    Tab.HABITS -> Color(0xFFB5653A)
    Tab.HEALTH -> Color(0xFF6E8B3D)
    Tab.DOCS -> Color(0xFF5A6B7D)
    Tab.ACCOUNTS -> Color(0xFF7C5CE6)
    Tab.GUIDE -> Color(0xFF9A7B3F)
    Tab.SHOPPING -> Color(0xFF3F8A5A)
    Tab.GOALS -> Color(0xFFC27B44)
    Tab.JOURNAL -> Color(0xFF7A5C8C)
    Tab.CAR -> Color(0xFF4A5A6A)
    Tab.HOME_UTIL -> Color(0xFF3F7C9A)
    Tab.PETS -> Color(0xFFA0683F)
    Tab.MONEY -> Color(0xFF8C5A3C)
    Tab.ANALYSIS -> Color(0xFF6C4F7A)
    Tab.WISHES -> Color(0xFFB0656B)
    Tab.NOTES -> Color(0xFF3F7C7A)
}

@Composable
fun ModuleIcon(t: Tab, selected: Boolean = false, size: Dp = 24.dp, tint: Color? = null) {
    val m = Modifier.size(size)
    val c = tint ?: androidx.compose.material3.LocalContentColor.current
    when (t) {
        Tab.HOME -> Icon(if (selected) Icons.Filled.Home else Icons.Outlined.Home, null, m, tint = c)
        Tab.CALENDAR -> Icon(if (selected) Icons.Filled.DateRange else Icons.Outlined.DateRange, null, m, tint = c)
        Tab.TASKS -> Icon(if (selected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle, null, m, tint = c)
        Tab.FINANCE -> Icon(painterResource(R.drawable.ic_tab_money), null, m, tint = c)
        Tab.HABITS -> Icon(painterResource(R.drawable.ic_repeat), null, m, tint = c)
        Tab.HEALTH -> Icon(painterResource(R.drawable.ic_health), null, m, tint = c)
        Tab.DOCS -> Icon(painterResource(R.drawable.ic_docs), null, m, tint = c)
        Tab.ACCOUNTS -> Icon(painterResource(R.drawable.ic_link), null, m, tint = c)
        Tab.GUIDE -> Icon(painterResource(R.drawable.ic_book), null, m, tint = c)
        Tab.SHOPPING -> Icon(painterResource(R.drawable.ic_cart), null, m, tint = c)
        Tab.GOALS -> Icon(painterResource(R.drawable.ic_flag), null, m, tint = c)
        Tab.JOURNAL -> Icon(painterResource(R.drawable.ic_journal), null, m, tint = c)
        Tab.CAR -> Icon(painterResource(R.drawable.ic_car), null, m, tint = c)
        Tab.HOME_UTIL -> Icon(painterResource(R.drawable.ic_house), null, m, tint = c)
        Tab.PETS -> Icon(painterResource(R.drawable.ic_paw), null, m, tint = c)
        Tab.MONEY -> Icon(painterResource(R.drawable.ic_group), null, m, tint = c)
        Tab.ANALYSIS -> Icon(painterResource(R.drawable.ic_tab_stats), null, m, tint = c)
        Tab.WISHES -> Icon(painterResource(R.drawable.ic_tab_gift), null, m, tint = c)
        Tab.NOTES -> Icon(painterResource(R.drawable.ic_tab_notes), null, m, tint = c)
    }
}

/** Значок модуля в заголовке карточки: просто цветной символ, без подложки. */
@Composable
fun CardIcon(t: Tab, size: Dp = 20.dp) = ModuleIcon(t, true, size, moduleColor(t))

/** Цветная квадратная плитка модуля. */
@Composable
fun ModuleBadge(t: Tab, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 3.2f)).background(moduleColor(t)),
        contentAlignment = Alignment.Center,
    ) { ModuleIcon(t, true, size * 0.55f, Color.White) }
}

/** Управление модулями: включить/выключить, порядок, место в нижней панели. */
@Composable
fun ModulesScreen(vm: MainViewModel, snackbar: SnackbarHostState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Модули") },
                navigationIcon = { IconButton(onClick = { vm.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Включайте только нужные модули. Выключенный модуль скрывается из меню и с Главной — " +
                        "его данные сохраняются. Стрелки меняют порядок в боковом меню.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(vm.moduleOrder, key = { _, t -> t.name }) { idx, t ->
                val on = vm.isEnabled(t)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ModuleBadge(t, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    t.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Switch(checked = on, onCheckedChange = { vm.setModuleEnabled(t, it) })
                        }
                        if (on) {
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Плитка на Главной", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                val z = vm.tileSize(t)
                                FilterChip(
                                    selected = t !in vm.homeHidden,
                                    onClick = { vm.setTileSize(t, app.calstats.TileSize.entries[(z.ordinal + 1) % 3]) },
                                    label = { Text(z.title) },
                                )
                                IconButton(onClick = { vm.moveModule(t, -1) }, enabled = idx > 0) {
                                    Icon(Icons.Filled.KeyboardArrowUp, "Выше")
                                }
                                IconButton(onClick = { vm.moveModule(t, 1) }, enabled = idx < vm.moduleOrder.lastIndex) {
                                    Icon(Icons.Filled.KeyboardArrowDown, "Ниже")
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Скоро здесь появятся новые модули — их можно будет включить так же.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Куда сохранить то, чем поделились из другого приложения. */
@Composable
fun IncomingDialog(vm: MainViewModel, sh: SharedText) {
    var pickList by remember { mutableStateOf(false) }
    val lists = vm.work.wishLists.filter { !it.archived }
    AlertDialog(
        onDismissRequest = { vm.incoming = null },
        title = { Text(if (pickList) "В какой список?" else "Сохранить") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!pickList) {
                    Text(
                        sh.text.take(240) + if (sh.text.length > 240) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("Из: ${sh.source}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(6.dp))
                    if (vm.isEnabled(Tab.NOTES)) ChoiceRow(Tab.NOTES, "Заметка") { vm.saveIncomingAsNote() }
                    if (vm.isEnabled(Tab.WISHES)) {
                        ChoiceRow(Tab.WISHES, "Желание") {
                            if (lists.size == 1) vm.saveIncomingAsWish(lists.first().id)
                            else if (lists.isEmpty()) {
                                val l = app.calstats.work.WishList(app.calstats.work.newId(), "Мои желания")
                                vm.updateWork { app.calstats.work.WorkOps.upsertWishList(it, l) }
                                vm.saveIncomingAsWish(l.id)
                            } else pickList = true
                        }
                    }
                    if (vm.isEnabled(Tab.TASKS)) ChoiceRow(Tab.TASKS, "Задача") { vm.saveIncomingAsTask() }
                    if (vm.isEnabled(Tab.SHOPPING)) ChoiceRow(Tab.SHOPPING, "В покупки") { vm.saveIncomingAsShopping() }
                } else {
                    lists.forEach { l ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.saveIncomingAsWish(l.id) }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(l.emoji)
                            Spacer(Modifier.width(10.dp))
                            Text(l.title)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { vm.incoming = null }) { Text("Отмена") } },
    )
}

@Composable
private fun ChoiceRow(t: Tab, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModuleBadge(t, 34.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

