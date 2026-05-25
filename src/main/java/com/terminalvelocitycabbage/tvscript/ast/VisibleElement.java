package com.terminalvelocitycabbage.tvscript.ast;

import com.terminalvelocitycabbage.tvscript.parsing.Token;

/**
 * Interface for AST elements that have visibility modifiers.
 */
public interface VisibleElement {
    /**
     * @return The visibility token (PUBLIC, PRIVATE, PROTECTED, MODULE) or null if default.
     */
    Token visibility();
}
