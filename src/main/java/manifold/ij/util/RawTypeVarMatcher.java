package manifold.ij.util;

import com.intellij.psi.PsiClassType;

/**
*/
public class RawTypeVarMatcher implements TypeVarToTypeMap.ITypeVarMatcher<PsiClassType>
{
  private static final RawTypeVarMatcher INSTANCE = new RawTypeVarMatcher();
  
  public static RawTypeVarMatcher instance() {
    return INSTANCE;
  }
  
  private RawTypeVarMatcher() {
  }
  
  @Override
  public boolean matches( PsiClassType thisOne, PsiClassType thatOne )
  {
    return thisOne.equals( thatOne );
  }
}
