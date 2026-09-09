package com.example.aicleanphonestorage.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aicleanphonestorage.core.locale.AppLanguageController
import com.example.aicleanphonestorage.core.locale.AppLanguages
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class LanguageSelectionState(
    val selected: String = "",
    val saving: Boolean = false,
    val error: Long = 0,
)

/** 只保存选择状态；不持有 Activity，重建期间不会再次提交语言写入。 */
internal class LanguageSettingsViewModel(private val languages: AppLanguageController) :
    ViewModel() {
    private val current = MutableStateFlow(LanguageSelectionState())
    val state = current.asStateFlow()

    init {
        viewModelScope.launch {
            languages.selected.collect { tag ->
                current.update {
                    it.copy(selected = AppLanguages.matching(tag.substringBefore(',')))
                }
            }
        }
    }

    fun refresh() = languages.refresh()

    fun select(tag: String) {
        if (current.value.saving || !AppLanguages.valid(tag) || current.value.selected == tag)
            return
        current.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                languages.select(tag)
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                current.update { it.copy(error = it.error + 1) }
            } catch (_: SecurityException) {
                current.update { it.copy(error = it.error + 1) }
            } finally {
                current.update { it.copy(saving = false) }
            }
        }
    }
}
