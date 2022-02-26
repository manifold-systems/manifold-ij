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

import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class MessageUtil
{
  private static final Logger log = Logger.getInstance( MessageUtil.class );

  @SuppressWarnings( "unused" )
  public static void showInfo( final Project project, final String format, final Object... args )
  {
    showMessage( project, MessageType.INFO, format, args );
  }

  public static void showWarning( final Project project, final String format, final Object... args )
  {
    showWarning( project, Placement.NORTH_EAST, format, args );
  }
  public static void showWarning( final Project project, Placement placement, final String format, final Object... args )
  {
    showMessage( project, MessageType.WARNING, placement, format, args );
  }

  public static void showError( final Project project, final String format, final Object... args )
  {
    showMessage( project, MessageType.ERROR, format, args );
  }

  @SuppressWarnings( "unused" )
  public static void showError( Project project, Throwable t )
  {
    showError( project, t.getMessage() );
  }

  @SuppressWarnings( "unused" )
  public static void showCriticalError( final Project project, final String format, final Object... args )
  {
    showMessage( project, MessageType.ERROR, format, args );
    log.error( String.format( format, args ) );
  }

  @SuppressWarnings( "unused" )
  public static void showCriticalError( Project project, Throwable t )
  {
    showError( project, t.getMessage() );
    log.error( t );
  }

  public enum Placement {CENTER, SOUTH, NORTH_EAST, NORTH_WEST, SOUTH_EAST, SOUTH_WEST}
  private static void showMessage( final Project project, final MessageType messageType, final String format, final Object[] args )
  {
    showMessage( project, messageType, Placement.NORTH_EAST, format, args );
  }
  private static void showMessage( final Project project, final MessageType messageType, Placement placement, final String format, final Object[] args )
  {
    String message = String.format( format, args );
    JBPopupFactory popupFactory = JBPopupFactory.getInstance();
    popupFactory.createHtmlTextBalloonBuilder( message, messageType, new PluginManagerMain.MyHyperlinkListener() )
      .setCloseButtonEnabled( true )
      .setShowCallout( false )
      .setHideOnClickOutside( false )
      .setDialogMode( true )
      .createBalloon()
      .show( getPosition( project, placement ), Balloon.Position.atRight );

    if( messageType == MessageType.INFO )
    {
      log.info( message );
    }
    else if( messageType == MessageType.WARNING )
    {
      log.warn( message );
    }
    else
    {
      log.debug( message );
    }
  }

  @NotNull
  private static RelativePoint getPosition( Project project, Placement placement )
  {
    // the project frame
    JFrame frame = WindowManager.getInstance().getFrame( project );
    if( frame == null )
    {
      // the "welcome" frame
      frame = WindowManager.getInstance().findVisibleFrame();
    }

    switch( placement )
    {
      case CENTER:
        return RelativePoint.getCenterOf( frame.getLayeredPane() );
      case SOUTH:
        return RelativePoint.getSouthOf( frame.getLayeredPane() );
      case SOUTH_EAST:
        return RelativePoint.getSouthEastOf( frame.getLayeredPane() );
      case SOUTH_WEST:
        return RelativePoint.getSouthWestOf( frame.getLayeredPane() );
      case NORTH_WEST:
        return RelativePoint.getNorthWestOf( frame.getLayeredPane() );
      case NORTH_EAST:
      default:
        return RelativePoint.getNorthEastOf( frame.getLayeredPane() );
    }
  }
}