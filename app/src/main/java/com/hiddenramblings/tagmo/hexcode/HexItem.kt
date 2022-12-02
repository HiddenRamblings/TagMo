package com.hiddenramblings.tagmo.hexcode

import android.graphics.Typeface

open class HexItem internal constructor(
    var text: String,
    var textStyle: Int,
    var backgroundColor: Int
) {

    constructor(text: String, backgroundColor: Int) : this(text, Typeface.BOLD, backgroundColor) {}
}