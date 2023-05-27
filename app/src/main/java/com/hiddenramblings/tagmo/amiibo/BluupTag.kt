package com.hiddenramblings.tagmo.amiibo

import com.hiddenramblings.tagmo.eightbit.charset.CharsetCompat
import com.hiddenramblings.tagmo.nfctech.TagArray

class BluupTag(name: List<String>) : Amiibo(null,
    TagArray.bytesToLong(name[1].toByteArray(CharsetCompat.UTF_8)), name[0], null
) {
    override val bluupTail: String get() = String(TagArray.longToBytes(id))
    override var bluupName: String? = name[0]
}