package com.terminalvelocitycabbage.tvscript.execution.values;

import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import java.util.List;
import java.util.Map;

public class TVMap implements ScriptValue {
    private final Map<Object, Object> values;
    private String keyType;
    private String valueType;

    public TVMap(Map<Object, Object> values) {
        this(values, null, null);
    }

    public TVMap(Map<Object, Object> values, String keyType, String valueType) {
        this.values = values;
        this.keyType = keyType;
        this.valueType = valueType;
    }

    public Map<Object, Object> values() {
        return values;
    }

    public String keyType() {
        return keyType;
    }

    public String valueType() {
        return valueType;
    }

    public void setTypes(String keyType, String valueType) {
        this.keyType = keyType;
        this.valueType = valueType;
    }

    @Override
    public Object get(Interpreter interpreter, Token name) {
        return switch (name.lexeme()) {
            case "size" -> values.size();
            case "put" -> new CollectionMethod("put", 2) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    ensureMapEntryType(interpreter, arguments.get(0), arguments.get(1), callToken);
                    values.put(arguments.get(0), arguments.get(1));
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
            case "containsKey" -> new CollectionMethod("containsKey", 1) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    return values.containsKey(arguments.get(0));
                }
            };
            case "containsValue" -> new CollectionMethod("containsValue", 1) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    return values.containsValue(arguments.get(0));
                }
            };
            case "get" -> new CollectionMethod("get", 1) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    return values.get(arguments.get(0));
                }
            };
            case "keys" -> new CollectionMethod("keys", 0) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    return new TVList(new java.util.ArrayList<>(values.keySet()), keyType);
                }
            };
            case "values" -> new CollectionMethod("values", 0) {
                @Override
                protected Object invoke(Interpreter interpreter, List<Object> arguments, Token callToken) {
                    return new TVList(new java.util.ArrayList<>(values.values()), valueType);
                }
            };
            default -> throw new RuntimeError(name, "Undefined property '" + name.lexeme() + "' on map.");
        };
    }

    @Override
    public Object getAt(Interpreter interpreter, Token bracket, Object index) {
        if (!values.containsKey(index)) {
            throw new RuntimeError(bracket, "Map key not found: " + interpreter.stringify(index) + ".");
        }
        return values.get(index);
    }

    @Override
    public void setAt(Interpreter interpreter, Token bracket, Object index, Object value) {
        ensureMapEntryType(interpreter, index, value, bracket);
        values.put(index, value);
    }

    private void ensureMapEntryType(Interpreter interpreter, Object key, Object value, Token token) {
        if (keyType != null) {
            if (!interpreter.matchesTypeName(key, keyType)) {
                throw new RuntimeError(token,
                        "Map expects keys of type '" + keyType + "' but found '" + interpreter.runtimeTypeName(key) + "'.");
            }
        }
        if (valueType != null) {
            if (!interpreter.matchesTypeName(value, valueType)) {
                throw new RuntimeError(token,
                        "Map expects values of type '" + valueType + "' but found '" + interpreter.runtimeTypeName(value) + "'.");
            }
        }
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
