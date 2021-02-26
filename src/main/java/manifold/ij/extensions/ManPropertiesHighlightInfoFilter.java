package manifold.ij.extensions;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import manifold.ext.props.rt.api.*;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.psi.ManLightModifierListImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


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

//    if( filterPackageAccessError( hi, firstElem ) )
//    {
//      return false;
//    }

    if( filterAbstractError( hi, firstElem ) )
    {
      return false;
    }

    if( filterFinalError( hi, firstElem ) )
    {
      return false;
    }

    if( filterCannotAssignToFinalError( hi, firstElem ) )
    {
      return false;
    }

//    if( filterPrivateAccessError( hi, firstElem ) )
//    {
//      return false;
//    }

    return true;
  }

  private boolean filterPrivateAccessError( HighlightInfo hi, PsiElement firstElem )
  {
    String msg = hi.getDescription();
    if( !msg.contains( "' has private access" ) )
    {
      return false;
    }

    PsiField field = getPropFieldFromExpr( firstElem );
    if( field == null )
    {
      return false;
    }

    PsiAnnotation propgenAnno = field.getAnnotation( propgen.class.getTypeName() );
    if( propgenAnno == null )
    {
      return false;
    }
    long flags = (long)((PsiLiteralValue)propgenAnno.findAttributeValue( "flags" )).getValue();
    List<String> modifiers = new ArrayList<>();
    for( ModifierMap modifier : ModifierMap.values() )
    {
      if( (flags & modifier.getMod()) != 0 )
      {
        //noinspection MagicConstant
        modifiers.add( modifier.getName() );
      }
    }
    ManLightModifierListImpl modifierList = new ManLightModifierListImpl(
      firstElem.getManager(), JavaLanguage.INSTANCE, modifiers.toArray( new String[0] ) );
    PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance( firstElem.getProject() ).getResolveHelper();
    return resolveHelper.isAccessible( field, modifierList, firstElem, null, null );
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

  private boolean filterPackageAccessError( HighlightInfo hi, PsiElement firstElem )
  {
    String msg = hi.getDescription();
    if( !(msg.startsWith( "'" + firstElem.getText() + "'" ) &&
      msg.contains( "Cannot be accessed from outside package" )) )
    {
      return false;
    }

    PsiField field = getPropFieldFromExpr( firstElem );
    if( field == null )
    {
      return false;
    }

    // properties default to PUBLIC access
    return field.hasModifierProperty( PsiModifier.PACKAGE_LOCAL );
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
    return field.hasAnnotation( var.class.getTypeName() );
  }

  private boolean isVal( PsiField field )
  {
    return field.hasAnnotation( val.class.getTypeName() );
  }

  private boolean isReadWrite( PsiField field )
  {
    return field.hasAnnotation( var.class.getTypeName() ) ||
      (field.hasAnnotation( get.class.getTypeName() ) &&
        field.hasAnnotation( set.class.getTypeName() ));
  }

  private boolean isReadOnly( PsiField field )
  {
    return field.hasAnnotation( val.class.getTypeName() ) ||
      (field.hasAnnotation( get.class.getTypeName() ) &&
        !field.hasAnnotation( set.class.getTypeName() ) &&
        !field.hasAnnotation( var.class.getTypeName() ));
  }

  private boolean isWriteOnly( PsiField field )
  {
    return field.hasAnnotation( set.class.getTypeName() ) &&
      !field.hasAnnotation( get.class.getTypeName() ) &&
      !field.hasAnnotation( val.class.getTypeName() );
  }

  private boolean filterIllegalReferenceTo_var( @NotNull HighlightInfo hi )
  {
    return hi.getDescription().equals( "Illegal reference to restricted type 'var'" );
  }
}
