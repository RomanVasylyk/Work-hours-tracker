package com.example.worktr.util

import com.example.worktr.data.Client

object ClientDefaults {
    const val NAME = "Demo klient s.r.o."
    const val STREET = "Obchodná 24"
    const val CITY = "Bratislava"
    const val ZIP = "81106"
    const val COUNTRY = "Slovensko"
    const val ICO = "87654321"
    const val DIC = "2120000000"
    const val ICDPH = "SK2120000000"
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
