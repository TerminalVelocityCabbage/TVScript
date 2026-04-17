package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.parsing.Token;

import java.util.HashMap;
import java.util.Map;

public class TVScriptInstance {
    private final TVScriptClass klass;
    private final Map<String, Object> fields = new HashMap<>();

    public TVScriptInstance(TVScriptClass klass) {
        this.klass = klass;
    }

    public TVScriptClass getType() {
        return klass;
    }

    public Object get(Token name) {
        if (fields.containsKey(name.lexeme())) {
            return fields.get(name.lexeme());
        }

        TVScriptFunction method = klass.findMethod(name.lexeme());
        if (method != null) return method.bind(this);

        throw new RuntimeError(name, "Undefined property '" + name.lexeme() + "'.");
    }

    public void set(Token name, Object value) {
        fields.put(name.lexeme(), value);
    }

    @Override
    public String toString() {
        return klass.name + " instance";
    }
}
