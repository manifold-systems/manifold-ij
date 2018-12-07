package manifold.ij.extensions;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.impl.source.resolve.PsiResolveHelperImpl;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.infos.CandidateInfo;
import manifold.ext.api.JailBreak;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ManPsiResolveHelperImpl extends PsiResolveHelperImpl
{
  public ManPsiResolveHelperImpl( PsiManager manager )
  {
    super( manager );
  }


  @Override
  public boolean isAccessible( @NotNull PsiMember member,
                               @Nullable PsiModifierList modifierList,
                               @NotNull PsiElement place,
                               @Nullable PsiClass accessObjectClass,
                               @Nullable PsiElement currentFileResolveScope )
  {
    boolean accessible = super.isAccessible( member, modifierList, place, accessObjectClass, currentFileResolveScope );
    if( accessible )
    {
      return true;
    }

    PsiType type = null;
    if( place instanceof PsiExpression )
    {
      type = ((PsiExpression)place).getType();
    }
    else if( place.getContext() instanceof PsiTypeElement )
    {
      type = ((PsiTypeElement)place.getContext()).getType();
    }
    else if( place.getContext() instanceof PsiNewExpression )
    {
      type = ((PsiNewExpression)place.getContext()).getType();
    }

    accessible = isJailBreakType( type );

    if( !accessible )
    {
      // for code completion candidates members...
      if( place.getUserData( CompletionContext.COMPLETION_CONTEXT_KEY ) != null )
      {
        if( place.getContext() instanceof PsiReferenceExpression )
        {
          PsiReferenceExpression context = (PsiReferenceExpression)place.getContext();
          if( context.getQualifier() instanceof PsiReferenceExpression )
          {
            // for completion on type annotated with @JailBreak
            type = ((PsiReferenceExpression)context.getQualifier()).getType();
            accessible = isJailBreakType( type );
          }
          else if( context.getQualifierExpression() instanceof PsiMethodCallExpressionImpl )
          {
            // for completion from jailbreak() method
            type = ((PsiMethodCallExpressionImpl)context.getQualifierExpression()).getMethodExpression().getType();
            accessible = isJailBreakType( type );
          }
        }
      }
    }
    return accessible;
  }

  @Override
  @NotNull
  public JavaResolveResult[] multiResolveConstructor( @NotNull PsiClassType type, @NotNull PsiExpressionList argumentList, @NotNull PsiElement place )
  {
    JavaResolveResult[] results = super.multiResolveConstructor( type, argumentList, place );
    for( JavaResolveResult result: results )
    {
      if( result instanceof CandidateInfo )
      {
        if( place instanceof PsiNewExpression )
        {
          PsiType t = ((PsiNewExpression)place).getType();
          if( isJailBreakType( t ) )
          {
            @JailBreak CandidateInfo info = (CandidateInfo)result;
            info.myAccessible = true;
          }
        }
        else if( place.getContext() instanceof PsiNewExpression )
        {
          PsiType t = ((PsiNewExpression)place.getContext()).getType();
          if( isJailBreakType( t ) )
          {
            @JailBreak CandidateInfo info = (CandidateInfo)result;
            info.myAccessible = true;
          }
        }
      }
    }
    return results;
  }

  private boolean isJailBreakType( PsiType type )
  {
    return type != null && type.findAnnotation( JailBreak.class.getTypeName() ) != null;
  }
}
