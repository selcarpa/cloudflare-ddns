package utils

import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

expect fun readFile(path: Path): String
