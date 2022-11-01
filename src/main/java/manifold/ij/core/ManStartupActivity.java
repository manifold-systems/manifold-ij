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

package manifold.ij.core;

import com.intellij.codeInsight.highlighting.PairedBraceMatcherAdapter;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageAnnotators;
import com.intellij.lang.LanguageBraceMatching;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import manifold.ij.extensions.ManifoldPsiClassAnnotator;
import manifold.ij.template.ManTemplateBraceMatcher;
import manifold.ij.template.ManTemplateLanguage;
import org.jetbrains.annotations.NotNull;

public class ManStartupActivity implements com.intellij.openapi.startup.StartupActivity
{
  private static volatile boolean Initialized = false;

  @Override
  public void runActivity( @NotNull Project project )
  {
    initForAllProjects();

    ApplicationManager.getApplication().runReadAction( () -> ManProject.manProjectFrom( project ).projectOpened() );
  }

  /**
   * Note the timing of calling this method is critical.  It must happen *after* the application has loaded, but before
   * the first project loads. This used to happen during {@link com.intellij.openapi.components.ProjectComponent#initComponent},
   * however JetBrains has deprecated components and there is no equivalent listener event (as far as I know).
   */
  public void initForAllProjects()
  {
    if( !Initialized )
    {
      synchronized( this )
      {
        if( !Initialized )
        {
          Initialized = true;
          registerAnnotatorWithAllLanguages();
        }
      }
    }
  }

  private void registerAnnotatorWithAllLanguages()
  {
    // effectively adds annotator to ALL languages
    LanguageAnnotators.INSTANCE.addExplicitExtension( Language.ANY, new ManifoldPsiClassAnnotator() );

    // add brace matcher to templates
    LanguageBraceMatching.INSTANCE.addExplicitExtension( ManTemplateLanguage.INSTANCE,
      new PairedBraceMatcherAdapter( new ManTemplateBraceMatcher(), ManTemplateLanguage.INSTANCE ) );
  }

}
