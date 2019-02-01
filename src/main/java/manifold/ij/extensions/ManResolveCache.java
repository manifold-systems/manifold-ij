package manifold.ij.extensions;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.tree.java.PsiMethodCallExpressionImpl;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.messages.MessageBus;
import java.util.Map;
import manifold.ext.api.Jailbreak;
import manifold.ij.util.ManVersionUtil;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;

public class ManResolveCache extends ResolveCache
{
  public ManResolveCache( @NotNull MessageBus messageBus )
  {
    super( messageBus );
  }

  @NotNull
  public <T extends PsiPolyVariantReference> ResolveResult[] resolveWithCaching( @NotNull final T ref,
                                                                                 @NotNull final PolyVariantContextResolver<T> resolver,
                                                                                 boolean needToPreventRecursion,
                                                                                 final boolean incompleteCode,
                                                                                 @NotNull final PsiFile containingFile )
  {
    ProgressIndicatorProvider.checkCanceled();
    ApplicationManager.getApplication().assertReadAccessAllowed();

    boolean physical = containingFile.isPhysical();
    Map<T, ResolveResult[]> map;
    @Jailbreak ResolveCache me = this;
    if( ManVersionUtil.is2018_1_orGreater() )
    {
      int index = me.getIndex( incompleteCode, true );
      map = me.getMap( physical, index );
    }
    else
    {
      int index = (int)ReflectUtil.method( me, "getIndex", boolean.class, boolean.class, boolean.class ).invoke( physical, incompleteCode, true );
      map = (Map<T, ResolveResult[]>)ReflectUtil.method( me, "getMap", int.class ).invoke( index );
    }
    ResolveResult[] results = map.get( ref );
    if( results != null )
    {
      return results;
    }

    results = super.resolveWithCaching( ref, resolver, needToPreventRecursion, incompleteCode, containingFile );
    for( ResolveResult result: results )
    {
      if( result instanceof CandidateInfo )
      {
        @Jailbreak CandidateInfo info = (CandidateInfo)result;
        if( ref instanceof PsiReferenceExpression )
        {
          Boolean accessible = info.myAccessible;
          if( accessible == null || !accessible )
          {
            PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
            if( refExpr.getQualifier() instanceof PsiReferenceExpression )
            {
              PsiType type = ((PsiReferenceExpression)refExpr.getQualifier()).getType();
              if( isJailbreakType( type ) )
              {
                info.myAccessible = true;
              }
            }
            else if( refExpr.getQualifier() instanceof PsiMethodCallExpressionImpl )
            {
              PsiMethodCallExpressionImpl qualifier = (PsiMethodCallExpressionImpl)refExpr.getQualifier();
              String referenceName = qualifier.getMethodExpression().getReferenceName();
              if( referenceName != null && referenceName.equals( "jailbreak" ) )
              {
                // special case for jailbreak() extension
                info.myAccessible = true;
              }
              else
              {
                PsiType type = refExpr.getType();
                if( type != null && type.findAnnotation( Jailbreak.class.getTypeName() ) != null )
                {
                  info.myAccessible = true;
                }
              }
            }
          }
        }
      }
    }
    return results;
  }

  private boolean isJailbreakType( PsiType type )
  {
    return type != null && type.findAnnotation( Jailbreak.class.getTypeName() ) != null;
  }
}