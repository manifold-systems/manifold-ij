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

package manifold.ij.android;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.vfs.VirtualFile;
import manifold.api.fs.IFile;
import manifold.ij.core.ManModule;
import manifold.ij.core.ManProject;
import manifold.ij.extensions.ManDefinitions;
import manifold.preprocessor.api.SymbolProvider;
import manifold.preprocessor.definitions.Definitions;
import manifold.rt.api.util.StreamUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BuildVariantSymbols implements SymbolProvider
{
  public static BuildVariantSymbols INSTANCE = null;

  private Map<ManModule, Map<String, String>> _buildConfigSyms;

  public BuildVariantSymbols()
  {
    INSTANCE = this;
  }

  public void reset()
  {
    _buildConfigSyms = null;
  }

  @Override
  public boolean isDefined( Definitions rootDef, IFile sourceFile, String def )
  {
    return loadBuildConfigSymbols( rootDef ).containsKey( def );
  }

  @Override
  public String getValue( Definitions rootDef, IFile sourceFile, String def )
  {
    return loadBuildConfigSymbols( rootDef ).get( def );
  }

  private Map<String, String> loadBuildConfigSymbols( Definitions rootDef )
  {
    ManModule module = ((ManDefinitions)rootDef).getModule();
    if( module == null )
    {
      return Collections.emptyMap();
    }

    if( _buildConfigSyms == null )
    {
      _buildConfigSyms = new ConcurrentHashMap<>();
    }

    return _buildConfigSyms.computeIfAbsent( module, m -> {
      String generatedClassesDir = getBuildConfigSourcePath( rootDef );
      if( generatedClassesDir != null )
      {
        File dir = new File( generatedClassesDir );
        File buildConfig = findBuildConfig( dir );
        if( buildConfig != null )
        {
          return parseFields( buildConfig );
        }
      }
      return Collections.emptyMap();
    } );
  }

  private Map<String, String> parseFields( File buildConfig )
  {
    Map<String, String> map = new HashMap<>();
    FileReader fileReader = new FileReader( buildConfig );
    String content = StreamUtil.getContent( fileReader );
    String p_s_f = "public static final";
    for( int psf = content.indexOf( p_s_f ); psf > 0; psf = content.indexOf( p_s_f, psf + p_s_f.length() ) )
    {
      int iEq = content.indexOf( '=', psf + p_s_f.length() );
      if( iEq > 0 )
      {
        String name = getVarName( content, iEq );
        String init = getInitializer( content, iEq );

        String value = null;
        if( init.startsWith( "\"" ) )
        {
          value = init.substring( 1, init.length()-1 );
        }
        else
        {
          try
          {
            long l = Long.parseLong( init );
            value = init;
          }
          catch( Exception e )
          {
            try
            {
              double d = Double.parseDouble( init );
              value = init;
            }
            catch( Exception e2 )
            {
              // hack to handle DEBUG init, where for some reason initializer is: Boolean.parseBooean("true")
              if( init.contains( "true" ) )
              {
                // preprocessor definition will be just defined, a "false" value will not be defined
                value = "";
              }
            }
          }
        }
        if( value != null )
        {
          map.put( name, value );
        }
      }
    }
    return map;
  }

  private String getVarName( String content, int iEq )
  {
    StringBuilder sb = new StringBuilder();
    for( int i = iEq-1; i > 0; i-- )
    {
      char c = content.charAt( i );
      if( !Character.isWhitespace( c ) )
      {
        sb.insert( 0, c );
      }
      else if( sb.length() > 0 )
      {
        break;
      }
    }
    return sb.toString();
  }

  private String getInitializer( String content, int iEq )
  {
    int i = iEq + 1;
    char c = content.charAt( i );
    while( Character.isWhitespace( c ) )
    {
      c = content.charAt( ++i );
    }

    StringBuilder sb = new StringBuilder();
    for( ; i < content.length(); i++ )
    {
      c = content.charAt( i );
      if( c != '\r' && c != '\n' )
      {
        sb.append( c );
      }
      else if( sb.length() > 0 )
      {
        while( sb.charAt( sb.length()-1 ) == ';' )
        {
          sb.deleteCharAt( sb.length()-1 );
        }
        break;
      }
    }
    return sb.toString();
  }

  private String getBuildConfigSourcePath( Definitions rootDef )
  {
    ManDefinitions manDefs = (ManDefinitions)rootDef;
    ManModule module = manDefs.getModule();

    String child = getCustomBuildDir( module );
    if( child != null )
    {
      return child;
    }

    String generatedClassesDir = null;
    for( VirtualFile vf: ManProject.getSourceRoots( module.getIjModule() ) )
    {
      String path = vf.getPath();
      if( path.contains( "/app/build/generated/source/buildConfig/" ) )
      {
        generatedClassesDir = path.replace( '/', File.separatorChar );
        break;
      }
    }
    return generatedClassesDir;
  }

  private static @Nullable String getCustomBuildDir( ManModule module ) throws IOException
  {
    PropertiesComponent state = PropertiesComponent.getInstance( module.getIjProject() );
    String customBuildDir = state.getValue( "manifold.android.build.dir" );
    if( customBuildDir != null )
    {
      customBuildDir = customBuildDir.trim();
      if( !customBuildDir.isEmpty() )
      {
        File path = new File( customBuildDir ).getCanonicalFile();
        if( path.isDirectory() )
        {
          File child = new File( path, "generated/source/buildConfig/" );
          if( child.isDirectory() )
          {
            return child.getAbsolutePath();
          }
        }
      }
    }
    return null;
  }

  private File findBuildConfig( File file )
  {
    if( file.isFile() )
    {
      if( file.getName().equals( "BuildConfig.java" ) )
      {
        return file;
      }
      return null;
    }
    else
    {
      File[] listing = file.listFiles();
      if( listing != null )
      {
        for( File f : listing )
        {
          File buildConfig = findBuildConfig( f );
          if( buildConfig != null )
          {
            return buildConfig;
          }
        }
      }
    }
    return null;
  }
}