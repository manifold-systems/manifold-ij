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
