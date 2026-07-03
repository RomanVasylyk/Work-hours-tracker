package com.example.worktr.util

import android.content.Context
import android.content.SharedPreferences
import com.example.worktr.data.Client

/**
 * Single home for the "invoice_prefs" SharedPreferences keys and defaults that
 * used to be copy-pasted across JobListFragment, JobDetailFragment,
 * SettingsFragment and ClientsFragment.
 */
object InvoicePrefs {
    const val NAME = "invoice_prefs"

    const val PREF_SUPPLIER_NAME = "supplier_name"
    const val PREF_SUPPLIER_STREET = "supplier_street"
    const val PREF_SUPPLIER_CITY = "supplier_city"
    const val PREF_SUPPLIER_ZIP = "supplier_zip"
    const val PREF_SUPPLIER_COUNTRY = "supplier_country"
    const val PREF_SUPPLIER_ICO = "supplier_ico"
    const val PREF_EXTRA_NAME = "extra_name"
    const val PREF_EXTRA_QUANTITY = "extra_quantity"
    const val PREF_EXTRA_UNIT = "extra_unit"
    const val PREF_EXTRA_PRICE = "extra_price"
    const val PREF_CURRENCY = "currency"
    const val PREF_PDF_LANGUAGE = "pdf_language"

    const val PREF_CLIENT_NAME = "name"
    const val PREF_CLIENT_STREET = "street"
    const val PREF_CLIENT_CITY = "city"
    const val PREF_CLIENT_ZIP = "zip"
    const val PREF_CLIENT_COUNTRY = "country"
    const val PREF_CLIENT_ICO = "ico"
    const val PREF_CLIENT_DIC = "dic"
    const val PREF_CLIENT_ICDPH = "icdph"
    const val PREF_CLIENT_DESCRIPTION = "description_template"

    const val DEFAULT_SUPPLIER_NAME = "Ukážkový dodávateľ"
    const val DEFAULT_SUPPLIER_STREET = "Hlavná 12"
    const val DEFAULT_SUPPLIER_CITY = "Nitra"
    const val DEFAULT_SUPPLIER_ZIP = "94901"
    const val DEFAULT_SUPPLIER_ICO = "12345678"
    const val DEFAULT_COUNTRY = "Slovensko"
    const val DEFAULT_EXTRA_NAME = "Doprava"
    const val DEFAULT_EXTRA_QUANTITY = "1"
    const val DEFAULT_EXTRA_UNIT = ""
    const val DEFAULT_EXTRA_PRICE = "10"
    const val DEFAULT_CURRENCY = "EUR"

    private const val CLIENT_PREFIX = "client"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun clientKey(jobId: Int, field: String): String = "${CLIENT_PREFIX}_${jobId}_$field"

    /** Per-job client value with fallback to the legacy global key, then to the default. */
    fun clientValue(prefs: SharedPreferences, jobId: Int, field: String, defaultValue: String): String =
        prefs.getString(clientKey(jobId, field), null)
            ?: prefs.getString(field, defaultValue)
            ?: defaultValue

    fun clientForJob(prefs: SharedPreferences, jobId: Int): Client =
        Client(
            jobId = jobId,
            name = clientValue(prefs, jobId, PREF_CLIENT_NAME, ClientDefaults.NAME),
            street = clientValue(prefs, jobId, PREF_CLIENT_STREET, ClientDefaults.STREET),
            city = clientValue(prefs, jobId, PREF_CLIENT_CITY, ClientDefaults.CITY),
            zip = clientValue(prefs, jobId, PREF_CLIENT_ZIP, ClientDefaults.ZIP),
            country = clientValue(prefs, jobId, PREF_CLIENT_COUNTRY, ClientDefaults.COUNTRY),
            ico = clientValue(prefs, jobId, PREF_CLIENT_ICO, ClientDefaults.ICO),
            dic = clientValue(prefs, jobId, PREF_CLIENT_DIC, ClientDefaults.DIC),
            icdph = clientValue(prefs, jobId, PREF_CLIENT_ICDPH, ClientDefaults.ICDPH),
            serviceTemplate = clientValue(prefs, jobId, PREF_CLIENT_DESCRIPTION, ClientDefaults.SERVICE_TEMPLATE)
        )
}
