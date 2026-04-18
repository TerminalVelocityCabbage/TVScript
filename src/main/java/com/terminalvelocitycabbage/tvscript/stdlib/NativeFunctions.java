package com.terminalvelocitycabbage.tvscript.stdlib;

import com.terminalvelocitycabbage.tvscript.execution.TVScriptNativeFunction;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.terminalvelocitycabbage.tvscript.execution.TVScriptNativeFunction.Parameter;

public class NativeFunctions {

    public static final TVScriptNativeFunction CLOCK = new TVScriptNativeFunction(
            "clock",
            List.of(),
            TokenType.TYPE_DECIMAL,
            args -> (double) System.currentTimeMillis()
    );

    public static final TVScriptNativeFunction ABS = new TVScriptNativeFunction(
            "abs",
            List.of(new Parameter("n", TokenType.TYPE_DECIMAL)),
            TokenType.TYPE_DECIMAL,
            (Map<String, Object> args) -> {
                Object val = args.get("n");
                if (val instanceof Integer) return Math.abs((int) val);
                if (val instanceof Double) return Math.abs((double) val);
                return 0;
            }
    );

    private static final List<TVScriptNativeFunction> FUNCTIONS = List.of(
            CLOCK,
            ABS
    );

    public static Collection<TVScriptNativeFunction> getAll() {
        return FUNCTIONS;
    }
}
