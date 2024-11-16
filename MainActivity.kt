import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var chart: LineChart
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        chart = findViewById(R.id.lineChart)
        setupChart()

        if (checkPermissions()) {
            initializeBLE()
            startScan()
        } else {
            requestPermissions()
        }
    }

    private fun setupChart() {
        val lineData = LineData()
        chart.data = lineData
        chart.description.isEnabled = false
    }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
        ActivityCompat.requestPermissions(this, permissions, 1001)
    }

    private fun initializeBLE() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Bluetooth no está habilitado", Toast.LENGTH_SHORT).show()
        } else {
            bluetoothLeScanner = bluetoothAdapter!!.bluetoothLeScanner
        }
    }

    private fun startScan() {
        bluetoothLeScanner?.startScan(object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val deviceName = device.name

                if (deviceName != null && deviceName == "MPU6050_LM35") {
                    bluetoothLeScanner?.stopScan(this)
                    connectToDevice(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BLE", "Error al escanear: $errorCode")
            }
        })
    }

    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d("BLE", "Conectado al dispositivo")
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d("BLE", "Desconectado del dispositivo")
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BLE", "Servicios descubiertos")
                    subscribeToCharacteristics(gatt)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                runOnUiThread {
                    val data = characteristic.getStringValue(0)
                    Log.d("BLE", "Datos recibidos: $data")
                    updateChart(data)
                }
            }
        })
    }

    private fun subscribeToCharacteristics(gatt: BluetoothGatt) {
        val serviceUUID = UUID.fromString("12345678-1234-1234-1234-123456789abc")
        val characteristicUUID = UUID.fromString("abcd1234-ab12-cd34-ef56-1234567890ab")

        val service = gatt.getService(serviceUUID)
        val characteristic = service?.getCharacteristic(characteristicUUID)

        characteristic?.let {
            gatt.setCharacteristicNotification(it, true)

            val descriptor = it.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun updateChart(data: String) {
        val lineData = chart.data ?: return
        val entries = lineData.getDataSetByIndex(0) as? LineDataSet ?: LineDataSet(null, "Sensor Data").also {
            lineData.addDataSet(it)
        }

        val values = data.split(",")
        val xValue = values[0].toFloatOrNull() ?: return
        val yValue = values[1].toFloatOrNull() ?: return

        entries.addEntry(Entry(entries.entryCount.toFloat(), xValue))
        lineData.notifyDataChanged()
        chart.notifyDataSetChanged()
        chart.invalidate()
    }
}
