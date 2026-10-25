package repertoire.android

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A widget on the home screen of the phone: one tap and the puzzle of an opening (or everything
 * due) is on the board. Which one is chosen when the widget is placed, and can be changed later.
 */
class LearnWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, manager, ids)

    override fun onDeleted(context: Context, ids: IntArray) {
        val edit = targets(context).edit()
        ids.forEach { edit.remove(it.toString()) }
        edit.apply()
    }

    companion object {
        private fun targets(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

        fun targetOf(context: Context, id: Int): LearnTarget? = LearnTarget.parse(targets(context).getString(id.toString(), null))

        fun choose(context: Context, id: Int, target: LearnTarget) {
            targets(context).edit().putString(id.toString(), target.saved).apply()
            render(context, AppWidgetManager.getInstance(context), intArrayOf(id))
        }

        /** Draws every widget anew, e.g. when the app goes to the background with changed variants or progress. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            render(context, manager, manager.getAppWidgetIds(ComponentName(context, LearnWidget::class.java)))
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val store = context.store()
            val library = store.loadLibrary()
            val progress = store.loadProgress()
            for (id in ids) {
                // Not chosen yet: the configuration is still open.
                val target = targetOf(context, id) ?: continue
                val (title, line) = target.label(library, progress)
                val views = RemoteViews(context.packageName, R.layout.widget_learn)
                views.setTextViewText(R.id.widget_title, title)
                views.setTextViewText(R.id.widget_line, line)
                val intent = target.intent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                views.setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
                )
                manager.updateAppWidget(id, views)
            }
        }
    }
}

/** Chooses what a new widget starts: everything due, an opening or a playlist of one's own. */
class LearnWidgetConfig : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        // Backing out without a choice leaves no widget behind.
        setResult(Activity.RESULT_CANCELED, result)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()

        val library = store().loadLibrary()
        val chosen = LearnWidget.targetOf(this, id)
        val pick = { target: LearnTarget ->
            LearnWidget.choose(this, id, target)
            setResult(Activity.RESULT_OK, result)
            finish()
        }
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.safeDrawingPadding()) {
                        item { Heading("Was soll das Widget starten?") }
                        item { Choice("Lernen", "alles Fällige, mit Spaced Repetition", chosen == LearnTarget.Due) { pick(LearnTarget.Due) } }
                        if (library.groups().isNotEmpty()) item { Heading("Eröffnung lernen") }
                        items(library.groups()) { g ->
                            val target = LearnTarget.Opening(g.opening)
                            Choice(g.opening, LearnTarget.variants(g.variants.size), chosen == target) { pick(target) }
                        }
                        if (library.playlists.isNotEmpty()) item { Heading("Meine Playlists") }
                        items(library.playlists) { p ->
                            val target = LearnTarget.Playlist(p.id)
                            Choice(p.name, LearnTarget.variants(library.variantsOf(p).size), chosen == target) { pick(target) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
private fun Choice(title: String, line: String, chosen: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(if (chosen) "✓ $title" else title, style = MaterialTheme.typography.bodyLarge)
        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

/**
 * The shortcuts that come up when the app icon is held: „Lernen“ and the first openings of the home
 * screen (the pinned ones first). Each can also be dragged onto the home screen on its own.
 */
fun Context.refreshShortcuts() {
    val manager = getSystemService(ShortcutManager::class.java) ?: return
    val library = store().loadLibrary()
    val icon = Icon.createWithResource(this, R.mipmap.ic_launcher)
    val due = ShortcutInfo.Builder(this, "due").setShortLabel("Lernen").setLongLabel("Lernen (alles Fällige)")
        .setIcon(icon).setIntent(LearnTarget.Due.intent(this)).setRank(0).build()
    val openings = library.groups().take((manager.maxShortcutCountPerActivity - 1).coerceAtMost(3)).mapIndexed { i, g ->
        ShortcutInfo.Builder(this, "opening:${g.opening}").setShortLabel(g.opening).setLongLabel("${g.opening} lernen")
            .setIcon(icon).setIntent(LearnTarget.Opening(g.opening).intent(this)).setRank(i + 1).build()
    }
    manager.dynamicShortcuts = listOf(due) + openings
}
