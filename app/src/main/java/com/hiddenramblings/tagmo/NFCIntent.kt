package com.hiddenramblings.tagmo

import com.hiddenramblings.tagmo.eightbit.io.Debug
import android.content.ComponentName
import android.content.Intent
import android.os.Build

object NFCIntent {
    @JvmStatic
    val FilterComponent = ComponentName(
        BuildConfig.APPLICATION_ID,
        "com.hiddenramblings.tagmo.NFCIntentFilter"
    )
    const val ACTION_EDIT_COMPLETE = BuildConfig.APPLICATION_ID + ".EDIT_COMPLETE"
    const val ACTION_SCAN_TAG = BuildConfig.APPLICATION_ID + ".SCAN_TAG"
    const val ACTION_WRITE_TAG_FULL = BuildConfig.APPLICATION_ID + ".WRITE_TAG_FULL"
    const val ACTION_WRITE_TAG_RAW = BuildConfig.APPLICATION_ID + ".WRITE_TAG_RAW"
    const val ACTION_WRITE_TAG_DATA = BuildConfig.APPLICATION_ID + ".WRITE_TAG_DATA"
    const val ACTION_UPDATE_TAG = BuildConfig.APPLICATION_ID + ".UPDATE_TAG"
    const val ACTION_WRITE_ALL_TAGS = BuildConfig.APPLICATION_ID + ".WRITE_ALL_TAGS"
    const val ACTION_ERASE_ALL_TAGS = BuildConfig.APPLICATION_ID + ".CLEAR_ALL_TAGS"
    const val ACTION_ACTIVATE_BANK = BuildConfig.APPLICATION_ID + ".ACTIVATE_BANK"
    const val ACTION_SET_BANK_COUNT = BuildConfig.APPLICATION_ID + ".SET_BANK_COUNT"
    const val ACTION_ERASE_BANK = BuildConfig.APPLICATION_ID + ".ERASE_BANK"
    const val ACTION_LOCK_AMIIBO = BuildConfig.APPLICATION_ID + ".LOCK_AMIIBO"
    const val ACTION_UNLOCK_UNIT = BuildConfig.APPLICATION_ID + ".UNLOCK_UNIT"
    const val ACTION_BACKUP_AMIIBO = BuildConfig.APPLICATION_ID + ".BACKUP_AMIIBO"
    const val ACTION_FIX_BANK_DATA = BuildConfig.APPLICATION_ID + ".FIX_BANK_DATA"
    const val ACTION_NFC_SCANNED = BuildConfig.APPLICATION_ID + ".NFC_SCANNED"
    const val ACTION_BLIND_SCAN = BuildConfig.APPLICATION_ID + ".BLIND_SCAN"
    const val EXTRA_TAG_DATA = BuildConfig.APPLICATION_ID + ".EXTRA_TAG_DATA"
    const val EXTRA_AMIIBO_LIST = BuildConfig.APPLICATION_ID + ".EXTRA_AMIIBO_LIST"
    const val EXTRA_IGNORE_TAG_ID = BuildConfig.APPLICATION_ID + ".EXTRA_IGNORE_TAG_ID"
    const val EXTRA_AMIIBO_ID = BuildConfig.APPLICATION_ID + ".AMIIBO_ID"
    const val EXTRA_AMIIBO_FILES = BuildConfig.APPLICATION_ID + ".EXTRA_AMIIBO_FILES"
    const val EXTRA_SIGNATURE = BuildConfig.APPLICATION_ID + ".EXTRA_SIGNATURE"
    const val EXTRA_ACTIVE_BANK = BuildConfig.APPLICATION_ID + ".EXTRA_ACTIVE_BANK"
    const val EXTRA_BANK_COUNT = BuildConfig.APPLICATION_ID + ".EXTRA_BANK_COUNT"
    const val EXTRA_CURRENT_BANK = BuildConfig.APPLICATION_ID + ".EXTRA_CURRENT_BANK"
    const val SITE_GITLAB_README = "https://tagmo.gitlab.io/"
    @JvmStatic
    fun getIntent(intent: Intent): Intent {
        return if (Debug.isNewer(Build.VERSION_CODES.N)) intent.addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION) else intent.addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
    }
}