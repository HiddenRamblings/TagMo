package com.hiddenramblings.tagmo.amiibo

import com.hiddenramblings.tagmo.eightbit.charset.CharsetCompat
import com.hiddenramblings.tagmo.nfctech.TagArray.toByteArray
import com.hiddenramblings.tagmo.nfctech.TagArray.toLong

class BluupTag(name: List<String>) : Amiibo(null,
        name[1].toByteArray(CharsetCompat.UTF_8).toLong(), name[0], null
) {
    override val bluupTail: String get() = run { String(id.toByteArray()) }
    override var bluupName: String? = name[0]
}