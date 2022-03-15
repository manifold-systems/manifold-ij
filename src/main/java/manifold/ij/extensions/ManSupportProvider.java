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

import com.intellij.ide.util.frameworkSupport.FrameworkSupportConfigurable;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportModel;
import com.intellij.ide.util.frameworkSupport.FrameworkSupportProviderBase;
import com.intellij.ide.util.frameworkSupport.FrameworkVersion;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.File;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Facilitates adding manifold-all and tools.jar when creating a new project.
 * Similarly, ManFrameworkType supports the same thing at the module level.
 */
public class ManSupportProvider extends FrameworkSupportProviderBase
{
  protected ManSupportProvider()
  {
    super( "manifold", "Manifold" );
  }

  @Nullable
  @Override
  public Icon getIcon()
  {
    return IconLoader.getIcon( "/manifold/ij/icons/manifold.png", getClass() );
  }

  @Override
  protected void addSupport( @NotNull Module module, @NotNull ModifiableRootModel rootModel, FrameworkVersion version, @Nullable Library library )
  {
    addToolsJar( rootModel );
  }

  static void addToolsJar( @NotNull ModifiableRootModel rootModel )
  {
    Sdk sdk = rootModel.getSdk();
    if( sdk == null )
    {
      return;
    }

    JavaSdkVersion version = JavaSdk.getInstance().getVersion( sdk );
    if( version == null || version.isAtLeast( JavaSdkVersion.JDK_1_9 ) )
    {
      // Tools.jar is only for Java 8-
      return;
    }

    if( hasToolsJar( rootModel ) )
    {
      return;
    }

    VirtualFile toolsJarFile = findToolsJarFile();
    if( toolsJarFile == null )
    {
      Notifications.Bus.notify( new Notification( "Project JDK", "tools.jar not found!", "Please add tools.jar to your JDK", NotificationType.ERROR ) );
      return;
    }

    SdkModificator sdkModificator = sdk.getSdkModificator();
    sdkModificator.addRoot( toolsJarFile, OrderRootType.CLASSES );
    sdkModificator.commitChanges();
  }

  private static boolean hasToolsJar( ModifiableRootModel rootModel )
  {
    Sdk sdk = rootModel.getSdk();
    if( sdk == null )
    {
      return false;
    }

    for( VirtualFile file : sdk.getRootProvider().getFiles( OrderRootType.CLASSES ) )
    {
      if( file.getName().equalsIgnoreCase( "tools.jar" ) )
      {
        return true;
      }
    }
    return false;
  }

  private static VirtualFile findToolsJarFile()
  {
    File file = new File( System.getProperty( "java.home" ) );
    if( file.getName().equalsIgnoreCase( "jre" ) )
    {
      file = file.getParentFile();
    }
    String[] defaultToolsLocation = {"lib", "tools.jar"};
    for( String name : defaultToolsLocation )
    {
      file = new File( file, name );
    }

    if( !file.exists() )
    {
      return null;
    }

    return LocalFileSystem.getInstance().findFileByIoFile( file );
  }

  @NotNull
  @Override
  public FrameworkSupportConfigurable createConfigurable( @NotNull FrameworkSupportModel model )
  {
    return new ManFrameworkSupportConfigurable( this, model );
  }

  @Override
  public boolean isEnabledForModuleType( @NotNull ModuleType moduleType )
  {
    return true;
  }
}
