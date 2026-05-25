package com.terminalvelocitycabbage.tvscript.errors;

import com.terminalvelocitycabbage.tvscript.parsing.Token;

public interface DiagnosticReporter {
    void error(int line, String message);
    void error(Token token, String message);
    void compileError(CompileError error);
    void runtimeError(RuntimeError error);
    void warning(Token token, String message);
    boolean hasError();
    boolean hasRuntimeError();
    CompileError getFirstCompileError();
    void reset();
}
