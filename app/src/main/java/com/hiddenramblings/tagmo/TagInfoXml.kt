package com.hiddenramblings.tagmo

import com.hiddenramblings.tagmo.eightbit.io.Debug
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

/*******************************************************************************************
    <scan>
        <section> (last)
            <subsection title="Memory Content">
                <block type="Ultralight">
                    <address>2</address>
                    <data access="* " comment="(BCC1, INT, LOCK0-LOCK1)">XX XX XX XX</data>
                </block>
                <block type="Ultralight">
                    <address>3</address>
                    <data access="* " comment="(OTP0-OTP3)">XX XX XX XX</data>
                </block>
            </subsection>
        </section>
    </scan>
 ******************************************************************************************/
class TagInfoXml {
    fun parseXml(tagInfo: String) {
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val xpp = factory.newPullParser()
            xpp.setInput(StringReader(tagInfo))
            var eventType = xpp.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_DOCUMENT) {
                    Debug.verbose(javaClass, "Start document")
                } else if (eventType == XmlPullParser.START_TAG) {
                    Debug.verbose(javaClass, "Start tag " + xpp.name)
                } else if (eventType == XmlPullParser.END_TAG) {
                    Debug.verbose(javaClass, "End tag " + xpp.name)
                } else if (eventType == XmlPullParser.TEXT) {
                    Debug.verbose(javaClass, "Text " + xpp.text)
                }
                eventType = xpp.next()
            }
            Debug.verbose(javaClass, "End document")
        } catch (e: XmlPullParserException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}