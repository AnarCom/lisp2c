package ru.nsu.lisp2c

data class GeneratedExpression(val body: String, val varName: String);

sealed interface Expression {
    fun generate(ctx: GeneratorContext): GeneratedExpression
}

class CallExpression(val target: Expression, val args: List<Expression>) : Expression {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val resultVarName = ctx.newVarName();
        val (_, targetName) = target.generate(ctx);
        val generatedArgs = args.map { it.generate(ctx) }
        val argsString = (arrayOf("$targetName->value.callable.clojure") + generatedArgs.map { it.varName }).joinToString(", ")
        val body = """
            ${generatedArgs.map { it.body }.joinToString("")}
            assert($targetName->type == CALLABLE);
            assert($targetName->value.callable.n_args == ${args.size});
            $lispObjectType $resultVarName = ((${functionType(args.size)})$targetName->value.callable.f)($argsString);
        """.trimIndent()
        return GeneratedExpression(body, resultVarName)
    }
}

class DefunExpression(val name: String, val arguments: List<IdentifierExpression>, val body: Expression) : Expression {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val cName = nameCName(name)
        val clojureType = cNameClojureType(cName)
        val bodyName = cNameBody(cName)
        val startLabel = cNameStartLabel(cName)
        ctx.scope[name] = Symbol(name, cName)

        val argumentString = (arrayOf("void *clj") + arguments.map { "$lispObjectType ${nameCName(it.name)}" }).joinToString(", ")

        val prototype = """
            $lispObjectType ${bodyName}($argumentString);
            $lispObjectType $cName = NULL;
        """.trimIndent()

        val mainInit = """
            $cName = lisp__callable_constructor($bodyName, ${arguments.size}, NULL);
        """.trimIndent()

        ctx.mainLines += mainInit;
        ctx.prototypes += prototype

        ctx.scope.pushScope()
        arguments.forEach { ctx.scope[it.name] = Symbol(it.name, variableCName(it.name)) }
        val (generatedBody, returnVarName) = body.generate(ctx)
        ctx.scope.popScope()


        val body = """
            $lispObjectType ${bodyName}($argumentString){
                $startLabel:;
                $generatedBody;
                return $returnVarName;
            }
        """.trimIndent()

        ctx.functionBodies += body;

        return GeneratedExpression("ERROR", "ERROR")
    }
}

class IfExpression(val condition: Expression, val ifTrue: Expression, val ifFalse: Expression) : Expression {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val resultVarName = ctx.newVarName();
        val (conditionBody, conditionVar) = condition.generate(ctx);
        val (trueBody, trueVar) = ifTrue.generate(ctx);
        val (falseBody, falseVar) = ifFalse.generate(ctx);

        val body = """
            $conditionBody
            $lispObjectType $resultVarName;
            if(lisp__is_true($conditionVar)){
                $trueBody
                $resultVarName = $trueVar;
            }else{
                $falseBody
                $resultVarName = $falseVar;
            }
        """.trimIndent()

        return GeneratedExpression(body, resultVarName)
    }
}

class IntExpression(val v: Int) : Expression {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        val varName = ctx.newVarName();
        val body = "$lispObjectType $varName = lisp__int_constructor($v);\n"
        return GeneratedExpression(body, varName);
    }
}

class IdentifierExpression(val name: String) : Expression {
    override fun generate(ctx: GeneratorContext): GeneratedExpression {
        return GeneratedExpression("", ctx.scope[name]!!.cName)
    }
}
