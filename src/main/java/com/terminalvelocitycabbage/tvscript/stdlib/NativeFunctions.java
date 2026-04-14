package com.terminalvelocitycabbage.tvscript.stdlib;

import com.terminalvelocitycabbage.tvscript.execution.TVScriptNativeFunction;
import java.util.Map;

public class NativeFunctions {

    public static final TVScriptNativeFunction ABS = new TVScriptNativeFunction(1, (Map<String, Object> args) -> {
        Object val = args.get("n");
        if (val instanceof Integer) return Math.abs((int) val);
        if (val instanceof Double) return Math.abs((double) val);
        return 0;
    });
}
