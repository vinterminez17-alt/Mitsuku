package moe.shizuku.manager.adb

import android.util.Log
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.utils.EnvironmentUtils
import java.io.IOException
import java.net.ConnectException

/**
 * "TCP mode": once Mitsuku has been started over Wi-Fi (wireless debugging) after a reboot,
 * adbd is asked to also listen on [ShizukuSettings.TCP_PORT] (the same thing as running
 * "adb tcpip 5555"). That port is reachable through 127.0.0.1, so Mitsuku can be restarted
 * later without any Wi-Fi connection. Android forgets the port on every reboot.
 */
object TcpMode {

    private const val TAG = "TcpMode"

    const val HOST = "127.0.0.1"

    /** True while adbd is listening on a TCP port (it is reset by every reboot). */
    fun isPortOpen(): Boolean = EnvironmentUtils.getAdbTcpPort() > 0

    fun createKey(): AdbKey =
        AdbKey(PreferenceAdbKeyStore(ShizukuSettings.getPreferences()), "shizuku")

    /**
     * Connects to adbd through the wireless debugging port and asks it to also listen on
     * [ShizukuSettings.TCP_PORT]. adbd restarts while doing this, so the dropped connection
     * is expected and is not treated as an error.
     */
    fun enable(host: String, wirelessPort: Int, key: AdbKey) {
        val client = AdbClient(host, wirelessPort, key)
        try {
            client.connect()
            try {
                client.tcpip(ShizukuSettings.TCP_PORT)
            } catch (e: IOException) {
                Log.d(TAG, "adbd dropped the connection while restarting (expected)", e)
            }
        } finally {
            client.close()
        }
    }

    /**
     * Opens a connection to [port] on this device. Right after [enable], adbd is still
     * restarting, so a refused connection is retried for a few seconds. Any other failure
     * (for example the user denying the "Allow USB debugging?" prompt) is not retried.
     */
    fun connectWithRetry(host: String, port: Int, key: AdbKey, attempts: Int = 6): AdbClient {
        var lastError: ConnectException? = null
        for (attempt in 0 until attempts) {
            if (attempt > 0) Thread.sleep(attempt * 1000L)
            val client = AdbClient(host, port, key)
            try {
                client.connect()
                return client
            } catch (e: ConnectException) {
                client.close()
                lastError = e
                Log.w(TAG, "Connect attempt ${attempt + 1} to $host:$port was refused")
            } catch (e: Throwable) {
                client.close()
                throw e
            }
        }
        throw lastError ?: ConnectException("Can't connect to $host:$port")
    }

    /**
     * Closes the TCP port again (same as "adb usb"). Does nothing if the port is not open.
     */
    fun disable(key: AdbKey) {
        val port = EnvironmentUtils.getAdbTcpPort()
        if (port <= 0) return

        val client = AdbClient(HOST, port, key)
        try {
            client.connect()
            try {
                client.usb()
            } catch (e: IOException) {
                Log.d(TAG, "adbd dropped the connection while restarting (expected)", e)
            }
        } finally {
            client.close()
        }
    }
}
