package ru.nsu.lisp2c

const val lispObjectType = "lisp__object *"
const val newObject = "gc__new_object"
const val varPrefix = "lisp_var"
fun nameCName(name: String) = "${varPrefix}_$name"
fun cNameClojureType(cname: String) = cname.replaceFirst(varPrefix, "lisp_clojure") + "_t"
fun cNameBody(cname: String) = cname.replaceFirst(varPrefix, "lisp_body")
fun cNameStartLabel(cname: String) = cname.replaceFirst(varPrefix, "lisp_start")
fun functionType(n: Int) = "lisp_fun_${n}_t"

fun reformatCode(code: String): String{
    val process = ProcessBuilder("clang-format")
        .redirectInput(ProcessBuilder.Redirect.PIPE)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    process.outputStream.write(code.encodeToByteArray())
    process.outputStream.close()
    return process.inputStream.bufferedReader().readText()
}