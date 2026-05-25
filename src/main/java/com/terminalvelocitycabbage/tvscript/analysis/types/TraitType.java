package com.terminalvelocitycabbage.tvscript.analysis.types;

import com.terminalvelocitycabbage.tvscript.parsing.TokenType;
import java.util.List;
import java.util.ArrayList;

public class TraitType implements Type {

    private final String name;
    private final List<Type> genericArguments;
    private final List<TraitType> supertraits;

    public TraitType(String name) {
        this(name, new ArrayList<>(), new ArrayList<>());
    }

    public TraitType(String name, List<TraitType> supertraits) {
        this(name, new ArrayList<>(), supertraits);
    }

    public TraitType(String name, List<Type> genericArguments, List<TraitType> supertraits) {
        this.name = name;
        this.genericArguments = genericArguments;
        this.supertraits = supertraits;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isAssignableTo(Type other) {
        if (this == other) return true;
        
        if (other instanceof TraitType otherTrait) {
            if (this.name.equals(otherTrait.name)) {
                // Check generics
                if (this.genericArguments.size() != otherTrait.genericArguments.size()) return false;
                for (int i = 0; i < this.genericArguments.size(); i++) {
                    if (!this.genericArguments.get(i).equals(otherTrait.genericArguments.get(i))) return false;
                }
                return true;
            }
            
            for (TraitType supertrait : supertraits) {
                if (supertrait.isAssignableTo(otherTrait)) return true;
            }
        }
        
        return false;
    }

    @Override
    public TokenType toTokenType() {
        return TokenType.TRAIT;
    }

    @Override
    public String getNamedType() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TraitType traitType = (TraitType) o;
        return name.equals(traitType.name) && genericArguments.equals(traitType.genericArguments);
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
