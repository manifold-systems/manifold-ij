package manifold.ij.template;

import com.intellij.lang.BracePair;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


import static manifold.ij.template.psi.ManTemplateTokenType.*;

public class ManTemplateBraceMatcher implements PairedBraceMatcher
{
  private static BracePair[] PAIRS = {
    new BracePair( DIR_ANGLE_BEGIN, ANGLE_END, true ),
    new BracePair( STMT_ANGLE_BEGIN, ANGLE_END, true ),
    new BracePair( EXPR_ANGLE_BEGIN, ANGLE_END, true ),
    new BracePair( EXPR_BRACE_BEGIN, EXPR_BRACE_END, true ),
  };

  @NotNull
  @Override
  public BracePair[] getPairs()
  {
    return PAIRS;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType( @NotNull IElementType lbraceType, @Nullable IElementType contextType )
  {
    return true;
  }

  @Override
  public int getCodeConstructStart( PsiFile file, int openingBraceOffset )
  {
    return openingBraceOffset;
  }
}