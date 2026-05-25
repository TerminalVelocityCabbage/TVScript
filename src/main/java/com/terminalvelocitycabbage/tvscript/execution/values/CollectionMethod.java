package com.terminalvelocitycabbage.tvscript.execution.values;

import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.execution.TVScriptCallable;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import java.util.List;
import java.util.Map;

public abstract class CollectionMethod implements TVScriptCallable {
    private final String name;
    private final int arity;

    public CollectionMethod(String name, int arity) {
        this.name = name;
        this.arity = arity;
    }

    @Override
    public int arity() {
        return arity;
    }

    @Override
    public Object call(Interpreter interpreter, Map<String, Object> arguments, Token callToken) {
        List<Object> positional = interpreter.extractPositionalArguments(arguments, callToken, name);
        if (positional.size() != arity) {
            throw new com.terminalvelocitycabbage.tvscript.errors.RuntimeError(callToken,
                    "Method '" + name + "' expects " + arity + " argument(s), but got " + positional.size() + ".");
        }
        return invoke(interpreter, positional, callToken);
    }

    protected abstract Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken);
}
