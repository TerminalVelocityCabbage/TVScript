package com.terminalvelocitycabbage.tvscript.util;

import com.terminalvelocitycabbage.tvscript.ast.Expression;
import com.terminalvelocitycabbage.tvscript.ast.Expression.*;
import java.util.ArrayList;
import java.util.List;

public class AstUtils {

    public static String flattenQualifiedName(Expression expr) {
        if (expr instanceof VariableExpression varExpr) {
            return varExpr.name().lexeme();
        } else if (expr instanceof GetExpression getExpr) {
            String objectName = flattenQualifiedName(getExpr.object());
            if (objectName == null) return null;
            return objectName + "." + getExpr.name().lexeme();
        }
        return null;
    }

    public static List<String> splitTopLevel(String value, char separator) {
        List<String> parts = new ArrayList<>();
        StringBuilder currentPart = new StringBuilder();
        int depth = 0;

        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '<' || current == '[') {
                depth++;
            } else if (current == '>' || current == ']') {
                depth--;
            }

            if (current == separator && depth == 0) {
                parts.add(currentPart.toString().trim());
                currentPart.setLength(0);
                continue;
            }

            currentPart.append(current);
        }

        if (!currentPart.isEmpty()) {
            parts.add(currentPart.toString().trim());
        }

        return parts;
    }

    public static String getScriptIdentifier(String path) {
        if (path == null || path.equals("default")) return "default";
        String id = path;
        if (id.endsWith(".tvs")) id = id.substring(0, id.length() - 4);
        id = id.replace('/', '.').replace('\\', '.');
        while (id.startsWith(".")) id = id.substring(1);
        return id;
    }

    public static String getFolder(String path) {
        if (!path.contains("/") && !path.contains("\\")) return "";
        int lastIndex = Math.max(path.lastIndexOf("/"), path.lastIndexOf("\\"));
        return path.substring(0, lastIndex);
    }
}
