package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.parsing.Token;
import java.util.Map;
import java.util.function.Function;

/**
 * Represents a standard library function implemented in Java.
 */
public class TVScriptNativeFunction implements TVScriptCallable {

    private final int arity;
    private final Function<Map<String, Object>, Object> implementation;

    public TVScriptNativeFunction(int arity, Function<Map<String, Object>, Object> implementation) {
        this.arity = arity;
        this.implementation = implementation;
    }

    @Override
    public int arity() {
        return arity;
    }

    @Override
    public Object call(Interpreter interpreter, Map<String, Object> arguments, Token callToken) {
        return implementation.apply(arguments);
    }

    @Override
    public String toString() {
        return "<native function>";
    }
}
