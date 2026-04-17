package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.parsing.Token;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class TVScriptClass {
    final String name;
    final TVScriptClass superclass;
    final List<TVScriptTrait> traits;
    final List<Statement.VarStatement> fields;
    final Map<String, TVScriptFunction> methods;
    final Map<String, TVScriptFunction> staticMethods;
    final List<TVScriptFunction> constructors;

    public TVScriptClass(String name, TVScriptClass superclass, List<TVScriptTrait> traits, List<Statement.VarStatement> fields, Map<String, TVScriptFunction> methods, Map<String, TVScriptFunction> staticMethods, List<TVScriptFunction> constructors) {
        this.name = name;
        this.superclass = superclass;
        this.traits = traits;
        this.fields = fields;
        this.methods = methods;
        this.staticMethods = staticMethods;
        this.constructors = constructors;
    }

    public TVScriptInstance instantiate(Interpreter interpreter, Map<String, Object> arguments, Token callToken) {
        TVScriptInstance instance = new TVScriptInstance(this);

        // Evaluate and set initial field values (including superclasses)
        initializeFields(instance, interpreter);

        // Find the best matching constructor
        TVScriptFunction constructor = findBestConstructor(arguments, callToken);

        // Call constructor
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
            instance.set(field.name(), value);
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

    @Override
    public String toString() {
        return name;
    }
}
