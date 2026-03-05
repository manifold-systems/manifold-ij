package com.intellij.java.syntax.parser;

import com.intellij.platform.syntax.SyntaxElementType;

//note: at runtime this interface is front loaded, see:
// resource file:  frontloads/JavaParserHook.frontload (a .class file with .frontload ext)
// source file:  JavaParserHook.kt in our clone of IJ platform from manifold-intellij-community-XXX_XXXXX_patch.patch
public interface JavaParserHook {
    void updateConverter( SyntaxElementType tuple_expression, SyntaxElementType tuple_value_expression );
}