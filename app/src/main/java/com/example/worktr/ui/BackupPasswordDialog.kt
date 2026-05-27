package com.example.worktr.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.example.worktr.R
import com.example.worktr.databinding.DialogBackupPasswordBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object BackupPasswordDialog {
    fun show(
        context: Context,
        inflater: LayoutInflater,
        confirmPassword: Boolean,
        onPassword: (String) -> Unit
    ) {
        val binding = DialogBackupPasswordBinding.inflate(inflater)
        binding.layoutBackupPasswordConfirm.visibility = if (confirmPassword) View.VISIBLE else View.GONE
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(if (confirmPassword) R.string.backup_export else R.string.backup_restore, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = binding.editBackupPassword.text?.toString().orEmpty()
                val confirmation = binding.editBackupPasswordConfirm.text?.toString().orEmpty()
                binding.layoutBackupPassword.error = null
                binding.layoutBackupPasswordConfirm.error = null
                when {
                    password.length < 6 -> binding.layoutBackupPassword.error =
                        context.getString(R.string.backup_password_too_short)
                    confirmPassword && password != confirmation -> binding.layoutBackupPasswordConfirm.error =
                        context.getString(R.string.backup_password_mismatch)
                    else -> {
                        dialog.dismiss()
                        onPassword(password)
                    }
                }
            }
        }
        dialog.show()
    }
}
