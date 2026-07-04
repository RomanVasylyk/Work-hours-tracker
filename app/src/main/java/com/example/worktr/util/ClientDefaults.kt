package com.example.worktr.util

import com.example.worktr.data.Client

object ClientDefaults {
    const val NAME = ""
    const val STREET = ""
    const val CITY = ""
    const val ZIP = ""
    const val COUNTRY = "Slovensko"
    const val ICO = ""
    const val DIC = ""
    const val ICDPH = ""
    const val SERVICE_TEMPLATE =
        "Fakturujem Vám za vykonanú prácu – kontrolu kvality v mesiaci {month}"

    fun forJob(jobId: Int): Client =
        Client(
            jobId = jobId,
            name = NAME,
            street = STREET,
            city = CITY,
            zip = ZIP,
            country = COUNTRY,
            ico = ICO,
            dic = DIC,
            icdph = ICDPH,
            serviceTemplate = SERVICE_TEMPLATE
        )
}
