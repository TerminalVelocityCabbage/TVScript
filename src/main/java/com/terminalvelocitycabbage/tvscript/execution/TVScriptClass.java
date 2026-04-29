package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.parsing.Token;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class TVScriptClass {
    final String name;
    final TVScriptClass superclass;
    final List<TVScriptTrait> traits;
    final List<Statement.VarStatement> fields;
    final Map<String, TVScriptFunction> methods;
    final Map<String, TVScriptFunction> staticMethods;
    final List<TVScriptFunction> constructors;
    final Map<String, List<TVScriptFunction>> operators;
    final boolean isType;

    public TVScriptClass(String name,
                         TVScriptClass superclass,
                         List<TVScriptTrait> traits,
                         List<Statement.VarStatement> fields,
                         Map<String, TVScriptFunction> methods,
                         Map<String, TVScriptFunction> staticMethods,
                         List<TVScriptFunction> constructors,
                         Map<String, List<TVScriptFunction>> operators,
                         boolean isType) {
        this.name = name;
        this.superclass = superclass;
        this.traits = traits;
        this.fields = fields;
        this.methods = methods;
        this.staticMethods = staticMethods;
        this.constructors = constructors;
        this.operators = operators;
        this.isType = isType;
    }

    public TVScriptInstance instantiate(Interpreter interpreter, Map<String, Object> arguments, Token callToken) {
        TVScriptInstance instance = new TVScriptInstance(this);

        // Evaluate and set initial field values (including superclasses)
        initializeFields(instance, interpreter);

        if (constructors.isEmpty()) {
            if (!isType) {
                throw new RuntimeError(callToken, "No matching constructor found for " + name + " with provided arguments.");
            }
            applyTypeArguments(instance, arguments, callToken);
            return instance;
        }

        TVScriptFunction constructor = findBestConstructor(arguments, callToken);
        constructor.bind(instance).call(interpreter, arguments, callToken);

        return instance;
    }

    private void initializeFields(TVScriptInstance instance, Interpreter interpreter) {
        if (superclass != null) {
            superclass.initializeFields(instance, interpreter);
        }

        for (Statement.VarStatement field : fields) {
            Object value = null;
            if (field.initializer() != null) {
                value = interpreter.evaluate(field.initializer());
            }
            instance.defineField(field.name(), value);
        }
    }

    private void applyTypeArguments(TVScriptInstance instance, Map<String, Object> arguments, Token callToken) {
        Map<String, Statement.VarStatement> fieldMap = new HashMap<>();
        for (Statement.VarStatement field : fields) {
            fieldMap.put(field.name().lexeme(), field);
        }

        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            Statement.VarStatement field = fieldMap.get(entry.getKey());
            if (field == null) {
                throw new RuntimeError(callToken, "Unknown field '" + entry.getKey() + "' for type '" + name + "'.");
            }
            instance.defineField(field.name(), entry.getValue());
        }
    }


    private TVScriptFunction findBestConstructor(Map<String, Object> arguments, Token callToken) {
        TVScriptFunction bestMatch = null;
        int minUnusedParameters = Integer.MAX_VALUE;

        for (TVScriptFunction constructor : constructors) {
            if (isCandidate(constructor, arguments)) {
                int unusedParams = constructor.arity() - arguments.size();
                if (unusedParams < minUnusedParameters) {
                    minUnusedParameters = unusedParams;
                    bestMatch = constructor;
                }
            }
        }

        if (bestMatch == null) {
            throw new RuntimeError(callToken, "No matching constructor found for " + name + " with provided arguments.");
        }

        return bestMatch;
    }

    private boolean isCandidate(TVScriptFunction constructor, Map<String, Object> arguments) {
        // All provided arguments must be in the parameter list
        for (String argName : arguments.keySet()) {
            boolean found = false;
            for (Statement.FunctionStatement.Parameter param : constructor.parameters()) {
                if (param.name().lexeme().equals(argName)) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }

        // All parameters without default value must be provided
        for (Statement.FunctionStatement.Parameter param : constructor.parameters()) {
            if (param.defaultValue() == null && !arguments.containsKey(param.name().lexeme())) {
                return false;
            }
        }

        return true;
    }

    TVScriptFunction findMethod(String name) {
        if (name.equals("constructor")) {
            // This is a bit of a hack to support super() calls, but it works for now
            // In a more complete implementation, we'd handle constructor matching properly
            return constructors.isEmpty() ? null : constructors.get(0);
        }

        if (methods.containsKey(name)) {
            return methods.get(name);
        }
        if (superclass != null) {
            return superclass.findMethod(name);
        }
        for (TVScriptTrait trait : traits) {
            TVScriptFunction method = trait.findMethod(name);
            if (method != null) return method;
        }
        return null;
    }

    TVScriptFunction findStaticMethod(String name) {
        return staticMethods.get(name);
    }

    TVScriptFunction findOperator(String operatorName, Object left, Object right) {
        List<TVScriptFunction> candidates = operators.get(operatorName);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        List<TVScriptFunction> matching = new ArrayList<>();
        for (TVScriptFunction function : candidates) {
            if (matches(function, left, right)) {
                matching.add(function);
            }
        }
        return matching.isEmpty() ? null : matching.get(0);
    }

    private boolean matches(TVScriptFunction function, Object left, Object right) {
        List<Statement.FunctionStatement.Parameter> parameters = function.parameters();
        if (parameters.size() == 1) {
            return isCompatibleArgument(parameters.get(0), right);
        }
        if (parameters.size() == 2) {
            return isCompatibleArgument(parameters.get(0), left) && isCompatibleArgument(parameters.get(1), right);
        }
        return false;
    }

    private boolean isCompatibleArgument(Statement.FunctionStatement.Parameter parameter, Object value) {
        Token type = parameter.type();
        return switch (type.type()) {
            case TYPE_INTEGER -> value instanceof Integer;
            case TYPE_DECIMAL -> value instanceof Double;
            case TYPE_STRING -> value instanceof String;
            case TYPE_BOOLEAN -> value instanceof Boolean;
            case NONE -> value == null;
            case IDENTIFIER -> {
                if (!(value instanceof TVScriptInstance instance)) {
                    yield false;
                }
                yield isSameOrSubclass(instance.getType(), type.lexeme());
            }
            default -> true;
        };
    }

    private boolean isSameOrSubclass(TVScriptClass actualType, String expectedTypeName) {
        TVScriptClass current = actualType;
        while (current != null) {
            if (current.name.equals(expectedTypeName)) {
                return true;
            }
            current = current.superclass;
        }
        return false;
    }

    @Override
    public String toString() {
        return name;
    }
}
