package ru.nsu.lisp2c

import LispLexer
import LispParser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.FileInputStream
import java.io.FileOutputStream

//data class Expression()
// Function call: Simple function call
// Defun        : Allowed only on top level
// Fn           : Fuckery with closures
// List         : Create list in C
// If           : Ternary operator
// Let          : I don't know
// Recurse      : Simple

data class Symbol(val name: String, val cName: String) {
    fun pair(): Pair<String, Symbol> = name to this
}

infix fun String.cname(cname: String) = this to Symbol(this, cname)
val builtinSymbols = mapOf(
    "*" cname "lisp__mul",
    "+" cname "lisp__add",
    "-" cname "lisp__sub",
    "/" cname "lisp__div",
    "=" cname "lisp__eq"

)


fun main(args: Array<String>) {
    val inputStream = FileInputStream("src/main/resources/factorial.lisp")
    val lexer = LispLexer(CharStreams.fromStream(inputStream))
    val parser = LispParser(CommonTokenStream(lexer))
    val prog = parser.program()
    val parsed = ProgramVisitor().visitProgram(prog)
    val generated = Generator().generate(parsed)
    val process = ProcessBuilder("clang-format")
        .redirectInput(ProcessBuilder.Redirect.PIPE)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()

    process.outputStream.write(generated.encodeToByteArray())
    process.outputStream.close()
    val formatted = process.inputStream.bufferedReader().readText()
    println(formatted)
    FileOutputStream("../c-lisp2c-runtime/main.c").bufferedWriter().apply {
        write(formatted)
        close()
    }
}