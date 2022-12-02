package com.hiddenramblings.tagmo.amiibo

class EliteTag(amiibo: Amiibo?) : Amiibo(
    amiibo?.manager,
    amiibo?.id ?: 0,
    amiibo?.name,
    amiibo?.releaseDates
) {
    var data: ByteArray? = null
    var index: Int = -1

}