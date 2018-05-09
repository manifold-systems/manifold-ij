package manifold.ij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.awt.RelativePoint;

public final class MessageUtil
{
  private static final Logger log = Logger.getInstance( MessageUtil.class );

  public static void showInfo( final Project project, final String format, final Object... args )
  {
    showMessage( project, MessageType.INFO, format, args );
  }

  public static void showError( final Project project, final String format, final Object... args )
  {
    showMessage( project, MessageType.ERROR, format, args );
  }

  public static void showError( Project project, Throwable t )
  {
    showError( project, t.getMessage() );
  }

  public static void showCriticalError( final Project project, final String format, final Object... args )
  {
    showMessage( project, MessageType.ERROR, format, args );
    log.error( String.format( format, args ) );
  }

  public static void showCriticalError( Project project, Throwable t )
  {
    showError( project, t.getMessage() );
    log.error( t );
  }

  private static void showMessage( final Project project, final MessageType messageType, final String format, final Object[] args )
  {
    StatusBar statusBar = WindowManager.getInstance().getStatusBar( project );
    if( statusBar == null || statusBar.getComponent() == null )
    {
      return;
    }
    String message = String.format( format, args );
    JBPopupFactory.getInstance().createHtmlTextBalloonBuilder( message, messageType, null )
      .setFadeoutTime( 7500 )
      .createBalloon()
      .show( RelativePoint.getNorthEastOf( statusBar.getComponent() ), Balloon.Position.atRight );

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
}