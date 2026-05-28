package com.terminalvelocitycabbage.tvscript.analysis;

import com.terminalvelocitycabbage.tvscript.analysis.types.*;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.errors.CompileError;
import com.terminalvelocitycabbage.tvscript.execution.NativeClass;
import com.terminalvelocitycabbage.tvscript.execution.TVType;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

import java.util.*;

import static com.terminalvelocitycabbage.tvscript.ast.Expression.*;
import static com.terminalvelocitycabbage.tvscript.ast.Statement.*;

public class ExpressionChecker implements Expression.Visitor<Type> {

    private final TypeChecker tc;
    private final TypeCheckerState state;

    public ExpressionChecker(TypeChecker tc, TypeCheckerState state) {
        this.tc = tc;
        this.state = state;
    }

    private Type check(Expression expr) {
        return tc.check(expr);
    }

    @Override
    public Type visitBinaryExpression(BinaryExpression expr) {
        Type left = check(expr.left());
        Type right = check(expr.right());

        switch (expr.operator().type()) {
            case GREATER, GREATER_EQUAL, LESS, LESS_EQUAL -> {
                if (!(left instanceof PrimitiveType pLeft && pLeft.isNumeric()) ||
                        !(right instanceof PrimitiveType pRight && pRight.isNumeric())) {
                    state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(expr.operator(), "Operands must be numbers."));
                }
                return PrimitiveType.BOOLEAN;
            }
            case BANG_EQUAL, EQUAL_EQUAL -> {
                return PrimitiveType.BOOLEAN;
            }
            case MINUS, SLASH, STAR, PERCENT -> {
                if (!(left instanceof PrimitiveType pLeft && pLeft.isNumeric()) ||
                        !(right instanceof PrimitiveType pRight && pRight.isNumeric())) {
                    state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(expr.operator(), "Operands must be numbers."));
                }
                if (left == PrimitiveType.DECIMAL || right == PrimitiveType.DECIMAL) return PrimitiveType.DECIMAL;
                return PrimitiveType.INTEGER;
            }
            case PLUS -> {
                if (left == PrimitiveType.STRING || right == PrimitiveType.STRING) return PrimitiveType.STRING;
                if (left instanceof PrimitiveType pLeft && pLeft.isNumeric() &&
                        right instanceof PrimitiveType pRight && pRight.isNumeric()) {
                    if (left == PrimitiveType.DECIMAL || right == PrimitiveType.DECIMAL) return PrimitiveType.DECIMAL;
                    return PrimitiveType.INTEGER;
                }
                state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(expr.operator(), "Operands must be two numbers or at least one string."));
            }
        }

        // Check for operator overloading
        Type overload = tc.findOperatorOverload(left, expr.operator().type(), right);
        if (overload != null) return overload;

        return PrimitiveType.VOID;
    }

    @Override
    public Type visitGroupingExpression(GroupingExpression expr) {
        return check(expr.expression());
    }

    @Override
    public Type visitLiteralExpression(LiteralExpression expr) {
        if (expr.value() == null) return PrimitiveType.VOID;
        if (expr.value() instanceof String) return PrimitiveType.STRING;
        if (expr.value() instanceof Integer || expr.value() instanceof Long) return PrimitiveType.INTEGER;
        if (expr.value() instanceof Double || expr.value() instanceof Float) return PrimitiveType.DECIMAL;
        if (expr.value() instanceof Boolean) return PrimitiveType.BOOLEAN;
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitLogicalExpression(LogicalExpression expr) {
        check(expr.left());
        check(expr.right());
        return PrimitiveType.BOOLEAN;
    }

    @Override
    public Type visitUnaryExpression(UnaryExpression expr) {
        Type right = check(expr.right());
        if (expr.operator().type() == com.terminalvelocitycabbage.tvscript.parsing.TokenType.BANG) return PrimitiveType.BOOLEAN;
        if (expr.operator().type() == com.terminalvelocitycabbage.tvscript.parsing.TokenType.MINUS) return right;
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitTernaryExpression(TernaryExpression expr) {
        check(expr.condition());
        Type trueBranch = check(expr.thenBranch());
        Type falseBranch = check(expr.elseBranch());

        if (!tc.isCompatible(trueBranch, falseBranch) && !tc.isCompatible(falseBranch, trueBranch)) {
            state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(expr.operator(), "Ternary branches have incompatible types: " + trueBranch + " and " + falseBranch));
        }

        return trueBranch;
    }

    @Override
    public Type visitInterpolationExpression(InterpolationExpression expr) {
        for (Expression expression : expr.expressions()) {
            check(expression);
        }
        return PrimitiveType.STRING;
    }

    @Override
    public Type visitVariableExpression(VariableExpression expr) {
        String name = expr.name().lexeme();
        
        // Handle qualified names (Module.Member)
        if (name.contains(".")) {
            String[] parts = name.split("\\.");
            String alias = parts[0];
            String member = parts[1];
            String resolved = tc.resolveQualified(alias, member);
            if (resolved != null) name = resolved;
        } else {
            // Handle imports
            String resolved = tc.resolveImport(name);
            if (resolved != null) name = resolved;
        }

        TypeCheckerState.VariableStaticInfo info = tc.lookup(expr.name());
        if (info != null) return info.type;

        // Check if it's a class or trait name (used for static access or as a type literal)
        if (state.classes.containsKey(name)) return tc.resolveClassType(name);
        if (state.traits.containsKey(name)) return tc.resolveTraitType(name);
        if (state.types.containsKey(name)) return tc.resolveType(new Token(com.terminalvelocitycabbage.tvscript.parsing.TokenType.IDENTIFIER, name, null, 0));

        state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(expr.name(), "Undefined variable '" + expr.name().lexeme() + "'."));
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitNativeExpression(NativeExpression expr) {
        // Native expressions are used for low-level interop, usually return VOID or are handled by specifically
        String name = expr.name().lexeme();
        if (state.nativeClasses.containsKey(name)) return tc.resolveClassType(name);
        // We don't have enough info here to know the exact type of a native, assume VOID or something based on usage
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitAssignExpression(AssignExpression expr) {
        Type valueType = check(expr.value());
        TypeCheckerState.VariableStaticInfo varInfo = tc.lookup(expr.name());
        if (varInfo != null) {
            if (varInfo.isConst) {
                state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(expr.name(), "Cannot assign to constant variable '" + expr.name().lexeme() + "'."));
            }
            if (!tc.isCompatible(varInfo.type, valueType)) {
                state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(expr.name(), "Incompatible types in assignment: expected " + varInfo.type + ", got " + valueType));
            }
            return valueType;
        }

        state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(expr.name(), "Undefined variable '" + expr.name().lexeme() + "'."));
        return valueType;
    }

    @Override
    public Type visitRangeExpression(RangeExpression expr) {
        check(expr.start());
        if (expr.end() != null) check(expr.end());
        return PrimitiveType.RANGE;
    }

    @Override
    public Type visitMatchExpression(MatchExpression expr) {
        Type conditionType = check(expr.condition());
        Type resultType = null;

        for (MatchExpression.Case caseExpr : expr.cases()) {
            for (Expression pattern : caseExpr.patterns()) {
                Type patternType = check(pattern);
                if (!tc.isCompatible(conditionType, patternType)) {
                    state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(expr.keyword(), "Pattern type " + patternType + " is not compatible with condition type " + conditionType));
                }
            }
            Type branchType = check(caseExpr.branch());
            if (resultType == null) {
                resultType = branchType;
            } else if (!tc.isCompatible(resultType, branchType)) {
                // Find common supertype? For now just error or assume first branch
            }
        }

        if (expr.defaultBranch() != null) {
            check(expr.defaultBranch());
        }

        return resultType != null ? resultType : PrimitiveType.VOID;
    }

    @Override
    public Type visitCallExpression(CallExpression expr) {
        Type calleeType = check(expr.callee());

        if (calleeType instanceof FunctionType funcType) {
            // Validate arguments
            // Simplified: check count and types
            return funcType.getReturnType();
        }

        // Handle class constructor calls if callee is a ClassType
        if (calleeType instanceof ClassType classType) {
            return classType;
        }

        return PrimitiveType.VOID;
    }

    @Override
    public Type visitFunctionExpression(FunctionExpression expr) {
        // Similar to visitFunctionStatement but returns a FunctionType
        return new FunctionType(new ArrayList<>(), PrimitiveType.VOID); // Stub
    }

    @Override
    public Type visitGetExpression(GetExpression expr) {
        Type objectType = check(expr.object());
        String name = expr.name().lexeme();

        if (objectType instanceof ClassType classType) {
            ClassStatement stmt = state.classes.get(classType.getName());
            if (stmt != null) {
                Type fieldType = tc.findFieldType(stmt, name, classType.getName());
                if (fieldType != null) return fieldType;

                Type methodType = tc.findMethodType(stmt, name, classType.getName());
                if (methodType != null) return methodType;
            }
        }

        state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(expr.name(), "Property '" + name + "' not found on type " + objectType));
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitSetExpression(SetExpression expr) {
        Type objectType = check(expr.object());
        Type valueType = check(expr.value());
        String name = expr.name().lexeme();

        if (objectType instanceof ClassType classType) {
            ClassStatement stmt = state.classes.get(classType.getName());
            if (stmt != null) {
                Type fieldType = tc.findFieldType(stmt, name, classType.getName());
                if (fieldType != null) {
                    if (!tc.isCompatible(fieldType, valueType)) {
                        state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(expr.name(), "Incompatible types in property assignment: expected " + fieldType + ", got " + valueType));
                    }
                    return valueType;
                }
            }
        }

        state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(expr.name(), "Property '" + name + "' not found on type " + objectType));
        return valueType;
    }

    @Override
    public Type visitThisExpression(ThisExpression expr) {
        if (state.currentClass == null && state.currentType == null) {
            state.reporter.compileError(new com.terminalvelocitycabbage.tvscript.errors.CompileError(expr.keyword(), "Cannot use 'this' outside of a class or type."));
            return PrimitiveType.VOID;
        }
        if (state.currentClass != null) return tc.resolveClassType(state.currentClass.name().lexeme());
        return tc.resolveType(state.currentType.name());
    }

    @Override
    public Type visitNewExpression(NewExpression expr) {
        Type type = check(expr.callee());
        // Additional validation...
        return type;
    }

    @Override
    public Type visitCollectionLiteralExpression(CollectionLiteralExpression expr) {
        Type elementType = PrimitiveType.VOID;
        if (!expr.elements().isEmpty()) {
            elementType = check(expr.elements().get(0));
        }
        
        if (expr.collectionType().type() == com.terminalvelocitycabbage.tvscript.parsing.TokenType.LIST) return new CollectionType(com.terminalvelocitycabbage.tvscript.parsing.TokenType.LIST, List.of(elementType));
        if (expr.collectionType().type() == com.terminalvelocitycabbage.tvscript.parsing.TokenType.SET) return new CollectionType(com.terminalvelocitycabbage.tvscript.parsing.TokenType.SET, List.of(elementType));
        if (expr.collectionType().type() == com.terminalvelocitycabbage.tvscript.parsing.TokenType.MAP) return new CollectionType(com.terminalvelocitycabbage.tvscript.parsing.TokenType.MAP, List.of(PrimitiveType.VOID, PrimitiveType.VOID)); // Stub
        
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitSliceExpression(SliceExpression expr) {
        check(expr.object());
        if (expr.start() != null) check(expr.start());
        if (expr.end() != null) check(expr.end());
        return PrimitiveType.VOID; // Should return collection type
    }

    @Override
    public Type visitIndexSetExpression(IndexSetExpression expr) {
        check(expr.object());
        check(expr.index());
        Type valueType = check(expr.value());
        return valueType;
    }

    @Override
    public Type visitIndexExpression(IndexExpression expr) {
        check(expr.object());
        check(expr.index());
        return PrimitiveType.VOID;
    }

    @Override
    public Type visitTypeBinaryExpression(TypeBinaryExpression expr) {
        check(expr.left());
        return PrimitiveType.BOOLEAN;
    }

    @Override
    public Type visitSuperExpression(SuperExpression expr) {
        return PrimitiveType.VOID; // Stub
    }
}
