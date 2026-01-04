package com.intellij.platform.syntax.psi;

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.platform.syntax.lexer.Lexer
import java.util.function.Consumer

interface PsiSyntaxBuilderFactoryHook : Consumer<PsiSyntaxBuilderFactory> {
  override fun accept(psiSyntaxBuilderFactory: PsiSyntaxBuilderFactory)
  fun createBuilder(chameleon: ASTNode, lexer: Lexer?, lang: Language, text: CharSequence)
  fun pushNode(chameleon: ASTNode?)
  fun popNode(builder: ASTNode?)
  fun peekNode(): ASTNode?
}
