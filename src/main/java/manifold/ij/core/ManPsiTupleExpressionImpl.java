/*
 *
 *  * Copyright (c) 2022 - Manifold Systems LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *       http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 *
 */

package manifold.ij.core;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaPsiFacadeEx;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.java.ExpressionPsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import manifold.ij.util.ManPsiUtil;
import manifold.internal.javac.ITupleTypeProvider;
import manifold.rt.api.Null;
import manifold.rt.api.util.ManClassUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static manifold.ij.core.ManElementType.TUPLE_EXPRESSION;

public class ManPsiTupleExpressionImpl extends ExpressionPsiElement implements ManPsiTupleExpression
{
  private static final Logger LOG = Logger.getInstance( ManPsiTupleValueExpressionImpl.class );

  public ManPsiTupleExpressionImpl()
  {
    super( TUPLE_EXPRESSION );
  }

  @Override
  public PsiExpression @NotNull [] getExpressions() {
    List<PsiExpression> expressions = getValueExpressions();
    return expressions == null ? new PsiExpression[0] : getValueExpressions().stream().map( e ->
      e instanceof ManPsiTupleValueExpression ? ((ManPsiTupleValueExpression)e).getValue() : e ).toArray( i -> new PsiExpression[i] );
  }

  @Override
  public int getExpressionCount() {
    PsiExpression[] expressions = getExpressions();
    return expressions.length;
  }

  @Override
  public boolean isEmpty() {
    return findChildByType(ElementType.EXPRESSION_BIT_SET) == null;
  }

  @Override
  public PsiType @NotNull [] getExpressionTypes() {
    PsiExpression[] expressions = getExpressions();
    PsiType[] types = PsiType.createArray(expressions.length);

    for (int i = 0; i < types.length; i++) {
      types[i] = expressions[i].getType();
    }

    return types;
  }

  @Override
  public List<PsiExpression> getValueExpressions()
  {
    PsiExpression[] expressions = getChildrenAsPsiElements( ElementType.EXPRESSION_BIT_SET, PsiExpression.ARRAY_FACTORY );
    return Arrays.asList( expressions );
  }

  @Override
  public @Nullable PsiType getType()
  {
    PsiClass topLevelClass = PsiUtil.getTopLevelClass( this );
    if( topLevelClass == null )
    {
      return null;
    }

    if( !ManProject.isManifoldInUse( this ) )
    {
      return null;
    }

    if( ManProject.getModule( this ) == null )
    {
      return null;
    }

    ManModule module = ManProject.getModule( this );
    if( module != null && !module.isTuplesEnabled() )
    {
      return null;
    }

    PsiClassType paramsClassType = (PsiClassType)handleNamedArgs();
    if( paramsClassType != null )
    {
      // type when tuple is used for optional parameters
      return paramsClassType;
    }

    String pkg = ManClassUtil.getPackage( topLevelClass.getQualifiedName() );
    String tupleTypeName = ManPsiUtil.runInTypeManifoldLoader( this,
      () -> ITupleTypeProvider.INSTANCE.get().makeType( pkg, makeTupleFieldMap() ) );
    PsiClass psiClass = JavaPsiFacade.getInstance( getProject() ).findClass( tupleTypeName, GlobalSearchScope.projectScope( getProject() ) );
    PsiExpression expr = JavaPsiFacadeEx.getInstanceEx( getProject() ).getParserFacade().createExpressionFromText( "new " + tupleTypeName + "(" +
      getValueExpressions().stream().map( v ->
        v instanceof ManPsiTupleValueExpression
          ? ((ManPsiTupleValueExpression)v).getValue() == null ? "" : ((ManPsiTupleValueExpression)v).getValue().getText()
          : v.getText() ).collect( Collectors.joining(", ") ) + ")", this );
    return psiClass == null ? null : expr.getType();
//    return psiClass == null ? null : PsiTypesUtil.getClassType( psiClass );
  }

  private PsiType handleNamedArgs()
  {
    PsiElement parent = getParent();
    if( parent instanceof PsiExpressionList && ((PsiExpressionList)parent).getExpressionCount() == 1 )
    {
      parent = parent.getParent();
      if( parent instanceof PsiCallExpression || parent instanceof PsiAnonymousClass || parent instanceof PsiEnumConstant )
      {
        return TupleNamedArgsUtil.getNewParamsClassExprType( parent, this );
      }
    }
    return null;
  }

  @Override
  public void accept( @NotNull PsiElementVisitor visitor )
  {
    if( visitor instanceof JavaElementVisitor )
    {
      ((JavaElementVisitor)visitor).visitExpression( this );
    }
    else
    {
      visitor.visitElement( this );
    }
  }

  private Map<String, String> makeTupleFieldMap()
  {
    Map<String, String> map = new LinkedHashMap<>();
    List<PsiExpression> valueExpressions = getValueExpressions();
    if( valueExpressions == null )
    {
      return map;
    }
    int nullNameCount = 0;
    for( PsiExpression expr : valueExpressions )
    {
      String name = null;
      PsiIdentifier label = expr instanceof ManPsiTupleValueExpression ? ((ManPsiTupleValueExpression)expr).getLabel() : null;
      if( label != null )
      {
        name = label.getText();
      }
      else
      {
        PsiExpression arg = expr instanceof ManPsiTupleValueExpression ? ((ManPsiTupleValueExpression)expr).getValue() : expr;
        if( arg instanceof PsiIdentifier )
        {
          name = arg.getText();
        }
        else if( arg instanceof PsiJavaCodeReferenceElement )
        {
          name = ((PsiJavaCodeReferenceElement)arg).getReferenceName();
        }
        else if( arg instanceof PsiMethodCallExpression )
        {
          String referenceName = ((PsiMethodCallExpression)arg).getMethodExpression().getReferenceName();
          name = referenceName == null ? null : getFieldNameFromMethodName( referenceName );
        }
      }
      String item = name == null ? "item" : name;
      if( name == null )
      {
        item += ++nullNameCount;
      }
      name = item;
      for( int i = 2; map.containsKey( item ); i++ )
      {
        item = name + '_' + i;
      }
      PsiExpression value = expr instanceof ManPsiTupleValueExpression ? ((ManPsiTupleValueExpression)expr).getValue() : expr;
      if( value != null && value.getType() != null )
      {
        PsiType type = value.getType();
        type = RecursiveTypeVarEraser.eraseTypeVars( type, value );
        map.put( item,
          type == null
          ? null
          : type == PsiTypes.nullType()
            ? Null.class.getTypeName()
            : type.getCanonicalText() );
      }
      else
      {
        map.put( item, null );
      }
    }
    return map;
  }

  private String getFieldNameFromMethodName( String methodName )
  {
    for( int i = 0; i < methodName.length(); i++ )
    {
      if( Character.isUpperCase( methodName.charAt( i ) ) )
      {
        StringBuilder name = new StringBuilder( methodName.substring( i ) );
        for( int j = 0; j < name.length(); j++ )
        {
          char c = name.charAt( j );
          if( Character.isUpperCase( c ) )
          {
            name.setCharAt( j, Character.toLowerCase( c ) );
          }
          else
          {
            break;
          }
        }
        return name.toString();
      }
    }
    return methodName;
  }

  @Override
  public ASTNode findChildByRole( int role )
  {
    LOG.assertTrue( ChildRole.isUnique( role ) );
    switch( role )
    {
      case ChildRole.LPARENTH:
        return getFirstChildNode() != null && getFirstChildNode().getElementType() == JavaTokenType.LPARENTH ? getFirstChildNode() : null;

      case ChildRole.RPARENTH:
        if( getLastChildNode() != null && getLastChildNode().getElementType() == JavaTokenType.RPARENTH )
        {
          return getLastChildNode();
        }
        else
        {
          return null;
        }

      default:
        return null;
    }
  }

  @Override
  public int getChildRole( @NotNull ASTNode child )
  {
    LOG.assertTrue( child.getTreeParent() == this );
    IElementType i = child.getElementType();
    if( i == JavaTokenType.COMMA )
    {
      return ChildRole.COMMA;
    }
    else if( i == JavaTokenType.LPARENTH )
    {
      return ChildRole.LPARENTH;
    }
    else if( i == JavaTokenType.RPARENTH )
    {
      return ChildRole.RPARENTH;
    }
    else
    {
      if( ElementType.EXPRESSION_BIT_SET.contains( child.getElementType() ) )
      {
        return ChildRole.EXPRESSION_IN_LIST;
      }
      return ChildRoleBase.NONE;
    }
  }


}
