package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Represents a standard library function implemented in Java.
 */
public class TVScriptNativeFunction implements TVScriptCallable {

    private final List<String> parameters;
    private final Function<Map<String, Object>, Object> implementation;

    public TVScriptNativeFunction(List<String> parameters, Function<Map<String, Object>, Object> implementation) {
        this.parameters = parameters;
        this.implementation = implementation;
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
            for (String parameter : parameters) {
                if (parameter.equals(argName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new RuntimeError(callToken, "Unexpected argument '" + argName + "'.");
            }
        }

        // Check for missing arguments
        for (String parameter : parameters) {
            if (!arguments.containsKey(parameter)) {
                throw new RuntimeError(callToken, "Missing argument '" + parameter + "'.");
            }
        }

        return implementation.apply(arguments);
    }

    @Override
    public String toString() {
        return "<native function>";
    }
}
