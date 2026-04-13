package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages variable bindings and scopes during execution.
 */
public class Environment {

    private final Environment enclosing;
    private final Map<String, VariableInfo> values = new HashMap<>();

    /**
     * Information about a variable.
     */
    public static class VariableInfo {
        public Object value;
        public final TokenType type;
        public final boolean isConst;

        public VariableInfo(Object value, TokenType type, boolean isConst) {
            this.value = value;
            this.type = type;
            this.isConst = isConst;
        }
    }

    /**
     * Creates a new global environment.
     */
    public Environment() {
        this.enclosing = null;
    }

    /**
     * Creates a new environment nested within the given enclosing environment.
     * @param enclosing The enclosing environment.
     */
    public Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    /**
     * Defines a new variable in the current environment.
     * @param name The name of the variable.
     * @param value The initial value.
     * @param type The type of the variable.
     * @param isConst Whether the variable is constant.
     */
    public void define(Token name, Object value, TokenType type, boolean isConst) {
        if (isAlreadyDefinedAnywhere(name.getLexeme())) {
            throw new RuntimeError(name, "Variable '" + name.getLexeme() + "' is already defined in this or an outer scope.");
        }
        value = castIfNeeded(type, value);
        values.put(name.getLexeme(), new VariableInfo(value, type, isConst));
    }

    private boolean isAlreadyDefinedAnywhere(String name) {
        if (values.containsKey(name)) return true;
        if (enclosing != null) return enclosing.isAlreadyDefinedAnywhere(name);
        return false;
    }

    /**
     * Retrieves the value of a variable.
     * @param name The name of the variable.
     * @return The value of the variable.
     */
    public Object get(Token name) {
        if (values.containsKey(name.getLexeme())) {
            return values.get(name.getLexeme()).value;
        }

        if (enclosing != null) return enclosing.get(name);

        throw new RuntimeError(name, "Undefined variable '" + name.getLexeme() + "'.");
    }

    /**
     * Retrieves information about a variable.
     * @param name The name of the variable.
     * @return The variable info.
     */
    public VariableInfo getInfo(Token name) {
        if (values.containsKey(name.getLexeme())) {
            return values.get(name.getLexeme());
        }

        if (enclosing != null) return enclosing.getInfo(name);

        throw new RuntimeError(name, "Undefined variable '" + name.getLexeme() + "'.");
    }

    /**
     * Assigns a new value to an existing variable.
     * @param name The name of the variable.
     * @param value The new value.
     */
    public void assign(Token name, Object value) {
        if (values.containsKey(name.getLexeme())) {
            VariableInfo info = values.get(name.getLexeme());
            if (info.isConst) {
                throw new RuntimeError(name, "Cannot assign to constant variable '" + name.getLexeme() + "'.");
            }
            value = castIfNeeded(info.type, value);
            checkType(name, info.type, value);
            info.value = value;
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name, "Undefined variable '" + name.getLexeme() + "'.");
    }

    private Object castIfNeeded(TokenType type, Object value) {
        if (type == TokenType.TYPE_DECIMAL && value instanceof Integer) {
            return (double) (int) value;
        }
        return value;
    }

    private void checkType(Token name, TokenType type, Object value) {
        if (value == null) return; // none is allowed for all types?
        
        switch (type) {
            case TYPE_INTEGER:
                if (!(value instanceof Integer)) {
                    throw new RuntimeError(name, "Expected integer value but got " + value.getClass().getSimpleName() + ".");
                }
                break;
            case TYPE_DECIMAL:
                if (!(value instanceof Double || value instanceof Integer)) {
                    throw new RuntimeError(name, "Expected decimal value but got " + value.getClass().getSimpleName() + ".");
                }
                break;
            case TYPE_STRING:
                if (!(value instanceof String)) {
                    throw new RuntimeError(name, "Expected string value but got " + value.getClass().getSimpleName() + ".");
                }
                break;
            case TYPE_BOOLEAN:
                if (!(value instanceof Boolean)) {
                    throw new RuntimeError(name, "Expected boolean value but got " + value.getClass().getSimpleName() + ".");
                }
                break;
            default:
                break;
        }
    }
}
