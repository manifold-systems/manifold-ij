package com.intellij.platform.syntax.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.platform.syntax.lexer.Lexer;
import com.intellij.platform.syntax.psi.PsiSyntaxBuilderFactory;
import java.util.function.Consumer;

//note: at runtime this interface is front loaded, see:
// resource file:  frontloads/PsiSyntaxBuilderFactoryHook.frontload (a .class file with .frontload ext)
// source file:  PsiSyntaxBuilderFactoryHook.kt in our clone of IJ platform from manifold-intellij-community-XXX_XXXXX_patch.patch
public interface PsiSyntaxBuilderFactoryHook extends Consumer<PsiSyntaxBuilderFactory> {
  @Override void accept(PsiSyntaxBuilderFactory psiSyntaxBuilderFactory);
  void createBuilder(ASTNode chameleon, Lexer lexer, Language lang, CharSequence text);
  void pushNode(ASTNode chameleon);
  void popNode(ASTNode builder);
  ASTNode peekNode();
}
