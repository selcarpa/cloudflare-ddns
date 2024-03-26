import platform.posix.execlp


fun main() {
    execlp("ping","127.0.0.1")


    println("press any key to continue...")
    readln()
}

