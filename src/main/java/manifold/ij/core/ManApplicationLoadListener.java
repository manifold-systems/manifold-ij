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

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.ApplicationLoadListener;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.psi.impl.java.stubs.JavaLiteralExpressionElementType;
import com.intellij.psi.impl.java.stubs.JavaStubElementTypes;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;

import java.nio.file.Path;
import java.util.function.Supplier;

import com.intellij.util.messages.MessageBusConnection;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import manifold.ij.extensions.ManJavaLiteralExpressionElementType;
import manifold.util.JdkAccessUtil;
import manifold.util.ReflectUtil;
import org.jetbrains.annotations.NotNull;

/**
 * This class is a main entry point for the Manifold plugin. It performs the following tasks:
 * <p/>
 * - handles project open and close events by hooking up ManProjects<br>
 * - overrides the JavaExpressionParser and other stuff before the application loads<br>
 * - registers the manifold Annotator with all languages, since a type manifold can add errors to existing languages/markups<br>
 * - adds a brace matcher for templates, which must happen early<br>
 * <p/>
 */
public class ManApplicationLoadListener implements ApplicationLoadListener
{
  @Override
  public Object beforeApplicationLoaded(Application application, Path configPath, Continuation<? super Unit> continuation)
  {
    overrideJavaParserStuff();
    listenToProjectOpenClose();
    return null;
  }

  public void overrideJavaParserStuff()
  {
    // Turn off LoadingState while overriding Java parser stuff, otherwise it nags

    //noinspection UnstableApiUsage
    Object check = ReflectUtil.field( LoadingState.class, "CHECK_LOADING_PHASE" ).getStatic();
    //noinspection UnstableApiUsage
    ReflectUtil.field( LoadingState.class, "CHECK_LOADING_PHASE" ).setStatic( false );
    try
    {
      overrideJavaStringLiterals();
      replaceJavaExpressionParser();
    }
    finally
    {
      //noinspection UnstableApiUsage
      ReflectUtil.field( LoadingState.class, "CHECK_LOADING_PHASE" ).setStatic( check );
    }
  }

  public void listenToProjectOpenClose()
  {
    final MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect();
    connection.subscribe( ProjectManager.TOPIC,
      new ProjectManagerListener()
      {
//jetbrains deprecated, see ManStartupActivity
//        @Override
//        public void projectOpened( @NotNull Project project )
//        {
//          initForAllProjects();
//
//          StartupManagerEx.getInstance( project ).registerStartupActivity( () ->
//            ApplicationManager.getApplication().runReadAction( () -> ManProject.manProjectFrom( project ).projectOpened() ) );
//        }

        @Override
        public void projectClosed( @NotNull Project project )
        {
          ManProject manProject = ManProject.manProjectFrom( project );
          if( manProject != null )
          {
            manProject.projectClosed();
          }
        }
      } );
  }

  /**
   * Override Java String literals to handle fragments
   * <p/>
   * NOTE!!! This must be done VERY EARLY before IntelliJ can reference the LITERAL_EXPRESSION field, thus we reset the
   * field value here in an ApplicationComponent within the scope of the ManApplicationComponent constructor.
   */
  private void overrideJavaStringLiterals()
  {
    JdkAccessUtil.openModules();
    ManJavaLiteralExpressionElementType override = new ManJavaLiteralExpressionElementType();
    ReflectUtil.field( JavaStubElementTypes.class, "LITERAL_EXPRESSION" ).setStatic( override );

    IElementType[] registry = (IElementType[])ReflectUtil.field( IElementType.class, "ourRegistry" ).getStatic();
    for( int i = 0; i < registry.length; i++ )
    {
      if( registry[i] instanceof JavaLiteralExpressionElementType )
      {
        // ensure the original JavaLiteralExpressionElementType is replaced with ours
        registry[i] = override;
      }
    }
  }

  private void replaceJavaExpressionParser()
  {
    ReflectUtil.field( JavaParser.class, "INSTANCE" ).setStatic( new ManJavaParser() );
    ReflectUtil.field( JavaElementType.BINARY_EXPRESSION, "myConstructor" ).set( (Supplier<?>)ManPsiBinaryExpressionImpl::new );
    ReflectUtil.field( JavaElementType.PREFIX_EXPRESSION, "myConstructor" ).set( (Supplier<?>)ManPsiPrefixExpressionImpl::new );
    ReflectUtil.field( JavaElementType.POSTFIX_EXPRESSION, "myConstructor" ).set( (Supplier<?>)ManPsiPostfixExpressionImpl::new );
    ReflectUtil.field( JavaElementType.ASSIGNMENT_EXPRESSION, "myConstructor" ).set( (Supplier<?>)ManPsiAssignmentExpressionImpl::new );
    ReflectUtil.field( JavaElementType.ARRAY_ACCESS_EXPRESSION, "myConstructor" ).set( (Supplier<?>)ManPsiArrayAccessExpressionImpl::new );
  }
}
