package com.hiddenramblings.tagmo.browser.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.hiddenramblings.tagmo.JoyCon
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.bluetooth.BluetoothHandler
import com.hiddenramblings.tagmo.bluetooth.BluetoothHandler.BluetoothListener
import com.hiddenramblings.tagmo.eightbit.io.Debug
import com.hiddenramblings.tagmo.widget.Toasty
import com.mumumusuc.libjoycon.BluetoothHelper
import com.mumumusuc.libjoycon.BluetoothHelper.StateChangedCallback

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class JoyConFragment : DialogFragment(), BluetoothListener {
    private var bluetoothHandler: BluetoothHandler? = null
    private var bluetoothHelper: BluetoothHelper? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_joy_con, container, false)
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    private val isBluetoothEnabled: Unit
        get() {
            if (null != bluetoothHelper) return
            bluetoothHandler = if (null != bluetoothHandler) bluetoothHandler else BluetoothHandler(
                requireContext(), requireActivity().activityResultRegistry, this@JoyConFragment
            )
            bluetoothHandler!!.requestPermissions(requireActivity())
        }

    private fun delayedBluetoothEnable() {
        Handler(Looper.getMainLooper()).postDelayed({ isBluetoothEnabled }, 125)
    }

    override fun onPermissionsFailed() {
        dismiss()
        Toasty(requireActivity()).Long(R.string.fail_permissions)
    }

    override fun onAdapterMissing() {
        dismiss()
        Toasty(requireActivity()).Long(R.string.fail_bluetooth_adapter)
    }

    @SuppressLint("MissingPermission")
    override fun onAdapterEnabled(adapter: BluetoothAdapter?) {
        var hasProController = false
        for (gamepad in InputDevice.getDeviceIds()) {
            Debug.verbose(
                JoyConFragment::class.java, "ID: " + gamepad + ", Name: "
                        + InputDevice.getDevice(gamepad).name + ", Descriptor: "
                        + InputDevice.getDevice(gamepad).descriptor
            )
            if (InputDevice.getDevice(gamepad).name == "Nintendo Switch Pro Controller") {
                hasProController = true
                break
            }
        }
        if (null != adapter && hasProController) {
            adapter.bondedDevices.forEach {
                if (it.name == "Pro Controller") {
                    bluetoothHelper = BluetoothHelper()
                    bluetoothHelper!!.register(requireContext(), object : StateChangedCallback {
                        override fun onStateChanged(
                            name: String?, address: String?, state: Int
                        ) { }
                    })
                    // bluetoothHelper.connectL2cap(device);
                    val proController = JoyCon(bluetoothHelper, it)
                    // proController.poll(JoyCon.PollType.STANDARD);
                    proController.setPlayer(0, 0)
                    proController.setPlayer(0, 4)
                    Toasty(requireActivity()).Short(
                        "1, 2, 3, 4. I'm connected. Is there more?"
                    )
                    proController.enableRumble(true)
                    val rumbleData = ByteArray(8)
                    rumbleData[0] = 0x1
                    proController.rumble(rumbleData)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = AlertDialog.Builder(requireActivity())
        dialog.setNegativeButton(R.string.close) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        val joyConDialog: Dialog = dialog.setView(view).create()
        joyConDialog.setOnShowListener { delayedBluetoothEnable() }
        return joyConDialog
    }

    override fun onCancel(dialog: DialogInterface) {
        if (null != bluetoothHelper) {
            bluetoothHelper!!.unregister(requireContext())
            bluetoothHelper = null
        }
        super.onCancel(dialog)
    }

    companion object {
        fun newInstance(): JoyConFragment {
            return JoyConFragment()
        }
    }
}