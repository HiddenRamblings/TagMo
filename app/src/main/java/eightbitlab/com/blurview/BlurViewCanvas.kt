package eightbitlab.com.blurview

import android.graphics.Bitmap
import android.graphics.Canvas

// Servers purely as a marker of a Canvas used in BlurView
// to skip drawing itself and other BlurViews on the View hierarchy snapshot
class BlurViewCanvas(bitmap: Bitmap) : Canvas(bitmap)