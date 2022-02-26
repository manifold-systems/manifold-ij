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

import java.awt.AWTEvent;
import java.awt.ActiveEvent;
import java.awt.Component;
import java.awt.MenuComponent;
import java.awt.Toolkit;

/**
 * Provides for ui modality using a local event queue to dispatch events while
 * a component is visible e.g., a frame or window.
 */
public class ModalEventQueue implements Runnable
{
  private IModalHandler _modalHandler;

  /**
   * @param visibleComponent A visible component. The modal event queue remains
   *                         operable/modal while the component is visible.
   */
  public ModalEventQueue( final Component visibleComponent )
  {
    _modalHandler = visibleComponent::isVisible;
  }

  public ModalEventQueue( IModalHandler modalHandler )
  {
    _modalHandler = modalHandler;
  }

  public void run()
  {
    while( isModal() )
    {
      handleIdleTime();
      try
      {
        AWTEvent event = Toolkit.getDefaultToolkit().getSystemEventQueue().getNextEvent();
        dispatchEvent( event );
      }
      catch( Throwable e )
      {
        handleUncaughtException( e );
      }
    }
  }

  private void handleIdleTime()
  {
    try
    {
      if( Toolkit.getDefaultToolkit().getSystemEventQueue().peekEvent() == null )
      {
        executeIdleTasks();
      }
    }
    catch( Throwable t )
    {
      handleUncaughtException( t );
    }
  }

  protected void executeIdleTasks()
  {
    // UpdateNotifier.instance().notifyActionComponentsNow();
  }

  protected void handleUncaughtException( Throwable t )
  {
    throw new RuntimeException( t );
  }

  protected boolean isModal()
  {
    return _modalHandler.isModal();
  }

  public void dispatchEvent( AWTEvent event )
  {
    Object src = event.getSource();
    if( event instanceof ActiveEvent )
    {
      ((ActiveEvent)event).dispatch();
    }
    else if( src instanceof Component )
    {
      ((Component)src).dispatchEvent( event );
    }
    else if( src instanceof MenuComponent )
    {
      ((MenuComponent)src).dispatchEvent( event );
    }
  }
}

