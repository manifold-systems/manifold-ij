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

import java.awt.Toolkit;

/**
 * This is a utility for basically pumping messages in-place until there are
 * none left in the queue. It's very handy for cases where one or more messages
 * are in the queue as a result of prior calls to EventQueue.invokeLater() and
 * you need those messages to be processed before you check state or whatever.
 * The idea is that you want to distribute all those messages and messages
 * resulting from distributing them etc. When there are no more messages in the
 * queue you can proceed with whatever.
 * <p/>
 * Simply call SettleModelEventQueue.instance().run()
 */
public class SettleModalEventQueue extends ModalEventQueue
{
  private static final SettleModalEventQueue INSTANCE = new SettleModalEventQueue();

  public static SettleModalEventQueue instance()
  {
    return INSTANCE;
  }

  private SettleModalEventQueue()
  {
    super( new SettleEventsHandler() );
  }

  private static class SettleEventsHandler implements IModalHandler
  {
    public boolean isModal()
    {
      // Keep pumping messages until there are none left in the queue.
      return Toolkit.getDefaultToolkit().getSystemEventQueue().peekEvent() != null;
    }
  }
}
