package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;
import com.terminalvelocitycabbage.tvscript.ast.Statement.FunctionStatement;
import com.terminalvelocitycabbage.tvscript.ast.Statement.FunctionStatement.Parameter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a user-defined function or closure in TVScript.
 */
public class TVScriptFunction implements TVScriptCallable {

    private final String name;
    private final List<Parameter> parameters;
    private final Statement body;
    private final Environment closure;
    private final Token returnType;
    private final boolean isOverride;
    private final boolean isDefault;

    private final TokenType visibility;
    private final String scriptPath;

    public TVScriptFunction(String name, List<Parameter> parameters, Statement body, Environment closure, Token returnType, boolean isOverride, boolean isDefault, TokenType visibility, String scriptPath) {
        this.name = name;
        this.parameters = parameters;
        this.body = body;
        this.closure = closure;
        this.returnType = returnType;
        this.isOverride = isOverride;
        this.isDefault = isDefault;
        this.visibility = visibility;
        this.scriptPath = scriptPath;
    }

    public TVScriptFunction(Expression.FunctionExpression declaration, Environment closure, String scriptPath) {
        this(null, declaration.parameters(), declaration.body(), closure, declaration.returnType(), false, false, TokenType.PRIVATE, scriptPath);
    }

    public TVScriptFunction(FunctionStatement declaration, Environment closure, String scriptPath) {
        this(declaration.name() != null ? declaration.name().lexeme() : null, declaration.parameters(), declaration.body(), closure, declaration.returnType(), declaration.isOverride(), declaration.isDefault(), declaration.visibility() != null ? declaration.visibility().type() : TokenType.PRIVATE, scriptPath);
    }

    public TVScriptFunction bind(TVScriptInstance instance) {
        Environment environment = new Environment(closure);
        // define "this" in the closure
        // We use a dummy token for 'this'
        environment.define(new Token(TokenType.THIS, "this", null, 0), instance, TokenType.NONE, true);
        return new TVScriptFunction(name, parameters, body, environment, returnType, isOverride, isDefault, visibility, scriptPath);
    }

    public List<Parameter> parameters() {
        return parameters;
    }

    @Override
    public int arity() {
        return parameters.size();
    }

    @Override
    public Object call(Interpreter interpreter, Map<String, Object> arguments, Token callToken) {
        Environment environment = new Environment(closure);

        // Map positional arguments to parameter names
        Map<String, Object> finalArgs = new HashMap<>(arguments);
        for (int i = 0; i < parameters.size(); i++) {
            String positionalKey = "$" + i;
            if (finalArgs.containsKey(positionalKey)) {
                Object value = finalArgs.remove(positionalKey);
                String paramName = parameters.get(i).name().lexeme();
                if (finalArgs.containsKey(paramName)) {
                    throw new RuntimeError(callToken, "Duplicate argument for parameter '" + paramName + "'.");
                }
                finalArgs.put(paramName, value);
            }
        }

        // Check for unexpected arguments
        for (String argName : finalArgs.keySet()) {
            boolean found = false;
            for (Parameter parameter : parameters) {
                if (parameter.name().lexeme().equals(argName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new RuntimeError(callToken, "Unexpected argument '" + argName + "'.");
            }
        }

        for (int i = 0; i < parameters.size(); i++) {
            Parameter parameter = parameters.get(i);
            String paramName = parameter.name().lexeme();
            Object value = finalArgs.get(paramName);

            // Handle default value if argument not provided
            if (value == null && !finalArgs.containsKey(paramName)) {
                if (parameter.defaultValue() != null) {
                    value = interpreter.evaluate(parameter.defaultValue());
                } else {
                    // This should ideally be caught earlier, but let's be safe
                    throw new RuntimeError(parameter.name(), "Missing argument '" + paramName + "'.");
                }
            }

            environment.define(parameter.name(), value, parameter.type().type(), false);
        }

        try {
            interpreter.executeBlock(((Statement.BlockStatement) body).statements(), environment);
        } catch (Return returnValue) {
            return returnValue.value;
        }

        return null;
    }

    @Override
    public String toString() {
        if (name == null) return "<function>";
        return "<function " + name + ">";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TVScriptFunction that = (TVScriptFunction) o;
        
        // Match parameters by name and type
        if (parameters.size() != that.parameters.size()) return false;
        for (int i = 0; i < parameters.size(); i++) {
            Parameter p1 = parameters.get(i);
            Parameter p2 = that.parameters.get(i);
            if (!p1.name().lexeme().equals(p2.name().lexeme())) return false;
            if (p1.type().type() != p2.type().type()) return false;
        }
        
        // Match return type
        TokenType rt1 = returnType != null ? returnType.type() : TokenType.NONE;
        TokenType rt2 = that.returnType != null ? that.returnType.type() : TokenType.NONE;
        return rt1 == rt2;
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameters, returnType);
    }

    /**
     * Exception used to unwind the stack when a return statement is executed.
     */
    public static class Return extends RuntimeException {
        public final Object value;
        public Return(Object value) {
            super(null, null, false, false);
            this.value = value;
        }
    }

    public boolean isDefault() {
        return isDefault;
    }

    public boolean isOverride() {
        return isOverride;
    }

    public TokenType getVisibility() {
        return visibility;
    }

    public String getScriptPath() {
        return scriptPath;
    }
}
