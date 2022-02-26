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

import com.intellij.find.FindBundle;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandler;
import com.intellij.find.findUsages.JavaFindUsagesHandlerFactory;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiPackage;
import java.util.Set;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Finds usages from elements in the <i>resource file</i>, as opposed to from code usages
 */
public class ManifoldFindUsagesHandlerFactory extends JavaFindUsagesHandlerFactory
{
  private static final String ACTION_STRING = FindBundle.message( "find.super.method.warning.action.verb" );

  public ManifoldFindUsagesHandlerFactory( Project project )
  {
    super( project );
  }

  @Override
  public boolean canFindUsages( @NotNull PsiElement element )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      // Manifold jars are not used in the project
      return false;
    }

    Set<PsiModifierListOwner> javaElem = ResourceToManifoldUtil.findJavaElementsFor( element );
    return !javaElem.isEmpty() && super.canFindUsages( javaElem.iterator().next() );
  }

  @Override // since IJ EAP 2019.1.0
  public FindUsagesHandler createFindUsagesHandler( @NotNull PsiElement element, @NotNull OperationMode operationMode )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      // Manifold jars are not used in the project
      return null;
    }

    return createFindUsagesHandler( element, operationMode == OperationMode.HIGHLIGHT_USAGES );
  }

  @Nullable
  @Override
  public FindUsagesHandler createFindUsagesHandler( @NotNull PsiElement element, boolean forHighlightUsages )
  {
    if( !ManProject.isManifoldInUse( element ) )
    {
      // Manifold jars are not used in the project
      return null;
    }

    Set<PsiModifierListOwner> javaElements = ResourceToManifoldUtil.findJavaElementsFor( element );
    if( javaElements.isEmpty() )
    {
      return null;
    }

    if( element instanceof PsiDirectory )
    {
      final PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage( (PsiDirectory)element );
      return psiPackage == null ? null : new JavaFindUsagesHandler( psiPackage, this );
    }

    if( element instanceof PsiMethod && !forHighlightUsages )
    {
      final PsiMethod[] methods = SuperMethodWarningUtil.checkSuperMethods( (PsiMethod)element, ACTION_STRING );
      if( methods.length > 1 )
      {
        return new JavaFindUsagesHandler( element, methods, this );
      }
      if( methods.length == 1 )
      {
        return new JavaFindUsagesHandler( methods[0], this );
      }
      return FindUsagesHandler.NULL_HANDLER;
    }

    return new JavaFindUsagesHandler( element, this )
    {
      @NotNull
      @Override
      public PsiElement[] getPrimaryElements()
      {
        return javaElements.toArray( new PsiElement[0] );
      }
    };
  }

}
