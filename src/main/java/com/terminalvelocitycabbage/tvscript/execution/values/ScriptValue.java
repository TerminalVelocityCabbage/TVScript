package com.terminalvelocitycabbage.tvscript.execution.values;

import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;

/**
 * Common interface for all runtime values in TVScript that are not represented by standard Java types.
 */
public interface ScriptValue {

    default Object get(Interpreter interpreter, Token name) {
        throw new RuntimeError(name, "Object of type '" + interpreter.runtimeTypeName(this) + "' does not have property '" + name.lexeme() + "'.");
    }

    default void set(Interpreter interpreter, Token name, Object value) {
        throw new RuntimeError(name, "Object of type '" + interpreter.runtimeTypeName(this) + "' does not support property assignment.");
    }

    default Object getAt(Interpreter interpreter, Token bracket, Object index) {
        throw new RuntimeError(bracket, "Object of type '" + interpreter.runtimeTypeName(this) + "' does not support index access.");
    }

    default void setAt(Interpreter interpreter, Token bracket, Object index, Object value) {
        throw new RuntimeError(bracket, "Object of type '" + interpreter.runtimeTypeName(this) + "' does not support index assignment.");
    }
}
