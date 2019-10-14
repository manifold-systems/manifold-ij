package manifold.ij.core;

import com.intellij.lang.java.parser.JavaParser;
import com.intellij.psi.impl.java.stubs.JavaLiteralExpressionElementType;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import java.util.function.Supplier;
import manifold.ij.extensions.ManJavaLiteralExpressionElementType;
import manifold.util.ReflectUtil;

public class ManApplicationComponent
{
  public ManApplicationComponent()
  {
    overrideJavaStringLiterals();
    replaceJavaExpressionParser();
  }

  /**
   * Override Java String literals to handle fragments
   * <p/>
   * NOTE!!! This must be done VERY EARLY before IntelliJ can reference the LITERAL_EXPRESSION field, thus we reset the
   * field value here in an ApplicationComponent within the scope of the ManApplicationComponent constructor.
   */
  private void overrideJavaStringLiterals()
  {
    ManJavaLiteralExpressionElementType override = new ManJavaLiteralExpressionElementType();
    ReflectUtil.field( JavaStubElementTypes.class, "LITERAL_EXPRESSION" ).setStatic( override );

    IElementType[] registry = (IElementType[])ReflectUtil.field( IElementType.class, "ourRegistry" ).getStatic();
    for( int i = 0; i < registry.length; i++ )
    {
      if( registry[i] instanceof JavaLiteralExpressionElementType )
      {
        // ensure the original JavaLiteralExpressionElementType is replaced with ours
        registry[i] = override;
      }
    }
  }

  private void replaceJavaExpressionParser()
  {
    ReflectUtil.field( JavaParser.INSTANCE, "myExpressionParser" ).set( new ManExpressionParser( JavaParser.INSTANCE ) );
    ReflectUtil.field( JavaElementType.BINARY_EXPRESSION, "myConstructor" ).set( (Supplier)ManPsiBinaryExpressionImpl::new );
    ReflectUtil.field( JavaElementType.PREFIX_EXPRESSION, "myConstructor" ).set( (Supplier)ManPsiPrefixExpressionImpl::new );

    ReflectUtil.field( JavaParser.INSTANCE, "myStatementParser" ).set( new ManStatementParser( JavaParser.INSTANCE ) );
  }
}
