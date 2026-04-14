package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a user-defined function or closure in TVScript.
 */
public class TVScriptFunction implements TVScriptCallable {

    private final String name;
    private final List<Statement.FunctionStatement.Parameter> parameters;
    private final Statement body;
    private final Environment closure;
    private final Token returnType;

    public TVScriptFunction(String name, List<Statement.FunctionStatement.Parameter> parameters, Statement body, Environment closure, Token returnType) {
        this.name = name;
        this.parameters = parameters;
        this.body = body;
        this.closure = closure;
        this.returnType = returnType;
    }

    public TVScriptFunction(Expression.FunctionExpression declaration, Environment closure) {
        this(null, declaration.parameters(), declaration.body(), closure, declaration.returnType());
    }

    public TVScriptFunction(Statement.FunctionStatement declaration, Environment closure) {
        this(declaration.name().lexeme(), declaration.parameters(), declaration.body(), closure, declaration.returnType());
    }

    @Override
    public int arity() {
        return parameters.size();
    }

    @Override
    public Object call(Interpreter interpreter, Map<String, Object> arguments, Token callToken) {
        Environment environment = new Environment(closure);

        // Check for unexpected arguments
        for (String argName : arguments.keySet()) {
            boolean found = false;
            for (Statement.FunctionStatement.Parameter parameter : parameters) {
                if (parameter.name().lexeme().equals(argName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new com.terminalvelocitycabbage.tvscript.errors.RuntimeError(callToken, "Unexpected argument '" + argName + "'.");
            }
        }

        for (int i = 0; i < parameters.size(); i++) {
            Statement.FunctionStatement.Parameter parameter = parameters.get(i);
            String paramName = parameter.name().lexeme();
            Object value = arguments.get(paramName);

            // Handle default value if argument not provided
            if (value == null && !arguments.containsKey(paramName)) {
                if (parameter.defaultValue() != null) {
                    value = interpreter.evaluate(parameter.defaultValue());
                } else {
                    // This should ideally be caught earlier, but let's be safe
                    throw new com.terminalvelocitycabbage.tvscript.errors.RuntimeError(parameter.name(), "Missing argument '" + paramName + "'.");
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
            Statement.FunctionStatement.Parameter p1 = parameters.get(i);
            Statement.FunctionStatement.Parameter p2 = that.parameters.get(i);
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
}
