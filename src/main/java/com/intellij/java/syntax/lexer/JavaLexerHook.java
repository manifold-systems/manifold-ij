package com.intellij.java.syntax.lexer;

import java.util.function.Consumer;


//note: at runtime this interface is front loaded, see:
// resource file:  frontloads/JavaLexerHook.frontload (a .class file with .frontload ext)
// source file:  JavaLexerHook.kt in our clone of IJ platform from manifold-intellij-community-XXX_XXXXX_patch.patch-
public interface JavaLexerHook extends Consumer<JavaLexer> {
  void accept(JavaLexer javaLexer);
  void start();
  boolean locateToken(char c);
}