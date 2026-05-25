package com.terminalvelocitycabbage.tvscript.execution.values;

import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import java.util.List;

public class TVList implements ScriptValue {
    private final List<Object> values;
    private String elementType;

    public TVList(List<Object> values) {
        this(values, null);
    }

    public TVList(List<Object> values, String elementType) {
        this.values = values;
        this.elementType = elementType;
    }

    public List<Object> values() {
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
                    ensureListElementType(interpreter, arguments.get(0), callToken);
                    values.add(arguments.get(0));
                    return null;
                }
            };
            case "insert" -> new CollectionMethod("insert", 2) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    if (!(arguments.get(0) instanceof Integer)) {
                        throw new RuntimeError(callToken, "First argument to 'insert' must be an integer.");
                    }
                    ensureListElementType(interpreter, arguments.get(1), callToken);
                    int index = (int) arguments.get(0);
                    int resolvedIndex = resolveListInsertIndex(callToken, index, values.size());
                    values.add(resolvedIndex, arguments.get(1));
                    return null;
                }
            };
            case "remove" -> new CollectionMethod("remove", 1) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    if (!(arguments.get(0) instanceof Integer)) {
                        throw new RuntimeError(callToken, "Argument to 'remove' must be an integer.");
                    }
                    int index = (int) arguments.get(0);
                    int resolvedIndex = resolveListIndex(callToken, index, values.size());
                    return values.remove(resolvedIndex);
                }
            };
            case "clear" -> new CollectionMethod("clear", 0) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    values.clear();
                    return null;
                }
            };
            case "pop" -> new CollectionMethod("pop", 0) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    if (values.isEmpty()) {
                        throw new RuntimeError(callToken, "Cannot pop from an empty list.");
                    }
                    return values.remove(values.size() - 1);
                }
            };
            case "reverse" -> new CollectionMethod("reverse", 0) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    java.util.Collections.reverse(values);
                    return null;
                }
            };
            case "contains" -> new CollectionMethod("contains", 1) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    return values.contains(arguments.get(0));
                }
            };
            default -> throw new RuntimeError(name, "Undefined property '" + name.lexeme() + "' on list.");
        };
    }

    @Override
    public Object getAt(Interpreter interpreter, Token bracket, Object index) {
        if (!(index instanceof Integer)) {
            throw new RuntimeError(bracket, "List index must be an integer.");
        }
        int resolvedIndex = resolveListIndex(bracket, (int) index, values.size());
        return values.get(resolvedIndex);
    }

    @Override
    public void setAt(Interpreter interpreter, Token bracket, Object index, Object value) {
        if (!(index instanceof Integer)) {
            throw new RuntimeError(bracket, "List index must be an integer.");
        }
        int resolvedIndex = resolveListIndex(bracket, (int) index, values.size());
        ensureListElementType(interpreter, value, bracket);
        values.set(resolvedIndex, value);
    }

    private void ensureListElementType(Interpreter interpreter, Object value, Token token) {
        if (elementType != null) {
            if (!interpreter.matchesTypeName(value, elementType)) {
                throw new RuntimeError(token,
                        "List expects elements of type '" + elementType + "' but found '" + interpreter.runtimeTypeName(value) + "'.");
            }
        }
    }

    public static int resolveListIndex(Token token, int index, int size) {
        int resolvedIndex = index < 0 ? size + index : index;
        if (resolvedIndex < 0 || resolvedIndex >= size) {
            throw new RuntimeError(token, "List index " + index + " is out of bounds for size " + size + ".");
        }
        return resolvedIndex;
    }

    private int resolveListInsertIndex(Token token, int index, int size) {
        int resolvedIndex = index < 0 ? size + index : index;
        if (resolvedIndex < 0 || resolvedIndex > size) {
            throw new RuntimeError(token, "List index " + index + " is out of bounds for size " + size + ".");
        }
        return resolvedIndex;
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
