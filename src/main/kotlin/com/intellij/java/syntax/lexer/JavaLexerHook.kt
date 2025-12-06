package com.intellij.java.syntax.lexer

import java.util.function.Consumer

interface JavaLexerHook : Consumer<JavaLexer> {
  override fun accept(javaLexer: JavaLexer)
  fun start()
  fun locateToken(c: Char): Boolean
}