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

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Main delegation annotator for @link using DelegationMaker.checkDelegation(). External annotation required due to the
 * nature of whole-class analysis.
 */
public class DelegationExternalAnnotator extends ExternalAnnotator<PsiFile, DelegationExternalAnnotator.Info>
{
  @Nullable
  @Override
  public PsiFile collectInformation( @NotNull PsiFile file )
  {
    return file;
  }

  @Nullable
  @Override
  public PsiFile collectInformation( @NotNull PsiFile file, @NotNull Editor editor, boolean hasErrors )
  {
    return file;
  }

  @Nullable
  @Override
  public Info doAnnotate( PsiFile file )
  {
    if( !(file instanceof PsiJavaFile) )
    {
      return Info.EMPTY;
    }

    return ApplicationManager.getApplication().runReadAction( (Computable<Info>)() -> {
      PsiClass[] classes = ((PsiJavaFile)file).getClasses();
      if( classes.length == 0 )
      {
        return Info.EMPTY;
      }

      PsiClass psiClass = classes[0];
      if( !(psiClass instanceof PsiExtensibleClass) )
      {
        return Info.EMPTY;
      }

      if( !ManProject.isManifoldInUse( file ) )
      {
        // Manifold jars are not used in the project
        return Info.EMPTY;
      }

      ManModule module = ManProject.getModule( file );
      if( module == null || !module.isDelegationEnabled() )
      {
        return Info.EMPTY;
      }

      Info info = new Info();
      annotate( (PsiExtensibleClass)psiClass, info );
      return info;
    } );
  }

  private static void annotate( PsiExtensibleClass psiClass, Info info )
  {
    DelegationMaker.checkDelegation( psiClass, info );

    for( PsiClass innerClass: psiClass.getInnerClasses() )
    {
      if( innerClass instanceof PsiExtensibleClass )
      {
        annotate( (PsiExtensibleClass)innerClass, info );
      }
    }
  }

  @Override
  public void apply( @NotNull PsiFile file, Info info, @NotNull AnnotationHolder holder )
  {
    if( !ManProject.isManifoldInUse( file ) )
    {
      // Manifold jars are not used in the project
      return;
    }

    ManModule module = ManProject.getModule( file );
    if( module == null || !module.isDelegationEnabled() )
    {
      // delegation not in use in module
      return;
    }

    info.getIssues().forEach( issue ->
      holder.newAnnotation( issue._severity, issue._msg )
        .range( issue._range )
        .create() );
  }

  public static class Info
  {
    public static final Info EMPTY = new Info();

    private final List<Issue> _issues = new ArrayList<>();

    public List<Issue> getIssues()
    {
      return _issues;
    }

    public void addIssue( HighlightSeverity severity, String msg, TextRange range )
    {
      _issues.add( new Issue( severity, msg, range ) );
    }

    static class Issue
    {
      HighlightSeverity _severity;
      String _msg;
      TextRange _range;

      public Issue( HighlightSeverity severity, String msg, TextRange range )
      {
        _severity = severity;
        _msg = msg;
        _range = range;
      }
    }
  }
}
