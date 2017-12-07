package manifold.ij.extensions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;

/**
 */
public class PsiErrorClassUtil
{
  private static final Key<Exception> KEY_ERROR_MESSAGE = new Key<>( "ErrorClassMessage" );

  public static PsiClass create( Project project, Exception e )
  {
    PsiClass errorClass = JavaPsiFacade.getInstance( project ).getParserFacade().createClassFromText( "", null );
    errorClass.putUserData( KEY_ERROR_MESSAGE, e );
    return errorClass;
  }

  public static boolean isErrorClass( PsiClass psiClass )
  {
    return getErrorFrom( psiClass ) != null;
  }

  public static Exception getErrorFrom( PsiClass errorClass )
  {
    return errorClass.getUserData( KEY_ERROR_MESSAGE );
  }
}
