package com.hiddenramblings.tagmo.amiibo

import java.io.Serializable
import java.util.Date

class AmiiboReleaseDates @JvmOverloads constructor(
    val northAmerica: Date? = null,
    val japan: Date? = null,
    val europe: Date? = null,
    val australia: Date? = null
) : Serializable