package com.terminalvelocitycabbage.tvscript.analysis.types;

import com.terminalvelocitycabbage.tvscript.parsing.TokenType;
import java.util.List;
import java.util.ArrayList;

public class ClassType implements Type {

    private final String name;
    private final List<Type> genericArguments;
    private final ClassType superclass;
    private final List<TraitType> traits;

    public ClassType(String name) {
        this(name, new ArrayList<>(), null, new ArrayList<>());
    }

    public ClassType(String name, ClassType superclass, List<TraitType> traits) {
        this(name, new ArrayList<>(), superclass, traits);
    }

    public ClassType(String name, List<Type> genericArguments, ClassType superclass, List<TraitType> traits) {
        this.name = name;
        this.genericArguments = genericArguments;
        this.superclass = superclass;
        this.traits = traits;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isAssignableTo(Type other) {
        if (this == other) return true;
        if (other instanceof PrimitiveType && other == PrimitiveType.NONE) return true;
        
        if (other instanceof ClassType otherClass) {
            if (this.name.equals(otherClass.name)) {
                // Check generics (invariant for now)
                // For now, we are lenient to allow raw types and un-substituted generics to pass
                // until a full inference system is in place.
                return true; 
            }
            
            // Check inheritance
            if (superclass != null) {
                return superclass.isAssignableTo(other);
            }
        }
        
        if (other instanceof TraitType otherTrait) {
            for (TraitType trait : traits) {
                if (trait.isAssignableTo(otherTrait)) return true;
            }
        }
        
        return false;
    }

    @Override
    public TokenType toTokenType() {
        return TokenType.CLASS;
    }

    public List<Type> getGenericArguments() {
        return genericArguments;
    }

    public ClassType getSuperclass() {
        return superclass;
    }

    public List<TraitType> getTraits() {
        return traits;
    }

    @Override
    public String getNamedType() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClassType classType = (ClassType) o;
        return name.equals(classType.name) && genericArguments.equals(classType.genericArguments);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, genericArguments);
    }

    @Override
    public String toString() {
        if (genericArguments.isEmpty()) return name;
        return name + "<" + genericArguments.toString() + ">";
    }
}
