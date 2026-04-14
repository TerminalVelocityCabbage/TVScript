package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.TVScript;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import static com.terminalvelocitycabbage.tvscript.ast.Expression.*;
import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

import java.util.List;

/**
 * Executes the AST by visiting each node.
 */
public class Interpreter implements Expression.Visitor<Object>, Statement.Visitor<Void> {

    private static class BreakException extends RuntimeException {
        BreakException() { super(null, null, false, false); }
    }

    private static class ContinueException extends RuntimeException {
        ContinueException() { super(null, null, false, false); }
    }

    private static class RangeValue {
        final int start;
        final int end;
        RangeValue(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private Environment environment = new Environment();

    public Interpreter() {
        environment.defineNative("clock", new TVScriptNativeFunction(0, args -> (double) System.currentTimeMillis() / 1000.0));
        environment.defineNative("abs", com.terminalvelocitycabbage.tvscript.stdlib.NativeFunctions.ABS);
    }

    public void reset() {
        environment = new Environment();
        environment.defineNative("clock", new TVScriptNativeFunction(0, args -> (double) System.currentTimeMillis() / 1000.0));
        environment.defineNative("abs", com.terminalvelocitycabbage.tvscript.stdlib.NativeFunctions.ABS);
    }

    /**
     * Interprets a list of statements.
     * @param statements The statements to interpret.
     */
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

    /**
     * Evaluates an expression.
     * @param expression The expression to evaluate.
     * @return The result of the evaluation.
     */
    public Object evaluate(Expression expression) {
        if (expression == null) return null;
        return expression.accept(this);
    }

    @Override
    public Object visitBinaryExpression(BinaryExpression expr) {
        Object left = evaluate(expr.left());
        Object right = evaluate(expr.right());

        switch (expr.operator().type()) {
            case GREATER:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left > (int) right;
                }
                return ((Number) left).doubleValue() > ((Number) right).doubleValue();
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left >= (int) right;
                }
                return ((Number) left).doubleValue() >= ((Number) right).doubleValue();
            case LESS:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left < (int) right;
                }
                return ((Number) left).doubleValue() < ((Number) right).doubleValue();
            case LESS_EQUAL:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left <= (int) right;
                }
                return ((Number) left).doubleValue() <= ((Number) right).doubleValue();
            case BANG_EQUAL: return !isEqual(left, right);
            case EQUAL_EQUAL: return isEqual(left, right);
            case MINUS:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left - (int) right;
                }
                return ((Number) left).doubleValue() - ((Number) right).doubleValue();
            case PLUS:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left + (int) right;
                }
                return ((Number) left).doubleValue() + ((Number) right).doubleValue();
            case SLASH:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    if ((int) right == 0) throw new RuntimeError(expr.operator(), "Division by zero.");
                    return (int) left / (int) right;
                }
                return ((Number) left).doubleValue() / ((Number) right).doubleValue();
            case STAR:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    return (int) left * (int) right;
                }
                return ((Number) left).doubleValue() * ((Number) right).doubleValue();
            case PERCENT:
                checkNumberOperands(expr.operator(), left, right);
                if (left instanceof Integer && right instanceof Integer) {
                    if ((int) right == 0) throw new RuntimeError(expr.operator(), "Modulo by zero.");
                    return (int) left % (int) right;
                }
                return ((Number) left).doubleValue() % ((Number) right).doubleValue();
            default:
                return null;
        }
    }

    @Override
    public Object visitGroupingExpression(GroupingExpression expr) {
        return evaluate(expr.expression());
    }

    @Override
    public Object visitLiteralExpression(LiteralExpression expr) {
        return expr.value();
    }

    @Override
    public Object visitLogicalExpression(LogicalExpression expr) {
        Object left = evaluate(expr.left());

        if (expr.operator().type() == TokenType.OR) {
            if (isTruthy(expr.operator(), left)) return left;
        } else {
            if (!isTruthy(expr.operator(), left)) return left;
        }

        Object right = evaluate(expr.right());
        isTruthy(expr.operator(), right); // Ensure right side is also a boolean
        return right;
    }

    @Override
    public Object visitUnaryExpression(UnaryExpression expr) {
        Object right = evaluate(expr.right());

        switch (expr.operator().type()) {
            case BANG:
                return !isTruthy(expr.operator(), right);
            case MINUS:
                checkNumberOperand(expr.operator(), right);
                if (right instanceof Integer) return -(int) right;
                return -(double) right;
            default:
                return null;
        }
    }

    @Override
    public Object visitTernaryExpression(TernaryExpression expr) {
        Object condition = evaluate(expr.condition());

        if (isTruthy(expr.operator(), condition)) {
            return evaluate(expr.thenBranch());
        } else {
            return evaluate(expr.elseBranch());
        }
    }

    @Override
    public Object visitInterpolationExpression(InterpolationExpression expr) {
        StringBuilder builder = new StringBuilder();
        for (Expression expression : expr.expressions()) {
            builder.append(stringify(evaluate(expression)));
        }
        return builder.toString();
    }

    @Override
    public Object visitVariableExpression(VariableExpression expr) {
        return environment.get(expr.name());
    }

    @Override
    public Object visitAssignExpression(AssignExpression expr) {
        Object value = evaluate(expr.value());
        environment.assign(expr.name(), value);
        return value;
    }

    @Override
    public Object visitRangeExpression(RangeExpression expr) {
        Object start = evaluate(expr.start());
        Object end = evaluate(expr.end());

        if (!(start instanceof Integer) || !(end instanceof Integer)) {
            throw new RuntimeError(expr.operator(), "Range bounds must be integers.");
        }

        return new RangeValue((int) start, (int) end);
    }

    @Override
    public Object visitMatchExpression(MatchExpression expr) {
        Object condition = evaluate(expr.condition());

        for (MatchExpression.Case matchCase : expr.cases()) {
            for (Expression pattern : matchCase.patterns()) {
                Object patternValue = evaluate(pattern);
                if (matchPattern(condition, patternValue)) {
                    return evaluate(matchCase.branch());
                }
            }
        }

        if (expr.defaultBranch() != null) {
            return evaluate(expr.defaultBranch());
        }

        throw new RuntimeError(expr.keyword(), "Match expression not exhaustive.");
    }

    @Override
    public Object visitCallExpression(CallExpression expr) {
        Object callee = evaluate(expr.callee());

        if (!(callee instanceof TVScriptCallable)) {
            throw new RuntimeError(expr.paren(), "Can only call functions and classes.");
        }

        TVScriptCallable function = (TVScriptCallable) callee;
        java.util.Map<String, Object> arguments = new java.util.HashMap<>();
        for (CallExpression.Argument arg : expr.arguments()) {
            arguments.put(arg.name().lexeme(), evaluate(arg.value()));
        }

        return function.call(this, arguments, expr.paren());
    }

    @Override
    public Object visitFunctionExpression(FunctionExpression expr) {
        return new TVScriptFunction(expr, environment);
    }

    private boolean matchPattern(Object condition, Object pattern) {
        if (pattern instanceof RangeValue) {
            RangeValue range = (RangeValue) pattern;
            if (condition instanceof Integer) {
                int val = (int) condition;
                return val >= range.start && val <= range.end;
            }
            if (condition instanceof Double) {
                double val = (double) condition;
                return val >= range.start && val <= range.end;
            }
        }
        return isEqual(condition, pattern);
    }

    @Override
    public Void visitBlockStatement(BlockStatement stmt) {
        executeBlock(stmt.statements(), new Environment(environment));
        return null;
    }

    public void executeBlock(List<Statement> statements, Environment environment) {
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
    public Void visitExpressionStatement(ExpressionStatement stmt) {
        evaluate(stmt.expression());
        return null;
    }

    @Override
    public Void visitIfStatement(IfStatement stmt) {
        if (isTruthy(stmt.keyword(), evaluate(stmt.condition()))) {
            execute(stmt.thenBranch());
        } else if (stmt.elseBranch() != null) {
            execute(stmt.elseBranch());
        }
        return null;
    }

    @Override
    public Void visitWhileStatement(WhileStatement stmt) {
        try {
            while (isTruthy(stmt.keyword(), evaluate(stmt.condition()))) {
                try {
                    execute(stmt.body());
                } catch (ContinueException e) {
                    // Do nothing, just continue
                }
            }
        } catch (BreakException e) {
            // Do nothing, just break
        }
        return null;
    }

    @Override
    public Void visitForStatement(ForStatement stmt) {
        Object rangeObj = evaluate(stmt.range());
        if (!(rangeObj instanceof RangeValue)) {
            throw new RuntimeError(stmt.keyword(), "Expected range in for loop.");
        }
        RangeValue range = (RangeValue) rangeObj;

        Environment previous = this.environment;
        try {
            for (int i = range.start; i <= range.end; i++) {
                if (stmt.name() != null) {
                    this.environment = new Environment(previous);
                    this.environment.define(stmt.name(), i, stmt.type().type(), false);
                } else {
                    this.environment = new Environment(previous);
                }

                try {
                    execute(stmt.body());
                } catch (ContinueException e) {
                    // continue
                } finally {
                    this.environment = previous;
                }
            }
        } catch (BreakException e) {
            // break
        } finally {
            this.environment = previous;
        }
        return null;
    }

    @Override
    public Void visitBreakStatement(BreakStatement stmt) {
        throw new BreakException();
    }

    @Override
    public Void visitContinueStatement(ContinueStatement stmt) {
        throw new ContinueException();
    }

    @Override
    public Void visitFunctionStatement(FunctionStatement stmt) {
        TVScriptFunction function = new TVScriptFunction(stmt, environment);
        environment.define(stmt.name(), function, TokenType.FUNCTION, true);
        return null;
    }

    @Override
    public Void visitReturnStatement(ReturnStatement stmt) {
        Object value = null;
        if (stmt.value() != null) value = evaluate(stmt.value());

        throw new TVScriptFunction.Return(value);
    }

    @Override
    public Void visitPrintStatement(PrintStatement stmt) {
        Object value = evaluate(stmt.expression());
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitVarStatement(VarStatement stmt) {
        Object value = null;
        if (stmt.initializer() != null) {
            value = evaluate(stmt.initializer());
        }

        TokenType type;
        if (stmt.type().type() == TokenType.VAR || stmt.type().type() == TokenType.CONST) {
            type = inferType(value);
            if (type == null) {
                throw new RuntimeError(stmt.name(), "Cannot infer type from 'none'.");
            }
        } else {
            type = stmt.type().type();
        }

        environment.define(stmt.name(), value, type, stmt.isConst());
        return null;
    }

    @Override
    public Void visitPassStatement(PassStatement stmt) {
        return null;
    }

    @Override
    public Void visitMatchStatement(MatchStatement stmt) {
        Object condition = evaluate(stmt.condition());

        for (MatchStatement.Case matchCase : stmt.cases()) {
            for (Expression pattern : matchCase.patterns()) {
                Object patternValue = evaluate(pattern);
                if (matchPattern(condition, patternValue)) {
                    execute(matchCase.branch());
                    return null;
                }
            }
        }

        if (stmt.defaultBranch() != null) {
            execute(stmt.defaultBranch());
        }

        return null;
    }

    private TokenType inferType(Object value) {
        if (value instanceof Integer) return TokenType.TYPE_INTEGER;
        if (value instanceof Double) return TokenType.TYPE_DECIMAL;
        if (value instanceof String) return TokenType.TYPE_STRING;
        if (value instanceof Boolean) return TokenType.TYPE_BOOLEAN;
        if (value instanceof TVScriptCallable) return TokenType.FUNCTION;
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
        if (operator == null) return false;
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
