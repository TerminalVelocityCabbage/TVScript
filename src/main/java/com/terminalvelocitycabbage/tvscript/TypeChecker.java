package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TypeChecker implements Statement.Visitor<Void>, Expression.Visitor<TokenType> {
    private final List<Map<String, VariableStaticInfo>> scopes = new ArrayList<>();
    private int loopDepth = 0;

    private static class VariableStaticInfo {
        final TokenType type;
        final boolean isConst;

        VariableStaticInfo(TokenType type, boolean isConst) {
            this.type = type;
            this.isConst = isConst;
        }
    }

    public TypeChecker() {
        scopes.add(new HashMap<>()); // Global scope
    }

    public void check(List<Statement> statements) {
        for (Statement statement : statements) {
            check(statement);
        }
    }

    private void check(Statement stmt) {
        stmt.accept(this);
    }

    private TokenType check(Expression expr) {
        return expr.accept(this);
    }

    @Override
    public Void visitBlockStmt(Statement.Block stmt) {
        beginScope();
        check(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitExpressionStmt(Statement.ExpressionStmt stmt) {
        check(stmt.expression);
        return null;
    }

    @Override
    public Void visitIfStmt(Statement.If stmt) {
        TokenType conditionType = check(stmt.condition);
        if (conditionType != TokenType.TYPE_BOOLEAN) {
            // Error, condition must be boolean
            TVScript.compileError(new CompileError(stmt.keyword, "Condition must be boolean."));
        }
        check(stmt.thenBranch);
        if (stmt.elseBranch != null) {
            check(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Statement.While stmt) {
        TokenType conditionType = check(stmt.condition);
        if (conditionType != TokenType.TYPE_BOOLEAN) {
            TVScript.compileError(new CompileError(stmt.keyword, "While condition must be a boolean."));
        }

        // Infinite loop detection
        if (stmt.condition instanceof Expression.Literal) {
            Object value = ((Expression.Literal) stmt.condition).value;
            if (Boolean.TRUE.equals(value)) {
                TVScript.warning(stmt.keyword, "Potential infinite loop: constant true condition.");
            }
        } else {
            List<String> vars = getVariablesUsed(stmt.condition);
            if (!vars.isEmpty() && !isMutated(stmt.body, vars)) {
                TVScript.warning(stmt.keyword, "Potential infinite loop: condition variables are not mutated in the loop body.");
            }
        }

        loopDepth++;
        check(stmt.body);
        loopDepth--;

        return null;
    }

    @Override
    public Void visitForStmt(Statement.For stmt) {
        TokenType rangeType = check(stmt.range);
        if (rangeType != TokenType.TYPE_RANGE) {
            TVScript.compileError(new CompileError(stmt.keyword, "For loop expects a range."));
        }

        beginScope();
        if (stmt.name != null) {
            declare(stmt.name, stmt.type.getType(), false);
        }

        loopDepth++;
        check(stmt.body);
        loopDepth--;

        endScope();
        return null;
    }

    @Override
    public Void visitBreakStmt(Statement.Break stmt) {
        if (loopDepth == 0) {
            TVScript.compileError(new CompileError(stmt.keyword, "Cannot use 'break' outside of a loop."));
        }
        return null;
    }

    @Override
    public Void visitContinueStmt(Statement.Continue stmt) {
        if (loopDepth == 0) {
            TVScript.compileError(new CompileError(stmt.keyword, "Cannot use 'continue' outside of a loop."));
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Statement.Print stmt) {
        check(stmt.expression);
        return null;
    }

    @Override
    public Void visitVarStmt(Statement.Var stmt) {
        TokenType declaredType = stmt.type.getType();
        TokenType inferredType = declaredType;

        if (stmt.initializer != null) {
            inferredType = check(stmt.initializer);
            if (declaredType == TokenType.VAR || declaredType == TokenType.CONST) {
                if (inferredType == null) {
                    TVScript.compileError(new CompileError(stmt.name, "Cannot infer type from none."));
                }
            } else if (inferredType != null && !isCompatible(declaredType, inferredType)) {
                TVScript.compileError(new CompileError(stmt.name, "Incompatible types in initialization."));
            }
        } else if (stmt.isConst) {
            TVScript.compileError(new CompileError(stmt.name, "Constant must be initialized."));
        } else if (declaredType == TokenType.VAR || declaredType == TokenType.CONST) {
            TVScript.compileError(new CompileError(stmt.name, "Type inference requires an initializer."));
        }

        declare(stmt.name, (declaredType == TokenType.VAR || declaredType == TokenType.CONST) ? inferredType : declaredType, stmt.isConst);
        return null;
    }

    @Override
    public Void visitPassStmt(Statement.Pass stmt) {
        return null;
    }

    @Override
    public TokenType visitBinaryExpr(Expression.Binary expr) {
        TokenType left = check(expr.left);
        TokenType right = check(expr.right);

        switch (expr.operator.getType()) {
            case GREATER:
            case GREATER_EQUAL:
            case LESS:
            case LESS_EQUAL:
                return TokenType.TYPE_BOOLEAN;
            case MINUS:
            case SLASH:
            case STAR:
            case PERCENT:
                if (left == TokenType.TYPE_INTEGER && right == TokenType.TYPE_INTEGER) {
                    return TokenType.TYPE_INTEGER;
                }
                return TokenType.TYPE_DECIMAL;
            case PLUS:
                if (left == TokenType.TYPE_STRING || right == TokenType.TYPE_STRING) {
                    return TokenType.TYPE_STRING;
                }
                if (left == TokenType.TYPE_INTEGER && right == TokenType.TYPE_INTEGER) {
                    return TokenType.TYPE_INTEGER;
                }
                return TokenType.TYPE_DECIMAL;
            case BANG_EQUAL:
            case EQUAL_EQUAL:
                return TokenType.TYPE_BOOLEAN;
        }
        return null;
    }

    @Override
    public TokenType visitGroupingExpr(Expression.Grouping expr) {
        return check(expr.expression);
    }

    @Override
    public TokenType visitLiteralExpr(Expression.Literal expr) {
        if (expr.value instanceof Integer) return TokenType.TYPE_INTEGER;
        if (expr.value instanceof Double) return TokenType.TYPE_DECIMAL;
        if (expr.value instanceof String) return TokenType.TYPE_STRING;
        if (expr.value instanceof Boolean) return TokenType.TYPE_BOOLEAN;
        return null;
    }

    @Override
    public TokenType visitLogicalExpr(Expression.Logical expr) {
        check(expr.left);
        check(expr.right);
        return TokenType.TYPE_BOOLEAN;
    }

    @Override
    public TokenType visitUnaryExpr(Expression.Unary expr) {
        TokenType right = check(expr.right);
        if (expr.operator.getType() == TokenType.BANG) return TokenType.TYPE_BOOLEAN;
        return right;
    }

    @Override
    public TokenType visitTernaryExpr(Expression.Ternary expr) {
        check(expr.condition);
        TokenType trueBranch = check(expr.trueBranch);
        TokenType falseBranch = check(expr.falseBranch);
        return trueBranch; // simplified
    }

    @Override
    public TokenType visitInterpolationExpr(Expression.Interpolation expr) {
        for (Expression e : expr.expressions) {
            check(e);
        }
        return TokenType.TYPE_STRING;
    }

    @Override
    public TokenType visitVariableExpr(Expression.Variable expr) {
        VariableStaticInfo info = lookup(expr.name);
        if (info == null) {
            TVScript.compileError(new CompileError(expr.name, "Variable used before declaration or undefined."));
            return null;
        }
        return info.type;
    }

    @Override
    public TokenType visitAssignExpr(Expression.Assign expr) {
        TokenType valueType = check(expr.value);
        VariableStaticInfo info = lookup(expr.name);
        if (info != null) {
            if (info.isConst) {
                TVScript.compileError(new CompileError(expr.name, "Cannot assign to constant variable."));
            }
            if (valueType != null && !isCompatible(info.type, valueType)) {
                 TVScript.compileError(new CompileError(expr.name, "Incompatible types in assignment."));
            }
        } else {
             TVScript.compileError(new CompileError(expr.name, "Variable undefined."));
        }
        return valueType;
    }

    @Override
    public TokenType visitRangeExpr(Expression.Range expr) {
        TokenType start = check(expr.start);
        TokenType end = check(expr.end);

        if (start != TokenType.TYPE_INTEGER || end != TokenType.TYPE_INTEGER) {
            TVScript.compileError(new CompileError(expr.operator, "Range bounds must be integers."));
        }

        return TokenType.TYPE_RANGE;
    }

    private void beginScope() {
        scopes.add(new HashMap<>());
    }

    private void endScope() {
        scopes.remove(scopes.size() - 1);
    }

    private void declare(Token name, TokenType type, boolean isConst) {
        // Redefinition in the same scope OR any outer scope is an error in TVScript
        if (isAlreadyDefined(name.getLexeme())) {
            TVScript.compileError(new CompileError(name, "Variable '" + name.getLexeme() + "' is already defined in this or an outer scope."));
            return;
        }

        Map<String, VariableStaticInfo> scope = scopes.get(scopes.size() - 1);
        scope.put(name.getLexeme(), new VariableStaticInfo(type, isConst));
    }

    private boolean isAlreadyDefined(String name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name)) return true;
        }
        return false;
    }

    private VariableStaticInfo lookup(Token name) {
        for (int i = scopes.size() - 1; i >= 0; i--) {
            if (scopes.get(i).containsKey(name.getLexeme())) {
                return scopes.get(i).get(name.getLexeme());
            }
        }
        return null;
    }

    private boolean isCompatible(TokenType expected, TokenType actual) {
        if (expected == actual) return true;
        if (expected == TokenType.TYPE_DECIMAL && actual == TokenType.TYPE_INTEGER) return true;
        return false;
    }

    private List<String> getVariablesUsed(Expression expression) {
        List<String> vars = new ArrayList<>();
        expression.accept(new Expression.Visitor<Void>() {
            @Override public Void visitBinaryExpr(Expression.Binary expr) { expr.left.accept(this); expr.right.accept(this); return null; }
            @Override public Void visitGroupingExpr(Expression.Grouping expr) { expr.expression.accept(this); return null; }
            @Override public Void visitLiteralExpr(Expression.Literal expr) { return null; }
            @Override public Void visitLogicalExpr(Expression.Logical expr) { expr.left.accept(this); expr.right.accept(this); return null; }
            @Override public Void visitUnaryExpr(Expression.Unary expr) { expr.right.accept(this); return null; }
            @Override public Void visitTernaryExpr(Expression.Ternary expr) { expr.condition.accept(this); expr.trueBranch.accept(this); expr.falseBranch.accept(this); return null; }
            @Override public Void visitInterpolationExpr(Expression.Interpolation expr) { for (Expression e : expr.expressions) e.accept(this); return null; }
            @Override public Void visitVariableExpr(Expression.Variable expr) { vars.add(expr.name.getLexeme()); return null; }
            @Override public Void visitAssignExpr(Expression.Assign expr) { vars.add(expr.name.getLexeme()); expr.value.accept(this); return null; }
            @Override public Void visitRangeExpr(Expression.Range expr) { expr.start.accept(this); expr.end.accept(this); return null; }
        });
        return vars;
    }

    private boolean isMutated(Statement body, List<String> vars) {
        final boolean[] mutated = {false};
        body.accept(new Statement.Visitor<Void>() {
            @Override public Void visitBlockStmt(Statement.Block stmt) { for (Statement s : stmt.statements) s.accept(this); return null; }
            @Override public Void visitExpressionStmt(Statement.ExpressionStmt stmt) { stmt.expression.accept(exprVisitor); return null; }
            @Override public Void visitIfStmt(Statement.If stmt) { stmt.thenBranch.accept(this); if (stmt.elseBranch != null) stmt.elseBranch.accept(this); return null; }
            @Override public Void visitPrintStmt(Statement.Print stmt) { return null; }
            @Override public Void visitVarStmt(Statement.Var stmt) { return null; }
            @Override public Void visitPassStmt(Statement.Pass stmt) { return null; }
            @Override public Void visitWhileStmt(Statement.While stmt) { stmt.body.accept(this); return null; }
            @Override public Void visitForStmt(Statement.For stmt) { stmt.body.accept(this); return null; }
            @Override public Void visitBreakStmt(Statement.Break stmt) { return null; }
            @Override public Void visitContinueStmt(Statement.Continue stmt) { return null; }

            private final Expression.Visitor<Void> exprVisitor = new Expression.Visitor<Void>() {
                @Override public Void visitBinaryExpr(Expression.Binary expr) { expr.left.accept(this); expr.right.accept(this); return null; }
                @Override public Void visitGroupingExpr(Expression.Grouping expr) { expr.expression.accept(this); return null; }
                @Override public Void visitLiteralExpr(Expression.Literal expr) { return null; }
                @Override public Void visitLogicalExpr(Expression.Logical expr) { expr.left.accept(this); expr.right.accept(this); return null; }
                @Override public Void visitUnaryExpr(Expression.Unary expr) { expr.right.accept(this); return null; }
                @Override public Void visitTernaryExpr(Expression.Ternary expr) { expr.condition.accept(this); expr.trueBranch.accept(this); expr.falseBranch.accept(this); return null; }
                @Override public Void visitInterpolationExpr(Expression.Interpolation expr) { for (Expression e : expr.expressions) e.accept(this); return null; }
                @Override public Void visitVariableExpr(Expression.Variable expr) { return null; }
                @Override public Void visitAssignExpr(Expression.Assign expr) { if (vars.contains(expr.name.getLexeme())) mutated[0] = true; expr.value.accept(this); return null; }
                @Override public Void visitRangeExpr(Expression.Range expr) { expr.start.accept(this); expr.end.accept(this); return null; }
            };
        });
        return mutated[0];
    }
}
