package com.terminalvelocitycabbage.tvscript.execution;

import com.terminalvelocitycabbage.tvscript.ast.Statement;
import java.util.List;
import java.util.Map;

public class TVScriptTrait {
    final String name;
    final List<TVScriptTrait> supertraits;
    final List<Statement.VarStatement> fields;
    final Map<String, TVScriptFunction> methods;
    final Map<String, Object> constantFields;

    public TVScriptTrait(String name, List<TVScriptTrait> supertraits, List<Statement.VarStatement> fields, Map<String, TVScriptFunction> methods, Map<String, Object> constantFields) {
        this.name = name;
        this.supertraits = supertraits;
        this.fields = fields;
        this.methods = methods;
        this.constantFields = constantFields;
    }

    public Object getConstantField(String name) {
        if (constantFields.containsKey(name)) {
            return constantFields.get(name);
        }
        for (TVScriptTrait supertrait : supertraits) {
            Object value = supertrait.getConstantField(name);
            if (value != null) return value;
        }
        return null;
    }

    public TVScriptFunction findMethod(String name) {
        if (methods.containsKey(name)) {
            return methods.get(name);
        }
        for (TVScriptTrait supertrait : supertraits) {
            TVScriptFunction method = supertrait.findMethod(name);
            if (method != null) return method;
        }
        return null;
    }

    public String getName() {
        return name;
    }

    public List<TVScriptTrait> getSupertraits() {
        return supertraits;
    }

    public List<Statement.VarStatement> getFields() {
        return fields;
    }

    public Map<String, TVScriptFunction> getMethods() {
        return methods;
    }

    @Override
    public String toString() {
        return name;
    }
}
