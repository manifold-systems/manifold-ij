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

package manifold.ij.extensions;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import manifold.ext.props.rt.api.get;
import manifold.ext.props.rt.api.set;
import manifold.ext.props.rt.api.val;
import manifold.ext.props.rt.api.var;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Arrays;


/**
 * Suppress errors around properties that are not really errors
 */
public class ManPropertiesHighlightInfoFilter implements HighlightInfoFilter
{
  @Override
  public boolean accept( @NotNull HighlightInfo hi, @Nullable PsiFile file )
  {
    if( file == null )
    {
      return true;
    }

    if( !ManProject.isManifoldInUse( file ) )
    {
      // Manifold jars are not used in the project
      return true;
    }

    if( !(file.getLanguage() instanceof JavaLanguage) )
    {
      return true;
    }

    ManModule module = ManProject.getModule( file );
    if( module != null && !module.isPropertiesEnabled() )
    {
      // project/module not using properties
      return true;
    }

    if( hi.getDescription() == null )
    {
      return true;
    }

    PsiElement firstElem = file.findElementAt( hi.getStartOffset() );
    if( firstElem == null )
    {
      return true;
    }

    PsiElement parent = firstElem.getParent();
    if( parent == null )
    {
      return true;
    }

    if( filterIllegalReferenceTo_var( hi ) )
    {
      return false;
    }

    if( filterAbstractError( hi, firstElem ) )
    {
      return false;
    }

    if( filterFinalError( hi, firstElem ) )
    {
      return false;
    }

    //noinspection RedundantIfStatement
    if( filterCannotAssignToFinalError( hi, firstElem ) )
    {
      return false;
    }

    return true;
  }

  private boolean filterCannotAssignToFinalError( HighlightInfo hi, PsiElement firstElem )
  {
    String msg = hi.getDescription();
    if( !msg.startsWith( "Cannot assign a value to final variable" ) )
    {
      return false;
    }

    PsiField field = getPropFieldFromExpr( firstElem );
    if( field == null )
    {
      return false;
    }

    // 'final' applies to getter/setter *methods*, read-only properties declared as @val are "final"
    return !isReadOnly( field );
  }

  private boolean filterAbstractError( HighlightInfo hi, PsiElement firstElem )
  {
    String msg = hi.getDescription();
    if( !(msg.contains( "'abstract'" ) && msg.contains( "not allowed here" )) )
    {
      return false;
    }

    // properties can be 'abstract'
    return getPropFieldFromDecl( firstElem ) != null;
  }

  private boolean filterFinalError( HighlightInfo hi, PsiElement firstElem )
  {
    String msg = hi.getDescription();
    if( !(msg.startsWith( "Variable" ) && msg.endsWith( "might not have been initialized" )) )
    {
      return false;
    }

    PsiField field = getPropFieldFromDecl( firstElem );
    // 'final' on properties applies to getter/setter *methods*;
    // todo: run the `final` checker on applicable @val fields separately
    return field != null;
  }

  private PsiField getPropFieldFromExpr( PsiElement elem )
  {
    while( !(elem instanceof PsiReferenceExpression) )
    {
      if( elem == null )
      {
        return null;
      }
      elem = elem.getParent();
    }

    PsiElement resolve = ((PsiReferenceExpression)elem).resolve();
    if( !(resolve instanceof PsiField) )
    {
      return getPropFieldFromExpr( elem.getParent() );
    }

    PsiField field = (PsiField)resolve;
    return isPropField( field ) ? field : null;
  }

  private PsiField getPropFieldFromDecl( PsiElement elem )
  {
    while( !(elem instanceof PsiField) )
    {
      if( elem == null )
      {
        return null;
      }
      elem = elem.getParent();
    }

    return isPropField( (PsiField)elem ) ? (PsiField)elem : null;
  }

  private boolean isPropField( PsiField field )
  {
    if( field.getCopyableUserData( PropertyInference.VAR_TAG ) != null )
    {
      return true;
    }

    for( Class<?> cls : Arrays.asList( var.class, val.class, get.class, set.class ) )
    {
      PsiAnnotation propAnno = field.getAnnotation( cls.getTypeName() );
      if( propAnno != null )
      {
        return true;
      }
    }
    return false;
  }

  private boolean isVar( PsiField field )
  {
    return hasVarTag( field, var.class ) || field.hasAnnotation( var.class.getTypeName() );
  }

  private boolean isVal( PsiField field )
  {
    return hasVarTag( field, val.class ) || field.hasAnnotation( val.class.getTypeName() );
  }

  private boolean isReadOnly( PsiField field )
  {
    return isVal( field ) ||
      (field.hasAnnotation( get.class.getTypeName() ) &&
        !field.hasAnnotation( set.class.getTypeName() ) &&
        !isVar( field ));
  }

  private boolean hasVarTag( PsiField field, Class<? extends Annotation> varClass )
  {
    PropertyInference.VarTagInfo tag = field.getCopyableUserData( PropertyInference.VAR_TAG );
    return tag != null && tag.varClass == varClass;
  }

  private boolean filterIllegalReferenceTo_var( @NotNull HighlightInfo hi )
  {
    return hi.getDescription().equals( "Illegal reference to restricted type 'var'" );
  }
}
