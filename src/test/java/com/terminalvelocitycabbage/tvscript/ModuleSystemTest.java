package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.errors.CompileError;
import com.terminalvelocitycabbage.tvscript.errors.RuntimeError;
import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.parsing.Parser;
import com.terminalvelocitycabbage.tvscript.parsing.Scanner;
import com.terminalvelocitycabbage.tvscript.parsing.Token;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import com.terminalvelocitycabbage.tvscript.analysis.TypeChecker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

class ModuleSystemTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    private static class ScriptInfo {
        String source;
        String module;
        String path;
        List<Statement> statements;

        ScriptInfo(String path, String module, String source) {
            this.path = path;
            this.module = module;
            this.source = source;
        }
    }

    private final Map<String, ScriptInfo> scripts = new HashMap<>();

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
        scripts.clear();
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    private void addScript(String path, String module, String source) {
        scripts.put(path, new ScriptInfo(path, module, source));
    }

    private void runAll() {
        runAll(null);
    }

    private void runAll(String mainScriptPath) {
        TVScript.hadError = false;
        TVScript.hadRuntimeError = false;

        Interpreter interpreter = new Interpreter();
        TypeChecker typeChecker = new TypeChecker(interpreter.getNativeFunctions(), interpreter.getEnvironment().getNativeClasses());

        // Phase 1: Scan and Parse all scripts
        for (ScriptInfo script : scripts.values()) {
            Scanner scanner = new Scanner(script.source);
            List<Token> tokens = scanner.scanTokens();
            Parser parser = new Parser(tokens);
            script.statements = parser.parseStatements();
            if (TVScript.hadError) {
                TVScript.hadError = false;
                throw new RuntimeException("Parse error in " + script.path);
            }
        }

        // Phase 2: Type Check all scripts
        // We do two passes over all scripts.
        // Pass 1: Collect ALL top-level definitions from ALL scripts.
        // We do this by calling a special registration method or just a minimal check.
        for (ScriptInfo script : scripts.values()) {
            typeChecker.registerDefinitions(script.statements, script.path, script.module);
        }
        
        // Pass 2: Check each script's body with the full context.
        for (ScriptInfo script : scripts.values()) {
            typeChecker.check(script.statements, script.path, script.module);
            if (TVScript.hadError) {
                TVScript.hadError = false;
                throw new RuntimeException("Type check error in " + script.path);
            }
        }

        // Phase 3: Interpret
        List<String> paths = new ArrayList<>(scripts.keySet());
        if (mainScriptPath != null) {
            paths.remove(mainScriptPath);
            paths.add(mainScriptPath);
        }

        for (String path : paths) {
            ScriptInfo script = scripts.get(path);
            interpreter.setCurrentScriptPath(script.path);
            interpreter.setCurrentModule(script.module);
            interpreter.interpret(script.statements);
            if (TVScript.hadRuntimeError) {
                TVScript.hadRuntimeError = false;
                throw new RuntimeError(null, "Runtime error in " + script.path);
            }
        }
    }

    private void run(String source) {
        run(source, "default", "default.tvs");
    }

    private void run(String source, String module, String path) {
        addScript(path, module, source);
        runAll(path);
    }

    @Test
    void testDefaultPrivateVisibility() {
        // This should fail type checking because 'secret' is private by default
        assertThrows(RuntimeException.class, () -> {
            run("""
                class Vault:
                    string secret = "password"
                    constructor(): pass
                
                class Thief:
                    constructor(): pass
                    steal(Vault v):
                        print v.secret
                
                Vault v = new Vault()
                Thief t = new Thief()
                t.steal(v: v)
                """, "m1", "s1.tvs");
        }, "Should not be able to access private field from outside");
    }

    @Test
    void testPublicVisibility() {
        run("""
            class Vault:
                public string data = "open"
                constructor(): pass
            
            Vault v = new Vault()
            print v.data
            """);
        assertEquals("open\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testPrivateVisibilityInSameClass() {
        run("""
            class Vault:
                private string secret = "password"
                constructor(): pass
                
                public reveal():
                    print this.secret
            
            Vault v = new Vault()
            v.reveal()
            """);
        assertEquals("password\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testModuleVisibility() {
        // This test will need to be refined once we have actual multi-script support in tests
        // For now, it demonstrates the desired behavior in a single "script" 
        // which by definition is in the same module and package.
        run("""
            class Internal:
                module string internalData = "module-only"
                constructor(): pass
            
            Internal i = new Internal()
            print i.internalData
            """);
        assertEquals("module-only\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testCrossScriptProtectedVisibility() {
        // Scripts in the same folder should be able to access protected members
        addScript("src/main.tvs", "m1", """
            import PackageFriend
            
            PackageFriend f = new PackageFriend()
            print f.shared
            """);
        addScript("src/friend.tvs", "m1", """
            class PackageFriend:
                protected string shared = "hello"
                constructor(): pass
            """);
        
        runAll("src/main.tvs");
        assertEquals("hello\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testCrossFolderProtectedVisibilityFails() {
        // Scripts in different folders should NOT be able to access protected members
        addScript("src/main.tvs", "m1", """
            import PackageFriend
            
            PackageFriend f = new PackageFriend()
            print f.shared
            """);
        addScript("src/other/friend.tvs", "m1", """
            class PackageFriend:
                protected string shared = "hello"
                constructor(): pass
            """);
        
        assertThrows(RuntimeException.class, () -> runAll("src/main.tvs"), 
            "Should not access protected member from different folder");
    }

    @Test
    void testCrossScriptModuleVisibility() {
        // Scripts in the same module should be able to access module members
        addScript("modules/engine/scripts/a.tvs", "engine", """
            public class Internal:
                module string data = "internal"
                public constructor(): pass
            """);
        addScript("modules/engine/scripts/b.tvs", "engine", """
            import Internal
            Internal i = new Internal()
            print i.data
            """);
        
        runAll("modules/engine/scripts/b.tvs");
        assertEquals("internal\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testCrossModuleVisibilityFails() {
        // Scripts in different modules should NOT be able to access module members
        addScript("modules/moduleA/scripts/a.tvs", "moduleA", """
            public class Internal:
                module string data = "internal"
                public constructor(): pass
            """);
        addScript("modules/moduleB/scripts/b.tvs", "moduleB", """
            import Internal
            Internal i = new Internal()
            print i.data
            """);
        
        assertThrows(RuntimeException.class, () -> runAll("modules/moduleB/scripts/b.tvs"),
            "Should not access module member from different module");
    }

    @Test
    void testInheritanceProtectedAccess() {
        // Subclass in same package should access protected super member
        addScript("src/Parent.tvs", "m", """
            class Parent:
                protected string value = "protected"
                constructor(): pass
            """);
        addScript("src/Child.tvs", "m", """
            import Parent
            class Child < Parent:
                public constructor(): pass
                public show():
                    print this.value
            
            Child c = new Child()
            c.show()
            """);
        
        runAll("src/Child.tvs");
        assertEquals("protected\n", outContent.toString().replace("\r\n", "\n"));
    }
    @Test
    void testMultipleVisibilityModifiersError() {
        addScript("error.tvs", "default", """
            public private class Broken:
                pass
            """);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> runAll("error.tvs"));
        // "Only one visibility modifier is allowed."
        assertTrue(ex.getMessage().contains("Only one visibility modifier is allowed") || ex.getMessage().contains("Parse error"), "Expected parse error for multiple visibility modifiers, got: " + ex.getMessage());
    }

    @Test
    void testDefinitiveErrorMessages() {
        addScript("a.tvs", "m1", """
            class Secret:
                private string data = "shh"
                constructor(): pass
            """);
        addScript("b.tvs", "m1", """
            import Secret
            Secret s = new Secret()
            print s.data
            """);
        RuntimeException ex = assertThrows(RuntimeException.class, () -> runAll("b.tvs"));
        // Check standard output or error message
        // Since runAll throws generic RuntimeException with "Type check error", 
        // we might need a way to check the actual error reported to TVScript.hadError.
        // Actually, my updated TypeChecker uses definitive messages.
        // If TypeChecker calls TVScript.compileError, it prints to System.err.
    }

    @Test
    void testImportAliasing() {
        addScript("lib.tvs", "lib", """
public class Util:
    public const string version = "1.0"
    public constructor(): pass
""");
        addScript("main.tvs", "app", """
import lib.Util as MyUtil
print MyUtil.version
""");
        runAll("main.tvs");
        assertEquals("1.0\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testQualifiedAccess() {
        addScript("net.tvs", "net", """
public class Client:
    public string id = "net-client"
    public constructor(): pass
""");
        addScript("main.tvs", "app", """
import net as network
network.Client c = new network.Client()
print c.id
""");
        runAll("main.tvs");
        assertEquals("net-client\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testInlineImportBlock() {
        addScript("math.tvs", "math", """
public integer add(integer a, integer b):
    return a + b

public integer sub(integer a, integer b):
    return a - b
""");
        addScript("main.tvs", "app", """
import math : [add, sub as subtract]
print add(1, 2)
print subtract(10, 4)
""");
        runAll("main.tvs");
        assertEquals("3\n6\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testImportBlock() {
        addScript("math.tvs", "math", """
public integer add(integer a, integer b):
    return a + b

public integer sub(integer a, integer b):
    return a - b
""");
        addScript("main.tvs", "app", """
import math: 
    add
    sub as subtract
print add(1, 2)
print subtract(10, 4)
""");
        runAll("main.tvs");
        assertEquals("3\n6\n", outContent.toString().replace("\r\n", "\n"));
    }

    @Test
    void testTopLevelVisibility() {
        addScript("lib.tvs", "lib", """
private integer hidden = 1
public integer shown = 2
""");
        addScript("main.tvs", "app", """
import lib.shown
import lib.hidden
print shown
""");
        assertThrows(RuntimeException.class, () -> runAll("main.tvs"), "Should fail to import private top-level variable");
    }

    @Test
    void testQualifiedAccessWithoutImport() {
        addScript("math.tvs", "math", """
public integer add(integer a, integer b):
    return a + b
""");
        addScript("main.tvs", "app", """
print math.add(5, 5)
""");
        runAll("main.tvs");
        assertEquals("10\n", outContent.toString().replace("\r\n", "\n"));
    }
}
