package com.terminalvelocitycabbage.tvscript.analysis;

import com.terminalvelocitycabbage.tvscript.analysis.types.PrimitiveType;
import com.terminalvelocitycabbage.tvscript.analysis.types.Type;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

import java.util.List;

import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;

public class DeclarationChecker implements Statement.Visitor<Void> {

    private final TypeChecker tc;
    private final TypeCheckerState state;

    public DeclarationChecker(TypeChecker tc, TypeCheckerState state) {
        this.tc = tc;
        this.state = state;
    }

    @Override
    public Void visitFunctionStatement(FunctionStatement stmt) {
        Type returnType = PrimitiveType.VOID;
        if (stmt.returnType() != null) {
            returnType = tc.resolveType(stmt.returnType());
        }

        Type previousReturnType = state.currentReturnType;
        state.currentReturnType = returnType;

        if (stmt.name().type() != TokenType.CONSTRUCTOR) {
            tc.declare(stmt.name(), PrimitiveType.FUNCTION, true);
            state.functions.put(stmt.name().lexeme(), stmt);
        }

        tc.beginScope();
        for (FunctionStatement.Parameter param : stmt.parameters()) {
            tc.declare(param.name(), tc.resolveType(param.type()), false);
            if (param.defaultValue() != null) tc.check(param.defaultValue());
        }

        if (stmt.body() != null) {
            tc.check(stmt.body());
        }

        tc.endScope();
        state.currentReturnType = previousReturnType;
        return null;
    }

    @Override
    public Void visitClassStatement(ClassStatement stmt) {
        ClassStatement previousClass = state.currentClass;
        state.currentClass = stmt;

        tc.traitImplementationChecker.checkTraitImplementations(stmt);

        for (VarStatement field : stmt.fields()) {
            tc.check(field);
        }

        for (FunctionStatement constructor : stmt.constructors()) {
            tc.check(constructor);
        }

        for (FunctionStatement method : stmt.methods()) {
            tc.check(method);
        }

        for (FunctionStatement staticMethod : stmt.staticMethods()) {
            tc.check(staticMethod);
        }

        state.currentClass = previousClass;
        return null;
    }

    @Override
    public Void visitTraitStatement(TraitStatement stmt) {
        for (FunctionStatement method : stmt.methods()) {
            tc.check(method);
        }
        return null;
    }

    @Override
    public Void visitTypeStatement(TypeStatement stmt) {
        TypeStatement previousType = state.currentType;
        state.currentType = stmt;

        tc.traitImplementationChecker.checkTypeTraitImplementations(stmt);

        for (FunctionStatement method : stmt.methods()) {
            tc.check(method);
        }
        for (FunctionStatement operator : stmt.operators()) {
            tc.check(operator);
        }

        state.currentType = previousType;
        return null;
    }

    @Override
    public Void visitConstraintStatement(ConstraintStatement stmt) {
        return null;
    }

    @Override
    public Void visitEventStatement(EventStatement stmt) {
        for (VarStatement field : stmt.fields()) {
            tc.check(field);
        }
        return null;
    }
}
