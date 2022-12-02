package com.example.bluetoothconnection

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import android.widget.Toast
import com.example.bluetoothconnection.databinding.ActivityMainBinding
import com.google.gson.Gson
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainActivity : Activity() {
    var h: Handler? = null
    val RECEIVE_MESSAGE = 1
    private var btAdapter: BluetoothAdapter? = null
    private var btSocket: BluetoothSocket? = null
    private val sb = StringBuilder()
    private var mConnectedThread: ConnectedThread? = null
    private lateinit var binding: ActivityMainBinding
    var settings = Settings(false, false, false, 0, 0, arrayOf(0, 0, 0))
    var data = Data(arrayOf(0, 0), arrayOf(0, 0), arrayOf(0, 0, 0))

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        h = object : Handler() {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    RECEIVE_MESSAGE -> {
                        val readBuf = msg.obj as ByteArray
                        val strIncom = String(readBuf, 0, msg.arg1)
                        sb.append(strIncom) // формируем строку
                        val endOfLineIndex = sb.indexOf("}") // определяем символы конца строки
                        if (endOfLineIndex > 0) {                                            // если встречаем конец строки,
                            var sbprint = sb.substring(0, endOfLineIndex) // то извлекаем строку
                            sb.delete(0, sb.length) // и очищаем sb
                            sbprint += "}"
                            Log.d(TAG, sbprint)
                            var gson =Gson()
                            if(REQUEST_ID == 0) {
                                settings = gson.fromJson(sbprint, Settings::class.java)
                            }
                            else{
                                if(REQUEST_ID == 1){
                                    data = gson.fromJson(sbprint, Data::class.java)
                                }
                            }
                        }
                    }
                }
            }
        }
        btAdapter = BluetoothAdapter.getDefaultAdapter()
        checkBTState()
        binding.RefreshButton.setOnClickListener {
            REQUEST_ID = 0
            mConnectedThread!!.write("%{\"type\":\"g_state_module\"}@")
            var string = "Состояние устройства:\n"
            if(settings.fan_c){
                string += "Вентилятор хол. воздуха: включён\n"
            }
            else{
                string += "Вентилятор хол. воздуха: отключён\n"
            }
            if(settings.fan_h){
                string += "Вентилятор гор. воздуха: включён\n"
            }
            else{
                string += "Вентилятор гор. воздуха: отключён\n"
            }
            if(settings.heat){
                string += "Нагреватель: включен\n"
            }
            else{
                string += "Нагреватель: отключен\n"
            }
            if(settings.servo == 0){
                string += "Моторчик: в изн. положении\n"
            }
            else{
                string += "Моторчик: повёрнут\n"
            }
            string += "Данные получены "
            val currentTime = Calendar.getInstance().time.toLocaleString()
            string += currentTime
            binding.textViewDataOfModules.text = string
        }
        binding.buttonAction.setOnClickListener {
            var reqest = "%{\"type\":\"manual\","
            if (binding.spinnerModule.selectedItemPosition == 0){
                reqest += "\"mod\":0,"
            }
            else{
                if (binding.spinnerModule.selectedItemPosition == 1){
                    reqest += "\"mod\":1,"
                }
                else{
                    if(binding.spinnerModule.selectedItemPosition == 2){
                        reqest += "\"mod\":2,"
                    }
                    else{
                        if(binding.spinnerModule.selectedItemPosition == 3){
                            reqest += "\"mod\":3,"
                        }
                    }
                }
            }
            if (binding.spinnerStatus.selectedItemPosition == 0){
                reqest += "\"state\":1"
            }
            else{
                reqest += "\"state\":0"
            }
            reqest += "}@"
            mConnectedThread!!.write(reqest)
        }
        binding.DataButton.setOnClickListener {
            REQUEST_ID = 1
            mConnectedThread!!.write("%{\"type\":\"g_temp_info\"}@")
            var string = "Показания датчиков:\n" +
                    " 1 датчик:\n" +
                    "    Температура: " + data.sensor_0[0].toString() + "C" +
                    "\n    Влажность: " + data.sensor_0[1].toString() + "%" +
                    "\n 2 датчик: \n"+
                    "    Температура: " + data.sensor_1[0].toString() + "C" +
                    "\n    Влажность: " + data.sensor_1[1].toString() + "%" +
                    "\nДанные получены " + Calendar.getInstance().time.toLocaleString()
            binding.textViewData.text = string
        }
    }

    public override fun onResume() {
        super.onResume()
        Log.d(TAG, "...onResume - попытка соединения...")
        val device = btAdapter!!.getRemoteDevice(address)
        try {
            btSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
        } catch (e: IOException) {
            errorExit("Fatal Error", "In onResume() and socket create failed: " + e.message + ".")
        }
        btAdapter!!.cancelDiscovery()
        Log.d(TAG, "...Соединяемся...")
        try {
            btSocket!!.connect()
            Log.d(TAG, "...Соединение установлено и готово к передачи данных...")
        } catch (e: IOException) {
            try {
                btSocket!!.close()
                Log.d(TAG, "Соединение не установлено")
           } catch (e2: IOException) {
                errorExit(
                    "Fatal Error",
                    "In onResume() and unable to close socket during connection failure" + e2.message + "."
                )
            }
        }
        Log.d(TAG, "...Создание Socket...")
        mConnectedThread = ConnectedThread(btSocket!!)
        mConnectedThread!!.start()
    }
    public override fun onPause() {
        super.onPause()
        Log.d(TAG, "...In onPause()...")
        try {
            btSocket!!.close()
        } catch (e2: IOException) {
            errorExit("Fatal Error", "In onPause() and failed to close socket." + e2.message + ".")
        }
    }
    private fun checkBTState() {
        if (btAdapter == null) {
            errorExit("Fatal Error", "Bluetooth не поддерживается")
        } else {
            if (btAdapter!!.isEnabled) {
                Log.d(TAG, "...Bluetooth включен...")
            } else {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
        }
    }
    private fun errorExit(title: String, message: String) {
        Toast.makeText(baseContext, "$title - $message", Toast.LENGTH_LONG).show()
        finish()
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream?
        private val mmOutStream: OutputStream?
        override fun run() {
            val buffer = ByteArray(256)
            var bytes: Int
            while (true) {
                try {
                    bytes =
                        mmInStream!!.read(buffer)
                    h?.obtainMessage(RECEIVE_MESSAGE, bytes, -1, buffer)
                        ?.sendToTarget()
                } catch (e: IOException) {
                    break
                }
            }
        }
        fun write(message: String) {
            try {
                mmOutStream!!.write(message.toByteArray())
                Log.d(TAG, "Данные отправлены")
            } catch (e: IOException) {
                Log.d(TAG, "...Ошибка отправки данных: " + e.message + "...")
            }
        }
        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
            }
        }
        init {
            var tmpIn: InputStream? = null
            var tmpOut: OutputStream? = null
            try {
                tmpIn = mmSocket.inputStream
                tmpOut = mmSocket.outputStream
            } catch (e: IOException) {
            }
            mmInStream = tmpIn
            mmOutStream = tmpOut
        }
    }
    companion object {
        private var REQUEST_ID = 0
        private const val TAG = "bluetooth"
        private const val REQUEST_ENABLE_BT = 1
        private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val address = "20:16:01:19:68:59"
    }
}