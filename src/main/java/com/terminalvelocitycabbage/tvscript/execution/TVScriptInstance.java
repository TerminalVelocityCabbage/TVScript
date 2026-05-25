package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.ast.Statement.VarStatement;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

import com.terminalvelocitycabbage.tvscript.execution.values.ScriptValue;

import java.util.HashMap;
import java.util.Map;

public class TVScriptInstance implements ScriptValue {
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

    @Override
    public Object get(Interpreter interpreter, Token name) {
        if (fields.containsKey(name.lexeme())) {
            // Check visibility for field
            for (VarStatement field : klass.fields) {
                if (field.name().lexeme().equals(name.lexeme())) {
                    klass.checkVisibility(field.visibility() != null ? field.visibility().type() : TokenType.PRIVATE,
                            klass.getScriptPath(), interpreter, "field " + name.lexeme());
                    break;
                }
            }
            return fields.get(name.lexeme());
        }

        TVScriptFunction method = klass.findMethod(name.lexeme());
        if (method != null) {
            klass.checkVisibility(method.getVisibility(), method.getScriptPath(), interpreter, "method " + name.lexeme());
            return method.bind(this);
        }

        Object nativeMember = klass.getNativeInstanceMember(this, name, interpreter);
        if (nativeMember != TVScriptClass.MISSING_MEMBER) {
            return nativeMember;
        }

        throw new RuntimeError(name, "Undefined property '" + name.lexeme() + "'.");
    }

    public Object get(Token name) {
        return get(this.interpreter, name);
    }

    public void defineField(Token name, Object value) {
        fields.put(name.lexeme(), value);
    }

    @Override
    public void set(Interpreter interpreter, Token name, Object value) {
        if (klass.setNativeInstanceProperty(this, name, value, interpreter)) {
            return;
        }

        // Check visibility for field
        for (VarStatement field : klass.fields) {
            if (field.name().lexeme().equals(name.lexeme())) {
                klass.checkVisibility(field.visibility() != null ? field.visibility().type() : TokenType.PRIVATE,
                        klass.getScriptPath(), interpreter, "field " + name.lexeme());
                break;
            }
        }

        if (klass.isType && fields.containsKey(name.lexeme())) {
            throw new RuntimeError(name, "Type fields are immutable.");
        }
        fields.put(name.lexeme(), value);
    }

    public void set(Token name, Object value) {
        set(this.interpreter, name, value);
    }

    @Override
    public String toString() {
        return klass.name + " instance";
    }
}
