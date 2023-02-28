package ru.nsu.lisp2c

import java.util.Stack

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
}


class Generator {
    val ctx = GeneratorContext()

    init {
        ctx.scope.pushScope()
        builtinSymbols.forEach { ctx.scope[it.key] = it.value }

    }

    fun generate(prog: List<Expression>): String {
        val defuns = prog.filterIsInstance<TopLevelOnlyExpressions>()
        val mainExpressions = prog.filterNot { it is TopLevelOnlyExpressions }
        defuns.forEach { it.generate(ctx) }

        val functionTypedefs = (0..10).map { n ->
            val args = (arrayOf("void*") + (0 until n).map { lispObjectType }).joinToString(", ")
            "typedef $lispObjectType(*${functionType(n)})($args);"
        }

        val mainSideEffects =
            mainExpressions.fold(mutableListOf<String>()) { acc, expr -> acc.apply { add(expr.generate(ctx).body) } }

        return """
            #include "main.h"
            #include "assert.h"
            #include "runtime.h"
            
            ${functionTypedefs.joinToString("\n")}
            ${ctx.prototypes.joinToString("\n")}
            ${ctx.functionBodies.joinToString("\n")}
            int main(){
                runtime__init();
                ${ctx.mainLines.joinToString("\n")}
                // non-defun expressions
                ${mainSideEffects.joinToString("\n")}
                return 0;
            }
        """.trimIndent()
    }
}