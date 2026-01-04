package manifold.ij.extensions;

import com.intellij.lang.ASTFactory;
import com.intellij.lang.DefaultASTFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.javadoc.PsiDocTagValueImpl;
import com.intellij.psi.impl.source.javadoc.PsiDocTokenImpl;
import com.intellij.psi.impl.source.javadoc.PsiSnippetAttributeValueImpl;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.java.PsiFragmentImpl;
import com.intellij.psi.impl.source.tree.java.PsiIdentifierImpl;
import com.intellij.psi.impl.source.tree.java.PsiJavaTokenImpl;
import com.intellij.psi.impl.source.tree.java.PsiKeywordImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.java.IJavaDocElementType;
import com.intellij.psi.tree.java.IJavaElementType;
import org.jetbrains.annotations.NotNull;


public final class ManJavaASTFactory extends ASTFactory {
  private final DefaultASTFactory myDefaultASTFactory = ApplicationManager.getApplication().getService(DefaultASTFactory.class);

  @Override
  public LeafElement createLeaf( @NotNull IElementType type, @NotNull CharSequence text) {

    // manifold:
    if( type == JavaTokenType.STRING_LITERAL || "TEXT_BLOCK_LITERAL".equals( type.toString() ) )
    {
      return new ManPsiStringLiteral( type, text );
    }
    // manifold end:

    if (type == JavaTokenType.C_STYLE_COMMENT || type == JavaTokenType.END_OF_LINE_COMMENT) {
      return myDefaultASTFactory.createComment(type, text);
    }
    if (type == JavaTokenType.IDENTIFIER) {
      return new PsiIdentifierImpl(text);
    }
    if ( ElementType.KEYWORD_BIT_SET.contains( type)) {
      return new PsiKeywordImpl(type, text);
    }
    if (ElementType.STRING_TEMPLATE_FRAGMENTS.contains(type)) {
      return new PsiFragmentImpl(type, text);
    }
    if (type instanceof IJavaElementType) {
      return new PsiJavaTokenImpl(type, text);
    }
    if (type instanceof IJavaDocElementType) {
      if ( type == JavaDocElementType.DOC_SNIPPET_ATTRIBUTE_VALUE) {
        return new PsiSnippetAttributeValueImpl(text);
      }
      assert type != JavaDocElementType.DOC_TAG_VALUE_ELEMENT;
      return new PsiDocTokenImpl(type, text);
    }

    return null;
  }

  @Override
  public CompositeElement createComposite( @NotNull IElementType type) {
    if (type == JavaDocElementType.DOC_TAG_VALUE_ELEMENT) {
      return new PsiDocTagValueImpl();
    }

    return null;
  }
}