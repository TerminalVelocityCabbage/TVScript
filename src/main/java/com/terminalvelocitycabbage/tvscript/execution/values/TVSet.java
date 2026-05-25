package com.terminalvelocitycabbage.tvscript.execution.values;

import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import java.util.List;
import java.util.Set;

public class TVSet implements ScriptValue {
    private final Set<Object> values;
    private String elementType;

    public TVSet(Set<Object> values) {
        this(values, null);
    }

    public TVSet(Set<Object> values, String elementType) {
        this.values = values;
        this.elementType = elementType;
    }

    public Set<Object> values() {
        return values;
    }

    public String elementType() {
        return elementType;
    }

    public void setElementType(String elementType) {
        this.elementType = elementType;
    }

    @Override
    public Object get(Interpreter interpreter, Token name) {
        return switch (name.lexeme()) {
            case "size" -> values.size();
            case "add" -> new CollectionMethod("add", 1) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    ensureSetElementType(interpreter, arguments.get(0), callToken);
                    values.add(arguments.get(0));
                    return null;
                }
            };
            case "remove" -> new CollectionMethod("remove", 1) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    return values.remove(arguments.get(0));
                }
            };
            case "clear" -> new CollectionMethod("clear", 0) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    values.clear();
                    return null;
                }
            };
            case "contains" -> new CollectionMethod("contains", 1) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    return values.contains(arguments.get(0));
                }
            };
            default -> throw new RuntimeError(name, "Undefined property '" + name.lexeme() + "' on set.");
        };
    }

    private void ensureSetElementType(Interpreter interpreter, Object value, Token token) {
        if (elementType != null) {
            if (!interpreter.matchesTypeName(value, elementType)) {
                throw new RuntimeError(token,
                        "Set expects elements of type '" + elementType + "' but found '" + interpreter.runtimeTypeName(value) + "'.");
            }
        }
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
