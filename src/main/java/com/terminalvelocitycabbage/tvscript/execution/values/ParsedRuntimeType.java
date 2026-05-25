package com.terminalvelocitycabbage.tvscript.execution.values;

import java.util.List;

public record ParsedRuntimeType(String baseName, List<String> arguments) implements ScriptValue {
}
