package com.terminalvelocitycabbage.tvscript.analysis;

import com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType;
import com.terminalvelocitycabbage.tvscript.analysis.types.Type;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.errors.CompileError;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

import java.util.List;

import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;

public class StatementChecker implements Statement.Visitor<Void> {

    private final TypeChecker tc;
    private final TypeCheckerState state;

    public StatementChecker(TypeChecker tc, TypeCheckerState state) {
        this.tc = tc;
        this.state = state;
    }

    private void check(Statement stmt) {
        tc.check(stmt);
    }

    private Type check(Expression expr) {
        return tc.check(expr);
    }

    @Override
    public Void visitBlockStatement(BlockStatement stmt) {
        tc.beginScope();
        for (Statement statement : stmt.statements()) {
            check(statement);
        }
        tc.endScope();
        return null;
    }

    @Override
    public Void visitExpressionStatement(ExpressionStatement stmt) {
        check(stmt.expression());
        return null;
    }

    @Override
    public Void visitIfStatement(IfStatement stmt) {
        check(stmt.condition());
        check(stmt.thenBranch());
        if (stmt.elseBranch() != null) {
            check(stmt.elseBranch());
        }
        return null;
    }

    @Override
    public Void visitWhileStatement(WhileStatement stmt) {
        check(stmt.condition());
        state.loopDepth++;
        check(stmt.body());
        state.loopDepth--;
        return null;
    }

    @Override
    public Void visitForStatement(ForStatement stmt) {
        Type iterableType = check(stmt.range());
        if (iterableType != null && iterableType != PrimitiveType.RANGE
                && iterableType.toTokenType() != com.terminalvelocitycabbage.tvscript.parsing.TokenType.IDENTIFIER
                && iterableType.toTokenType() != com.terminalvelocitycabbage.tvscript.parsing.TokenType.LIST
                && iterableType.toTokenType() != com.terminalvelocitycabbage.tvscript.parsing.TokenType.SET
                && iterableType.toTokenType() != com.terminalvelocitycabbage.tvscript.parsing.TokenType.MAP) {
            state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(stmt.keyword(), "For loop expects a range or collection."));
        }

        if (stmt.valueName() != null && iterableType == PrimitiveType.RANGE) {
            state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(stmt.keyword(), "Range iteration supports only a single loop variable."));
        }

        tc.beginScope();
        if (stmt.name() != null) {
            tc.declare(stmt.name(), tc.resolveType(stmt.type()), false);
        }
        if (stmt.valueName() != null) {
            tc.declare(stmt.valueName(), tc.resolveType(stmt.valueType()), false);
        }

        state.loopDepth++;
        check(stmt.body());
        state.loopDepth--;

        tc.endScope();
        return null;
    }

    @Override
    public Void visitBreakStatement(BreakStatement stmt) {
        if (state.loopDepth == 0) {
            state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(stmt.keyword(), "Cannot use 'break' outside of a loop."));
        }
        return null;
    }

    @Override
    public Void visitContinueStatement(ContinueStatement stmt) {
        if (state.loopDepth == 0) {
            state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(stmt.keyword(), "Cannot use 'continue' outside of a loop."));
        }
        return null;
    }

    @Override
    public Void visitReturnStatement(ReturnStatement stmt) {
        Type valueType = PrimitiveType.VOID;
        if (stmt.value() != null) {
            valueType = check(stmt.value());
        }

        if (state.currentReturnType != null && !tc.isCompatible(state.currentReturnType, valueType)) {
            state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(stmt.keyword(), "Incompatible return type: expected " + state.currentReturnType + ", got " + valueType));
        }

        return null;
    }

    @Override
    public Void visitPrintStatement(PrintStatement stmt) {
        check(stmt.expression());
        return null;
    }

    @Override
    public Void visitVarStatement(VarStatement stmt) {
        Type type = tc.resolveType(stmt.type());
        if (stmt.initializer() != null) {
            Type initializerType = check(stmt.initializer());
            if (!tc.isCompatible(type, initializerType)) {
                state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(stmt.name(), "Incompatible types in variable initialization: expected " + type + ", got " + initializerType));
            }
        }
        tc.declare(stmt.name(), type, stmt.isConst());
        return null;
    }

    @Override
    public Void visitPassStatement(PassStatement stmt) {
        return null;
    }

    @Override
    public Void visitImportStatement(ImportStatement stmt) {
        // Handled in registerDefinitions mostly, but could do visibility checks here
        return null;
    }

    @Override
    public Void visitMatchStatement(MatchStatement stmt) {
        check(stmt.condition());
        for (MatchStatement.Case c : stmt.cases()) {
            for (Expression pattern : c.patterns()) {
                check(pattern);
            }
            check(c.branch());
        }
        if (stmt.defaultBranch() != null) {
            check(stmt.defaultBranch());
        }
        return null;
    }

    @Override
    public Void visitFunctionStatement(FunctionStatement stmt) {
        return tc.declarationChecker.visitFunctionStatement(stmt);
    }

    @Override
    public Void visitClassStatement(ClassStatement stmt) {
        return tc.declarationChecker.visitClassStatement(stmt);
    }

    @Override
    public Void visitTraitStatement(TraitStatement stmt) {
        return tc.declarationChecker.visitTraitStatement(stmt);
    }

    @Override
    public Void visitTypeStatement(TypeStatement stmt) {
        return tc.declarationChecker.visitTypeStatement(stmt);
    }

    @Override
    public Void visitConstraintStatement(ConstraintStatement stmt) {
        return tc.declarationChecker.visitConstraintStatement(stmt);
    }

    @Override
    public Void visitEventStatement(EventStatement stmt) {
        return tc.declarationChecker.visitEventStatement(stmt);
    }

    @Override
    public Void visitOnStatement(OnStatement stmt) {
        check(stmt.body());
        return null;
    }

    @Override
    public Void visitDispatchStatement(DispatchStatement stmt) {
        for (Expression.Argument arg : stmt.arguments()) {
            check(arg.value());
        }
        return null;
    }
}
