package app.calstats.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.calstats.MainActivity
import app.calstats.work.WorkStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Виджет «Карты»: самые используемые бонусные карты — нажатие открывает штрихкод. */
class CardsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cards = withContext(Dispatchers.IO) {
            runCatching { WorkStore(context).load().cards.sortedByDescending { it.uses * 10 + (it.lastUsed / 86_400_000).toInt() }.take(4) }.getOrDefault(emptyList())
        }
        provideContent {
            Column(GlanceModifier.fillMaxSize().background(ColorProvider(day = Color(0xFFF3F2F7), night = Color(0xFF121721))).cornerRadius(20.dp).padding(8.dp)) {
                if (cards.isEmpty()) Text("Добавьте карты в приложении", GlanceModifier.padding(8.dp).clickable(actionStartActivity<MainActivity>()),
                    style = TextStyle(color = ColorProvider(day = Color(0xFF5E6472), night = Color(0xFFAAAEB6)), fontSize = 13.sp))
                cards.forEach { c ->
                    val i = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_QUICK, "card:${c.id}")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP).setAction("card_${c.id}")
                    Box(
                        GlanceModifier.fillMaxWidth().height(44.dp).background(ColorProvider(day = Color(c.color), night = Color(c.color))).cornerRadius(12.dp)
                            .padding(horizontal = 12.dp).clickable(actionStartActivity(i)),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        val light = (Color(c.color).red * 0.3f + Color(c.color).green * 0.59f + Color(c.color).blue * 0.11f) > 0.6f
                        Text("💳 ${c.store}", style = TextStyle(color = ColorProvider(day = if (light) Color(0xFF2B2F35) else Color.White, night = if (light) Color(0xFF2B2F35) else Color.White), fontWeight = FontWeight.Medium, fontSize = 14.sp))
                    }
                    Spacer(GlanceModifier.height(6.dp))
                }
            }
        }
    }

    companion object {
        suspend fun refresh(ctx: Context) = runCatching { CardsWidget().updateAll(ctx) }
    }
}

class CardsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CardsWidget()
}
