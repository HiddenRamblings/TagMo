package com.hiddenramblings.tagmo.fragment

import android.annotation.SuppressLint
import android.app.Dialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.hiddenramblings.tagmo.JoyCon
import com.hiddenramblings.tagmo.R
import com.hiddenramblings.tagmo.bluetooth.BluetoothHandler
import com.hiddenramblings.tagmo.bluetooth.BluetoothHandler.BluetoothListener
import com.hiddenramblings.tagmo.widget.Toasty
import com.mumumusuc.libjoycon.BluetoothHelper
import com.mumumusuc.libjoycon.BluetoothHelper.StateChangedCallback

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
class JoyConFragment : DialogFragment(), BluetoothListener {
    private var bluetoothHandler: BluetoothHandler? = null
    private var bluetoothHelper: BluetoothHelper? = null

    private var controllerList: LinearLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_joystick, container, false)
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        controllerList = view.findViewById(R.id.controller_list)
        super.onViewCreated(view, savedInstanceState)
    }

    private val isBluetoothEnabled: Boolean
        get() {
            context?.run {
                if (InputDevice.getDeviceIds().any { gamepad ->
                        InputDevice.getDevice(gamepad).name == "Nintendo Switch Pro Controller"
                                || InputDevice.getDevice(gamepad).name.contains("JoyCon (R)")
                    }) {
                    bluetoothHandler = bluetoothHandler ?: BluetoothHandler(
                        this, requireActivity().activityResultRegistry,
                        this@JoyConFragment
                    )
                    bluetoothHandler?.requestPermissions(requireActivity())
                } else {
                    Toasty(requireActivity()).Short(R.string.no_controllers)
                    dismiss()
                }
            } ?: delayedBluetoothEnable()
            return false
        }

    private fun delayedBluetoothEnable() {
        Handler(Looper.getMainLooper()).postDelayed({ isBluetoothEnabled }, 125)
    }

    override fun onPermissionsFailed() {
        dismiss()
        Toasty(requireContext()).Long(R.string.fail_permissions)
    }

    override fun onAdapterMissing() {
        dismiss()
        Toasty(requireContext()).Long(R.string.fail_bluetooth_adapter)
    }

    override fun onAdapterRestricted() {
        delayedBluetoothEnable()
    }

    @SuppressLint("InflateParams")
    private fun configureController(adapter: BluetoothAdapter, device: BluetoothDevice) {
        val item = this.layoutInflater.inflate(R.layout.device_controller, null)
        item.findViewById<TextView>(R.id.controller_name).text = device.name
        val onClickListener = OnClickListener {
            bluetoothHelper = BluetoothHelper(adapter).apply {
                register(requireContext(), object : StateChangedCallback {
                    override fun onStateChanged(
                        name: String?, address: String?, state: Int
                    ) { }
                })
            }
            // bluetoothHelper.connectL2cap(device);
            val joycon = JoyCon(bluetoothHelper, device)
            // proController.poll(JoyCon.PollType.STANDARD);
            joycon.setPlayer(0, 0)
            joycon.setPlayer(0, 4)
            Toasty(requireActivity()).Short(
                "1, 2, 3, 4. I'm connected. Is there more?"
            )
            joycon.enableRumble(true)
            val rumbleData = ByteArray(8)
            rumbleData[0] = 0x1
            joycon.rumble(rumbleData)
        }
        item.findViewById<View>(R.id.connect_pro).run {
            setOnClickListener(onClickListener)
            if (device.name != "Pro Controller") isVisible = false
        }
        item.findViewById<View>(R.id.connect_rjc).run {
            setOnClickListener(onClickListener)
            if (device.name != "JoyCon (R)") isVisible = false
        }
        controllerList?.addView(item)
    }

    @SuppressLint("MissingPermission")
    override fun onAdapterEnabled(adapter: BluetoothAdapter?) {
        val proBluetooth = adapter?.bondedDevices?.filter { device -> device.name == "Pro Controller" }
        val joyBluetooth = adapter?.bondedDevices?.filter { device -> device.name == "JoyCon (R)" }
        if (proBluetooth.isNullOrEmpty() && joyBluetooth.isNullOrEmpty()) {
            Toasty(requireActivity()).Short(R.string.no_controllers)
            dismiss()
            return
        }
        if (!proBluetooth.isNullOrEmpty()) {
            proBluetooth.forEach {
                configureController(adapter, it)
            }
        }
        if (!joyBluetooth.isNullOrEmpty()) {
            joyBluetooth.forEach {
                configureController(adapter, it)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = AlertDialog.Builder(requireActivity())
        dialog.setNegativeButton(R.string.close) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }

        val joyConDialog: Dialog = dialog.setView(view).create()
        joyConDialog.setOnShowListener { delayedBluetoothEnable() }

        val width = ViewGroup.LayoutParams.MATCH_PARENT
        val height = ViewGroup.LayoutParams.MATCH_PARENT
        joyConDialog.window?.setLayout(width, height)

        return joyConDialog
    }

    override fun onCancel(dialog: DialogInterface) {
        bluetoothHelper?.unregister(requireContext())
        bluetoothHelper = null
        bluetoothHandler?.unregisterResultContracts()
        super.onCancel(dialog)
    }

    companion object {
        fun newInstance(): JoyConFragment {
            return JoyConFragment()
        }
    }
}