import java.io.File
import java.io.FileFilter
import java.util.regex.Pattern

fun getCoreNum(): Int {
    class CpuFilter : FileFilter {
        override fun accept(pathname: File): Boolean {
            // Check if filename is "cpu", followed by a single digit number
            return Pattern.matches("cpu[0-9]", pathname.name)
        }
    }
    return try {
        // Get directory containing CPU info
        val dir = File("/sys/devices/system/cpu/")
        // Filter to only list the devices we care about
        val files = dir.listFiles(CpuFilter())
        // Return the number of cores (virtual CPU devices)
        files.size
    } catch (e: Exception) {
        // Default to return 1 core
        1
    }
}