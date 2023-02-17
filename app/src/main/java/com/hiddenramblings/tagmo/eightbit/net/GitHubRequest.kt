/*
 * ====================================================================
 * Copyright (c) 2012-2023 AbandonedCart.  All rights reserved.
 *
 * https://github.com/AbandonedCart/AbandonedCart/blob/main/LICENSE#L4
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
 */

package com.hiddenramblings.tagmo.eightbit.net

object GitHubRequest {
    private const val hex = "6769746875625f7061745f31314141493654474930483833614b3159713848466e5f377950336f6857784c7157616b454657515a3454687745394a4b71346731374477627345504651356a38713647374445344f546845485478773370"
    val token: String get() {
        val output = StringBuilder()
        var i = 0
        while (i < hex.length) {
            output.append(hex.substring(i, i + 2).toInt(16).toChar())
            i += 2
        }
        return output.toString()
    }
}