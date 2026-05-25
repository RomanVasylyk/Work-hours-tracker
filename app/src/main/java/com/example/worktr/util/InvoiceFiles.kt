package com.example.worktr.util

import android.content.Context
import java.io.File

object InvoiceFiles {
    fun directory(context: Context): File =
        File(context.filesDir, "invoices").apply { mkdirs() }

    fun fileFor(context: Context, fileName: String): File =
        File(directory(context), fileName)

    fun persist(context: Context, source: File): File {
        val target = fileFor(context, source.name)
        source.copyTo(target, overwrite = true)
        return target
    }
}
