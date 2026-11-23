package app.calstats.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import app.calstats.MainViewModel

/** Состояние вкладки внутри модуля, которое запоминается в истории «назад». */
@Composable
fun <T> navView(vm: MainViewModel, key: String, initial: T): MutableState<T> = object : MutableState<T> {
    @Suppress("UNCHECKED_CAST")
    override var value: T
        get() = (vm.subViews[key] as T?) ?: initial
        set(v) = vm.setSubView(key, v)
    override fun component1(): T = value
    override fun component2(): (T) -> Unit = { value = it }
}
