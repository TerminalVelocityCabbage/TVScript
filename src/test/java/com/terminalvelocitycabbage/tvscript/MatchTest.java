package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.parsing.Scanner;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.parsing.Parser;
import com.terminalvelocitycabbage.tvscript.analysis.TypeChecker;
import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import com.terminalvelocitycabbage.tvscript.errors.DefaultDiagnosticReporter;
import com.terminalvelocitycabbage.tvscript.errors.DiagnosticReporter;
import static org.junit.jupiter.api.Assertions.*;

class MatchTest {
    private final DiagnosticReporter reporter = new DefaultDiagnosticReporter();

    private String run(String source) {
        reporter.reset();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(out));
        try {
            Scanner scanner = new Scanner(source, reporter);
            List<Token> tokens = scanner.scanTokens();
            Parser parser = new Parser(tokens, reporter);
            List<Statement> statements = parser.parseStatements();
            if (reporter.hasError()) {
                throw new RuntimeException("Syntax Error");
            }
            TypeChecker typeChecker = new TypeChecker(reporter);
            typeChecker.check(statements);
            if (reporter.hasError()) {
                throw new RuntimeException("Compile Error");
            }
            Interpreter interpreter = new Interpreter(reporter);
            interpreter.interpret(statements);
        } finally {
            System.setOut(originalOut);
        }
        return out.toString().trim();
    }

    @Test
    void testBasicMatchStatement() {
        String source = """
            integer a = 2
            match a:
                1: print "one"
                2: print "two"
                default: print "other"
            """;
        assertEquals("two", run(source));
    }

    @Test
    void testMatchMultiplePatterns() {
        String source = """
            integer a = 1
            match a:
                1, 2: print "one or two"
                default: print "other"
            """;
        assertEquals("one or two", run(source));
        
        source = """
            integer a = 2
            match a:
                1, 2: print "one or two"
                default: print "other"
            """;
        assertEquals("one or two", run(source));
    }

    @Test
    void testMatchRange() {
        String source = """
            integer a = 5
            match a:
                1..10: print "in range"
                default: print "out of range"
            """;
        assertEquals("in range", run(source));
        
        source = """
            integer a = 15
            match a:
                1..10: print "in range"
                default: print "out of range"
            """;
        assertEquals("out of range", run(source));
    }

    @Test
    void testMatchBlock() {
        String source = """
            integer a = 1
            match a:
                1:
                    print "line 1"
                    print "line 2"
                default: print "other"
            """;
        assertEquals("line 1\nline 2", run(source).replace("\r\n", "\n"));
    }

    @Test
    void testMatchExpression() {
        String source = """
            integer a = 2
            string s = match a:
                1: "one"
                2: "two"
                default: "other"
            print s
            """;
        assertEquals("two", run(source));
    }

    @Test
    void testMatchExhaustivenessError() {
        // match statements must be exhaustive. 
        // For integers, it's impossible to cover all values without default.
        String source = """
            integer a = 1
            match a:
                1: print "one"
            """;
        // Should throw an error in TypeChecker
        assertThrows(RuntimeException.class, () -> run(source));
    }

    @Test
    void testMatchIncompatibleTypesInExpression() {
        String source = """
            integer a = 1
            var x = match a:
                1: "one"
                2: 2
                default: none
            """;
        assertThrows(RuntimeException.class, () -> run(source));
    }
}
