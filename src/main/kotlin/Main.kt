package pingu

import FastFile

fun main() {
//    val fs = FastFile("C:\\Users\\Administrator\\Desktop\\BNB\\SMAgent\\FXGS\\FxServer")
//    val fs = FastFile("C:\\Users\\Administrator\\Desktop\\BNB\\SMAgent\\backup2\\FXGS\\FxServer")
    val fs = FastFile("C:\\Users\\Administrator\\Desktop\\BNB\\BNB_KR_37\\Fx")

    val port = 1337
    println("Server running on http://localhost:$port")
    startServer(fs, port)
}