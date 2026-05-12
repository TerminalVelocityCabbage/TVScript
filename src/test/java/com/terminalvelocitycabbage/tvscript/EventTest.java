package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.analysis.TypeChecker;
import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.parsing.Parser;
import com.terminalvelocitycabbage.tvscript.parsing.Scanner;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EventTest {

    private Interpreter run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        List<Statement> statements = parser.parseStatements();
        
        TypeChecker typeChecker = new TypeChecker();
        typeChecker.check(statements);
        
        Interpreter interpreter = new Interpreter();
        interpreter.interpret(statements);
        return interpreter;
    }

    @Test
    void testBasicEvent() {
        Interpreter interpreter = run("""
            event PlayerDeathEvent:
                string victim
                string killer
            
            var victimName = ""
            
            on PlayerDeathEvent(string victim, string killer):
                victimName = victim
            
            dispatch PlayerDeathEvent(victim: "Alice", killer: "Bob")
            """);
            
        assertEquals("Alice", interpreter.getEnvironment().get("victimName"));
    }

    @Test
    void testEventPatternMatching() {
        Interpreter interpreter = run("""
            event PlayerDeathEvent:
                string victim
                string killer
                string reason
            
            var stabbedCount = 0
            
            on PlayerDeathEvent(string reason: reason == "STABBED"):
                stabbedCount = stabbedCount + 1
            
            dispatch PlayerDeathEvent(victim: "Alice", killer: "Bob", reason: "STABBED")
            dispatch PlayerDeathEvent(victim: "Charlie", killer: "Bob", reason: "SHOT")
            dispatch PlayerDeathEvent(victim: "Dave", killer: "Bob", reason: "STABBED")
            """);
            
        assertEquals(2, interpreter.getEnvironment().get("stabbedCount"));
    }

    @Test
    void testInitializedEvent() {
        Interpreter interpreter = run("""
            var initialized = false
            
            on InitializedEvent:
                initialized = true
            """);
            
        assertEquals(true, interpreter.getEnvironment().get("initialized"));
    }

    @Test
    void testEventRuntimeError() {
        assertThrows(RuntimeError.class, () -> {
            run("""
                event ErrorEvent:
                    integer value
                
                on ErrorEvent(integer value):
                    var x = 10 / value
                
                dispatch ErrorEvent(value: 0)
                """);
        });
    }
}
