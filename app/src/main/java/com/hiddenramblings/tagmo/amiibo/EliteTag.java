package com.hiddenramblings.tagmo.amiibo;

public class EliteTag extends Amiibo {

    public byte[] data;
    public int index;

    public EliteTag(Amiibo amiibo) {
        super(
                null != amiibo ? amiibo.manager : null,
                null != amiibo ? amiibo.id : 0,
                null != amiibo ? amiibo.name : null,
                null != amiibo ? amiibo.releaseDates : null
        );
        this.data = null;
        this.index = -1;
    }
}
