package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.TVScript;
import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Expression.*;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.ast.Statement.*;
import com.terminalvelocitycabbage.tvscript.execution.values.*;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.util.AstUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatementInterpreter implements Statement.Visitor<Void> {

    private final Interpreter interpreter;

    public StatementInterpreter(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    @Override
    public Void visitExpressionStatement(ExpressionStatement stmt) {
        interpreter.evaluate(stmt.expression());
        return null;
    }

    @Override
    public Void visitBlockStatement(BlockStatement stmt) {
        interpreter.executeBlock(stmt.statements(), new Environment(interpreter.environment));
        return null;
    }

    @Override
    public Void visitIfStatement(IfStatement stmt) {
        // Special case for pattern matching alias: if obj is Type -> alias:
        if (stmt.condition() instanceof TypeBinaryExpression && ((TypeBinaryExpression) stmt.condition()).alias() != null) {
            TypeBinaryExpression tbe = (TypeBinaryExpression) stmt.condition();
            Object value = interpreter.evaluate(tbe.left());
            Object resolvedType = null;
            try {
                resolvedType = interpreter.environment.get(tbe.typeName());
            } catch (RuntimeError e) {}

            if (interpreter.checkType(value, tbe.typeName(), resolvedType)) {
                Environment previous = interpreter.environment;
                interpreter.environment = new Environment(interpreter.environment);
                try {
                    interpreter.environment.define(tbe.alias(), value, interpreter.inferType(value), true);
                    interpreter.execute(stmt.thenBranch());
                } finally {
                    interpreter.environment = previous;
                }
                return null;
            }
        }

        if (interpreter.isTruthy(stmt.keyword(), interpreter.evaluate(stmt.condition()))) {
            interpreter.execute(stmt.thenBranch());
        } else if (stmt.elseBranch() != null) {
            interpreter.execute(stmt.elseBranch());
        }
        return null;
    }

    // ... more visit methods will be moved here ...

    @Override
    public Void visitWhileStatement(WhileStatement stmt) {
        while (interpreter.isTruthy(stmt.keyword(), interpreter.evaluate(stmt.condition()))) {
            try {
                interpreter.execute(stmt.body());
            } catch (Interpreter.BreakException e) {
                break;
            } catch (Interpreter.ContinueException e) {
                // continue
            }
        }
        return null;
    }
    @Override
    public Void visitForStatement(ForStatement stmt) {
        Object iterableObj = interpreter.evaluate(stmt.range());

        Environment previous = interpreter.environment;
        try {
            if (iterableObj instanceof RangeValue range) {
                if (stmt.valueName() != null) {
                    throw new RuntimeError(stmt.keyword(), "Range iteration supports only a single loop variable.");
                }
                interpreter.executeRangeLoop(stmt, previous, range);
            } else if (iterableObj instanceof TVList list) {
                if (stmt.valueName() != null) {
                    throw new RuntimeError(stmt.keyword(), "List iteration supports only a single loop variable.");
                }
                interpreter.executeValueLoop(stmt, previous, list.values());
            } else if (iterableObj instanceof TVSet set) {
                if (stmt.valueName() != null) {
                    throw new RuntimeError(stmt.keyword(), "Set iteration supports only a single loop variable.");
                }
                interpreter.executeValueLoop(stmt, previous, set.values());
            } else if (iterableObj instanceof TVMap map) {
                interpreter.executeMapLoop(stmt, previous, map.values());
            } else {
                throw new RuntimeError(stmt.keyword(), "Expected range, list, set, or map in for loop.");
            }
        } catch (Interpreter.BreakException e) {
            // break
        } finally {
            interpreter.environment = previous;
        }
        return null;
    }
    @Override
    public Void visitMatchStatement(MatchStatement stmt) {
        Object condition = interpreter.evaluate(stmt.condition());

        for (MatchStatement.Case matchCase : stmt.cases()) {
            for (Expression pattern : matchCase.patterns()) {
                Object patternValue = interpreter.evaluate(pattern);
                if (interpreter.matchPattern(condition, patternValue)) {
                    interpreter.execute(matchCase.branch());
                    return null;
                }
            }
        }

        if (stmt.defaultBranch() != null) {
            interpreter.execute(stmt.defaultBranch());
        }

        return null;
    }
    @Override
    public Void visitFunctionStatement(FunctionStatement stmt) {
        TVScriptFunction function = new TVScriptFunction(stmt, interpreter.environment, interpreter.currentScriptPath);
        interpreter.environment.define(stmt.name(), function, TokenType.FUNCTION, true);

        // Register qualified name
        String scriptId = AstUtils.getScriptIdentifier(interpreter.currentScriptPath);
        interpreter.environment.define(scriptId + "." + stmt.name().lexeme(), function, TokenType.FUNCTION, true);

        return null;
    }
    @Override
    public Void visitClassStatement(ClassStatement stmt) {
        NativeClass nativeClassBinding = null;
        if (stmt.isNative()) {
            nativeClassBinding = interpreter.environment.getNativeClass(stmt.name().lexeme());
            if (nativeClassBinding == null) {
                throw new RuntimeError(stmt.name(), "Native class binding for '" + stmt.name().lexeme() + "' not found.");
            }
        }

        TVScriptClass superclass = null;
        if (stmt.superclass() != null) {
            Object obj = interpreter.environment.get(stmt.superclass());
            if (!(obj instanceof TVScriptClass)) {
                throw new RuntimeError(stmt.superclass(), "Superclass must be a class.");
            }
            superclass = (TVScriptClass) obj;
        }

        List<TVScriptTrait> traits = new ArrayList<>();
        for (Token traitToken : stmt.traits()) {
            Object obj = interpreter.environment.get(traitToken);
            if (!(obj instanceof TVScriptTrait)) {
                throw new RuntimeError(traitToken, "Only traits can be implemented.");
            }
            traits.add((TVScriptTrait) obj);
        }

        Map<String, TVScriptFunction> methods = new HashMap<>();
        for (FunctionStatement method : stmt.methods()) {
            methods.put(method.name().lexeme(), new TVScriptFunction(method, interpreter.environment, interpreter.currentScriptPath));
        }

        Map<String, TVScriptFunction> staticMethods = new HashMap<>();
        for (FunctionStatement method : stmt.staticMethods()) {
            staticMethods.put(method.name().lexeme(), new TVScriptFunction(method, interpreter.environment, interpreter.currentScriptPath));
        }

        Map<String, Object> classConstants = new HashMap<>();
        List<VarStatement> instanceFields = new ArrayList<>();
        for (VarStatement field : stmt.fields()) {
            if (field.isConst()) {
                Object constantValue = field.initializer() == null ? null : interpreter.evaluate(field.initializer());
                classConstants.put(field.name().lexeme(), constantValue);
            } else {
                instanceFields.add(field);
            }
        }

        if (nativeClassBinding != null) {
            for (NativeClass.ConstantBinding constantBinding : nativeClassBinding.constants().values()) {
                classConstants.putIfAbsent(constantBinding.name(), constantBinding.value());
            }
        }

        List<TVScriptFunction> constructors = new ArrayList<>();
        if (!stmt.isNative()) {
            for (FunctionStatement constructorStmt : stmt.constructors()) {
                constructors.add(new TVScriptFunction(constructorStmt, interpreter.environment, interpreter.currentScriptPath));
            }
        }

        TVScriptClass klass = new TVScriptClass(
                stmt.name().lexeme(),
                superclass,
                traits,
                instanceFields,
                methods,
                staticMethods,
                constructors,
                new HashMap<>(),
                false,
                nativeClassBinding,
                classConstants,
                stmt.visibility() != null ? stmt.visibility().type() : TokenType.PRIVATE,
                interpreter.currentScriptPath
        );
        interpreter.environment.define(stmt.name(), klass, TokenType.CLASS, true);

        // Register qualified name
        String scriptId = AstUtils.getScriptIdentifier(interpreter.currentScriptPath);
        interpreter.environment.define(scriptId + "." + stmt.name().lexeme(), klass, TokenType.CLASS, true);

        return null;
    }
    @Override
    public Void visitTraitStatement(TraitStatement stmt) {
        List<TVScriptTrait> traits = new ArrayList<>();
        for (Token traitToken : stmt.traits()) {
            Object obj = interpreter.environment.get(traitToken);
            if (!(obj instanceof TVScriptTrait)) {
                throw new RuntimeError(traitToken, "Only traits can be extended by other traits.");
            }
            traits.add((TVScriptTrait) obj);
        }

        Map<String, TVScriptFunction> traitMethods = new HashMap<>();
        for (FunctionStatement method : stmt.methods()) {
            TVScriptFunction function = new TVScriptFunction(method, interpreter.environment, interpreter.currentScriptPath);
            traitMethods.put(method.name().lexeme(), function);
        }

        Map<String, Object> constantFields = new HashMap<>();
        for (VarStatement field : stmt.fields()) {
            Object value = null;
            if (field.initializer() != null) {
                value = interpreter.evaluate(field.initializer());
            }
            constantFields.put(field.name().lexeme(), value);
        }

        TVScriptTrait trait = new TVScriptTrait(stmt.name().lexeme(), traits, stmt.fields(), traitMethods, constantFields);
        interpreter.environment.define(stmt.name(), trait, TokenType.TRAIT, true);
        return null;
    }
    @Override
    public Void visitTypeStatement(TypeStatement stmt) {
        List<TVScriptTrait> traits = new ArrayList<>();
        for (Token traitToken : stmt.traits()) {
            Object obj = interpreter.environment.get(traitToken);
            if (!(obj instanceof TVScriptTrait)) {
                throw new RuntimeError(traitToken, "Only traits can be implemented.");
            }
            traits.add((TVScriptTrait) obj);
        }

        Map<String, TVScriptFunction> methods = new HashMap<>();
        for (FunctionStatement method : stmt.methods()) {
            TVScriptFunction function = new TVScriptFunction(method, interpreter.environment, interpreter.currentScriptPath);
            methods.put(method.name().lexeme(), function);
        }

        Map<String, List<TVScriptFunction>> operators = new HashMap<>();
        for (FunctionStatement operator : stmt.operators()) {
            TVScriptFunction function = new TVScriptFunction(operator, interpreter.environment, interpreter.currentScriptPath);
            operators.computeIfAbsent(operator.name().lexeme(), k -> new ArrayList<>()).add(function);
        }

        TVScriptClass type = new TVScriptClass(
                stmt.name().lexeme(),
                null,
                traits,
                stmt.fields(),
                methods,
                new HashMap<>(),
                new ArrayList<>(),
                operators,
                true,
                null,
                new HashMap<>(),
                TokenType.PUBLIC, // Types are public for now
                interpreter.currentScriptPath
        );
        interpreter.environment.define(stmt.name(), type, TokenType.CLASS, true);
        return null;
    }
    @Override public Void visitConstraintStatement(ConstraintStatement stmt) { return null; }
    @Override
    public Void visitEventStatement(EventStatement stmt) {
        interpreter.eventSystem.registerEvent(stmt);
        return null;
    }
    @Override
    public Void visitOnStatement(OnStatement stmt) {
        interpreter.eventSystem.registerListener(stmt, interpreter, interpreter.environment);
        return null;
    }
    @Override
    public Void visitDispatchStatement(DispatchStatement stmt) {
        interpreter.eventSystem.dispatch(stmt.eventName(), stmt.arguments(), interpreter, interpreter.environment);
        return null;
    }
    @Override
    public Void visitReturnStatement(ReturnStatement stmt) {
        Object value = null;
        if (stmt.value() != null) value = interpreter.evaluate(stmt.value());
        throw new TVScriptFunction.Return(value);
    }
    @Override
    public Void visitBreakStatement(BreakStatement stmt) {
        throw new Interpreter.BreakException();
    }
    @Override
    public Void visitContinueStatement(ContinueStatement stmt) {
        throw new Interpreter.ContinueException();
    }
    @Override
    public Void visitPrintStatement(PrintStatement stmt) {
        Object value = interpreter.evaluate(stmt.expression());
        System.out.println(interpreter.stringify(value));
        return null;
    }
    @Override
    public Void visitVarStatement(VarStatement stmt) {
        Object value = null;
        if (stmt.initializer() != null) {
            value = interpreter.evaluate(stmt.initializer());
        }

        TokenType type;
        if (stmt.type().type() == TokenType.VAR || stmt.type().type() == TokenType.CONST) {
            type = interpreter.inferType(value);
            if (type == null) {
                throw new RuntimeError(stmt.name(), "Cannot infer type from 'none'.");
            }
        } else {
            type = stmt.type().type();
        }

        interpreter.applyDeclaredCollectionTypes(stmt.type(), value, stmt.name());
        interpreter.environment.define(stmt.name(), value, type, stmt.isConst());

        // Wait, how do I check if it's a global variable?
        // Interpreter doesn't have an easy way to check if environment is global.
        // But usually top-level vars are defined in the main script environment.
        // For TVScript, globals are usually in the outer-most environment.
        // Let's assume if environment has no enclosing (other than configuredGlobals), it's global.
        if (interpreter.environment.getEnclosing() == interpreter.configuredGlobals || interpreter.environment.getEnclosing() == null) {
            String scriptId = AstUtils.getScriptIdentifier(interpreter.currentScriptPath);
            interpreter.environment.define(scriptId + "." + stmt.name().lexeme(), value, type, stmt.isConst());
        }

        return null;
    }
    @Override
    public Void visitImportStatement(ImportStatement stmt) {
        String modulePath = stmt.module().lexeme();
        Map<String, String> currentImports = interpreter.scriptImports.computeIfAbsent(interpreter.currentScriptPath, k -> new HashMap<>());
        Map<String, String> currentQualifiedImports = interpreter.scriptQualifiedImports.computeIfAbsent(interpreter.currentScriptPath, k -> new HashMap<>());

        if (stmt.items().isEmpty()) {
            // Whole module OR single item import
            int lastDot = modulePath.lastIndexOf('.');
            if (lastDot != -1) {
                String fullPath = modulePath;
                if (interpreter.environment.isAlreadyDefinedAnywhere(fullPath)) {
                    String alias = stmt.alias() != null ? stmt.alias().lexeme() : modulePath.substring(lastDot + 1);
                    currentImports.put(alias, fullPath);
                    return null;
                }
            }

            // Whole module import: import a.b.c as d
            String alias = stmt.alias() != null ? stmt.alias().lexeme() : modulePath.substring(modulePath.lastIndexOf('.') + 1);
            currentQualifiedImports.put(alias, modulePath);
        } else {
            // Selective import: import a.b.c : [x as y, z]
            for (ImportStatement.ImportItem item : stmt.items()) {
                String originalName = item.name().lexeme();
                String fullPath = modulePath + "." + originalName;
                String alias = item.alias() != null ? item.alias().lexeme() : originalName;
                currentImports.put(alias, fullPath);
            }
        }
        return null;
    }
    @Override
    public Void visitPassStatement(PassStatement stmt) {
        return null;
    }
}
