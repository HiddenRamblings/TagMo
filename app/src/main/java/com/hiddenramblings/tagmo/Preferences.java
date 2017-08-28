package com.hiddenramblings.tagmo;

import org.androidannotations.annotations.sharedpreferences.DefaultBoolean;
import org.androidannotations.annotations.sharedpreferences.DefaultInt;
import org.androidannotations.annotations.sharedpreferences.SharedPref;

@SharedPref(value=SharedPref.Scope.UNIQUE)
public interface Preferences {
    String query();

    @DefaultInt(BrowserActivity.SORT_NAME)
    int sort();

    String filterGameSeries();

    String filterCharacter();

    String filterAmiiboSeries();

    String filterAmiiboType();

    @DefaultBoolean(keyRes=R.string.settings_enable_amiibo_browser, value=false)
    boolean enableAmiiboBrowser();

    @DefaultBoolean(keyRes=R.string.settings_enable_tag_type_validation, value=true)
    boolean enableTagTypeValidation();

    @DefaultInt(BrowserActivity.VIEW_TYPE_COMPACT)
    int browserView();

    @DefaultInt(MainActivity.VIEW_TYPE_COMPACT)
    int mainView();
}
