package com.terminalvelocitycabbage.tvscript.analysis.types;

import com.terminalvelocitycabbage.tvscript.parsing.TokenType;
import java.util.List;

public class CollectionType implements Type {

    private final TokenType kind; // LIST, SET, or MAP
    private final List<Type> elementTypes;

    public CollectionType(TokenType kind, List<Type> elementTypes) {
        this.kind = kind;
        this.elementTypes = elementTypes;
    }

    @Override
    public String getName() {
        return kind.name().toLowerCase();
    }

    @Override
    public boolean isAssignableTo(Type other) {
        if (this == other) return true;
        if (other instanceof CollectionType otherColl) {
            if (this.kind != otherColl.kind) return false;
            if (this.elementTypes.size() != otherColl.elementTypes.size()) return false;
            for (int i = 0; i < elementTypes.size(); i++) {
                Type thisElem = elementTypes.get(i);
                Type otherElem = otherColl.elementTypes.get(i);
                
                // Allow assignment from void/none elements (empty collection)
                if (thisElem == PrimitiveType.VOID || thisElem == PrimitiveType.NONE) continue;
                
                // Collections are invariant in TVScript for now
                if (!thisElem.equals(otherElem)) return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public TokenType toTokenType() {
        return kind;
    }

    public List<Type> getElementTypes() {
        return elementTypes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CollectionType that = (CollectionType) o;
        return kind == that.kind && elementTypes.equals(that.elementTypes);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(kind, elementTypes);
    }

    @Override
    public String toString() {
        return kind.name() + "<" + elementTypes.toString() + ">";
    }
}
