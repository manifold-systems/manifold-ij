package manifold.ij.extensions;

import com.intellij.java.syntax.parser.JavaParserHook;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.psi.ElementTypeConverter;
import com.intellij.platform.syntax.psi.ElementTypeConverters;
import manifold.ij.core.ManElementType;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;

public class ManJavaParserHook implements JavaParserHook
{
  @Override
  public void updateConverter( @NotNull SyntaxElementType tuple_expression, @NotNull SyntaxElementType tuple_value_expression )
  {
    ElementTypeConverter conv = ElementTypeConverters.getConverter( JavaLanguage.INSTANCE );
    var syntaxToOld = ReflectUtil.field( conv, "syntaxToOld" ).get();
    ReflectUtil.method( syntaxToOld, "put", int.class, Object.class ).invoke( tuple_expression.getIndex(), ManElementType.TUPLE_EXPRESSION );
    ReflectUtil.method( syntaxToOld, "put", int.class, Object.class ).invoke( tuple_value_expression.getIndex(), ManElementType.TUPLE_VALUE_EXPRESSION );
    var oldToSyntax = ReflectUtil.field( conv, "oldToSyntax" ).get();
    ReflectUtil.method( oldToSyntax, "put", int.class, Object.class ).invoke( ManElementType.TUPLE_EXPRESSION.getIndex(), tuple_expression.getIndex() );
    ReflectUtil.method( oldToSyntax, "put", int.class, Object.class ).invoke( ManElementType.TUPLE_VALUE_EXPRESSION.getIndex(), tuple_value_expression.getIndex() );
  }
}
