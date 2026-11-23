/*
 * YumaPlayer (2026) | Modified work by MuwMix
 * Mochy-Music (2026) | Original work by © Mochy
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rukamori.archivetune.playback.audio.pass

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import moe.rukamori.archivetune.playback.audio.UsbDacReader
import timber.log.Timber

internal class UsbDacAccess(context: Context, private val onChanged: () -> Unit) {

    private val app = context.applicationContext
    val manager: UsbManager? = app.getSystemService(UsbManager::class.java)
    private val permissionAction = "${app.packageName}.AUDIO_PASS_USB"
    private val descriptors = HashMap<Int, UsbAudioDescriptors.Dac>()
    private var asked: Int? = null
    private var started = false

    var onDetached: ((Int) -> Unit)? = null

    private val _permitted = MutableStateFlow<Boolean?>(null)
    val permitted: StateFlow<Boolean?> = _permitted.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_DETACHED -> device?.let {
                    synchronized(this@UsbDacAccess) {
                        descriptors.remove(it.deviceId)
                        if (asked == it.deviceId) asked = null
                    }
                    onDetached?.invoke(it.deviceId)
                }
                permissionAction -> synchronized(this@UsbDacAccess) { asked = null }
            }
            refresh()
            onChanged()
        }
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter(permissionAction).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        runCatching { ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { app.unregisterReceiver(receiver) }
    }

    fun device(): UsbDevice? = manager?.let(UsbDacReader::audioDevice)

    fun hasPermission(device: UsbDevice): Boolean = runCatching { manager?.hasPermission(device) == true }.getOrDefault(false)

    fun refresh() {
        _permitted.value = device()?.let(::hasPermission)
    }

    @Synchronized
    fun requestPermission() {
        val usb = manager ?: return
        val device = device() ?: return
        if (hasPermission(device) || asked == device.deviceId) return
        asked = device.deviceId
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent = PendingIntent.getBroadcast(app, device.deviceId, Intent(permissionAction).setPackage(app.packageName), flags)
        runCatching { usb.requestPermission(device, intent) }.onFailure {
            asked = null
            Timber.tag("UsbDacAccess").w(it, "usb permission request failed")
        }
    }

    @Synchronized
    fun dac(device: UsbDevice): UsbAudioDescriptors.Dac? {
        descriptors[device.deviceId]?.let { return it }
        val usb = manager ?: return null
        if (!hasPermission(device)) return null
        val connection = runCatching { usb.openDevice(device) }.getOrNull() ?: return null
        return try {
            connection.rawDescriptors?.let(UsbAudioDescriptors::parse)?.also { descriptors[device.deviceId] = it }
        } catch (error: Exception) {
            Timber.tag("UsbDacAccess").w(error, "couldn't read the dac's descriptors")
            null
        } finally {
            connection.close()
        }
    }
}
