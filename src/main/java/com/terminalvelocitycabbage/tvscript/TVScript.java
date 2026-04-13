package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.analysis.TypeChecker;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.errors.CompileError;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.parsing.Parser;
import com.terminalvelocitycabbage.tvscript.parsing.Scanner;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.TokenType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Main entry point for the TVScript language.
 */
public class TVScript {

    public static boolean hadError = false;
    public static boolean hadRuntimeError = false;
    private static final Interpreter interpreter = new Interpreter();

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: tvscript [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));

        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        for (;;) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null) break;
            run(line);
            hadError = false;
        }
    }

    public static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        List<Statement> statements = parser.parseStatements();

        // Stop if there was a syntax error.
        if (hadError) return;

        TypeChecker typeChecker = new TypeChecker();
        typeChecker.check(statements);

        // Stop if there was a static analysis error.
        if (hadError) return;

        try {
            interpreter.interpret(statements);
        } catch (RuntimeError error) {
            // Already reported.
        }
    }

    /**
     * Reports an error at a specific line.
     * @param line The line number.
     * @param message The error message.
     */
    public static void error(int line, String message) {
        report(line, "", message);
    }

    /**
     * Reports an error at a specific token.
     * @param token The token where the error occurred.
     * @param message The error message.
     */
    public static void error(Token token, String message) {
        if (token.getType() == TokenType.EOF) {
            report(token.getLine(), " at end", message);
        } else {
            report(token.getLine(), " at '" + token.getLexeme() + "'", message);
        }
    }

    /**
     * Reports a runtime error.
     * @param error The runtime error.
     */
    public static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() +
                "\n[line " + error.token.getLine() + "]");
        hadRuntimeError = true;
    }

    /**
     * Reports a compilation error.
     * @param error The compilation error.
     */
    public static void compileError(CompileError error) {
        if (error.token.getType() == TokenType.EOF) {
            report(error.token.getLine(), " at end", error.getMessage());
        } else {
            report(error.token.getLine(), " at '" + error.token.getLexeme() + "'", error.getMessage());
        }
    }

    /**
     * Reports a warning.
     * @param token The token where the warning occurred.
     * @param message The warning message.
     */
    public static void warning(Token token, String message) {
        if (token.getType() == TokenType.EOF) {
            System.err.println("[line " + token.getLine() + "] Warning at end: " + message);
        } else {
            System.err.println("[line " + token.getLine() + "] Warning at '" + token.getLexeme() + "': " + message);
        }
    }

    public static void reset() {
        hadError = false;
        hadRuntimeError = false;
        interpreter.reset();
    }

    private static void report(int line, String where, String message) {
        System.err.println("[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }
}
