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
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.java.ExpressionPsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.PsiErrorElementUtil;
import manifold.ij.extensions.PsiErrorClassUtil;
import manifold.ij.util.ManPsiUtil;
import manifold.internal.javac.ITupleTypeProvider;
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
  public @Nullable List<ManPsiTupleValueExpression> getValueExpressions()
  {
    PsiExpression[] expressions = getChildrenAsPsiElements( ElementType.EXPRESSION_BIT_SET, PsiExpression.ARRAY_FACTORY );
    return Arrays.stream( expressions ).map( e -> (ManPsiTupleValueExpression)e ).collect( Collectors.toList() );
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

    String pkg = ManClassUtil.getPackage( topLevelClass.getQualifiedName() );
    String tupleTypeName = ManPsiUtil.runInTypeManifoldLoader( this,
      () -> ITupleTypeProvider.INSTANCE.get().makeType( pkg, makeTupleFieldMap() ) );
    PsiClass psiClass = JavaPsiFacade.getInstance( getProject() ).findClass( tupleTypeName, GlobalSearchScope.projectScope( getProject() ) );
    return psiClass == null ? null : PsiTypesUtil.getClassType( psiClass );
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
    List<ManPsiTupleValueExpression> valueExpressions = getValueExpressions();
    if( valueExpressions == null )
    {
      return map;
    }
    int nullNameCount = 0;
    for( ManPsiTupleValueExpression valueExpression : valueExpressions )
    {
      String name = null;
      PsiIdentifier label = valueExpression.getLabel();
      if( label != null )
      {
        name = label.getText();
      }
      else
      {
        PsiExpression arg = valueExpression.getValue();
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
      PsiExpression value = valueExpression.getValue();
      if( value != null && value.getType() != null )
      {
        PsiType type = value.getType();
        type = RecursiveTypeVarEraser.eraseTypeVars( type, value );
        map.put( item, type == null ? null : type.getCanonicalText() );
      }
      else
      {
        map.put( item, null );
      }
    }
    return map;
  }

//  private PsiType eraseBound( PsiType t, PsiType bound )
//  {
//    if( bound == null  )
//    {
//      return bound;
//    }
//
//    PsiType erasedBound;
//    if( bound.contains( t ) )
//    {
//      erasedBound = visit( _types.erasure( bound ) );
//    }
//    else
//    {
//      erasedBound = visit( bound );
//    }
//    return erasedBound;
//  }

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
