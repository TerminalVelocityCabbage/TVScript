package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;

import java.util.List;

public class Interpreter implements Expression.Visitor<Object>, Statement.Visitor<Void> {

    private Environment environment = new Environment();

    public void interpret(List<Statement> statements) {
        try {
            for (Statement statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            TVScript.runtimeError(error);
            throw error;
        }
    }

    private void execute(Statement stmt) {
        stmt.accept(this);
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
        return environment.get(expr.name);
    }

    @Override
    public Object visitAssignExpr(Expression.Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value;
    }

    @Override
    public Void visitBlockStmt(Statement.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    private void executeBlock(List<Statement> statements, Environment environment) {
        Environment previous = this.environment;
        try {
            this.environment = environment;
            for (Statement statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    @Override
    public Void visitExpressionStmt(Statement.ExpressionStmt stmt) {
        evaluate(stmt.expression);
        return null;
    }

    @Override
    public Void visitIfStmt(Statement.If stmt) {
        if (isTruthy(stmt.keyword, evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Statement.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitVarStmt(Statement.Var stmt) {
        Object value = null;
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }

        TokenType type;
        if (stmt.type.getType() == TokenType.VAR || stmt.type.getType() == TokenType.CONST) {
            type = inferType(value);
            if (type == null) {
                throw new RuntimeError(stmt.name, "Cannot infer type from 'none'.");
            }
        } else {
            type = stmt.type.getType();
        }

        environment.define(stmt.name, value, type, stmt.isConst);
        return null;
    }

    @Override
    public Void visitPassStmt(Statement.Pass stmt) {
        return null;
    }

    private TokenType inferType(Object value) {
        if (value instanceof Integer) return TokenType.TYPE_INTEGER;
        if (value instanceof Double) return TokenType.TYPE_DECIMAL;
        if (value instanceof String) return TokenType.TYPE_STRING;
        if (value instanceof Boolean) return TokenType.TYPE_BOOLEAN;
        return null;
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
        if (operator == null) return false; // Default for if condition if not boolean?
        throw new RuntimeError(operator, "Expected boolean value.");
    }

    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null) return false;

        return a.equals(b);
    }

    private String stringify(Object object) {
        if (object == null) return "none";

        return object.toString();
    }
}
