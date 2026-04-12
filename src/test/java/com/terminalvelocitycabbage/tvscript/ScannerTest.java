package com.terminalvelocitycabbage.tvscript;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ScannerTest {

    @Test
    void testSimpleTokens() {
        Scanner scanner = new Scanner("()[]{},.+-*/%|?:;! == != < <= > >= = += -= *= /= %= ++ -- .. -> @");
        List<Token> tokens = scanner.scanTokens();

        assertEquals(TokenType.LEFT_PAREN, tokens.get(0).type);
        assertEquals(TokenType.RIGHT_PAREN, tokens.get(1).type);
        assertEquals(TokenType.LEFT_BRACKET, tokens.get(2).type);
        assertEquals(TokenType.RIGHT_BRACKET, tokens.get(3).type);
        assertEquals(TokenType.LEFT_BRACE, tokens.get(4).type);
        assertEquals(TokenType.RIGHT_BRACE, tokens.get(5).type);
        assertEquals(TokenType.COMMA, tokens.get(6).type);
        assertEquals(TokenType.DOT, tokens.get(7).type);
        assertEquals(TokenType.PLUS, tokens.get(8).type);
        assertEquals(TokenType.MINUS, tokens.get(9).type);
        assertEquals(TokenType.STAR, tokens.get(10).type);
        assertEquals(TokenType.SLASH, tokens.get(11).type);
        assertEquals(TokenType.PERCENT, tokens.get(12).type);
        assertEquals(TokenType.PIPE, tokens.get(13).type);
        assertEquals(TokenType.QUESTION, tokens.get(14).type);
        assertEquals(TokenType.COLON, tokens.get(15).type);
        assertEquals(TokenType.SEMICOLON, tokens.get(16).type);
        assertEquals(TokenType.BANG, tokens.get(17).type);
        assertEquals(TokenType.EQUAL_EQUAL, tokens.get(18).type);
        assertEquals(TokenType.BANG_EQUAL, tokens.get(19).type);
        assertEquals(TokenType.LESS, tokens.get(20).type);
        assertEquals(TokenType.LESS_EQUAL, tokens.get(21).type);
        assertEquals(TokenType.GREATER, tokens.get(22).type);
        assertEquals(TokenType.GREATER_EQUAL, tokens.get(23).type);
        assertEquals(TokenType.EQUAL, tokens.get(24).type);
        assertEquals(TokenType.PLUS_EQUAL, tokens.get(25).type);
        assertEquals(TokenType.MINUS_EQUAL, tokens.get(26).type);
        assertEquals(TokenType.STAR_EQUAL, tokens.get(27).type);
        assertEquals(TokenType.SLASH_EQUAL, tokens.get(28).type);
        assertEquals(TokenType.PERCENT_EQUAL, tokens.get(29).type);
        assertEquals(TokenType.PLUS_PLUS, tokens.get(30).type);
        assertEquals(TokenType.MINUS_MINUS, tokens.get(31).type);
        assertEquals(TokenType.DOT_DOT, tokens.get(32).type);
        assertEquals(TokenType.ARROW, tokens.get(33).type);
        assertEquals(TokenType.AT, tokens.get(34).type);
    }

    @Test
    void testKeywords() {
        Scanner scanner = new Scanner("import main public private protected mod var const integer decimal string boolean function return if else for while match default break continue print none class new trait type operator this super override is has as list set map enum event on dispatch annotation throw throws try catch async await launch all timeout pass and or true false");
        List<Token> tokens = scanner.scanTokens();

        assertEquals(TokenType.IMPORT, tokens.get(0).type);
        assertEquals(TokenType.MAIN, tokens.get(1).type);
        assertEquals(TokenType.PUBLIC, tokens.get(2).type);
        assertEquals(TokenType.PRIVATE, tokens.get(3).type);
        assertEquals(TokenType.PROTECTED, tokens.get(4).type);
        assertEquals(TokenType.MOD, tokens.get(5).type);
        assertEquals(TokenType.VAR, tokens.get(6).type);
        assertEquals(TokenType.CONST, tokens.get(7).type);
        assertEquals(TokenType.TYPE_INTEGER, tokens.get(8).type);
        assertEquals(TokenType.TYPE_DECIMAL, tokens.get(9).type);
        assertEquals(TokenType.TYPE_STRING, tokens.get(10).type);
        assertEquals(TokenType.TYPE_BOOLEAN, tokens.get(11).type);
        assertEquals(TokenType.FUNCTION, tokens.get(12).type);
        assertEquals(TokenType.RETURN, tokens.get(13).type);
        assertEquals(TokenType.IF, tokens.get(14).type);
        assertEquals(TokenType.ELSE, tokens.get(15).type);
        assertEquals(TokenType.FOR, tokens.get(16).type);
        assertEquals(TokenType.WHILE, tokens.get(17).type);
        assertEquals(TokenType.MATCH, tokens.get(18).type);
        assertEquals(TokenType.DEFAULT, tokens.get(19).type);
        assertEquals(TokenType.BREAK, tokens.get(20).type);
        assertEquals(TokenType.CONTINUE, tokens.get(21).type);
        assertEquals(TokenType.PRINT, tokens.get(22).type);
        assertEquals(TokenType.NONE, tokens.get(23).type);
        assertEquals(TokenType.CLASS, tokens.get(24).type);
        assertEquals(TokenType.NEW, tokens.get(25).type);
        assertEquals(TokenType.TRAIT, tokens.get(26).type);
        assertEquals(TokenType.TYPE, tokens.get(27).type);
        assertEquals(TokenType.OPERATOR, tokens.get(28).type);
        assertEquals(TokenType.THIS, tokens.get(29).type);
        assertEquals(TokenType.SUPER, tokens.get(30).type);
        assertEquals(TokenType.OVERRIDE, tokens.get(31).type);
        assertEquals(TokenType.IS, tokens.get(32).type);
        assertEquals(TokenType.HAS, tokens.get(33).type);
        assertEquals(TokenType.AS, tokens.get(34).type);
        assertEquals(TokenType.LIST, tokens.get(35).type);
        assertEquals(TokenType.SET, tokens.get(36).type);
        assertEquals(TokenType.MAP, tokens.get(37).type);
        assertEquals(TokenType.ENUM, tokens.get(38).type);
        assertEquals(TokenType.EVENT, tokens.get(39).type);
        assertEquals(TokenType.ON, tokens.get(40).type);
        assertEquals(TokenType.DISPATCH, tokens.get(41).type);
        assertEquals(TokenType.ANNOTATION, tokens.get(42).type);
        assertEquals(TokenType.THROW, tokens.get(43).type);
        assertEquals(TokenType.THROWS, tokens.get(44).type);
        assertEquals(TokenType.TRY, tokens.get(45).type);
        assertEquals(TokenType.CATCH, tokens.get(46).type);
        assertEquals(TokenType.ASYNC, tokens.get(47).type);
        assertEquals(TokenType.AWAIT, tokens.get(48).type);
        assertEquals(TokenType.LAUNCH, tokens.get(49).type);
        assertEquals(TokenType.ALL, tokens.get(50).type);
        assertEquals(TokenType.TIMEOUT, tokens.get(51).type);
        assertEquals(TokenType.PASS, tokens.get(52).type);
        assertEquals(TokenType.AND, tokens.get(53).type);
        assertEquals(TokenType.OR, tokens.get(54).type);
        assertEquals(TokenType.TRUE, tokens.get(55).type);
        assertEquals(TokenType.FALSE, tokens.get(56).type);
    }

    @Test
    void testNumbers() {
        Scanner scanner = new Scanner("123 123.456");
        List<Token> tokens = scanner.scanTokens();

        assertEquals(TokenType.INTEGER, tokens.get(0).type);
        assertEquals(123, tokens.get(0).value);
        assertEquals(TokenType.DECIMAL, tokens.get(1).type);
        assertEquals(123.456, tokens.get(1).value);
    }

    @Test
    void testStrings() {
        Scanner scanner = new Scanner("\"hello\" \"\"\"triple\"\"\"");
        List<Token> tokens = scanner.scanTokens();

        assertEquals(TokenType.STRING, tokens.get(0).type);
        assertEquals("\"hello\"", tokens.get(0).lexeme);
        assertEquals(TokenType.STRING, tokens.get(1).type);
        assertEquals("\"\"\"triple\"\"\"", tokens.get(1).lexeme);
    }

    @Test
    void testIndentation() {
        String source = "if true:\n    print \"hi\"\n    if false:\n        print \"lo\"\n    print \"back\"\nprint \"done\"";
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        int i = 0;
        assertEquals(TokenType.IF, tokens.get(i++).type);
        assertEquals(TokenType.TRUE, tokens.get(i++).type);
        assertEquals(TokenType.COLON, tokens.get(i++).type);
        assertEquals(TokenType.NEWLINE, tokens.get(i++).type);
        assertEquals(TokenType.INDENT, tokens.get(i++).type);
        assertEquals(TokenType.PRINT, tokens.get(i++).type);
        assertEquals(TokenType.STRING, tokens.get(i++).type);
        assertEquals(TokenType.NEWLINE, tokens.get(i++).type);
        assertEquals(TokenType.IF, tokens.get(i++).type);
        assertEquals(TokenType.FALSE, tokens.get(i++).type);
        assertEquals(TokenType.COLON, tokens.get(i++).type);
        assertEquals(TokenType.NEWLINE, tokens.get(i++).type);
        assertEquals(TokenType.INDENT, tokens.get(i++).type);
        assertEquals(TokenType.PRINT, tokens.get(i++).type);
        assertEquals(TokenType.STRING, tokens.get(i++).type);
        assertEquals(TokenType.NEWLINE, tokens.get(i++).type);
        assertEquals(TokenType.DEDENT, tokens.get(i++).type);
        assertEquals(TokenType.PRINT, tokens.get(i++).type);
        assertEquals(TokenType.STRING, tokens.get(i++).type);
        assertEquals(TokenType.NEWLINE, tokens.get(i++).type);
        assertEquals(TokenType.DEDENT, tokens.get(i++).type);
        assertEquals(TokenType.PRINT, tokens.get(i++).type);
        assertEquals(TokenType.STRING, tokens.get(i++).type);
    }

    @Test
    void testStringInterpolation() {
        Scanner scanner = new Scanner("\"hello {name}!\"");
        List<Token> tokens = scanner.scanTokens();

        assertEquals(TokenType.STRING_PART, tokens.get(0).type);
        assertEquals("\"hello ", tokens.get(0).lexeme);
        assertEquals(TokenType.LEFT_BRACE, tokens.get(1).type);
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).type);
        assertEquals("name", tokens.get(2).lexeme);
        assertEquals(TokenType.RIGHT_BRACE, tokens.get(3).type);
        assertEquals(TokenType.STRING, tokens.get(4).type);
        assertEquals("!\"", tokens.get(4).lexeme);
    }

    @Test
    void testComments() {
        Scanner scanner = new Scanner("foo // comment\nbar /// block\ncomment /// baz");
        List<Token> tokens = scanner.scanTokens();

        assertEquals(TokenType.IDENTIFIER, tokens.get(0).type);
        assertEquals("foo", tokens.get(0).lexeme);
        assertEquals(TokenType.NEWLINE, tokens.get(1).type);
        assertEquals(TokenType.IDENTIFIER, tokens.get(2).type);
        assertEquals("bar", tokens.get(2).lexeme);
        assertEquals(TokenType.IDENTIFIER, tokens.get(3).type);
        assertEquals("baz", tokens.get(3).lexeme);
    }
    
    @Test
    void testTripleQuotedStringsMultiline() {
        Scanner scanner = new Scanner("\"\"\"line 1\nline 2\"\"\"");
        List<Token> tokens = scanner.scanTokens();

        assertEquals(TokenType.STRING, tokens.get(0).type);
        assertEquals("\"\"\"line 1\nline 2\"\"\"", tokens.get(0).lexeme);
    }
}
