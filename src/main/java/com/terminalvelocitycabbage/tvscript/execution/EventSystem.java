package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.parsing.Token;

import java.util.List;
import java.util.Map;

/**
 * Interface for the event system used by TVScript.
 */
public interface EventSystem {
    /**
     * Registers a new event definition.
     */
    void registerEvent(Statement.EventStatement stmt);

    /**
     * Registers a listener for an event.
     */
    void registerListener(Statement.OnStatement stmt, Interpreter interpreter, Environment closure);

    /**
     * Dispatches an event.
     */
    void dispatch(Token eventName, List<Expression.Argument> arguments, Interpreter interpreter, Environment environment);

    /**
     * Dispatches an event with pre-evaluated arguments.
     */
    void dispatch(String eventName, Map<String, Object> arguments);
}
