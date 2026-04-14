package com.terminalvelocitycabbage.tvscript.util;

import com.terminalvelocitycabbage.tvscript.ast.Expression;
import static com.terminalvelocitycabbage.tvscript.ast.Expression.*;

/**
 * Utility for printing a string representation of the AST.
 */
public class AstPrinter implements Expression.Visitor<String> {

    /**
     * Prints the given expression as a string.
     * @param expr The expression to print.
     * @return The string representation.
     */
    public String print(Expression expr) {
        if (expr == null) return "null";
        return expr.accept(this);
    }

    @Override
    public String visitBinaryExpression(BinaryExpression expr) {
        return parenthesize(expr.operator().lexeme(), expr.left(), expr.right());
    }

    @Override
    public String visitGroupingExpression(GroupingExpression expr) {
        return parenthesize("group", expr.expression());
    }

    @Override
    public String visitLiteralExpression(LiteralExpression expr) {
        if (expr.value() == null) return "none";
        return expr.value().toString();
    }

    @Override
    public String visitLogicalExpression(LogicalExpression expr) {
        return parenthesize(expr.operator().lexeme(), expr.left(), expr.right());
    }

    @Override
    public String visitUnaryExpression(UnaryExpression expr) {
        return parenthesize(expr.operator().lexeme(), expr.right());
    }

    @Override
    public String visitTernaryExpression(TernaryExpression expr) {
        return parenthesize("?", expr.condition(), expr.thenBranch(), expr.elseBranch());
    }

    @Override
    public String visitInterpolationExpression(InterpolationExpression expr) {
        StringBuilder builder = new StringBuilder();
        builder.append("(interpolation");
        for (Expression expression : expr.expressions()) {
            builder.append(" ");
            builder.append(expression.accept(this));
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitVariableExpression(VariableExpression expr) {
        return expr.name().lexeme();
    }

    @Override
    public String visitAssignExpression(AssignExpression expr) {
        return parenthesize("= " + expr.name().lexeme(), expr.value());
    }

    @Override
    public String visitRangeExpression(RangeExpression expr) {
        return parenthesize("..", expr.start(), expr.end());
    }

    @Override
    public String visitMatchExpression(MatchExpression expr) {
        StringBuilder builder = new StringBuilder();
        builder.append("(match ").append(expr.condition().accept(this));
        for (MatchExpression.Case matchCase : expr.cases()) {
            builder.append(" (case ");
            for (Expression pattern : matchCase.patterns()) {
                builder.append(pattern.accept(this)).append(" ");
            }
            builder.append(matchCase.branch().accept(this)).append(")");
        }
        if (expr.defaultBranch() != null) {
            builder.append(" (default ").append(expr.defaultBranch().accept(this)).append(")");
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitCallExpression(CallExpression expr) {
        StringBuilder builder = new StringBuilder();
        builder.append("(call ").append(expr.callee().accept(this));
        for (CallExpression.Argument arg : expr.arguments()) {
            builder.append(" ").append(arg.name().lexeme()).append(":").append(arg.value().accept(this));
        }
        builder.append(")");
        return builder.toString();
    }

    @Override
    public String visitFunctionExpression(FunctionExpression expr) {
        return "(function)";
    }

    private String parenthesize(String name, Expression... exprs) {
        StringBuilder builder = new StringBuilder();

        builder.append("(").append(name);
        for (Expression expr : exprs) {
            builder.append(" ");
            builder.append(expr.accept(this));
        }
        builder.append(")");

        return builder.toString();
    }
}
