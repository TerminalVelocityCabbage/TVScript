package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.errors.DiagnosticReporter;

import java.util.List;

/**
 * The result of a compilation process.
 */
public record CompilationResult(
    List<Statement> statements,
    DiagnosticReporter reporter
) {
    public boolean hasErrors() {
        return reporter.hasError();
    }
}
