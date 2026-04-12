package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.ast.Expression;
import java.util.ArrayList;
import java.util.List;

public class Interpreter implements Expression.Visitor<Object> {

    public void interpret(Expression expression) {
        try {
            Object value = evaluate(expression);
            System.out.println(stringify(value));
        } catch (RuntimeError error) {
            TVScript.runtimeError(error);
        }
    }

    public Object evaluate(Expression expression) {
        if (expression == null) return null;
        return expression.accept(this);
    }

    @Override
    public Object visitBinaryExpr(Expression.Binary expr) {
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.getType()) {
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left > (int) right;
                }
                return ((Number) left).doubleValue() > ((Number) right).doubleValue();
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left >= (int) right;
                }
                return ((Number) left).doubleValue() >= ((Number) right).doubleValue();
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left < (int) right;
                }
                return ((Number) left).doubleValue() < ((Number) right).doubleValue();
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left <= (int) right;
                }
                return ((Number) left).doubleValue() <= ((Number) right).doubleValue();
            case BANG_EQUAL: return !isEqual(left, right);
            case EQUAL_EQUAL: return isEqual(left, right);
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left - (int) right;
                }
                return ((Number) left).doubleValue() - ((Number) right).doubleValue();
            case PLUS:
                checkNumberOperands(expr.operator, left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left + (int) right;
                }
                return ((Number) left).doubleValue() + ((Number) right).doubleValue();
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    if ((int) right == 0) throw new RuntimeError(expr.operator, "Division by zero.");
                    return (int) left / (int) right;
                }
                return ((Number) left).doubleValue() / ((Number) right).doubleValue();
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left * (int) right;
                }
                return ((Number) left).doubleValue() * ((Number) right).doubleValue();
            case PERCENT:
                checkNumberOperands(expr.operator, left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    if ((int) right == 0) throw new RuntimeError(expr.operator, "Modulo by zero.");
                    return (int) left % (int) right;
                }
                return ((Number) left).doubleValue() % ((Number) right).doubleValue();
        }

        // Unreachable
        return null;
    }

    @Override
    public Object visitGroupingExpr(Expression.Grouping expr) {
        return evaluate(expr.expression);
    }

    @Override
    public Object visitLiteralExpr(Expression.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitLogicalExpr(Expression.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.getType() == TokenType.OR) {
            if (isTruthy(expr.operator, left)) return left;
        } else {
            if (!isTruthy(expr.operator, left)) return left;
        }

        Object right = evaluate(expr.right);
        isTruthy(expr.operator, right); // Ensure right side is also a boolean
        return right;
    }

    @Override
    public Object visitUnaryExpr(Expression.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.getType()) {
            case BANG:
                return !isTruthy(expr.operator, right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                if (right instanceof Integer) return -(int) right;
                return -(double) right;
        }

        // Unreachable
        return null;
    }

    @Override
    public Object visitTernaryExpr(Expression.Ternary expr) {
        Object condition = evaluate(expr.condition);

        if (isTruthy(expr.operator, condition)) {
            return evaluate(expr.trueBranch);
        } else {
            return evaluate(expr.falseBranch);
        }
    }

    @Override
    public Object visitInterpolationExpr(Expression.Interpolation expr) {
        StringBuilder builder = new StringBuilder();
        for (Expression expression : expr.expressions) {
            builder.append(stringify(evaluate(expression)));
        }
        return builder.toString();
    }

    @Override
    public Object visitVariableExpr(Expression.Variable expr) {
        throw new RuntimeError(expr.name, "Undefined variable '" + expr.name.getLexeme() + "'.");
    }

    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Integer || operand instanceof Double) return;
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if ((left instanceof Integer || left instanceof Double) &&
            (right instanceof Integer || right instanceof Double)) return;
        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    private boolean isTruthy(Token operator, Object object) {
        if (object instanceof Boolean) return (boolean) object;
        throw new RuntimeError(operator, "Expected boolean value.");
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    private String stringify(Object object) {
        if (object == null) return "none";

        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }
}
