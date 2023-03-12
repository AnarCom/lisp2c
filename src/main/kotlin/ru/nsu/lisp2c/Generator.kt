package ru.nsu.lisp2c

import java.nio.file.Files
import java.nio.file.Paths
import java.util.Stack
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.name
import kotlin.io.path.walk

data class RecurContext(val label: String, val cNames: List<String>)

data class GeneratorContext(
    val prototypes: MutableList<String> = mutableListOf(),
    val functionBodies: MutableList<String> = mutableListOf(),
    val mainLines: MutableList<String> = mutableListOf(),
    private var nextID: Int = 0,
    val scope: MapStack<String, Symbol> = MapStack(),
) {
    private val recurContextStack = Stack<RecurContext>()
    fun newVarName() = "lisp_var_${nextID++}"

    fun pushRecurContext(ctx: RecurContext) = recurContextStack.push(ctx)
    fun popRecurContext() = recurContextStack.pop()
    val recurContext: RecurContext
        get() = recurContextStack.peek()

    var lastUsableSnapshot: String = ""
    var nextMacroExpansionId = 0;
    val macroExpansionDir = Files.createDirectories(Paths.get("tmp_macro_expansion"))
}


class Generator {
    val ctx = GeneratorContext()

    init {
        ctx.scope.pushScope()
        builtinSymbols.forEach { ctx.scope[it.key] = it.value }

    }

    @OptIn(ExperimentalPathApi::class)
    // TODO: compile sequentially
    fun generate(prog: List<Expression>): String {
        ctx.macroExpansionDir.walk().forEach {
            println(it.name)
        }

        val defuns = prog.filterIsInstance<TopLevelOnlyExpressions>()
        val mainExpressions = prog.filterNot { it is TopLevelOnlyExpressions }
        defuns.forEach {
            ctx.lastUsableSnapshot = generateSnapshot()
            it.generate(ctx)
        }
        ctx.lastUsableSnapshot = generateSnapshot()

        val mainSideEffects =
            mainExpressions.fold(mutableListOf<String>()) { acc, expr -> acc.apply { add(expr.generate(ctx).body) } }

        return """
            ${generateSnapshot()}
            ${mainSideEffects.joinToString("\n")}
            return 0;
            }
        """.trimIndent()
    }

    private fun generateSnapshot(): String {

        val functionTypedefs = (0..10).map { n ->
            val args = (arrayOf("void*") + (0 until n).map { lispObjectType }).joinToString(", ")
            "typedef $lispObjectType(*${functionType(n)})($args);"
        }

        return """
            #include "main.h"
            #include "assert.h"
            #include "runtime.h"
            #include "macro.h"
            
            ${functionTypedefs.joinToString("\n")}
            ${ctx.prototypes.joinToString("\n")}
            ${ctx.functionBodies.joinToString("\n")}
            int main(){
                runtime__init();
                ${ctx.mainLines.joinToString("\n")}
                // PRELUDE END
                
        """.trimIndent()
    }
}