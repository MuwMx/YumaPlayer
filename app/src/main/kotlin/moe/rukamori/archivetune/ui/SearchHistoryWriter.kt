package moe.rukamori.archivetune.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.search.AddSearchHistoryUseCase

class SearchHistoryWriter(
    private val coroutineScope: CoroutineScope,
    private val addSearchHistoryUseCase: AddSearchHistoryUseCase?,
) {
    fun addSearchHistory(query: String) {
        coroutineScope.launch {
            addSearchHistoryUseCase?.invoke(query)
        }
    }
}
