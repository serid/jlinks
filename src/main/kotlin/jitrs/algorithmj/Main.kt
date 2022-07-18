package jitrs.algorithmj

fun main() {
    val compiler = Compiler.new()

    while (true) {
        print(">>> ")
        val line = readLine() ?: break
        if (line == "") continue

        val type = compiler.infer(compiler.getIr(line))
        println(type)
    }
}