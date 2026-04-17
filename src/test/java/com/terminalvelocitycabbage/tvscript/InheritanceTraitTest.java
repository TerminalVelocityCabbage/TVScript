package com.terminalvelocitycabbage.tvscript;

import com.terminalvelocitycabbage.tvscript.execution.Interpreter;
import com.terminalvelocitycabbage.tvscript.parsing.Parser;
import com.terminalvelocitycabbage.tvscript.parsing.Scanner;
import com.terminalvelocitycabbage.tvscript.ast.Statement;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class InheritanceTraitTest {

    private String run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream outStream = new PrintStream(out);
        PrintStream errStream = new PrintStream(err);
        System.setOut(outStream);
        System.setErr(errStream);
        
        try {
            TVScript.run(source);
        } catch (Exception e) {
            errStream.println("Caught exception: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace(errStream);
        } finally {
            System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out)));
            System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err)));
        }
        
        String combined = out.toString() + err.toString();
        return combined.trim().replace("\r\n", "\n");
    }

    @Test
    public void testBasicInheritance() {
        String source = """
            class Entity:
                string name
                constructor(string name):
                    this.name = name
                
                onSpawn():
                    print "spawned {this.name}"
              
            class Player < Entity:
                integer health = 10
                constructor(string name):
                    super(name: name)

            main:
                Player p = new Player(name: "Junie")
                p.onSpawn()
                print p.health
            """;
        String output = run(source);
        assertEquals("spawned Junie\n10", output);
    }

    @Test
    public void testOverrideInheritance() {
        String source = """
            class Entity:
                string name
                constructor(string name):
                    this.name = name
                
                onSpawn():
                    print "spawned {this.name}"
              
            class Player < Entity:
                integer health = 10
                constructor(string name):
                    super(name: name)
                
                override onSpawn():
                    print "player spawned {this.name}"

            main:
                Player p = new Player(name: "Junie")
                p.onSpawn()
                print p.health
            """;
        String output = run(source);
        assertEquals("player spawned Junie\n10", output);
    }

    @Test
    public void testTraits() {
        String source = """
            trait EmitsSound:
                playSound()

            trait CanDie:
                default checkForDeath(integer health):
                    if health <= 0:
                        print "dead"

            class Actor < [EmitsSound, CanDie]:
                constructor():
                    pass
                
                override playSound():
                    print "beep"

            main:
                Actor a = new Actor()
                a.playSound()
                a.checkForDeath(health: 0)
            """;
        String output = run(source);
        assertEquals("beep\ndead", output);
    }
    
    @Test
    public void testTraitInheritance() {
        String source = """
            trait A:
                a()
            
            trait B < [A]:
                b()
            
            class C < [B]:
                constructor():
                    pass
                override a():
                    print "override a"
                override b():
                    print "override b"
            
            main:
                C c = new C()
                c.a()
                c.b()
            """;
        String output = run(source);
        assertEquals("override a\noverride b", output);
    }

    @Test
    public void testIsHasAs() {
        String source = """
            trait T:
                pass
            class A:
                constructor():
                    pass
            class B < A [T]:
                constructor():
                    pass
            
            main:
                B b = new B()
                A a = b
                print a is B
                print a is A
                print a has T
                
                if a is B -> realB:
                    print "it is B"
                
                B castedB = a as B
                print "casted"
            """;
        String output = run(source);
        assertEquals("true\ntrue\ntrue\nit is B\ncasted", output);
    }

    @Test
    public void testConflictError() {
        String source = """
            trait A:
                default m(): print "A"
            trait B:
                default m(): print "B"
            class C < [A, B]:
                constructor(): pass
            """;
        String output = run(source);
        assertTrue(output.contains("must override method 'm' because it is provided by multiple traits"));
    }

    @Test
    public void testMissingImplementationError() {
        String source = """
            trait A:
                m()
            class C < [A]:
                constructor(): pass
            """;
        String output = run(source);
        assertTrue(output.contains("must implement method 'm' from trait A"));
    }

    @Test
    public void testTraitConstantFields() {
        String source = """
            trait HasConfig:
                const integer MAX_HP = 100
                const string NAME = "Base"
            
            class Actor < [HasConfig]:
                constructor(): pass
                
                printConfig():
                    print HasConfig.MAX_HP
                    print HasConfig.NAME
            
            main:
                Actor a = new Actor()
                a.printConfig()
                print HasConfig.MAX_HP
            """;
        String output = run(source);
        assertEquals("100\nBase\n100", output);
    }

    @Test
    public void testTraitFieldAccessViaThisFails() {
        String source = """
            trait HasConfig:
                const integer MAX_HP = 100
            
            class Actor < [HasConfig]:
                constructor(): pass
                
                printConfig():
                    print this.MAX_HP
            
            main:
                Actor a = new Actor()
                a.printConfig()
            """;
        String output = run(source);
        assertTrue(output.contains("Undefined property 'MAX_HP' on 'this'."));
    }

    @Test
    public void testPatternMatchFieldAccess() {
        String source = """
            class Entity:
                string name
                constructor(string name):
                    this.name = name
            
            class Player < Entity:
                integer health = 100
                constructor(string name):
                    super(name: name)
            
            main:
                Entity e = new Player(name: "Junie")
                if e is Player -> p:
                    print p.name
                    print p.health
            """;
        String output = run(source);
        assertEquals("Junie\n100", output);
    }
}
