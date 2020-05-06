package manifold.ij.util;

//import com.intellij.openapi.util.Pair;
//import com.intellij.psi.JavaPsiFacade;
//import com.intellij.psi.LambdaUtil;
//import com.intellij.psi.PsiArrayType;
//import com.intellij.psi.PsiClass;
//import com.intellij.psi.PsiClassType;
//import com.intellij.psi.PsiDisjunctionType;
//import com.intellij.psi.PsiElementFactory;
//import com.intellij.psi.PsiIntersectionType;
//import com.intellij.psi.PsiManager;
//import com.intellij.psi.PsiMethod;
//import com.intellij.psi.PsiModifier;
//import com.intellij.psi.PsiParameter;
//import com.intellij.psi.PsiPrimitiveType;
//import com.intellij.psi.PsiReferenceList;
//import com.intellij.psi.PsiSubstitutor;
//import com.intellij.psi.PsiType;
//import com.intellij.psi.PsiTypeParameter;
//import com.intellij.psi.PsiWildcardType;
//import com.intellij.psi.search.GlobalSearchScope;
//import com.intellij.psi.util.ClassUtil;
//import com.intellij.psi.util.PsiTypesUtil;
//import com.intellij.psi.util.TypeConversionUtil;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.HashSet;
//import java.util.LinkedHashSet;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Set;

/**
 */
public class TypeUtil
{
//  public static boolean isStructurallyAssignable( PsiType toType, PsiType fromType, boolean structural )
//  {
//    if( fromType == PsiType.NULL )
//    {
//      return true;
//    }
//
//    if( toType instanceof PsiClassType && fromType instanceof PsiClassType )
//    {
//      return isStructurallyAssignable( type( toType ), type( fromType ), structural );
//    }
//    return false;
//  }
//
//  public static boolean isStructurallyAssignable( PsiClass toType, PsiClass fromType, boolean structural )
//  {
//    if( toType == null || fromType == null )
//    {
//      return false;
//    }
//
//    TypeVarToTypeMap inferenceMap = new TypeVarToTypeMap();
//    return isStructurallyAssignable( toType, fromType, inferenceMap, structural );
//  }
//
//  public static boolean isStructurallyAssignable( PsiClass toType, PsiClass fromType, TypeVarToTypeMap inferenceMap, boolean structural )
//  {
//    if( !isStructuralInterface( toType ) )
//    {
//      return false;
//    }
//
//    return isStructurallyAssignable_Laxed( toType, fromType, inferenceMap, structural );
//  }
//
//  public static boolean isStructurallyAssignable_Laxed( PsiClass toType, PsiClass fromType, TypeVarToTypeMap inferenceMap )
//  {
//    return isStructurallyAssignable_Laxed( toType, fromType, inferenceMap, true );
//  }
//  public static boolean isStructurallyAssignable_Laxed( PsiClass toType, PsiClass fromType, TypeVarToTypeMap inferenceMap, boolean structural )
//  {
//    if( fromType == PsiType.NULL )
//    {
//      return true;
//    }
//
//    List<Pair<PsiMethod, PsiSubstitutor>> toMethods = toType.getAllMethodsAndTheirSubstitutors();
//
//    inferenceMap.setStructural( true );
//
//    for( Pair<PsiMethod, PsiSubstitutor> pair : toMethods )
//    {
//      PsiMethod toMi = pair.getFirst();
//      if( isObjectMethod( toMi ) )
//      {
//        continue;
//      }
//      if( toMi.getContainingClass().getModifierList().findAnnotation( "manifold.ext.ExtensionMethod" ) != null )
//      {
//        continue;
//      }
//      if( toMi.hasModifierProperty( PsiModifier.DEFAULT ) || toMi.hasModifierProperty( PsiModifier.STATIC ) )
//      {
//        continue;
//      }
//      PsiMethod fromMi = findAssignableMethod( structural, fromType, toMi, inferenceMap );
//      if( fromMi == null )
//      {
//        return false;
//      }
//    }
//    return true;
//  }
//
//  public static PsiMethod findAssignableMethod( boolean structural, PsiClass fromMethods, PsiMethod miTo, TypeVarToTypeMap inferenceMap )
//  {
//    String mname = miTo.getName();
//    PsiMethod[] methods = fromMethods.findMethodsByName( mname, true );
//    if( methods.length == 0 )
//    {
//      return null;
//    }
//    PsiClass ownersType = miTo.getContainingClass();
//    PsiMethod foundMethod = null;
//    TypeVarToTypeMap foundInferenceMap = null;
//    PsiParameter[] toParams = miTo.getParameterList().getParameters();
//    int iTopScore = 0;
//    outer:
//    for( PsiMethod miFrom : methods )
//    {
//      if( miFrom.getName().equals( mname ) )
//      {
//        TypeVarToTypeMap copyInferenceMap = new TypeVarToTypeMap( inferenceMap );
//
//        PsiType toReturnType = maybeInferReturnType( copyInferenceMap, type( ownersType ), miFrom.getReturnType(), miTo.getReturnType() );
//        PsiType fromReturnType = replaceTypeVariableTypeParametersWithBoundingTypes( miFrom.getReturnType(), type( miFrom.getContainingClass() ) );
//        if( !isAssignable( structural, true, toReturnType, fromReturnType ) )
//        {
//          continue;
//        }
//        PsiParameter[] fromParams = miFrom.getParameterList().getParameters();
//        if( fromParams.length == toParams.length )
//        {
//          if( fromParams.length == 0 )
//          {
//            foundMethod = miFrom;
//            foundInferenceMap = copyInferenceMap;
//          }
//          int iScore = 0;
//          for( int ip = 0; ip < fromParams.length; ip++ )
//          {
//            PsiParameter fromParam = fromParams[ip];
//            PsiParameter toParam = toParams[ip];
//            PsiType toParamType = maybeInferParamType( copyInferenceMap, type( ownersType ), fromParam.getType(), toParam.getType() );
//            PsiType fromParamType = replaceTypeVariableTypeParametersWithBoundingTypes( fromParam.getType(), type( miFrom.getContainingClass() ) );
//            if( fromParamType.equals( toParamType ) )
//            {
//              // types are the same
//              iScore += 2;
//            }
//            else if( isAssignable( structural, false, fromParamType, toParamType ) )
//            {
//              // types are contravariant
//              iScore += 1;
//            }
//            else
//            {
//              continue outer;
//            }
//          }
//          if( iTopScore < iScore )
//          {
//            foundMethod = miFrom;
//            foundInferenceMap = copyInferenceMap;
//            iTopScore = iScore;
//          }
//        }
//      }
//    }
//    if( foundMethod != null )
//    {
//      inferenceMap.putAllAndInferred( foundInferenceMap );
//    }
//    return foundMethod;
//  }
//
//  private static boolean isAssignable( boolean structural, boolean covariant, PsiType to, PsiType from )
//  {
//    if( to.equals( from ) )
//    {
//      return true;
//    }
//
//    if( structural )
//    {
//      return TypeConversionUtil.isAssignable( to, from ) ||
//             arePrimitiveTypesAssignable( to, from ) ||
//             isStructurallyAssignable( to, from, structural ) ||
//             TypeConversionUtil.boxingConversionApplicable( to, from );
//    }
//
//    if( covariant )
//    {
//      TypeConversionUtil.isAssignable( to, from );
//    }
//
//    return false;
//  }
//
//  public static boolean arePrimitiveTypesAssignable( PsiType toType, PsiType fromType )
//  {
//    if( toType == null || fromType == null || !(toType instanceof PsiPrimitiveType) || !(fromType instanceof PsiPrimitiveType) )
//    {
//      return false;
//    }
//    if( toType == fromType )
//    {
//      return true;
//    }
//
//    if( toType == PsiType.DOUBLE )
//    {
//      return fromType == PsiType.FLOAT ||
//             fromType == PsiType.INT ||
//             fromType == PsiType.CHAR ||
//             fromType == PsiType.SHORT ||
//             fromType == PsiType.BYTE;
//    }
//    if( toType == PsiType.FLOAT )
//    {
//      return fromType == PsiType.CHAR ||
//             fromType == PsiType.SHORT ||
//             fromType == PsiType.BYTE;
//    }
//    if( toType == PsiType.LONG )
//    {
//      return fromType == PsiType.INT ||
//             fromType == PsiType.CHAR ||
//             fromType == PsiType.SHORT ||
//             fromType == PsiType.BYTE;
//    }
//    if( toType == PsiType.INT )
//    {
//      return fromType == PsiType.SHORT ||
//             fromType == PsiType.CHAR ||
//             fromType == PsiType.BYTE;
//    }
//    if( toType == PsiType.SHORT )
//    {
//      return fromType == PsiType.BYTE;
//    }
//
//    return false;
//  }
//
//  public static boolean isStructuralInterface( PsiClass toType )
//  {
//    return toType.getModifierList().findAnnotation( "manifold.ext.api.Structural" ) != null;
//  }
//
//  public static boolean isStructuralInterface( PsiType toType )
//  {
//    PsiClass psiClass = type( toType );
//    return psiClass != null && psiClass.getModifierList().findAnnotation( "manifold.ext.api.Structural" ) != null;
//  }
//
//  private static PsiClass type( PsiType psiType )
//  {
//    if( psiType instanceof PsiClassType )
//    {
//      return ((PsiClassType)psiType).resolve();
//    }
//    return null;
//  }
//
//  private static PsiType type( PsiClass psiClass )
//  {
//    return JavaPsiFacade.getInstance( psiClass.getProject() ).getElementFactory().createType( psiClass );
//  }
//
//  public static boolean isObjectMethod( PsiMethod mi )
//  {
//    PsiClass objectClass = ClassUtil.findPsiClass( mi.getManager(), Object.class.getName() );
//    PsiMethod objMethod = objectClass.findMethodBySignature( mi, false );
//    return objMethod != null;
//  }
//
//
//  public static PsiType maybeInferParamType( TypeVarToTypeMap inferenceMap, PsiType ownersType, PsiType fromParamType, PsiType toParamType )
//  {
//    int iCount = inferenceMap.size();
//
//    PsiType toCompType = toParamType;
//    while( toCompType instanceof PsiArrayType )
//    {
//      toCompType = ((PsiArrayType)toCompType).getComponentType();
//    }
//    if( isTypeVariable( toCompType ) || isParameterizedType( toCompType ) )
//    {
//      inferTypeVariableTypesFromGenParamTypeAndConcreteType_Reverse( toParamType, fromParamType, inferenceMap );
//      if( inferenceMap.size() > iCount )
//      {
//        PsiType actualType = getActualType( toParamType, inferenceMap, false );
//        toParamType = actualType == null ? toParamType : actualType;
//      }
//    }
//    return replaceTypeVariableTypeParametersWithBoundingTypes( toParamType, ownersType );
//  }
//
//  private static boolean isTypeVariable( PsiType toCompType )
//  {
//    if( toCompType instanceof PsiClassType )
//    {
//      PsiClass psiClass = ((PsiClassType)toCompType).resolve();
//      return psiClass instanceof PsiTypeParameter;
//    }
//    return false;
//  }
//
//  public static PsiType maybeInferReturnType( TypeVarToTypeMap inferenceMap, PsiType ownersType, PsiType fromReturnType, PsiType toReturnType )
//  {
//    int iCount = inferenceMap.size();
//
//    PsiType toCompType = toReturnType;
//    while( toCompType instanceof PsiArrayType )
//    {
//      toCompType = ((PsiArrayType)toCompType).getComponentType();
//    }
//    boolean bTypeVar = isTypeVariable( toCompType );
//    if( bTypeVar || isParameterizedType( toCompType ) )
//    {
//      inferTypeVariableTypesFromGenParamTypeAndConcreteType( toReturnType, fromReturnType, inferenceMap );
//      if( bTypeVar && inferenceMap.get( (PsiClassType)toCompType ) != null || inferenceMap.size() > iCount )
//      {
//        PsiType actualType = getActualType( toReturnType, inferenceMap, false );
//        toReturnType = actualType == null ? toReturnType : actualType;
//      }
//    }
//    return replaceTypeVariableTypeParametersWithBoundingTypes( toReturnType, ownersType );
//  }
//
//  public static boolean isParameterizedType( PsiType type )
//  {
//    return type instanceof PsiClassType && ((PsiClassType)type).getParameterCount() > 0;
//  }
//
//  public static PsiClassType parameterizeType( PsiClassType type, PsiType... args )
//  {
//    PsiClass psiClass = type( type );
//    return PsiElementFactory.getInstance( psiClass.getProject() ).createType( psiClass, args );
//  }
//
//  public static PsiType getActualType( PsiType type, TypeVarToTypeMap actualParamByVarName )
//  {
//    return getActualType( type, actualParamByVarName, false );
//  }
//
//  public static PsiType getActualType( PsiType type, TypeVarToTypeMap actualParamByVarName, boolean bKeepTypeVars )
//  {
//    return getActualType( type, actualParamByVarName, bKeepTypeVars, new LinkedHashSet<PsiType>() );
//  }
//
//  public static PsiType getActualType( PsiType type, TypeVarToTypeMap actualParamByVarName, boolean bKeepTypeVars, LinkedHashSet<PsiType> recursiveTypes )
//  {
//    PsiType retType;
//    if( type instanceof PsiClassType )
//    {
//      retType = type;
//    }
//    else if( isTypeVariable( type ) )
//    {
//      retType = actualParamByVarName.getByMatcher( (PsiClassType)type, RawTypeVarMatcher.instance() );
//      if( retType == null )
//      {
//        // the type must come from the map, otherwise it comes from a context where there is no argument for the type var, hence the error type
//        return null; //## todo: need "ErrorType"
//      }
//      if( bKeepTypeVars && isTypeVariable( retType ) && type.equals( retType ) && getBoundingType( (PsiTypeParameter)type( retType ) ) != null )
//      {
//        PsiClass typeVar = ((PsiClassType)retType).resolve();
//
//        PsiClass boundingType = getBoundingType( (PsiTypeParameter)typeVar );
//        PsiType actualBoundingType = getActualType( type( boundingType ), actualParamByVarName, bKeepTypeVars );
//        if( !actualBoundingType.equals( type( boundingType ) ) )
//        {
//          retType = actualBoundingType;
//        }
//      }
//      else if( !bKeepTypeVars )
//      {
//        retType = getDefaultParameterizedTypeWithTypeVars( retType );
//      }
//    }
//    else if( type instanceof PsiWildcardType )
//    {
//      PsiType bound = ((PsiWildcardType)type).getExtendsBound();
//      PsiType lowerBound = maybeGetLowerBound( (PsiWildcardType)type, actualParamByVarName, bKeepTypeVars, recursiveTypes );
//      if( lowerBound != null )
//      {
//        bound = lowerBound;
//      }
//      retType = getActualType( bound, actualParamByVarName, bKeepTypeVars, recursiveTypes );
//    }
//    else if( isParameterizedType( type ) )
//    {
//      recursiveTypes.add( type );
//      try
//      {
//        PsiType genType = getActualType( ((PsiClassType)type).rawType(), actualParamByVarName, bKeepTypeVars, recursiveTypes );
//        PsiType[] typeArgs = ((PsiClassType)type).getParameters();
//        if( typeArgs == null || typeArgs.length == 0 )
//        {
//          retType = genType;
//        }
//        else
//        {
//          PsiType[] types = new PsiType[typeArgs.length];
//          for( int i = 0; i < types.length; i++ )
//          {
//            PsiType typeArg = typeArgs[i];
//            if( !bKeepTypeVars && isTypeVariable( typeArg ) )
//            {
//              PsiType bound = type( getBoundingType( (PsiTypeParameter)((PsiClassType)typeArg).resolve() ) );
//              if( !recursiveTypes.contains( bound ) )
//              {
//                types[i] = getActualType( bound, actualParamByVarName, bKeepTypeVars, recursiveTypes );
//              }
//              else if( isParameterizedType( bound ) )
//              {
//                types[i] = getActualType( ((PsiClassType)bound).rawType(), actualParamByVarName, bKeepTypeVars, recursiveTypes );
//              }
//              else
//              {
//                throw new IllegalStateException( "Expecting bound to be a ParameterizedType here" );
//              }
//            }
//            else
//            {
//              if( typeArg instanceof PsiWildcardType && (((PsiWildcardType)typeArg).getExtendsBound().equalsToText( Object.class.getName() ) ||
//                                                         ((PsiWildcardType)typeArg).getSuperBound() != PsiType.NULL) )
//              {
//                PsiType lowerBound = maybeGetLowerBound( (PsiWildcardType)typeArg, actualParamByVarName, bKeepTypeVars, recursiveTypes );
//                if( lowerBound == null )
//                {
//                  // Object is the default type for the naked <?> wildcard, so we have to get the actual bound, if different, from the corresponding type var
//                  PsiClass boundingTypes = getBoundingType( ((PsiClassType)type).rawType().resolve().getTypeParameters()[i] );
//                  PsiType boundingType = boundingTypes == null ? null : type( boundingTypes );
//                  if( boundingType != null )
//                  {
//                    typeArg = boundingType;
//                  }
//                }
//              }
//
//              types[i] = getActualType( typeArg, actualParamByVarName, bKeepTypeVars, recursiveTypes );
//            }
//          }
//          retType = parameterizeType( (PsiClassType)genType, types );
//        }
//      }
//      finally
//      {
//        recursiveTypes.remove( type );
//      }
//    }
//    else if( type.getArrayDimensions() > 0 )
//    {
//      retType = new PsiArrayType( getActualType( ((PsiArrayType)type).getComponentType(), actualParamByVarName, bKeepTypeVars, recursiveTypes ) );
//    }
//    else
//    {
//      //retType = parseType( normalizeJavaTypeName( type ), actualParamByVarName, bKeepTypeVars, null );
//      throw new IllegalStateException();
//    }
//    return retType;
//  }
//
//  public static PsiType getDefaultParameterizedTypeWithTypeVars( PsiType type )
//  {
//    return getDefaultParameterizedTypeWithTypeVars( type, null, new HashSet<PsiType>() );
//  }
//
//  public static PsiType getDefaultParameterizedTypeWithTypeVars( PsiType type, TypeVarToTypeMap map )
//  {
//    return getDefaultParameterizedTypeWithTypeVars( type, map, new HashSet<PsiType>() );
//  }
//
//  public static PsiType getDefaultParameterizedTypeWithTypeVars( PsiType type, TypeVarToTypeMap map, Set<PsiType> visited )
//  {
//    if( type.getArrayDimensions() > 0 )
//    {
//      return new PsiArrayType( getDefaultParameterizedTypeWithTypeVars( ((PsiArrayType)type).getComponentType(), map, visited ) );
//    }
//
//    if( isTypeVariable( type ) )
//    {
//      if( map != null )
//      {
//        final PsiType assignedType = map.get( (PsiClassType)type );
//        if( assignedType != null )
//        {
//          return assignedType;
//        }
//      }
//      return getDefaultParameterizedTypeWithTypeVars( getBoundingType( type ), map, visited );
//    }
//
//    if( isTypeVariable( type.getDeepComponentType() ) )
//    {
//      return new PsiArrayType( getDefaultParameterizedTypeWithTypeVars( getBoundingType( ((PsiArrayType)type).getComponentType() ), map, visited ) );
//    }
//
//    if( !isGenericType( type ) && !isParameterizedType( type ) )
//    {
//      return type;
//    }
//
//    if( !visited.contains( type ) )
//    {
//      visited.add( type );
//      if( isParameterizedType( type ) && isRecursiveType( type ) )
//      {
//        PsiType[] typeParameters = ((PsiClassType)type).getParameters();
//        PsiType[] typeParams = new PsiType[typeParameters.length];
//        int i = 0;
//        for( PsiType param : typeParameters )
//        {
//          typeParams[i++] = getDefaultParameterizedTypeWithTypeVars( param, map, visited );
//        }
//        return parameterizeType( ((PsiClassType)type).rawType(), typeParams );
//      }
//      else if( isGenericType( type ) && !isParameterizedType( type ) && isRecursiveTypeFromBase( type ) )
//      {
//        final PsiTypeParameter[] gvs = type( type ).getTypeParameters();
//        PsiType[] typeParams = new PsiType[gvs.length];
//        int i = 0;
//        for( PsiTypeParameter param : gvs )
//        {
//          final PsiClassType typeVar = (PsiClassType)type( param );
//          if( typeVar != null )
//          {
//            if( isRecursiveType( typeVar, getBoundingType( typeVar ) ) )
//            {
//              // short-circuit recursive typevar
//              typeParams[i++] = ((PsiClassType)getBoundingType( typeVar )).rawType();
//            }
//            else
//            {
//              typeParams[i++] = getDefaultParameterizedTypeWithTypeVars( typeVar, map, visited );
//            }
//          }
//          else
//          {
//            typeParams[i++] = getDefaultParameterizedTypeWithTypeVars( getBoundingType( typeVar ), map, visited );
//          }
//        }
//        return parameterizeType( ((PsiClassType)type).rawType(), typeParams );
//      }
//    }
//
//    type = ((PsiClassType)type).rawType();
//    return makeDefaultParameterizedType( type );
//  }
//
//  public static PsiType makeDefaultParameterizedType( PsiType type )
//  {
//    if( type != null && !isStructuralInterface( type ) &&
//        !isParameterizedType( type ) && isGenericType( type ) )
//    {
//      PsiTypeParameter[] typeVars = type( type ).getTypeParameters();
//      PsiType[] boundingTypes = new PsiType[typeVars.length];
//      for( int i = 0; i < boundingTypes.length; i++ )
//      {
//        PsiTypeParameter typeVar = typeVars[i];
//        boundingTypes[i] = type( getBoundingType( typeVar ) );
//        if( isRecursiveType( (PsiClassType)type( typeVar ), boundingTypes[i] ) )
//        {
//          return type;
//        }
//      }
//
//      if( boundingTypes.length == 0 )
//      {
//        return type;
//      }
//
//      type = parameterizeType( (PsiClassType)type, boundingTypes );
//    }
//    return type;
//  }
//
//  private static PsiType getBoundingType( PsiType type )
//  {
//    return type( getBoundingType( (PsiTypeParameter)type( type ) ) );
//  }
//
//  public static boolean isRecursiveTypeFromBase( PsiType rootType )
//  {
//    if( !isGenericType( rootType ) && !isParameterizedType( rootType ) )
//    {
//      return false;
//    }
//
//    PsiType genType = ((PsiClassType)rootType).rawType();
//    if( !genType.equals( getDefaultParameterizedType( genType, type( rootType ).getManager() ) ) )
//    {
//      return false;
//    }
//
//    if( isGenericType( rootType ) && !isParameterizedType( rootType ) )
//    {
//      if( rootType.equals( getDefaultParameterizedType( rootType, type( rootType ).getManager() ) ) )
//      {
//        return true;
//      }
//    }
//    else if( isParameterizedType( rootType ) )
//    {
//      for( PsiType typeParam : ((PsiClassType)rootType).getParameters() )
//      {
//        if( isRecursiveTypeFromBase( typeParam ) )
//        {
//          return true;
//        }
//      }
//    }
//    return false;
//  }
//
//  public static PsiType getDefaultParameterizedType( PsiType type, PsiManager mgr )
//  {
//    if( type.getArrayDimensions() > 0 )
//    {
//      PsiType defType = getDefaultParameterizedType( ((PsiArrayType)type).getComponentType(), mgr );
//      if( !defType.equals( type ) )
//      {
//        return new PsiArrayType( defType );
//      }
//      return type;
//    }
//    if( type instanceof PsiIntersectionType )
//    {
//      return makeDefaultParameterizedTypeForCompoundType( (PsiIntersectionType)type, mgr );
//    }
//    if( type instanceof PsiDisjunctionType )
//    {
//      return getDefaultParameterizedType( PsiTypesUtil.getLowestUpperBoundClassType( (PsiDisjunctionType)type ), mgr );
//    }
//    if( !isGenericType( type ) && !isParameterizedType( type ) )
//    {
//      return type;
//    }
//    type = ((PsiClassType)type).rawType();
//    return makeDefaultParameterizedType( type );
//  }
//
//  private static PsiType makeDefaultParameterizedTypeForCompoundType( PsiIntersectionType type, PsiManager mgr )
//  {
//    PsiType[] types = type.getConjuncts();
//    PsiType[] defCompTypes = new PsiType[types.length];
//    int i = 0;
//    boolean bDifferent = false;
//    for( PsiType compType : types )
//    {
//      defCompTypes[i++] = getDefaultParameterizedType( compType, mgr );
//      bDifferent = bDifferent || !defCompTypes[i].equals( compType );
//    }
//    if( bDifferent )
//    {
//      return PsiIntersectionType.createIntersection( defCompTypes );
//    }
//    return type;
//  }
//
//  private static PsiClass getBoundingType( PsiTypeParameter tp )
//  {
//    PsiReferenceList extendsList = tp.getExtendsList();
//    PsiClassType[] referencedTypes = extendsList.getReferencedTypes();
//    if( referencedTypes.length > 0 )
//    {
//      return referencedTypes[0].resolve();
//    }
//    return ClassUtil.findPsiClass( tp.getManager(), Object.class.getName() );
//  }
//
//  private static PsiType maybeGetLowerBound( PsiWildcardType type, TypeVarToTypeMap actualParamByVarName, boolean bKeepTypeVars, LinkedHashSet<PsiType> recursiveTypes )
//  {
//    PsiType lower = type.getSuperBound();
//    if( lower != PsiType.NULL && recursiveTypes.size() > 0 )
//    {
//      // This is a "super" (contravariant) wildcard
//
//      LinkedList<PsiType> list = new LinkedList<>( recursiveTypes );
//      PsiType enclType = list.getLast();
//      if( isParameterizedType( enclType ) )
//      {
//        PsiType genType = getActualType( ((PsiClassType)enclType).rawType(), actualParamByVarName, bKeepTypeVars, recursiveTypes );
//        if( LambdaUtil.isFunctionalType( genType ) )
//        {
//          // For functional interfaces we keep the lower bound as an upper bound so that blocks maintain contravariance wrt the single method's parameters
//          return lower;
//        }
//      }
//    }
//    return null;
//  }
//
//  public static PsiType replaceTypeVariableTypeParametersWithBoundingTypes( PsiType type )
//  {
//    return replaceTypeVariableTypeParametersWithBoundingTypes( type, null );
//  }
//
//  public static PsiType replaceTypeVariableTypeParametersWithBoundingTypes( PsiType type, PsiType enclType )
//  {
//    if( isTypeVariable( type ) )
//    {
//      PsiClass boundingType = getBoundingType( (PsiTypeParameter)((PsiClassType)type).resolve() );
//
//      if( isRecursiveType( (PsiClassType)type, type( boundingType ) ) )
//      {
//        // short-circuit recursive typevar
//        return ((PsiClassType)type( boundingType )).rawType();
//      }
//
//      if( enclType != null && isParameterizedType( enclType ) )
//      {
//        TypeVarToTypeMap map = mapTypeByVarName( enclType, enclType );
//        return replaceTypeVariableTypeParametersWithBoundingTypes( getActualType( type( boundingType ), map, true ) );
//      }
//
//      return replaceTypeVariableTypeParametersWithBoundingTypes( type( boundingType ), enclType );
//    }
//
//    if( type.getArrayDimensions() > 0 )
//    {
//      return new PsiArrayType( replaceTypeVariableTypeParametersWithBoundingTypes( ((PsiArrayType)type).getComponentType(), enclType ) );
//    }
//
//    if( type instanceof PsiIntersectionType )
//    {
//      PsiType[] types = ((PsiIntersectionType)type).getConjuncts();
//      Set<PsiType> newTypes = new HashSet<>();
//      for( PsiType t : types )
//      {
//        newTypes.add( replaceTypeVariableTypeParametersWithBoundingTypes( t ) );
//      }
//      if( newTypes.size() == 1 )
//      {
//        return newTypes.iterator().next();
//      }
//      return PsiIntersectionType.createIntersection( new ArrayList<>( newTypes ) );
//    }
//
//    if( isParameterizedType( type ) )
//    {
//      PsiType[] typeParams = ((PsiClassType)type).getParameters();
//      PsiType[] concreteParams = new PsiType[typeParams.length];
//      for( int i = 0; i < typeParams.length; i++ )
//      {
//        concreteParams[i] = replaceTypeVariableTypeParametersWithBoundingTypes( typeParams[i], enclType );
//      }
//      type = parameterizeType( (PsiClassType)type, concreteParams );
//    }
//    else if( type instanceof PsiClassType )
//    {
//      PsiClass psiClass = ((PsiClassType)type).resolve();
//      PsiTypeParameter[] typeVars = psiClass.getTypeParameters();
//      PsiType[] boundingTypes = new PsiType[typeVars.length];
//      for( int i = 0; i < boundingTypes.length; i++ )
//      {
//        boundingTypes[i] = type( getBoundingType( typeVars[i] ) );
//
//        if( isRecursiveType( (PsiClassType)type( typeVars[i] ), boundingTypes[i] ) )
//        {
//          return type;
//        }
//      }
//      for( int i = 0; i < boundingTypes.length; i++ )
//      {
//        boundingTypes[i] = replaceTypeVariableTypeParametersWithBoundingTypes( boundingTypes[i], enclType );
//      }
//      type = parameterizeType( (PsiClassType)type, boundingTypes );
//    }
//    else if( type instanceof PsiWildcardType )
//    {
//      replaceTypeVariableTypeParametersWithBoundingTypes( ((PsiWildcardType)type).getExtendsBound() );
//    }
//    return type;
//  }
//
//  public static TypeVarToTypeMap mapTypeByVarName( PsiType ownersType, PsiType declaringType )
//  {
//    TypeVarToTypeMap actualParamByVarName;
//    ownersType = findActualDeclaringType( ownersType, declaringType );
//    if( ownersType != null && isParameterizedType( ownersType ) )
//    {
//      actualParamByVarName = mapActualTypeByVarName( ownersType );
//    }
//    else
//    {
//      actualParamByVarName = mapGenericTypeByVarName( ownersType );
//      if( ownersType != null )
//      {
//        while( type( ownersType ).getContainingClass() != null )
//        {
//          ownersType = type( type( ownersType ).getContainingClass() );
//          TypeVarToTypeMap vars = mapGenericTypeByVarName( ownersType );
//          if( actualParamByVarName.isEmpty() )
//          {
//            actualParamByVarName = vars;
//          }
//          else
//          {
//            actualParamByVarName.putAll( vars );
//          }
//        }
//      }
//    }
//    return actualParamByVarName;
//  }
//
//  private static TypeVarToTypeMap mapActualTypeByVarName( PsiType ownersType )
//  {
//    TypeVarToTypeMap actualParamByVarName = new TypeVarToTypeMap();
//    PsiTypeParameter[] vars = type( ownersType ).getTypeParameters();
//    if( vars != null )
//    {
//      PsiType[] paramArgs = ((PsiClassType)ownersType).getParameters();
//      for( int i = 0; i < vars.length; i++ )
//      {
//        PsiClassType typeVar = (PsiClassType)type( vars[i] );
//        if( paramArgs.length > i )
//        {
//          actualParamByVarName.put( typeVar, paramArgs[i] );
//        }
//      }
//    }
//    return actualParamByVarName;
//  }
//
//  private static TypeVarToTypeMap mapGenericTypeByVarName( PsiType ownersType )
//  {
//    TypeVarToTypeMap genericParamByVarName = TypeVarToTypeMap.EMPTY_MAP;
//    if( !(ownersType instanceof PsiClassType) )
//    {
//      return genericParamByVarName;
//    }
//
//    PsiClassType genType = null;
//    if( ownersType != null )
//    {
//      genType = ((PsiClassType)ownersType).rawType();
//    }
//    if( genType != null )
//    {
//      genericParamByVarName = new TypeVarToTypeMap();
//      PsiTypeParameter[] vars = type( genType ).getTypeParameters();
//      if( vars != null )
//      {
//        for( int i = 0; i < vars.length; i++ )
//        {
//          PsiTypeParameter typeVar = vars[i];
//          PsiClassType type = (PsiClassType)type( typeVar );
//          if( !genericParamByVarName.containsKey( type ) )
//          {
//            genericParamByVarName.put( type, type );
//          }
//        }
//      }
//    }
//    return genericParamByVarName;
//  }
//
//  // If the declaring type is generic and the owning type is parameterized, we need to
//  // find the corresponding parameterized type of the declaring type e.g.
//  //
//  // class Base<T> {
//  //   function blah() : Bar<T> {}
//  // }
//  // class Foo<T> extends Base<T> {}
//  //
//  // new Foo<String>().blah() // infer return type as Bar<String>
//  //
//  // The declaring class of blah() is generic class Base<T> (not a parameterized one),
//  // while the owner's type is Foo<String>, thus in order to resolve the actual return
//  // type for blah() we must walk the ancestry of Foo<String> until find the corresponding
//  // parameterized type for Base<T>.
//  private static PsiType findActualDeclaringType( PsiType ownersType, PsiType declaringType )
//  {
//    if( ownersType == null || ownersType == PsiType.NULL )
//    {
//      return null;
//    }
//
//    if( isParameterizedType( declaringType ) && !isGenericType( declaringType ) )
//    {
//      return declaringType;
//    }
//
//    if( ownersType.equals( declaringType ) )
//    {
//      return declaringType;
//    }
//
//    if( ((PsiClassType)ownersType).rawType().equals( declaringType ) )
//    {
//      return ownersType;
//    }
//
//    PsiType actualDeclaringType = findActualDeclaringType( type( type( ownersType ).getSuperClass() ), declaringType );
//    if( actualDeclaringType != null && !actualDeclaringType.equals( declaringType ) )
//    {
//      return actualDeclaringType;
//    }
//
//    for( PsiClass iface : type( ownersType ).getInterfaces() )
//    {
//      actualDeclaringType = findActualDeclaringType( type( iface ), declaringType );
//      if( actualDeclaringType != null && !actualDeclaringType.equals( declaringType ) )
//      {
//        return actualDeclaringType;
//      }
//    }
//
//    return declaringType;
//  }
//
//  public static boolean isGenericType( PsiType type )
//  {
//    return type instanceof PsiClassType && ((PsiClassType)type).resolve().getTypeParameters().length > 0;
//  }
//
//  public static boolean isRecursiveType( PsiType declaringClass )
//  {
//    return _isRecursiveType( declaringClass, new HashSet<>() );
//  }
//
//  private static boolean _isRecursiveType( PsiType declaringClass, Set<PsiClassType> visited )
//  {
//    if( isTypeVariable( declaringClass ) )
//    {
//      if( visited.contains( declaringClass ) )
//      {
//        return true;
//      }
//      visited.add( (PsiClassType)declaringClass );
//      try
//      {
//        return _isRecursiveType( getBoundingType( declaringClass ), visited );
//      }
//      finally
//      {
//        visited.remove( declaringClass );
//      }
//    }
//
//    if( declaringClass.getArrayDimensions() > 0 )
//    {
//      return _isRecursiveType( ((PsiArrayType)declaringClass).getComponentType(), visited );
//    }
//
//    if( !isGenericType( declaringClass ) && !isParameterizedType( declaringClass ) )
//    {
//      return false;
//    }
//
//    if( isGenericType( declaringClass ) && !isParameterizedType( declaringClass ) )
//    {
//      PsiTypeParameter[] typeVars = ((PsiClassType)declaringClass).rawType().resolve().getTypeParameters();
//      for( PsiTypeParameter gtv : typeVars )
//      {
//        if( _isRecursiveType( type( gtv ), visited ) )
//        {
//          return true;
//        }
//      }
//    }
//    else if( isParameterizedType( declaringClass ) )
//    {
//      for( PsiType typeParam : ((PsiClassType)declaringClass).getParameters() )
//      {
//        if( _isRecursiveType( typeParam, visited ) )
//        {
//          return true;
//        }
//      }
//    }
//    return false;
//  }
//
//  public static boolean isRecursiveType( PsiClassType subject, PsiType... types )
//  {
//    return _isRecursiveType( subject, new HashSet<>(), types );
//  }
//
//  private static boolean _isRecursiveType( PsiClassType subject, Set<PsiClassType> visited, PsiType... types )
//  {
//    visited.add( subject );
//
//    for( PsiType csr : types )
//    {
//      if( !(csr instanceof PsiClassType) )
//      {
//        continue;
//      }
//
//      for( PsiClassType subj : visited )
//      {
//        if( ((PsiClassType)csr).rawType().equals( subj.rawType() ) )
//        {
//          // Short-circuit recursive type parameterization e.g., class Foo<T extends Foo<T>>
//          return true;
//        }
//      }
//      if( isParameterizedType( csr ) )
//      {
//        if( _isRecursiveType( subject, visited, ((PsiClassType)csr).getParameters() ) )
//        {
//          return true;
//        }
//      }
//      else if( isGenericType( csr ) )
//      {
//        PsiTypeParameter[] typeVars = ((PsiClassType)csr).rawType().resolve().getTypeParameters();
//        for( PsiTypeParameter gtv : typeVars )
//        {
//          if( _isRecursiveType( subject, visited, type( gtv ) ) )
//          {
//            return true;
//          }
//        }
//      }
//      else if( isTypeVariable( csr ) )
//      {
//        if( !visited.contains( csr ) && _isRecursiveType( (PsiClassType)csr, visited, getBoundingType( csr ) ) )
//        {
//          return true;
//        }
//        visited.remove( csr );
//        if( _isRecursiveType( subject, visited, getBoundingType( csr ) ) )
//        {
//          return true;
//        }
//      }
//      else if( csr.getArrayDimensions() > 0 )
//      {
//        if( _isRecursiveType( subject, visited, ((PsiArrayType)csr).getComponentType() ) )
//        {
//          return true;
//        }
//      }
//    }
//    return false;
//  }
//
//  /**
//   */
//  public static void inferTypeVariableTypesFromGenParamTypeAndConcreteType( PsiType genParamType, PsiType argType, TypeVarToTypeMap inferenceMap )
//  {
//    inferTypeVariableTypesFromGenParamTypeAndConcreteType( genParamType, argType, inferenceMap, new HashSet<>(), false );
//  }
//
//  public static void inferTypeVariableTypesFromGenParamTypeAndConcreteType_Reverse( PsiType genParamType, PsiType argType, TypeVarToTypeMap inferenceMap )
//  {
//    inferTypeVariableTypesFromGenParamTypeAndConcreteType( genParamType, argType, inferenceMap, new HashSet<>(), true );
//  }
//
//  public static void inferTypeVariableTypesFromGenParamTypeAndConcreteType( PsiType genParamType, PsiType argType, TypeVarToTypeMap inferenceMap, HashSet<PsiClassType> inferredInCallStack, boolean bReverse )
//  {
//    if( argType == PsiType.NULL )
//    {
//      return;
//    }
//
//    if( argType instanceof PsiPrimitiveType )
//    {
//      argType = ((PsiPrimitiveType)argType).getBoxedType( type( genParamType ) );
//    }
//
//    if( argType instanceof PsiWildcardType )
//    {
//      argType = ((PsiWildcardType)argType).getExtendsBound();
//    }
//
//    if( genParamType.getArrayDimensions() > 0 )
//    {
//      if( argType.getArrayDimensions() <= 0 )
//      {
//        return;
//      }
//      //## todo: DON'T allow a null component type here; we do it now as a hack that enables gosu arrays to be compatible with java arrays
//      //## todo: same as JavaMethodInfo.inferTypeVariableTypesFromGenParamTypeAndConcreteType()
//      if( ((PsiArrayType)argType).getComponentType() == null || !(((PsiArrayType)argType).getComponentType() instanceof PsiPrimitiveType) )
//      {
//        inferTypeVariableTypesFromGenParamTypeAndConcreteType( ((PsiArrayType)genParamType).getComponentType(), ((PsiArrayType)argType).getComponentType(), inferenceMap, inferredInCallStack, bReverse );
//      }
//    }
//    else if( isParameterizedType( genParamType ) )
//    {
//      if( isStructuralInterface( type( genParamType ) ) )
//      {
//        if( isStructurallyAssignable_Laxed( type( genParamType ), type( argType ), inferenceMap ) )
//        {
//          return;
//        }
//      }
//
//      PsiType argTypeInTermsOfParamType = bReverse ? findParameterizedType_Reverse( (PsiClassType)argType, (PsiClassType)genParamType ) : findParameterizedType( (PsiClassType)argType, ((PsiClassType)genParamType).rawType() );
//      if( argTypeInTermsOfParamType == null )
//      {
//        argTypeInTermsOfParamType = !bReverse ? findParameterizedType_Reverse( (PsiClassType)argType, (PsiClassType)genParamType ) : findParameterizedType( (PsiClassType)argType, ((PsiClassType)genParamType).rawType() );
//        if( argTypeInTermsOfParamType == null )
//        {
//          return;
//        }
//      }
//      PsiType[] concreteTypeParams = ((PsiClassType)argTypeInTermsOfParamType).getParameters();
//      if( concreteTypeParams != null && concreteTypeParams.length > 0 )
//      {
//        int i = 0;
//        PsiType[] genTypeParams = ((PsiClassType)genParamType).getParameters();
//        if( concreteTypeParams.length >= genTypeParams.length )
//        {
//          for( PsiType typeArg : genTypeParams )
//          {
//            inferTypeVariableTypesFromGenParamTypeAndConcreteType( typeArg, concreteTypeParams[i++], inferenceMap, inferredInCallStack, bReverse );
//          }
//        }
//      }
//    }
//    else if( isTypeVariable( genParamType ) && argType != PsiType.NULL )
//    {
//      PsiClassType tvType = (PsiClassType)genParamType;
//      Pair<PsiType, Boolean> pair = inferenceMap.getPair( tvType );
//      PsiType type = pair == null ? null : pair.getFirst();
//      boolean pairReverse = pair != null && pair.getSecond();
//
//      if( type == null || type instanceof PsiClassType )
//      {
//        // Infer the type
//        inferenceMap.put( tvType, getActualType( argType, inferenceMap, true ), bReverse );
//        inferredInCallStack.add( tvType );
//        if( type != null && type.equals( argType ) )
//        {
//          return;
//        }
//      }
//      else if( type != null )
//      {
//        PsiType combinedType = solveType( genParamType, argType, inferenceMap, bReverse || pairReverse, tvType, type );
//        inferenceMap.put( tvType, combinedType, bReverse );
//      }
//      PsiType boundingType = getBoundingType( genParamType );
//      if( !isRecursiveType( (PsiClassType)genParamType, boundingType ) )
//      {
//        inferTypeVariableTypesFromGenParamTypeAndConcreteType( boundingType, argType, inferenceMap, inferredInCallStack, bReverse );
//      }
//    }
//  }
//
//  private static PsiType solveType( PsiType genParamType, PsiType argType, TypeVarToTypeMap inferenceMap, boolean bReverse, PsiClassType tvType, PsiType type )
//  {
//    // Solve the type.  Either LUB or GLB.
//    //
//    // Infer the type as the intersection of the existing inferred type and this one.  This is most relevant for
//    // case where we infer a given type var from more than one type context e.g., a method call:
//    // var l : String
//    // var s : StringBuilder
//    // var r = foo( l, s ) // here we must use the LUB of String and StringBuilder, which is CharSequence & Serializable
//    // function foo<T>( t1: T, t2: T ) {}
//    //
//    // Also handle inferring a type from a structure type's methods:
//    //
//
//    PsiType lubType;
//    if( bReverse )
//    {
//      // Contravariant
//      lubType = findGreatestLowerBound( type, argType );
//    }
//    else
//    {
//      if( inferenceMap.isInferredForCovariance( tvType ) )
//      {
//        // Covariant
//        lubType = argType.equals( genParamType )
//                  ? type
//                  : PsiTypesUtil.getLowestUpperBoundClassType( (PsiDisjunctionType)PsiDisjunctionType.createDisjunction( Arrays.asList( type, argType ), type( type ).getManager() ) );
//      }
//      else
//      {
//        // Contravariant
//
//        // This is the first type encountered in a return/covariant position, the prior type[s] are in contravariant positions,
//        // therefore we can apply contravariance to maintain the return type's more specific type i.e., since the other type[s]
//        // are all param types and are contravariant with tvType, we should keep the more specific type between them.  Note if
//        // the param type is more specific, tvType's variance is broken either way (both lub and glb produce a type that is not
//        // call-compatible).
//        lubType = findGreatestLowerBound( type, argType );
//      }
//
//      // We have inferred tvType from a covariant position, so we infer using covariance in subsequent positions
//      inferenceMap.setInferredForCovariance( tvType );
//    }
//    return lubType;
//  }
//
//  private static PsiType findGreatestLowerBound( PsiType t1, PsiType t2 )
//  {
//    if( t1.equals( t2 ) )
//    {
//      return t1;
//    }
//    if( t1.isAssignableFrom( t2 ) )
//    {
//      return t2;
//    }
//    if( t2.isAssignableFrom( t1 ) )
//    {
//      return t1;
//    }
//    return t1; //## todo: return JavaTypes.VOID() or return null or Object?
//  }
//
//  /**
//   * Finds a parameterized type in the ancestry of a given type. For instance,
//   * given the type for ArrayList&lt;Person&gt; as the sourceType and List as
//   * the rawGenericType, returns List&lt;Person&gt;.
//   *
//   * @param sourceType     The type to search in.
//   * @param rawGenericType The raw generic type of the parameterized type to
//   *                       search for e.g., List is the raw generic type of List&lt;String&gt;.
//   *
//   * @return A parameterization of rawGenericType corresponding with the type
//   * params of sourceType.
//   */
//  // List<Foo>                    ArrayList<Foo>    List
//  public static PsiClassType findParameterizedType( PsiClassType sourceType, PsiClassType rawGenericType )
//  {
//    return findParameterizedType( sourceType, rawGenericType, false );
//  }
//
//  public static PsiClassType findParameterizedType( PsiClassType sourceType, PsiClassType rawGenericType, boolean bForAssignability )
//  {
//    if( sourceType == null )
//    {
//      return null;
//    }
//
//    rawGenericType = rawGenericType.rawType();
//
//    final PsiType srcRawType = sourceType.rawType();
//    PsiClass sourcePsiClass = type( sourceType );
//    if( srcRawType.equals( rawGenericType ) ||
//        !bForAssignability && rawGenericType.equals( PsiType.getJavaLangClass( sourcePsiClass.getManager(), GlobalSearchScope.allScope( sourcePsiClass.getProject() ) ) ) )
//    {
//      return sourceType;
//    }
//
//    PsiClassType parameterizedType = findParameterizedType( (PsiClassType)type( sourcePsiClass.getSuperClass() ), rawGenericType, bForAssignability );
//    if( parameterizedType != null )
//    {
//      return parameterizedType;
//    }
//
//    PsiClass[] interfaces = sourceType.resolve().getInterfaces();
//    for( int i = 0; i < interfaces.length; i++ )
//    {
//      PsiClass iface = interfaces[i];
//      parameterizedType = findParameterizedType( (PsiClassType)type( iface ), rawGenericType, bForAssignability );
//      if( parameterizedType != null )
//      {
//        return parameterizedType;
//      }
//    }
//
//    return null;
//  }
//
//  // ArrayList<Foo>                       List<Foo>         ArrayList<Z>
//  public static PsiClassType findParameterizedType_Reverse( PsiClassType sourceType, PsiClassType targetType )
//  {
//    if( sourceType == null || targetType == null )
//    {
//      return null;
//    }
//
//    if( !isParameterizedType( sourceType ) )
//    {
//      return null;
//    }
//
//    // List<Z>
//    PsiClassType sourceTypeInHier = findParameterizedType( targetType, sourceType.rawType() );
//
//    if( sourceTypeInHier == null || !isParameterizedType( sourceTypeInHier ) )
//    {
//      return null;
//    }
//
//    TypeVarToTypeMap map = new TypeVarToTypeMap();
//    PsiType[] params = sourceTypeInHier.getParameters();
//    for( int iPos = 0; iPos < params.length; iPos++ )
//    {
//      if( isTypeVariable( params[iPos] ) )
//      {
//        map.put( (PsiClassType)params[iPos], sourceType.getParameters()[iPos] );
//      }
//    }
//    // ArrayList<Foo>
//    return (PsiClassType)getActualType( targetType, map, true );
//  }
}
