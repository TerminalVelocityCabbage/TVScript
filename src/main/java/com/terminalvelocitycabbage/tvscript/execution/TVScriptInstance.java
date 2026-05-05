package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.parsing.Token;

import java.util.HashMap;
import java.util.Map;

public class TVScriptInstance {
    private final TVScriptClass klass;
    private final Interpreter interpreter;
    private final Object nativeObject;
    private final Map<String, Object> fields = new HashMap<>();

    public TVScriptInstance(TVScriptClass klass) {
        this(klass, null, null);
    }

    public TVScriptInstance(TVScriptClass klass, Interpreter interpreter, Object nativeObject) {
        this.klass = klass;
        this.interpreter = interpreter;
        this.nativeObject = nativeObject;
    }

    public TVScriptClass getType() {
        return klass;
    }

    public Object getNativeObject() {
        return nativeObject;
    }

    public Object get(Token name) {
        if (fields.containsKey(name.lexeme())) {
            return fields.get(name.lexeme());
        }

        TVScriptFunction method = klass.findMethod(name.lexeme());
        if (method != null) return method.bind(this);

        if (interpreter != null) {
            Object nativeMember = klass.getNativeInstanceMember(this, name, interpreter);
            if (nativeMember != TVScriptClass.MISSING_MEMBER) {
                return nativeMember;
            }
        }

        throw new RuntimeError(name, "Undefined property '" + name.lexeme() + "'.");
    }

    public void defineField(Token name, Object value) {
        fields.put(name.lexeme(), value);
    }

    public void set(Token name, Object value) {
        if (interpreter != null && klass.setNativeInstanceProperty(this, name, value, interpreter)) {
            return;
        }
        if (klass.isType && fields.containsKey(name.lexeme())) {
            throw new RuntimeError(name, "Type fields are immutable.");
        }
        fields.put(name.lexeme(), value);
    }

    @Override
    public String toString() {
        return klass.name + " instance";
    }
}
