package com.example.aicleanphonestorage.feature.appmanager.ui

import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import com.example.aicleanphonestorage.R
import com.example.aicleanphonestorage.databinding.ScreenAppManagerBinding
import com.example.aicleanphonestorage.feature.appmanager.data.AppManagerSort
import com.example.aicleanphonestorage.feature.appmanager.data.AppSortKey

/** Flow 在窄屏/大字号下自动换行，选中项重复点击切换升降序。 */
internal class AppManagerSortControls(
    binding: ScreenAppManagerBinding,
    select: (AppSortKey) -> Unit,
) {
    private val regular = ResourcesCompat.getFont(binding.root.context, R.font.home_roboto_regular)
    private val medium = ResourcesCompat.getFont(binding.root.context, R.font.home_roboto_medium)
    private val buttons =
        mapOf(
            AppSortKey.LAST_USED to binding.appManagerSortUsed,
            AppSortKey.SIZE to binding.appManagerSortSize,
            AppSortKey.NAME to binding.appManagerSortName,
        )

    init {
        buttons.forEach { (key, button) -> button.setOnClickListener { select(key) } }
    }

    fun render(sort: AppManagerSort) =
        buttons.forEach { (key, button) ->
            val selected = sort.key == key
            button.isChecked = selected
            button.typeface = if (selected) medium else regular
            button.setIconResource(
                when {
                    !selected -> R.drawable.ic_app_manager_sort_inactive
                    sort.descending -> R.drawable.ic_app_manager_sort_active
                    else -> R.drawable.ic_app_manager_sort_ascending
                }
            )
            ViewCompat.setStateDescription(
                button,
                if (selected)
                    button.context.getString(
                        if (sort.descending) R.string.app_manager_sort_descending
                        else R.string.app_manager_sort_ascending
                    )
                else null,
            )
        }
}
