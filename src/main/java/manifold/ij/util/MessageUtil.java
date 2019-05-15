package manifold.ij.util;

import com.intellij.ide.plugins.PluginManagerMain;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

public final class MessageUtil
{
  private static final Logger log = Logger.getInstance( MessageUtil.class );

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
    switch( placement )
    {
      case CENTER:
        return RelativePoint.getCenterOf( ((WindowManagerEx)WindowManager.getInstance()).getFrame( project ).getLayeredPane() );
      case SOUTH:
        return RelativePoint.getSouthOf( ((WindowManagerEx)WindowManager.getInstance()).getFrame( project ).getLayeredPane() );
      case NORTH_EAST:
        return RelativePoint.getNorthEastOf( ((WindowManagerEx)WindowManager.getInstance()).getFrame( project ).getLayeredPane() );
      case NORTH_WEST:
        return RelativePoint.getNorthWestOf( ((WindowManagerEx)WindowManager.getInstance()).getFrame( project ).getLayeredPane() );
      case SOUTH_EAST:
        return RelativePoint.getSouthEastOf( ((WindowManagerEx)WindowManager.getInstance()).getFrame( project ).getLayeredPane() );
      case SOUTH_WEST:
        return RelativePoint.getSouthWestOf( ((WindowManagerEx)WindowManager.getInstance()).getFrame( project ).getLayeredPane() );
      default:
        return RelativePoint.getNorthEastOf( ((WindowManagerEx)WindowManager.getInstance()).getFrame( project ).getLayeredPane() );
    }
  }
}