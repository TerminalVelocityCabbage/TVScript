package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.ast.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatementTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    private void run(String source) {
        TVScript.hadError = false;
        TVScript.hadRuntimeError = false;

        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        List<Statement> statements = parser.parseStatements();
        if (TVScript.hadError) throw new RuntimeException("Parse error");

        TypeChecker typeChecker = new TypeChecker();
        typeChecker.check(statements);
        if (TVScript.hadError) throw new RuntimeException("Type check error");

        Interpreter interpreter = new Interpreter();
        interpreter.interpret(statements);
    }

    @Test
    void testPrintStatement() {
        run("print 1 + 2");
        assertEquals("3\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testVariableDeclaration() {
        run("""
            integer a = 10
            print a""");
        assertEquals("10\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testVarKeyword() {
        run("""
            var a = 10
            print a""");
        assertEquals("10\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testAssignment() {
        run("""
            integer a = 10
            a = 20
            print a""");
        assertEquals("20\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testBlockScope() {
        run("""
            integer a = 10
            if true:
                integer b = 20
                print a + b""");
        assertEquals("30\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testNoShadowingError() {
        // Redefining in outer scope is an error
        assertThrows(RuntimeException.class, () -> run("""
            integer a = 10
            integer a = 20"""));
        
        // Shadowing in inner scope is also an error in TVScript
        assertThrows(RuntimeException.class, () -> run("""
            integer a = 10
            if true:
                integer a = 20"""));
    }

    @Test
    void testVariableUsedBeforeDeclaration() {
        // This should be a compile-time error
        assertThrows(RuntimeException.class, () -> run("""
            print a
            integer a = 10"""));
    }

    @Test
    void testConstVariable() {
        run("""
            const integer a = 10
            print a""");
        assertEquals("10\n", outContent.toString().replace("\r\n", "\n"));
        
        // Assigning to const should be a runtime error (or compile error)
        // For now, let's say it's caught at runtime if we don't have the static pass yet.
        assertThrows(RuntimeException.class, () -> run("""
            const integer a = 10
            a = 20"""));
    }

    @Test
    void testIfElse() {
        run("""
            if true: print "yes" else: print "no" """);
        assertEquals("yes\n", outContent.toString().replace("\r\n", "\n"));

        outContent.reset();
        run("""
            if false: print "yes" else: print "no" """);
        assertEquals("no\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testNestedBlocks() {
        run("""
            integer a = 1
            if true:
                integer b = 2
                if true:
                    integer c = 3
                    print a + b + c""");
        assertEquals("6\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testInconsistentIndentation() {
        // Mixed styles or wrong increments should fail
        assertThrows(RuntimeException.class, () -> run("""
            if true:
              print 1
               print 2""")); // 3 spaces when indent is 2
    }

    @Test
    void testVarIncompatibleAssignment() {
        // var a = 10 (a is integer)
        // a = "hello" (error: integer expected)
        assertThrows(RuntimeException.class, () -> run("""
            var a = 10
            a = "hello" """));
    }

    @Test
    void testExplicitTypeIncompatibleAssignment() {
        // integer a = 10
        // a = "hello" (error: integer expected)
        assertThrows(RuntimeException.class, () -> run("""
            integer a = 10
            a = "hello" """));
    }

    @Test
    void testVarNoneInferenceError() {
        // Cannot infer type
        assertThrows(RuntimeException.class, () -> run("""
            var a
            """));
        // Cannot infer type from none
        assertThrows(RuntimeException.class, () -> run("""
            var a = none
            """));
    }

    @Test
    void testIfConditionBooleanError() {
        // Condition must be boolean
        assertThrows(RuntimeException.class, () -> run("""
            if 10:
                print "not boolean" """));
    }

    @Test
    void testDefaultValueNone() {
        run("""
            integer a
            print a""");
        assertEquals("none\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testReassignmentInScope() {
        run("""
            integer a = 10
            if true:
                a = 20
                print a""");
        assertEquals("20\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testReassignmentInNestedScopeIncompatible() {
        assertThrows(RuntimeException.class, () -> run("""
            integer a = 10
            if true:
                a = "hello" """));
    }

    @Test
    void testDecimalIntegerCompatibility() {
        // Decimal can be assigned integer
        run("""
            decimal a = 10.5
            a = 20
            print a""");
        assertEquals("20.0\n", outContent.toString().replace("\r\n", "\n"));
    }
}
