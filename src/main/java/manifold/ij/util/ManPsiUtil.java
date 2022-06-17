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

package manifold.ij.util;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import manifold.ext.rt.api.Structural;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;

import java.util.concurrent.Callable;

public class ManPsiUtil
{
  public static boolean isStructuralInterface( PsiClass psiClass )
  {
    PsiAnnotation structuralAnno = psiClass == null || psiClass.getModifierList() == null
                                   ? null
                                   : psiClass.getModifierList().findAnnotation( Structural.class.getTypeName() );
    return structuralAnno != null;
  }

  public static void runInTypeManifoldLoader( PsiElement context, Runnable code )
  {
    ManModule module = ManProject.getModule( context );
    if( module == null )
    {
      throw new NullPointerException();
    }
    module.runWithLoader( code );
  }
  public static <R> R runInTypeManifoldLoader( PsiElement context, Callable<R> code )
  {
    ManModule module = ManProject.getModule( context );
    if( module == null )
    {
      throw new NullPointerException();
    }
    return module.runWithLoader( code );
  }
}
