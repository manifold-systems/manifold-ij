package manifold.ij.extensions;

import com.intellij.psi.EmptySubstitutor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiCapturedWildcardType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeMapper;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.PsiWildcardType;
import com.intellij.psi.TypeAnnotationProvider;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.ArrayUtil;
import java.util.Arrays;
import manifold.ext.api.Self;
import org.jetbrains.annotations.NotNull;

public class SelfTypeUtil
{
  private static final SelfTypeUtil INSTANCE = new SelfTypeUtil();

  public static SelfTypeUtil instance()
  {
    return INSTANCE;
  }

  PsiType handleSelfType2( PsiType type, PsiType exprType, PsiReferenceExpression methodExpression )
  {
    if( !hasSelfAnnotation( type ) )
    {
      return exprType;
    }

    PsiType qualifierType;
    PsiExpression qualifier = (PsiExpression)methodExpression.getQualifier();
    if( qualifier == null )
    {
      PsiClass thisClass = RefactoringChangeUtil.getThisClass( methodExpression );
      String qualifiedName = thisClass.getQualifiedName();
      if( qualifiedName == null )
      {
        return exprType;
      }
      PsiElementFactory elementFactory = JavaPsiFacade.getInstance( methodExpression.getProject() )
        .getElementFactory();
      if( thisClass.getTypeParameters().length == 0 )
      {
        qualifierType = elementFactory.createType( thisClass );
      }
      else
      {
        // If the self type is generic, it must be parameterized with its type vars
        qualifierType = elementFactory.createType( thisClass,
          Arrays.stream( thisClass.getTypeParameters() ).map( elementFactory::createType ).toArray( PsiType[]::new ) );
      }
    }
    else
    {
      qualifierType = qualifier.getType();
    }

    return type.accept( new SelfReplacer( qualifierType ) );
  }

  class SelfReplacer extends PsiTypeMapper
  {
    private final PsiType _self;

    SelfReplacer( PsiType self )
    {
      _self = self;
    }

    @Override
    public PsiType visitArrayType( PsiArrayType type )
    {
      if( hasSelfAnnotationDirectly( type ) )
      {
        PsiType componentType = type.getComponentType();
        return new PsiArrayType( _self.annotate( getMergedProviderMinusSelf( componentType, _self ) ),
          type.getAnnotationProvider() );
      }
      return super.visitArrayType( type );
    }

    @Override
    public PsiType visitClassType( PsiClassType classType )
    {
      if( hasSelfAnnotationDirectly( classType ) )
      {
        return makeSelfTypeDirectly( classType );
      }

      if( hasSelfAnnotation( classType ) )
      {
        PsiClassType.ClassResolveResult resolve = classType.resolveGenerics();
        PsiClass psiClass = resolve.getElement();
        if( psiClass == null )
        {
          return classType;
        }

        PsiSubstitutor substitutor = resolve.getSubstitutor();
        int i = 0;
        for( PsiType typeParam: classType.getParameters() )
        {
          PsiType newType = typeParam.accept( this );
          PsiTypeParameter typeVar = psiClass.getTypeParameters()[i];
          substitutor = substitutor.put( typeVar, newType );
          i++;
        }
        classType = new PsiImmediateClassType( resolve.getElement(), substitutor );
      }

      return classType;
    }

    private PsiType makeSelfTypeDirectly( PsiClassType classType )
    {
      PsiClassType replacedType = (PsiClassType)_self.annotate( getMergedProviderMinusSelf( classType, _self ) );
// no need to derive type from @Self declared type, since only the *exact* enclosing class is allowed e.g., @Self Foo<T>  *not* @Self Foo<String>
//      if( classType.getParameterCount() > 0 )
//      {
//        replacedType = deriveParameterizedTypeFromAncestry( (PsiClassType)_self, classType );
//      }
      return replacedType;
    }

    private PsiClassType deriveParameterizedTypeFromAncestry( PsiClassType subType, PsiClassType superType )
    {
      if( subType.rawType().equals( superType.rawType() ) )
      {
        return superType;
      }

      PsiType[] superTypes = subType.getSuperTypes();
      for( PsiType st: superTypes )
      {
        PsiClassType superT = deriveParameterizedTypeFromAncestry( (PsiClassType)st, superType );
        if( superT != null )
        {
          return deriveParameterizedTypeFromSuper( subType, superT );
        }
      }
      return null;
    }

    private PsiClassType deriveParameterizedTypeFromSuper( PsiClassType subType, PsiClassType superType )
    {
      PsiClass superPsiClass = superType.resolve();
      PsiClass subPsiClass = subType.resolve();
      if( subPsiClass == null || superPsiClass == null )
      {
        return subType;
      }

      PsiSubstitutor substitutor = EmptySubstitutor.getInstance();

      for( PsiClassType st: subPsiClass.getSuperTypes() )
      {
        if( st.getParameterCount() == 0 )
        {
          continue;
        }

        PsiClass stClass = st.resolve();
        if( stClass != null )
        {
          String stFqn = stClass.getQualifiedName();
          if( stFqn != null && stFqn.equals( superPsiClass.getQualifiedName() ) )
          {
            PsiType[] parameters = st.getParameters();
            for( int i = 0; i < parameters.length; i++ )
            {
              PsiType param = parameters[i];
              PsiTypeParameter[] typeParameters = subPsiClass.getTypeParameters();
              for( int j = 0; j < typeParameters.length; j++ )
              {
                PsiTypeParameter subParam = typeParameters[j];
                String subName = subParam.getName();
                if( subName != null && subName.equals( ((PsiClassType)param).getName() ) )
                {
                  PsiClass actualSubParam = ((PsiClassType)subType.getParameters()[j]).resolve();
                  if( actualSubParam instanceof PsiTypeParameter )
                  {
                    substitutor = substitutor.put( (PsiTypeParameter)actualSubParam, superType.getParameters()[i] );
                  }
                }
              }
            }
          }
        }
      }
      return (PsiClassType)substitutor.substitute( subType );
    }

    @NotNull
    private TypeAnnotationProvider getMergedProviderMinusSelf( @NotNull PsiType type1, @NotNull PsiType type2 )
    {
      TypeAnnotationProvider result;
      if( type1.getAnnotationProvider() == TypeAnnotationProvider.EMPTY && !(type1 instanceof PsiClassReferenceType) )
      {
        result = type2.getAnnotationProvider();
      }
      else if( type2.getAnnotationProvider() == TypeAnnotationProvider.EMPTY && !(type2 instanceof PsiClassReferenceType) )
      {
        result = type1.getAnnotationProvider();
      }
      else
      {
        result = () -> ArrayUtil.mergeArrays( type1.getAnnotations(), type2.getAnnotations() );
      }
      return () -> Arrays.stream( result.getAnnotations() )
               .filter( e -> !Self.class.getTypeName().equals( e.getQualifiedName() ) )
               .toArray( PsiAnnotation[]::new );
    }
  }

  private boolean hasSelfAnnotationDirectly( PsiType type )
  {
    if( type == null )
    {
      return false;
    }

    for( PsiAnnotation anno: type.getAnnotations() )
    {
      if( Self.class.getTypeName().equals( anno.getQualifiedName() ) )
      {
        return true;
      }
    }
    return false;
  }

  boolean hasSelfAnnotation( PsiType type )
  {
    if( type instanceof PsiPrimitiveType )
    {
      return false;
    }

    if( hasSelfAnnotationDirectly( type ) )
    {
      return true;
    }

    if( type instanceof PsiClassType )
    {
      for( PsiType typeArg: ((PsiClassType)type).getParameters() )
      {
        if( hasSelfAnnotation( typeArg ) )
        {
          return true;
        }
      }
    }

    if( type instanceof PsiArrayType )
    {
      if( hasSelfAnnotation( ((PsiArrayType)type).getComponentType() ) )
      {
        return true;
      }
    }

    if( type instanceof PsiWildcardType )
    {
      PsiType bound = ((PsiWildcardType)type).getBound();
      if( bound != null && hasSelfAnnotation( bound ) )
      {
        return true;
      }
    }

    if( type instanceof PsiCapturedWildcardType )
    {
      return hasSelfAnnotation( ((PsiCapturedWildcardType)type).getWildcard() );
    }

    return false;
  }

}
