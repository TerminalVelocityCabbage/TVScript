package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Represents a standard library function implemented in Java.
 */
public class TVScriptNativeFunction implements TVScriptCallable {

    public record Parameter(String name, TokenType type) {}

    private final String name;
    private final List<Parameter> parameters;
    private final TokenType returnType;
    private final Function<Map<String, Object>, Object> implementation;

    public TVScriptNativeFunction(String name, List<Parameter> parameters, TokenType returnType, Function<Map<String, Object>, Object> implementation) {
        this.name = name;
        this.parameters = parameters;
        this.returnType = returnType;
        this.implementation = implementation;
    }

    public String name() {
        return name;
    }

    public List<Parameter> parameters() {
        return parameters;
    }

    public TokenType returnType() {
        return returnType;
    }

    public String signature() {
        StringBuilder builder = new StringBuilder();
        builder.append("native ").append(name).append("(");
        for (int i = 0; i < parameters.size(); i++) {
            Parameter parameter = parameters.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(typeName(parameter.type())).append(" ").append(parameter.name());
        }
        builder.append(") -> ").append(typeName(returnType));
        return builder.toString();
    }

    @Override
    public int arity() {
        return parameters.size();
    }

    @Override
    public Object call(Interpreter interpreter, Map<String, Object> arguments, Token callToken) {
        // Check for unexpected arguments
        for (String argName : arguments.keySet()) {
            boolean found = false;
            for (Parameter parameter : parameters) {
                if (parameter.name().equals(argName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new RuntimeError(callToken, "Expected " + signature() + ", but found unexpected argument '" + argName + "'.");
            }
        }

        // Check for missing arguments
        for (Parameter parameter : parameters) {
            if (!arguments.containsKey(parameter.name())) {
                throw new RuntimeError(callToken, "Expected " + signature() + ", but missing argument '" + parameter.name() + "'.");
            }

            Object value = arguments.get(parameter.name());
            if (!isCompatible(parameter.type(), value)) {
                throw new RuntimeError(callToken, "Expected " + signature() + ", but argument '" + parameter.name() + "' received type '" + runtimeTypeName(value) + "'.");
            }
        }

        return implementation.apply(arguments);
    }

    @Override
    public String toString() {
        return "<native function " + name + ">";
    }

    private boolean isCompatible(TokenType expectedType, Object value) {
        if (value == null) {
            return true;
        }

        return switch (expectedType) {
            case TYPE_INTEGER -> value instanceof Integer;
            case TYPE_DECIMAL -> value instanceof Double || value instanceof Integer;
            case TYPE_STRING -> value instanceof String;
            case TYPE_BOOLEAN -> value instanceof Boolean;
            default -> true;
        };
    }

    private String runtimeTypeName(Object value) {
        if (value == null) {
            return "none";
        }
        if (value instanceof Integer) {
            return "integer";
        }
        if (value instanceof Double) {
            return "decimal";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        return value.getClass().getSimpleName();
    }

    private String typeName(TokenType type) {
        return switch (type) {
            case TYPE_INTEGER -> "integer";
            case TYPE_DECIMAL -> "decimal";
            case TYPE_STRING -> "string";
            case TYPE_BOOLEAN -> "boolean";
            case TYPE_RANGE -> "range";
            case FUNCTION -> "function";
            case NONE -> "none";
            default -> type.name().toLowerCase();
        };
    }
}
