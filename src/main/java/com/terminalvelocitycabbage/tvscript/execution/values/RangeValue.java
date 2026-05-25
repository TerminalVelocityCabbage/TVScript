package com.terminalvelocitycabbage.tvscript.execution.values;

public record RangeValue(int start, int end) implements ScriptValue {

    @Override
    public String toString() {
        return start + ".." + end;
    }
}
