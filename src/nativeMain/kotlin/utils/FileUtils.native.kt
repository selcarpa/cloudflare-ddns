package utils

import okio.FileSystem
import okio.buffer
import okio.use

actual fun readFile(path: okio.Path): String {
    var firstLine = true
    FileSystem.SYSTEM.source(path).use { fileSource ->
        var result = ""
        fileSource.buffer().use { bufferedFileSource ->
            while (true) {
                val line = bufferedFileSource.readUtf8Line() ?: break
                result += if (firstLine) {
                    firstLine=false
                    line
                } else {
                    "\n" + line
                }
            }
        }
        return result
    }
}
