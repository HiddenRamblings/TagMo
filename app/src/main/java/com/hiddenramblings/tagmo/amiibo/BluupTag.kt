package com.hiddenramblings.tagmo.amiibo

import com.hiddenramblings.tagmo.nfctech.TagArray.toByteArray
import com.hiddenramblings.tagmo.nfctech.TagArray.toLong
import java.nio.charset.StandardCharsets

class BluupTag(name: List<String>) : Amiibo(null,
        name[1].toByteArray(StandardCharsets.UTF_8).toLong(), name[0], null
) {
    override val bluupTail: String get() = run { String(id.toByteArray()) }
    override var bluupName: String? = name[0]
}