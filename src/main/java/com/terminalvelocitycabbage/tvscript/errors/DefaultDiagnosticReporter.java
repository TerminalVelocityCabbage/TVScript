package com.terminalvelocitycabbage.tvscript.errors;

import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

public class DefaultDiagnosticReporter implements DiagnosticReporter {

    private boolean hadError = false;
    private boolean hadRuntimeError = false;
    private CompileError firstCompileError = null;

    @Override
    public void error(int line, String message) {
        if (firstCompileError == null) {
            firstCompileError = new CompileError(new Token(TokenType.NONE, "", null, line), message);
        }
        report(line, "", message);
    }

    @Override
    public void error(Token token, String message) {
        if (firstCompileError == null) {
            firstCompileError = new CompileError(token, message);
        }
        if (token.type() == TokenType.EOF) {
            report(token.line(), " at end", message);
        } else {
            report(token.line(), " at '" + token.lexeme() + "'", message);
        }
    }

    @Override
    public void compileError(CompileError error) {
        if (firstCompileError == null) {
            firstCompileError = error;
        }
        error(error.token, error.getMessage());
    }

    @Override
    public void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() + "\n[line " + error.token.line() + "]");
        hadRuntimeError = true;
    }

    @Override
    public void warning(Token token, String message) {
        if (token.type() == TokenType.EOF) {
            System.err.println("[line " + token.line() + "] Warning at end: " + message);
        } else {
            System.err.println("[line " + token.line() + "] Warning at '" + token.lexeme() + "': " + message);
        }
    }

    @Override
    public boolean hasError() {
        return hadError;
    }

    @Override
    public boolean hasRuntimeError() {
        return hadRuntimeError;
    }

    @Override
    public CompileError getFirstCompileError() {
        return firstCompileError;
    }

    @Override
    public void reset() {
        hadError = false;
        hadRuntimeError = false;
        firstCompileError = null;
    }

    private void report(int line, String where, String message) {
        System.err.println("[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }
}
