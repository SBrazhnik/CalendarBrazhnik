package app.calstats.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.widget.EditText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Редактор с оформлением, как в Samsung Notes: жирный, курсив, подчёркнутый, зачёркнутый,
 * размер, цвет текста и маркер, списки и галочки, отмена/повтор.
 * Текст хранится как есть (body), оформление — отдельно (rich, JSON отрезков).
 */
object Rich {
    const val B = "b"; const val I = "i"; const val U = "u"; const val S = "s"; const val SIZE = "size"; const val FG = "fg"; const val BG = "bg"

    fun encode(t: Spanned): String {
        val arr = JSONArray()
        t.getSpans(0, t.length, Any::class.java).forEach { sp ->
            val s = t.getSpanStart(sp); val e = t.getSpanEnd(sp)
            if (s < 0 || e <= s) return@forEach
            val o = JSONObject().put("s", s).put("e", e)
            when (sp) {
                is StyleSpan -> when (sp.style) { Typeface.BOLD -> o.put("t", B); Typeface.ITALIC -> o.put("t", I); else -> return@forEach }
                is UnderlineSpan -> if (sp.javaClass == UnderlineSpan::class.java) o.put("t", U) else return@forEach
                is StrikethroughSpan -> o.put("t", S)
                is RelativeSizeSpan -> o.put("t", SIZE).put("v", sp.sizeChange.toDouble())
                is ForegroundColorSpan -> o.put("t", FG).put("v", sp.foregroundColor)
                is BackgroundColorSpan -> o.put("t", BG).put("v", sp.backgroundColor)
                else -> return@forEach
            }
            arr.put(o)
        }
        return if (arr.length() == 0) "" else JSONObject().put("v", 1).put("spans", arr).toString()
    }

    fun decode(text: String, rich: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder(text)
        if (rich.isBlank()) return sb
        runCatching {
            val arr = JSONObject(rich).getJSONArray("spans")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val s = o.getInt("s").coerceIn(0, text.length); val e = o.getInt("e").coerceIn(0, text.length)
                if (e <= s) continue
                make(o.getString("t"), o.opt("v"))?.let { sb.setSpan(it, s, e, Spanned.SPAN_EXCLUSIVE_INCLUSIVE) }
            }
        }
        return sb
    }

    fun make(t: String, v: Any?): Any? = when (t) {
        B -> StyleSpan(Typeface.BOLD); I -> StyleSpan(Typeface.ITALIC); U -> UnderlineSpan(); S -> StrikethroughSpan()
        SIZE -> RelativeSizeSpan((v as? Number)?.toFloat() ?: 1f)
        FG -> ForegroundColorSpan((v as? Number)?.toInt() ?: 0); BG -> BackgroundColorSpan((v as? Number)?.toInt() ?: 0)
        else -> null
    }

    fun matches(sp: Any, t: String): Boolean = when (t) {
        B -> sp is StyleSpan && sp.style == Typeface.BOLD
        I -> sp is StyleSpan && sp.style == Typeface.ITALIC
        U -> sp is UnderlineSpan && sp.javaClass == UnderlineSpan::class.java
        S -> sp is StrikethroughSpan
        SIZE -> sp is RelativeSizeSpan; FG -> sp is ForegroundColorSpan; BG -> sp is BackgroundColorSpan
        else -> false
    }

    val PREFIXES = listOf("• ", "☐ ", "☑ ")
    val NUM = Regex("""^(\d+)\. """)
}

/** Состояние редактора для панели над клавиатурой. */
class RichState {
    var view: RichEditText? = null
    var focused by mutableStateOf(false)
    var active by mutableStateOf(setOf<String>())
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)

    fun toggle(t: String) = view?.toggle(t)
    fun size(v: Float) = view?.applyValue(Rich.SIZE, if (v == 1f) null else v)
    fun color(c: Int?) = view?.applyValue(Rich.FG, c)
    fun highlight(c: Int?) = view?.applyValue(Rich.BG, c)
    fun line(prefix: String) = view?.toggleLinePrefix(prefix)
    fun clear() = view?.clearFormat()
    fun undo() = view?.undo()
    fun redo() = view?.redo()
    fun insert(text: String) = view?.insertAtCursor(text)
    fun plain(): String = view?.text?.toString().orEmpty()
    fun selectedOrAll(): String = view?.let { v -> val s = v.selectionStart; val e = v.selectionEnd; if (s in 0 until e) v.text.substring(s, e) else v.text.toString() }.orEmpty()
}

@SuppressLint("ViewConstructor")
class RichEditText(ctx: Context, private val state: RichState, private val onChanged: (String, String) -> Unit) : EditText(ctx) {
    private var pending = mutableMapOf<String, Any?>()   // стиль для набираемого текста (без выделения)
    private var muted = false
    private val history = ArrayList<Pair<String, String>>()
    private var pos = -1
    private var lastSnap = 0L
    private var insStart = 0
    private var insCount = 0

    init {
        background = null
        gravity = Gravity.TOP or Gravity.START
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        isSingleLine = false
        setHorizontallyScrolling(false)
        setPadding(0, 0, 0, 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        setLineSpacing(0f, 1.18f)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { insStart = start; insCount = count }
            override fun afterTextChanged(e: Editable) {
                if (muted) return
                muted = true
                try {
                    if (insCount > 0) {
                        // стиль, включённый кнопкой без выделения, — на набранный текст
                        pending.forEach { (t, v) -> Rich.make(t, v)?.let { e.setSpan(it, insStart, insStart + insCount, Spanned.SPAN_EXCLUSIVE_INCLUSIVE) } }
                        autoFormat(e, insStart, insCount)
                    }
                } finally { muted = false }
                snapshot(false)
                onChanged(e.toString(), Rich.encode(e))
            }
        })
    }

    fun load(text: String, rich: String) {
        muted = true
        setText(Rich.decode(text, rich), BufferType.EDITABLE)
        muted = false
        history.clear(); pos = -1; snapshot(true)
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        @Suppress("SENSELESS_COMPARISON")
        if (state == null) return
        if (selStart != selEnd) pending.clear()
        refreshActive()
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        state.focused = focused
    }

    /** Нажатие на ☐/☑ в начале строки переключает галочку. */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && layout != null) {
            val off = getOffsetForPosition(event.x, event.y)
            val t = text
            val ls = if (off == 0) 0 else t.lastIndexOf('\n', off - 1) + 1
            if (ls < t.length && (t[ls] == '☐' || t[ls] == '☑') && off - ls <= 2) {
                val lineEnd = t.indexOf('\n', ls).let { if (it < 0) t.length else it }
                val checked = t[ls] == '☐'
                t.replace(ls, ls + 1, if (checked) "☑" else "☐")
                // Отмеченный пункт зачёркиваем
                if (checked) t.setSpan(StrikethroughSpan(), (ls + 2).coerceAtMost(lineEnd), lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                else t.getSpans(ls, lineEnd, StrikethroughSpan::class.java).forEach { t.removeSpan(it) }
                onChanged(t.toString(), Rich.encode(t))
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun refreshActive() {
        val t = text ?: return
        val s = selectionStart.coerceAtLeast(0); val e = selectionEnd.coerceAtLeast(0)
        val a = mutableSetOf<String>()
        for (k in listOf(Rich.B, Rich.I, Rich.U, Rich.S)) {
            if (k in pending) { a += k; continue }
            val covered = if (s == e) t.getSpans((s - 1).coerceAtLeast(0), s, Any::class.java).any { Rich.matches(it, k) && t.getSpanEnd(it) >= s }
            else t.getSpans(s, e, Any::class.java).any { Rich.matches(it, k) && t.getSpanStart(it) <= s && t.getSpanEnd(it) >= e }
            if (covered) a += k
        }
        state.active = a
    }

    fun toggle(k: String) {
        val t = text ?: return
        val s = selectionStart; val e = selectionEnd
        if (s < 0) return
        if (s == e) {
            if (k in pending || k in state.active) pending.remove(k) else pending[k] = null
            // убрать стиль у дальнейшего ввода: «закрываем» отрезок на курсоре
            if (k !in pending) t.getSpans(s, s, Any::class.java).filter { Rich.matches(it, k) }.forEach { sp ->
                val ss = t.getSpanStart(sp); val se = t.getSpanEnd(sp)
                t.removeSpan(sp); if (ss < s) t.setSpan(Rich.make(k, null), ss, s, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (se > s) t.setSpan(Rich.make(k, null), s, se, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
            }
            refreshActive(); return
        }
        val has = t.getSpans(s, e, Any::class.java).any { Rich.matches(it, k) && t.getSpanStart(it) <= s && t.getSpanEnd(it) >= e }
        removeRange(t, k, s, e)
        if (!has) t.setSpan(Rich.make(k, null), s, e, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        changed()
    }

    fun applyValue(k: String, v: Any?) {
        val t = text ?: return
        val s = selectionStart; val e = selectionEnd
        if (s < 0) return
        if (s == e) { if (v == null) pending.remove(k) else pending[k] = v; return }
        removeRange(t, k, s, e)
        if (v != null) t.setSpan(Rich.make(k, v), s, e, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        changed()
    }

    /** Снять стиль k на [s, e), сохранив его по краям. */
    private fun removeRange(t: Editable, k: String, s: Int, e: Int) {
        t.getSpans(s, e, Any::class.java).filter { Rich.matches(it, k) }.forEach { sp ->
            val ss = t.getSpanStart(sp); val se = t.getSpanEnd(sp)
            val v: Any? = when (sp) { is RelativeSizeSpan -> sp.sizeChange; is ForegroundColorSpan -> sp.foregroundColor; is BackgroundColorSpan -> sp.backgroundColor; else -> null }
            t.removeSpan(sp)
            if (ss < s) t.setSpan(Rich.make(k, v), ss, s, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (se > e) t.setSpan(Rich.make(k, v), e, se, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        }
    }

    fun clearFormat() {
        val t = text ?: return
        var s = selectionStart; var e = selectionEnd
        if (s == e) { s = 0; e = t.length }
        listOf(Rich.B, Rich.I, Rich.U, Rich.S, Rich.SIZE, Rich.FG, Rich.BG).forEach { removeRange(t, it, s, e) }
        pending.clear(); changed()
    }

    /** Маркер, галочка или номер в начале строк выделения. */
    fun toggleLinePrefix(prefix: String) {
        val t = text ?: return
        val s = selectionStart.coerceAtLeast(0); val e = selectionEnd.coerceAtLeast(0)
        var ls = t.lastIndexOf('\n', (s - 1).coerceAtLeast(0)).let { if (s == 0 || it < 0) 0 else it + 1 }
        if (s > 0 && t.getOrNull(s - 1) == '\n') ls = s
        val lines = mutableListOf<Int>()
        var p = ls
        while (true) { lines += p; val n = t.indexOf('\n', p); if (n < 0 || n >= e) break; p = n + 1 }
        val all = lines.all { st -> lineHas(t, st, prefix) }
        muted = true
        var num = 1
        for (st in lines.reversed()) {
            val cur = currentPrefix(t, st)
            if (cur != null) t.delete(st, st + cur.length)
            if (!all) t.insert(st, if (prefix == "1. ") "${lines.indexOf(st) + 1}. " else prefix)
            num++
        }
        muted = false
        changed()
    }

    private fun lineHas(t: CharSequence, st: Int, prefix: String): Boolean {
        val cur = currentPrefix(t, st) ?: return false
        return if (prefix == "1. ") Rich.NUM.matches(cur) else cur == prefix || (prefix == "☐ " && cur == "☑ ")
    }

    private fun currentPrefix(t: CharSequence, st: Int): String? {
        val rest = t.subSequence(st, minOf(t.length, st + 6)).toString()
        Rich.PREFIXES.firstOrNull { rest.startsWith(it) }?.let { return it }
        return Rich.NUM.find(rest)?.value
    }

    /** Автоформат: «- » → «• », «[] » → «☐ », продолжение списка по Enter, пустой пункт + Enter — конец списка. */
    private fun autoFormat(e: Editable, start: Int, count: Int) {
        if (count == 1 && e.getOrNull(start) == ' ') {
            val ls = e.lastIndexOf('\n', start - 1).let { if (start == 0) 0 else it + 1 }
            val head = e.subSequence(ls, start + 1).toString()
            when (head) {
                "- ", "* " -> { e.replace(ls, start + 1, "• "); return }
                "[] ", "[ ] " -> { e.replace(ls, start + 1, "☐ "); return }
            }
        }
        if (count == 1 && e.getOrNull(start) == '\n') {
            val prevStart = e.lastIndexOf('\n', start - 1).let { if (start == 0) 0 else it + 1 }
            val prevLine = e.subSequence(prevStart, start).toString()
            val pre = Rich.PREFIXES.firstOrNull { prevLine.startsWith(it) } ?: Rich.NUM.find(prevLine)?.value ?: return
            if (prevLine.length == pre.length) {
                // пустой пункт — выходим из списка
                e.delete(prevStart, start + 1)
                return
            }
            val next = when {
                pre == "☑ " -> "☐ "
                Rich.NUM.matches(pre) -> "${(Rich.NUM.find(pre)!!.groupValues[1].toInt() + 1)}. "
                else -> pre
            }
            e.insert(start + 1, next)
            setSelection(start + 1 + next.length)
        }
    }

    fun insertAtCursor(s: String) {
        val t = text ?: return
        val p = selectionStart.coerceAtLeast(0)
        val add = if (p > 0 && t[p - 1] != '\n' && t[p - 1] != ' ') " $s" else s
        t.insert(p, add)
    }

    private fun changed() {
        val t = text ?: return
        refreshActive(); snapshot(true)
        onChanged(t.toString(), Rich.encode(t))
    }

    private fun snapshot(force: Boolean) {
        val t = text ?: return
        val now = System.currentTimeMillis()
        val snap = t.toString() to Rich.encode(t)
        if (pos >= 0 && history[pos] == snap) return
        if (!force && pos >= 0 && now - lastSnap < 900) { history[pos] = snap }
        else {
            while (history.size > pos + 1) history.removeAt(history.size - 1)
            history += snap; pos = history.size - 1
            if (history.size > 100) { history.removeAt(0); pos-- }
        }
        lastSnap = now
        state.canUndo = pos > 0; state.canRedo = pos < history.size - 1
    }

    private fun restore(i: Int) {
        val (txt, rich) = history[i]
        muted = true
        setText(Rich.decode(txt, rich), BufferType.EDITABLE)
        setSelection(txt.length)
        muted = false
        pos = i
        state.canUndo = pos > 0; state.canRedo = pos < history.size - 1
        onChanged(txt, rich)
    }

    fun undo() { if (pos > 0) restore(pos - 1) }
    fun redo() { if (pos < history.size - 1) restore(pos + 1) }

    @Suppress("unused")
    private fun spannable(): Spannable? = text
}
