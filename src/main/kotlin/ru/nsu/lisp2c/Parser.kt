package ru.nsu.lisp2c

import LispBaseVisitor
import LispParser

class ProgramVisitor : LispBaseVisitor<List<Expression>>() {
    override fun visitProgram(ctx: LispParser.ProgramContext): List<Expression> {
        return ctx.expressions.map { ExpressionVisitor().visitExpression(it) }
    }
}

class ExpressionVisitor : LispBaseVisitor<Expression>() {
    override fun visitExpression(ctx: LispParser.ExpressionContext?): Expression {
        return visitChildren(ctx)
    }

    override fun visitDefun_expression(ctx: LispParser.Defun_expressionContext): Expression {
        return DefunExpression(
            name = ctx.name.text,
            arguments = ctx.args.map { visitIdentifier_expression(it) as IdentifierExpression },
            body = visitExpression(ctx.body)
        )
    }

    override fun visitIf_expression(ctx: LispParser.If_expressionContext): Expression {
        return IfExpression(
            condition = visitExpression(ctx.condition),
            ifTrue = visitExpression(ctx.ifTrue),
            ifFalse = visitExpression(ctx.ifFalse),
        )
    }

    override fun visitCall_expression(ctx: LispParser.Call_expressionContext): Expression {
        return CallExpression(
            target = visitExpression(ctx.fn),
            args = ctx.args.map { visitExpression(it) }
        )
    }

    override fun visitIdentifier_expression(ctx: LispParser.Identifier_expressionContext): IdentifierExpression {
        return IdentifierExpression(ctx.text)
    }

    override fun visitInteger_expression(ctx: LispParser.Integer_expressionContext): Expression {
        return IntExpression(ctx.text.toInt())
    }

    override fun visitDefunc_expression(ctx: LispParser.Defunc_expressionContext): Expression {
        return DefunCExpression(
            ctx.name.text,
            ctx.args.map { visitIdentifier_expression(it) },
            ctx.body.text.split('\n').let { lines ->
                lines.slice(1 until lines.size - 1)
            }.joinToString("\n"),
        )
    }

    override fun visitFn_expression(ctx: LispParser.Fn_expressionContext): Expression {
        return FnExpression(
            arguments = ctx.args.map { visitIdentifier_expression(it) },
            body = visitExpression(ctx.body)
        )
    }

    override fun visitRecur_expression(ctx: LispParser.Recur_expressionContext): Expression {
        return RecurExpression(
            args = ctx.args.map { visitExpression(it) },
        )
    }

    override fun visitDo_expression(ctx: LispParser.Do_expressionContext): Expression {
        return DoExpression(
            args = ctx.args.map { visitExpression(it) },
        )
    }

    override fun visitMacro_expand_expression(ctx: LispParser.Macro_expand_expressionContext): Expression {
        return MacroExpandExpression(
            ctx.name.text,
            SimplifiedListExpression(ctx.args.map { SimplifiedExpressionVisitor().visit(it) }, 0)
        )
    }

    override fun visitBoolean_expression(ctx: LispParser.Boolean_expressionContext): Expression {
        return BooleanExpression(
            when(ctx.text){
                "true" -> true
                "false" -> false
                else -> throw Exception("Invalid boolean expression")
            }
        )
    }

    override fun visitString_expression(ctx: LispParser.String_expressionContext): Expression {
        return StringExpression(ctx.text.trim('"').replace("\\n", "\n"))
    }
}

class SimplifiedExpressionVisitor: LispBaseVisitor<SimplifiedExpression>(){
    override fun visitSimplified_round_expression(ctx: LispParser.Simplified_round_expressionContext): SimplifiedExpression {
        return SimplifiedListExpression(ctx.args.map { visit(it) }, 0)
    }

    override fun visitSimplified_square_expression(ctx: LispParser.Simplified_square_expressionContext): SimplifiedExpression {
        return SimplifiedListExpression(ctx.args.map { visit(it) }, 1)
    }

    override fun visitSimplified_expression_arg(ctx: LispParser.Simplified_expression_argContext): SimplifiedExpression {
        return SimplifiedStringExpression(ctx.text)
    }
}