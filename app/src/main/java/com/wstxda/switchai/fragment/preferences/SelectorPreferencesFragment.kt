package com.wstxda.switchai.fragment.preferences

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import com.wstxda.switchai.R
import com.wstxda.switchai.fragment.BasePreferenceFragment
import com.wstxda.switchai.preference.MaterialMultiSelectListPreference
import com.wstxda.switchai.ui.component.AssistantGridDialog
import com.wstxda.switchai.ui.component.AssistantManagerDialog
import com.wstxda.switchai.utils.Constants
import com.wstxda.switchai.viewmodel.AssistantSelectorViewModel

class SelectorPreferencesFragment : BasePreferenceFragment() {

    private val assistantSelectorViewModel: AssistantSelectorViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
    }

    override val preferencesResId: Int get() = R.xml.preferences_selector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        parentFragmentManager.setFragmentResultListener(
            Constants.ASSISTANT_MANAGER_DIALOG_RESULT, this
        ) { _, result ->
            val selected = result.getStringArrayList(Constants.EXTRA_SELECTED_ASSISTANTS)
                ?.toHashSet() ?: return@setFragmentResultListener

            findPreference<MaterialMultiSelectListPreference>(
                Constants.SELECTOR_MANAGER_MANUAL_PREF_KEY
            )?.let { preference ->
                if (preference.callChangeListener(selected)) preference.values = selected
            }
        }
    }

    override fun setupObservers() {
        assistantSelectorViewModel.isDynamicModeEnabled.observe(viewLifecycleOwner) { isDynamic ->
            findPreference<MaterialMultiSelectListPreference>(
                Constants.SELECTOR_MANAGER_MANUAL_PREF_KEY
            )?.isVisible = !isDynamic
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == Constants.SELECTOR_GRID_PREF_KEY) {
            AssistantGridDialog.show(parentFragmentManager)
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (parentFragmentManager.findFragmentByTag(Constants.PREFERENCE_DIALOG) != null) return

        if (preference is MaterialMultiSelectListPreference) {
            val dialog = AssistantManagerDialog.newInstance(
                title = preference.title ?: getString(R.string.pref_selector_manager_manual),
                entries = preference.entries,
                entryValues = preference.entryValues,
                selected = preference.values,
                minSelection = preference.minSelection,
                maxSelection = preference.maxSelection,
            )
            dialog.show(parentFragmentManager, Constants.PREFERENCE_DIALOG)
        } else {
            super.onDisplayPreferenceDialog(preference)
        }
    }
}
