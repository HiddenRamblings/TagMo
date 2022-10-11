package com.hiddenramblings.tagmo.amiibo;

import com.hiddenramblings.tagmo.charset.CharsetCompat;
import com.hiddenramblings.tagmo.nfctech.TagArray;

public class FlaskTag extends Amiibo {

    public FlaskTag(String tail) {
        super(null, TagArray.bytesToLong(
                tail.getBytes(CharsetCompat.UTF_8)
        ), "New Tag ", null);
    }
}
