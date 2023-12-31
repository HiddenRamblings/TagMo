/*
 * ====================================================================
 * Copyright (c) 2016 AndroidMad / Mushtaq M A
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software
 * is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * Copyright (C) 2022 AbandonedCart @ TagMo
 * ====================================================================
 */

package androidmads.library.qrgenearator

import android.graphics.Bitmap
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.hiddenramblings.tagmo.eightbit.os.Version
import java.nio.charset.StandardCharsets
import java.util.*

class QRGEncoder(data: String?, bundle: Bundle?, type: Int, private var dimension: Int) {
    
    private var colorWhite = -0x1
    private var colorBlack = -0x1000000
    private var contents: String? = null
    private var displayContents: String? = null
    var title: String? = null
        private set
    private var format: BarcodeFormat = BarcodeFormat.QR_CODE
    private var encoded = false

    private val keysPhone = arrayOf<String?>(
        ContactsContract.Intents.Insert.PHONE,
        ContactsContract.Intents.Insert.SECONDARY_PHONE,
        ContactsContract.Intents.Insert.TERTIARY_PHONE
    )
    @Suppress("unused")
    private val keysTypePhone = arrayOf(
        ContactsContract.Intents.Insert.PHONE_TYPE,
        ContactsContract.Intents.Insert.SECONDARY_PHONE_TYPE,
        ContactsContract.Intents.Insert.TERTIARY_PHONE_TYPE
    )
    private val keysEmail = arrayOf<String?>(
        ContactsContract.Intents.Insert.EMAIL,
        ContactsContract.Intents.Insert.SECONDARY_EMAIL,
        ContactsContract.Intents.Insert.TERTIARY_EMAIL
    )
    @Suppress("unused")
    private val keysTypeEmail = arrayOf(
        ContactsContract.Intents.Insert.EMAIL_TYPE,
        ContactsContract.Intents.Insert.SECONDARY_EMAIL_TYPE,
        ContactsContract.Intents.Insert.TERTIARY_EMAIL_TYPE
    )

    init {
        encoded = encodeContents(data, bundle, type)
    }

    private fun encodeContents(data: String?, bundle: Bundle?, type: Int): Boolean {
        encodeQRCodeContents(data, bundle, type)
        return contents?.isNotEmpty() == true
    }

    private fun retrieveStringContents(data: String?) {
        if (!data.isNullOrEmpty()) {
            contents = data
            displayContents = data
        }
    }

    private fun encodeQRCodeContents(data: String?, bundle: Bundle?, type: Int) {
        when (type) {
            Barcode.TYPE_WIFI -> {
                retrieveStringContents(data)
                title = "WiFi"
            }
            Barcode.TYPE_URL -> {
                retrieveStringContents(data)
                title = "URL"
            }
            Barcode.TYPE_PRODUCT -> {
                retrieveStringContents(data)
                title = "Product"
            }
            Barcode.TYPE_TEXT -> {
                retrieveStringContents(data)
                title = "Text"
            }
            Barcode.TYPE_CALENDAR_EVENT -> {
                retrieveStringContents(data)
                title = "Calendar"
            }
            Barcode.TYPE_DRIVER_LICENSE -> {
                retrieveStringContents(data)
                title = "License"
            }
            Barcode.TYPE_EMAIL -> {
                val email = trim(data)
                if (email != null) {
                    contents = "mailto:$email"
                    displayContents = email
                    title = "E-Mail"
                }
            }
            Barcode.TYPE_PHONE -> {
                val phone = trim(data)
                if (phone != null) {
                    contents = "tel:$phone"
                    displayContents = if (Version.isLollipop)
                        PhoneNumberUtils.formatNumber(phone, Locale.getDefault().country)
                    else
                        @Suppress("deprecation") PhoneNumberUtils.formatNumber(phone)
                    title = "Phone"
                }
            }
            Barcode.TYPE_SMS -> {
                val sms = trim(data)
                if (sms != null) {
                    contents = "sms:$sms"
                    displayContents = if (Version.isLollipop)
                        PhoneNumberUtils.formatNumber(sms, Locale.getDefault().country)
                    else
                        @Suppress("deprecation") PhoneNumberUtils.formatNumber(sms)
                    title = "SMS"
                }
            }
            Barcode.TYPE_CONTACT_INFO -> if (bundle != null) {
                val newContents = StringBuilder(100)
                val newDisplayContents = StringBuilder(100)
                newContents.append("BEGIN:VCARD\n")
                val name = trim(bundle.getString(ContactsContract.Intents.Insert.NAME))
                if (name != null) {
                    newContents.append("N:").append(escapeVCard(name)).append(';')
                    newDisplayContents.append(name)
                    newContents.append("\n")
                }
                val address = trim(bundle.getString(ContactsContract.Intents.Insert.POSTAL))
                if (address != null) {
                    //the append ; is removed because it is unnecessary because we are breaking into new row
                    newContents.append("ADR:").append(escapeVCard(address)) //.append(';')
                    newContents.append("\n")
                    newDisplayContents.append('\n').append(address)
                }
                val uniquePhones: MutableCollection<String> = HashSet(keysPhone.size)
                keysPhone.forEach { number ->
                    trim(bundle.getString(number))?.let { uniquePhones.add(it) }
                }
                uniquePhones.forEach {
                    newContents.append("TEL:").append(escapeVCard(it)) //.append(';')
                    newContents.append("\n")
                    newDisplayContents.append('\n').append(
                        if (Version.isLollipop)
                            PhoneNumberUtils.formatNumber(it, Locale.getDefault().country)
                        else
                            @Suppress("deprecation") PhoneNumberUtils.formatNumber(it)
                    )
                }
                val uniqueEmails: MutableCollection<String> = HashSet(keysEmail.size)
                keysEmail.forEach {
                    val email = trim(bundle.getString(it))
                    if (email != null) {
                        uniqueEmails.add(email)
                    }
                }
                uniqueEmails.forEach {
                    newContents.append("EMAIL:").append(escapeVCard(it)) //.append(';')
                    newContents.append("\n")
                    newDisplayContents.append('\n').append(it)
                }
                val organization = trim(bundle.getString(ContactsContract.Intents.Insert.COMPANY))
                if (organization != null) {
                    newContents.append("ORG:").append(organization) //.append(';')
                    newContents.append("\n")
                    newDisplayContents.append('\n').append(organization)
                }
                val url = trim(bundle.getString(ContactsContract.Intents.Insert.DATA))
                if (url != null) {
                    // in this field only the website name and the domain are necessary (example : somewebsite.com)
                    newContents.append("URL:").append(escapeVCard(url)) //.append(';');
                    newContents.append("\n")
                    newDisplayContents.append('\n').append(url)
                }
                val note = trim(bundle.getString(ContactsContract.Intents.Insert.NOTES))
                if (note != null) {
                    newContents.append("NOTE:").append(escapeVCard(note)) //.append(';')
                    newContents.append("\n")
                    newDisplayContents.append('\n').append(note)
                }

                // Make sure we've encoded at least one field.
                if (newDisplayContents.isNotEmpty()) {
                    //this end vcard needs to be at the end in order for the default phone reader to recognize it as a contact
                    newContents.append("END:VCARD")
                    newContents.append(';')
                    contents = newContents.toString()
                    displayContents = newDisplayContents.toString()
                    title = "Contact"
                } else {
                    contents = null
                    displayContents = null
                }
            }
            Barcode.TYPE_GEO -> if (bundle != null) {
                // These must use Bundle.getFloat(), not getDouble(), it's part of the API.
                val latitude = bundle.getFloat("LAT", Float.MAX_VALUE)
                val longitude = bundle.getFloat("LONG", Float.MAX_VALUE)
                if (latitude != Float.MAX_VALUE && longitude != Float.MAX_VALUE) {
                    contents = "geo:$latitude,$longitude"
                    displayContents = "$latitude,$longitude"
                    title = "Location"
                }
            }
            Barcode.TYPE_ISBN -> {
                retrieveStringContents(data)
                title = "ISBN"
            }
            Barcode.TYPE_UNKNOWN -> {
                retrieveStringContents(data)
                title = "Unknown"
            }
        }
    }

    // All are 0, or black, by default
    val bitmap: Bitmap?
        get() = if (!encoded) null else try {
            val hints: MutableMap<EncodeHintType?, Any?> = EnumMap(EncodeHintType::class.java)
            guessAppropriateEncoding(contents)?.let {
                hints[EncodeHintType.CHARACTER_SET] = it
            }
            val writer = MultiFormatWriter()
            val result = writer.encode(contents, format, dimension, dimension, hints)
            val width = result.width
            val height = result.height
            val pixels = IntArray(width * height)
            // All are 0, or black, by default
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (result[x, y]) colorWhite else colorBlack
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (ex: Exception) {
            null
        }

    @Suppress("unused")
    fun getBitmap(margin: Int?): Bitmap? {
        return if (!encoded) null else try {
            val hints: MutableMap<EncodeHintType?, Any?> = EnumMap(EncodeHintType::class.java)
            guessAppropriateEncoding(contents)?.let {
                hints[EncodeHintType.CHARACTER_SET] = it
            }
            // Setting the margin width
            margin?.let {
                hints[EncodeHintType.MARGIN] = margin
            }
            val writer = MultiFormatWriter()
            val result = writer.encode(contents, format, dimension, dimension, hints)
            val width = result.width
            val height = result.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (result[x, y]) colorBlack else colorWhite
                }
            }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (ex: Exception) {
            null
        }
    }

    private fun guessAppropriateEncoding(contents: CharSequence?): String? {
        // Very crude at the moment
        if (contents != null) {
            for (element in contents) {
                if (element.code > 0xFF) return StandardCharsets.UTF_8.name()
            }
        }
        return null
    }

    private fun trim(s: String?): String? {
        if (null == s) return null
        return s.trim { it <= ' ' }.ifEmpty { null }
    }

    private fun escapeVCard(input: String?): String? {
        if (null == input || input.indexOf(':') < 0 && input.indexOf(';') < 0) {
            return input
        }
        val length = input.length
        val result = StringBuilder(length)
        for (i in 0 until length) {
            val c = input[i]
            if (c == ':' || c == ';') result.append('\\')
            result.append(c)
        }
        return result.toString()
    }
}