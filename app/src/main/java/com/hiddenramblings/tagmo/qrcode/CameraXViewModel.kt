package com.hiddenramblings.tagmo.qrcode

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.hiddenramblings.tagmo.eightbit.io.Debug
import java.util.concurrent.ExecutionException

/**
 * View model for interacting with CameraX.
 * Create an instance which interacts with the camera service via the given application context.
 */
class CameraXViewModel(application: Application) : AndroidViewModel(application) {
    private var cameraProviderLiveData: MutableLiveData<ProcessCameraProvider>? = null

    // Handle any errors (including cancellation) here.
    val processCameraProvider: LiveData<ProcessCameraProvider>
        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        get() {
            if (cameraProviderLiveData == null) {
                cameraProviderLiveData = MutableLiveData()
                val cameraProviderFuture = ProcessCameraProvider.getInstance(getApplication())
                cameraProviderFuture.addListener({
                        try {
                            cameraProviderLiveData!!.setValue(cameraProviderFuture.get())
                        } catch (e: ExecutionException) {
                            // Handle any errors (including cancellation) here.
                            Debug.error(e)
                        } catch (e: InterruptedException) {
                            Debug.error(e)
                        }
                    },
                    ContextCompat.getMainExecutor(getApplication())
                )
            }
            return cameraProviderLiveData!!
        }
}