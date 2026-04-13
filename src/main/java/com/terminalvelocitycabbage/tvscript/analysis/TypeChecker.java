package com.terminalvelocitycabbage.tvscript.analysis;

import com.terminalvelocitycabbage.tvscript.TVScript;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import static com.terminalvelocitycabbage.tvscript.ast.Expression.*;
import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;
import com.terminalvelocitycabbage.tvscript.errors.CompileError;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Performs static type checking on the AST.
 */
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

    /**
     * Checks a list of statements for type errors.
     * @param statements The statements to check.
     */
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
    public Void visitBlockStatement(BlockStatement stmt) {
        beginScope();
        check(stmt.statements());
        endScope();
        return null;
    }

    @Override
    public Void visitExpressionStatement(ExpressionStatement stmt) {
        check(stmt.expression());
        return null;
    }

    @Override
    public Void visitIfStatement(IfStatement stmt) {
        TokenType conditionType = check(stmt.condition());
        if (conditionType != TokenType.TYPE_BOOLEAN) {
            TVScript.compileError(new CompileError(stmt.keyword(), "Condition must be boolean."));
        }
        check(stmt.thenBranch());
        if (stmt.elseBranch() != null) {
            check(stmt.elseBranch());
        }
        return null;
    }

    @Override
    public Void visitWhileStatement(WhileStatement stmt) {
        TokenType conditionType = check(stmt.condition());
        if (conditionType != TokenType.TYPE_BOOLEAN) {
            TVScript.compileError(new CompileError(stmt.keyword(), "While condition must be a boolean."));
        }

        // Infinite loop detection
        if (stmt.condition() instanceof LiteralExpression) {
            Object value = ((LiteralExpression) stmt.condition()).value();
            if (Boolean.TRUE.equals(value)) {
                TVScript.warning(stmt.keyword(), "Potential infinite loop: constant true condition.");
            }
        } else {
            List<String> vars = getVariablesUsed(stmt.condition());
            if (!vars.isEmpty() && !isMutated(stmt.body(), vars)) {
                TVScript.warning(stmt.keyword(), "Potential infinite loop: condition variables are not mutated in the loop body.");
            }
        }

        loopDepth++;
        check(stmt.body());
        loopDepth--;

        return null;
    }

    @Override
    public Void visitForStatement(ForStatement stmt) {
        TokenType rangeType = check(stmt.range());
        if (rangeType != TokenType.TYPE_RANGE) {
            TVScript.compileError(new CompileError(stmt.keyword(), "For loop expects a range."));
        }

        beginScope();
        if (stmt.name() != null) {
            declare(stmt.name(), stmt.type().getType(), false);
        }

        loopDepth++;
        check(stmt.body());
        loopDepth--;

        endScope();
        return null;
    }

    @Override
    public Void visitBreakStatement(BreakStatement stmt) {
        if (loopDepth == 0) {
            TVScript.compileError(new CompileError(stmt.keyword(), "Cannot use 'break' outside of a loop."));
        }
        return null;
    }

    @Override
    public Void visitContinueStatement(ContinueStatement stmt) {
        if (loopDepth == 0) {
            TVScript.compileError(new CompileError(stmt.keyword(), "Cannot use 'continue' outside of a loop."));
        }
        return null;
    }

    @Override
    public Void visitMatchStatement(MatchStatement stmt) {
        TokenType conditionType = check(stmt.condition());

        for (MatchStatement.Case matchCase : stmt.cases()) {
            for (Expression pattern : matchCase.patterns()) {
                TokenType patternType = check(pattern);
                if (patternType != null && !isCompatible(conditionType, patternType) && !isCompatible(patternType, conditionType)) {
                     TVScript.compileError(new CompileError(stmt.keyword(), "Pattern type " + patternType + " is not compatible with condition type " + conditionType + "."));
                }
            }
            check(matchCase.branch());
        }

        if (stmt.defaultBranch() != null) {
            check(stmt.defaultBranch());
        } else if (!isExhaustive(conditionType, stmt.cases())) {
            TVScript.compileError(new CompileError(stmt.keyword(), "Match statement must be exhaustive. Add a 'default' case."));
        }

        return null;
    }

    private boolean isExhaustive(TokenType type, List<?> cases) {
        // TODO: Implement actual exhaustiveness checking
        return false; 
    }

    @Override
    public Void visitPrintStatement(PrintStatement stmt) {
        check(stmt.expression());
        return null;
    }

    @Override
    public Void visitVarStatement(VarStatement stmt) {
        TokenType declaredType = stmt.type().getType();
        TokenType inferredType = declaredType;

        if (stmt.initializer() != null) {
            inferredType = check(stmt.initializer());
            if (declaredType == TokenType.VAR || declaredType == TokenType.CONST) {
                if (inferredType == null) {
                    TVScript.compileError(new CompileError(stmt.name(), "Cannot infer type from none."));
                }
            } else if (inferredType != null && !isCompatible(declaredType, inferredType)) {
                TVScript.compileError(new CompileError(stmt.name(), "Incompatible types in initialization."));
            }
        } else if (stmt.isConst()) {
            TVScript.compileError(new CompileError(stmt.name(), "Constant must be initialized."));
        } else if (declaredType == TokenType.VAR || declaredType == TokenType.CONST) {
            TVScript.compileError(new CompileError(stmt.name(), "Type inference requires an initializer."));
        }

        declare(stmt.name(), (declaredType == TokenType.VAR || declaredType == TokenType.CONST) ? inferredType : declaredType, stmt.isConst());
        return null;
    }

    @Override
    public Void visitPassStatement(PassStatement stmt) {
        return null;
    }

    @Override
    public TokenType visitBinaryExpression(BinaryExpression expr) {
        TokenType left = check(expr.left());
        TokenType right = check(expr.right());

        switch (expr.operator().getType()) {
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
            default:
                return null;
        }
    }

    @Override
    public TokenType visitGroupingExpression(GroupingExpression expr) {
        return check(expr.expression());
    }

    @Override
    public TokenType visitLiteralExpression(LiteralExpression expr) {
        if (expr.value() instanceof Integer) return TokenType.TYPE_INTEGER;
        if (expr.value() instanceof Double) return TokenType.TYPE_DECIMAL;
        if (expr.value() instanceof String) return TokenType.TYPE_STRING;
        if (expr.value() instanceof Boolean) return TokenType.TYPE_BOOLEAN;
        return null;
    }

    @Override
    public TokenType visitLogicalExpression(LogicalExpression expr) {
        check(expr.left());
        check(expr.right());
        return TokenType.TYPE_BOOLEAN;
    }

    @Override
    public TokenType visitUnaryExpression(UnaryExpression expr) {
        TokenType right = check(expr.right());
        if (expr.operator().getType() == TokenType.BANG) return TokenType.TYPE_BOOLEAN;
        return right;
    }

    @Override
    public TokenType visitTernaryExpression(TernaryExpression expr) {
        check(expr.condition());
        TokenType trueBranch = check(expr.thenBranch());
        TokenType falseBranch = check(expr.elseBranch());
        // TODO: Properly check if branches are compatible
        return trueBranch;
    }

    @Override
    public TokenType visitInterpolationExpression(InterpolationExpression expr) {
        for (Expression e : expr.expressions()) {
            check(e);
        }
        return TokenType.TYPE_STRING;
    }

    @Override
    public TokenType visitVariableExpression(VariableExpression expr) {
        VariableStaticInfo info = lookup(expr.name());
        if (info == null) {
            TVScript.compileError(new CompileError(expr.name(), "Variable used before declaration or undefined."));
            return null;
        }
        return info.type;
    }

    @Override
    public TokenType visitAssignExpression(AssignExpression expr) {
        TokenType valueType = check(expr.value());
        VariableStaticInfo info = lookup(expr.name());
        if (info != null) {
            if (info.isConst) {
                TVScript.compileError(new CompileError(expr.name(), "Cannot assign to constant variable."));
            }
            if (valueType != null && !isCompatible(info.type, valueType)) {
                 TVScript.compileError(new CompileError(expr.name(), "Incompatible types in assignment."));
            }
        } else {
             TVScript.compileError(new CompileError(expr.name(), "Variable undefined."));
        }
        return valueType;
    }

    @Override
    public TokenType visitRangeExpression(RangeExpression expr) {
        TokenType start = check(expr.start());
        TokenType end = check(expr.end());

        if (start != TokenType.TYPE_INTEGER || end != TokenType.TYPE_INTEGER) {
            TVScript.compileError(new CompileError(expr.operator(), "Range bounds must be integers."));
        }

        return TokenType.TYPE_RANGE;
    }

    @Override
    public TokenType visitMatchExpression(MatchExpression expr) {
        TokenType conditionType = check(expr.condition());
        TokenType resultType = null;

        for (MatchExpression.Case matchCase : expr.cases()) {
            for (Expression pattern : matchCase.patterns()) {
                TokenType patternType = check(pattern);
                if (patternType != null && !isCompatible(conditionType, patternType) && !isCompatible(patternType, conditionType)) {
                    TVScript.compileError(new CompileError(expr.keyword(), "Pattern type " + patternType + " is not compatible with condition type " + conditionType + "."));
                }
            }
            TokenType branchType = check(matchCase.branch());
            if (resultType == null) {
                resultType = branchType;
            } else if (branchType != null && !isCompatible(resultType, branchType)) {
                // Try the other way around if it's decimal/integer
                if (isCompatible(branchType, resultType)) {
                    resultType = branchType;
                } else {
                    TVScript.compileError(new CompileError(expr.keyword(), "Incompatible types in match expression branches."));
                }
            }
        }

        if (expr.defaultBranch() != null) {
            TokenType defaultType = check(expr.defaultBranch());
            if (resultType == null) {
                resultType = defaultType;
            } else if (defaultType != null && !isCompatible(resultType, defaultType)) {
                 if (isCompatible(defaultType, resultType)) {
                    resultType = defaultType;
                } else {
                    TVScript.compileError(new CompileError(expr.keyword(), "Incompatible types in match expression branches."));
                }
            }
        } else if (!isExhaustive(conditionType, expr.cases())) {
            TVScript.compileError(new CompileError(expr.keyword(), "Match expression must be exhaustive. Add a 'default' case."));
        }

        return resultType;
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
        if (expected == TokenType.TYPE_INTEGER && actual == TokenType.TYPE_RANGE) return true;
        if (expected == TokenType.TYPE_DECIMAL && actual == TokenType.TYPE_RANGE) return true;
        return false;
    }

    private List<String> getVariablesUsed(Expression expression) {
        List<String> vars = new ArrayList<>();
        expression.accept(new Expression.Visitor<Void>() {
            @Override public Void visitBinaryExpression(BinaryExpression expr) { expr.left().accept(this); expr.right().accept(this); return null; }
            @Override public Void visitGroupingExpression(GroupingExpression expr) { expr.expression().accept(this); return null; }
            @Override public Void visitLiteralExpression(LiteralExpression expr) { return null; }
            @Override public Void visitLogicalExpression(LogicalExpression expr) { expr.left().accept(this); expr.right().accept(this); return null; }
            @Override public Void visitUnaryExpression(UnaryExpression expr) { expr.right().accept(this); return null; }
            @Override public Void visitTernaryExpression(TernaryExpression expr) { expr.condition().accept(this); expr.thenBranch().accept(this); expr.elseBranch().accept(this); return null; }
            @Override public Void visitInterpolationExpression(InterpolationExpression expr) { for (Expression e : expr.expressions()) e.accept(this); return null; }
            @Override public Void visitVariableExpression(VariableExpression expr) { vars.add(expr.name().getLexeme()); return null; }
            @Override public Void visitAssignExpression(AssignExpression expr) { vars.add(expr.name().getLexeme()); expr.value().accept(this); return null; }
            @Override public Void visitRangeExpression(RangeExpression expr) { expr.start().accept(this); expr.end().accept(this); return null; }
            @Override public Void visitMatchExpression(MatchExpression expr) {
                expr.condition().accept(this);
                for (MatchExpression.Case c : expr.cases()) {
                    for (Expression p : c.patterns()) p.accept(this);
                    c.branch().accept(this);
                }
                if (expr.defaultBranch() != null) expr.defaultBranch().accept(this);
                return null;
            }
        });
        return vars;
    }

    private boolean isMutated(Statement body, List<String> vars) {
        final boolean[] mutated = {false};
        body.accept(new Statement.Visitor<Void>() {
            @Override public Void visitBlockStatement(BlockStatement stmt) { for (Statement s : stmt.statements()) s.accept(this); return null; }
            @Override public Void visitExpressionStatement(ExpressionStatement stmt) { stmt.expression().accept(exprVisitor); return null; }
            @Override public Void visitIfStatement(IfStatement stmt) { stmt.thenBranch().accept(this); if (stmt.elseBranch() != null) stmt.elseBranch().accept(this); return null; }
            @Override public Void visitPrintStatement(PrintStatement stmt) { return null; }
            @Override public Void visitVarStatement(VarStatement stmt) { return null; }
            @Override public Void visitPassStatement(PassStatement stmt) { return null; }
            @Override public Void visitWhileStatement(WhileStatement stmt) { stmt.body().accept(this); return null; }
            @Override public Void visitForStatement(ForStatement stmt) { stmt.body().accept(this); return null; }
            @Override public Void visitMatchStatement(MatchStatement stmt) {
                stmt.condition().accept(exprVisitor);
                for (MatchStatement.Case c : stmt.cases()) {
                    for (Expression p : c.patterns()) p.accept(exprVisitor);
                    c.branch().accept(this);
                }
                if (stmt.defaultBranch() != null) stmt.defaultBranch().accept(this);
                return null;
            }
            @Override public Void visitBreakStatement(BreakStatement stmt) { return null; }
            @Override public Void visitContinueStatement(ContinueStatement stmt) { return null; }

            private final Expression.Visitor<Void> exprVisitor = new Expression.Visitor<Void>() {
                @Override public Void visitBinaryExpression(BinaryExpression expr) { expr.left().accept(this); expr.right().accept(this); return null; }
                @Override public Void visitGroupingExpression(GroupingExpression expr) { expr.expression().accept(this); return null; }
                @Override public Void visitLiteralExpression(LiteralExpression expr) { return null; }
                @Override public Void visitLogicalExpression(LogicalExpression expr) { expr.left().accept(this); expr.right().accept(this); return null; }
                @Override public Void visitUnaryExpression(UnaryExpression expr) { expr.right().accept(this); return null; }
                @Override public Void visitTernaryExpression(TernaryExpression expr) { expr.condition().accept(this); expr.thenBranch().accept(this); expr.elseBranch().accept(this); return null; }
                @Override public Void visitInterpolationExpression(InterpolationExpression expr) { for (Expression e : expr.expressions()) e.accept(this); return null; }
                @Override public Void visitVariableExpression(VariableExpression expr) { return null; }
                @Override public Void visitAssignExpression(AssignExpression expr) { if (vars.contains(expr.name().getLexeme())) mutated[0] = true; expr.value().accept(this); return null; }
                @Override public Void visitRangeExpression(RangeExpression expr) { expr.start().accept(this); expr.end().accept(this); return null; }
                @Override public Void visitMatchExpression(MatchExpression expr) {
                    expr.condition().accept(this);
                    for (MatchExpression.Case c : expr.cases()) {
                        for (Expression p : c.patterns()) p.accept(this);
                        c.branch().accept(this);
                    }
                    if (expr.defaultBranch() != null) expr.defaultBranch().accept(this);
                    return null;
                }
            };
        });
        return mutated[0];
    }
}
