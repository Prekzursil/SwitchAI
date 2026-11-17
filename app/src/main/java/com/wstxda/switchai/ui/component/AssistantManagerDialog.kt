package com.wstxda.switchai.ui.component

import android.app.Dialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.wstxda.switchai.R
import com.wstxda.switchai.utils.Constants

/**
 * Multi-select assistant manager dialog.
 *
 * Self-contained replacement for the previous PreferenceDialogFragmentCompat
 * implementation: androidx.preference 1.2.1 resolves the target preference
 * through the deprecated Fragment.setTargetFragment API, so this dialog now
 * receives its data via arguments and reports the selection back through the
 * FragmentResult API (Constants.ASSISTANT_MANAGER_DIALOG_RESULT).
 */
class AssistantManagerDialog : DialogFragment() {

    private val currentSelections = HashSet<String>()
    private var toastShownForInvalidSelection = false

    private val minSelection: Int
        get() = requireArguments().getInt(ARG_MIN_SELECTION)

    private val maxSelection: Int
        get() = requireArguments().getInt(ARG_MAX_SELECTION)

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_ENTRIES = "entries"
        private const val ARG_ENTRY_VALUES = "entry_values"
        private const val ARG_SELECTED = "selected"
        private const val ARG_MIN_SELECTION = "min_selection"
        private const val ARG_MAX_SELECTION = "max_selection"
        private const val STATE_SELECTIONS = "selections"

        fun newInstance(
            title: CharSequence,
            entries: Array<CharSequence>,
            entryValues: Array<CharSequence>,
            selected: Set<String>,
            minSelection: Int,
            maxSelection: Int,
        ) = AssistantManagerDialog().apply {
            arguments = Bundle().apply {
                putCharSequence(ARG_TITLE, title)
                putCharSequenceArray(ARG_ENTRIES, entries)
                putCharSequenceArray(ARG_ENTRY_VALUES, entryValues)
                putStringArrayList(ARG_SELECTED, ArrayList(selected))
                putInt(ARG_MIN_SELECTION, minSelection)
                putInt(ARG_MAX_SELECTION, maxSelection)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentSelections.clear()
        val restored = savedInstanceState?.getStringArrayList(STATE_SELECTIONS)
        if (restored != null) {
            currentSelections.addAll(restored)
        } else {
            currentSelections.addAll(requireArguments().getStringArrayList(ARG_SELECTED).orEmpty())
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_SELECTIONS, ArrayList(currentSelections))
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        val entries = args.getCharSequenceArray(ARG_ENTRIES).orEmpty()
        val entryValues = args.getCharSequenceArray(ARG_ENTRY_VALUES).orEmpty()
        val checkedItems = BooleanArray(entryValues.size) { index ->
            currentSelections.contains(entryValues[index].toString())
        }

        val builder = AlertDialog.Builder(requireContext()).apply {
            setTitle(args.getCharSequence(ARG_TITLE))
            setMultiChoiceItems(entries, checkedItems) { _, which, isChecked ->
                val value = entryValues[which].toString()
                if (isChecked) currentSelections.add(value) else currentSelections.remove(value)
                updatePositiveButtonState()
            }
            setPositiveButton(android.R.string.ok) { _, _ -> }
            setNegativeButton(android.R.string.cancel) { _, _ -> }
        }

        val dialog = builder.create()
        dialog.setOnShowListener { updatePositiveButtonState() }
        return dialog
    }

    private fun updatePositiveButtonState() {
        val okButton = (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE) ?: return

        val count = currentSelections.size
        val isValid = count >= minSelection && count <= maxSelection
        okButton.isEnabled = isValid
        okButton.setOnClickListener {
            if (isSelectionValid()) saveSelections() else updatePositiveButtonState()
        }

        if (isValid) {
            toastShownForInvalidSelection = false
            return
        }

        if (!toastShownForInvalidSelection) {
            val limit: Int
            val msgRes: Int
            when {
                count < minSelection -> {
                    limit = minSelection
                    msgRes = R.string.error_min_selection
                }

                count > maxSelection -> {
                    limit = maxSelection
                    msgRes = R.string.error_max_selection
                }

                else -> return
            }
            Toast.makeText(requireContext(), getString(msgRes, limit), Toast.LENGTH_SHORT).show()
            toastShownForInvalidSelection = true
        }
    }

    private fun isSelectionValid(): Boolean {
        val count = currentSelections.size
        return count >= minSelection && count <= maxSelection
    }

    private fun saveSelections() {
        parentFragmentManager.setFragmentResult(
            Constants.ASSISTANT_MANAGER_DIALOG_RESULT,
            Bundle().apply {
                putStringArrayList(
                    Constants.EXTRA_SELECTED_ASSISTANTS, ArrayList(currentSelections)
                )
            }
        )
        dismiss()
    }
}
