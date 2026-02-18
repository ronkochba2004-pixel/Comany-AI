package com.example.mspsupportassistant.ui.settings
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.mspsupportassistant.R
import com.example.mspsupportassistant.AdminActivity
import com.example.mspsupportassistant.CurrentUserHolder
import com.example.mspsupportassistant.LoginActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SettingsBottomSheetDialogFragment : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_IS_ADMIN = "is_admin"
        private const val ARG_EMAIL = "email"
        private const val ARG_NAME = "name"
        private const val ARG_COMPANY = "company"


        fun newInstance(isAdmin: Boolean, email: String, name: String, company: String): SettingsBottomSheetDialogFragment {
            val fragment = SettingsBottomSheetDialogFragment()
            val args = Bundle()
            args.putBoolean(ARG_IS_ADMIN, isAdmin)
            args.putString(ARG_EMAIL, email)
            args.putString(ARG_NAME, name)
            args.putString(ARG_COMPANY, company)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_dialog_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isAdmin = arguments?.getBoolean(ARG_IS_ADMIN) ?: false
        val email = arguments?.getString(ARG_EMAIL) ?: ""
        val name = arguments?.getString(ARG_NAME) ?: ""
        val company = arguments?.getString(ARG_COMPANY) ?: ""


        val emailText = view.findViewById<TextView>(R.id.textEmailValue)
        val nameText = view.findViewById<TextView>(R.id.textNameValue)
        val companyText = view.findViewById<TextView>(R.id.textCompanyValue)


        emailText.text = email
        nameText.text = name
        companyText.text = company

        val adminRow = view.findViewById<View>(R.id.rowAdminSettings)
        adminRow.visibility = if (isAdmin) View.VISIBLE else View.GONE

        adminRow.setOnClickListener {
            dismiss()
            val intent = Intent(requireContext(), AdminActivity::class.java)
            startActivity(intent)
        }

        val logoutText = view.findViewById<TextView>(R.id.textLogout)
        logoutText.setOnClickListener {
            // Clear current user state
            CurrentUserHolder.user = null
            CurrentUserHolder.company = null

            // Go to LoginActivity and clear back stack
            val intent = Intent(requireContext(), LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)

            dismiss()
        }

        val languageRow = view.findViewById<View>(R.id.rowLanguage)
        languageRow.setOnClickListener {
            showLanguagePickerDialog(view)
        }
        val themeRow = view.findViewById<View>(R.id.rowTheme)
        themeRow.setOnClickListener {
            showThemePickerDialog(view)
        }

        // set initial value (English/עברית) when opening settings
        updateLanguageRowValue(view)
        // same for the theme
        updateThemeRowValue(view)
    }


    private fun updateLanguageRowValue(root: View) {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val valueText = root.findViewById<TextView>(R.id.textLanguageValue)
        valueText.text = if (tags.startsWith("he")) "עברית" else "English"
    }

    private fun showLanguagePickerDialog(root: View) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_language_picker, null)

        val radioEnglish = dialogView.findViewById<RadioButton>(R.id.radioEnglish)
        val radioHebrew = dialogView.findViewById<RadioButton>(R.id.radioHebrew)

        // Preselect current language
        val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (currentTags.startsWith("he")) {
            radioHebrew.isChecked = true
        } else {
            radioEnglish.isChecked = true
        }

        val alertDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener {
            alertDialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btnOk).setOnClickListener {
            val tags = if (radioHebrew.isChecked) "he" else "en"
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))

            // Update the visible value in the settings row
            updateLanguageRowValue(root)

            alertDialog.dismiss()
        }

        alertDialog.show()
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun getCurrentThemeMode(): Int {
        return AppCompatDelegate.getDefaultNightMode()
    }

    private fun showThemePickerDialog(root: View) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_theme_picker, null)

        val radioSystem = dialogView.findViewById<RadioButton>(R.id.radioSystem)
        val radioLight = dialogView.findViewById<RadioButton>(R.id.radioLight)
        val radioDark = dialogView.findViewById<RadioButton>(R.id.radioDark)

        // Preselect current theme
        when (getCurrentThemeMode()) {
            AppCompatDelegate.MODE_NIGHT_YES -> radioDark.isChecked = true
            AppCompatDelegate.MODE_NIGHT_NO -> radioLight.isChecked = true
            else -> radioSystem.isChecked = true // FOLLOW_SYSTEM / UNSPECIFIED
        }

        val alertDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<View>(R.id.btnCancel).setOnClickListener {
            alertDialog.dismiss()
        }

        dialogView.findViewById<View>(R.id.btnOk).setOnClickListener {
            val mode = when {
                radioDark.isChecked -> AppCompatDelegate.MODE_NIGHT_YES
                radioLight.isChecked -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }

            AppCompatDelegate.setDefaultNightMode(mode)

            updateThemeRowValue(root)

            alertDialog.dismiss()
        }

        alertDialog.show()
        alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun updateThemeRowValue(root: View) {
        val valueText = root.findViewById<TextView>(R.id.textThemeValue)

        val mode = AppCompatDelegate.getDefaultNightMode()
        valueText.text = when (mode) {
            AppCompatDelegate.MODE_NIGHT_YES -> "Dark"
            AppCompatDelegate.MODE_NIGHT_NO -> "Light"
            else -> "System"
        }
    }

}