package utils

import okio.*


fun readFile(path: Path): String {
    var firstLine = true
    FileSystem.SYSTEM.source(path).use { fileSource ->
        var result = ""
        fileSource.buffer().use { bufferedFileSource ->
            while (true) {
                val line = bufferedFileSource.readUtf8Line() ?: break
                result += if (firstLine) {
                    firstLine = false
                    line
                } else {
                    "\n" + line
                }
            }
        }
        return result
    }
}

fun writeFile(path: Path, content: String) {
    FileSystem.SYSTEM.write(path) {
        writeUtf8(content)
    }
}
