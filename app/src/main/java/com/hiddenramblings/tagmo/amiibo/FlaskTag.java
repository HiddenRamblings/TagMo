package com.hiddenramblings.tagmo.amiibo;

import com.hiddenramblings.tagmo.charset.CharsetCompat;
import com.hiddenramblings.tagmo.nfctech.TagArray;

public class FlaskTag extends Amiibo {

    public FlaskTag(String[] name) {
        super(null, TagArray.bytesToLong(
                name[2].getBytes(CharsetCompat.UTF_8)
        ), name[0], null);
    }
}
