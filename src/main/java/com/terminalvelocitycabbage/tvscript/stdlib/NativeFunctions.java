package com.terminalvelocitycabbage.tvscript.stdlib;

import com.terminalvelocitycabbage.tvscript.execution.TVScriptNativeFunction;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class NativeFunctions {

    public record NativeFunctionDescriptor(String name, int numArguments, TokenType returnType, TVScriptNativeFunction function) {}

    private static final Map<String, NativeFunctionDescriptor> FUNCTIONS = new HashMap<>();

    static {
        register("clock", List.of(), TokenType.TYPE_DECIMAL, new TVScriptNativeFunction(List.of(), args -> (double) System.currentTimeMillis()));
        register("abs", List.of("n"), TokenType.TYPE_DECIMAL, (Map<String, Object> args) -> {
            Object val = args.get("n");
            if (val instanceof Integer) return Math.abs((int) val);
            if (val instanceof Double) return Math.abs((double) val);
            return 0;
        });
    }

    private static void register(String name, List<String> parameters, TokenType returnType, TVScriptNativeFunction function) {
        FUNCTIONS.put(name, new NativeFunctionDescriptor(name, parameters.size(), returnType, function));
    }

    private static void register(String name, List<String> parameters, TokenType returnType, Function<Map<String, Object>, Object> implementation) {
        register(name, parameters, returnType, new TVScriptNativeFunction(parameters, implementation));
    }

    public static Collection<NativeFunctionDescriptor> getAll() {
        return FUNCTIONS.values();
    }
}
