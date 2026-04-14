package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.parsing.Token;
import java.util.List;
import java.util.Map;

/**
 * Interface for anything that can be called in TVScript.
 */
public interface TVScriptCallable {
    /**
     * @return The number of parameters this callable expects.
     */
    int arity();

    /**
     * Calls the callable.
     * @param interpreter The current interpreter.
     * @param arguments The named arguments passed to the call.
     * @param callToken The token where the call occurred (e.g., '(').
     * @return The return value of the call.
     */
    Object call(Interpreter interpreter, Map<String, Object> arguments, Token callToken);
}
