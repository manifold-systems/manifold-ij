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

/*
 * Manifold
 */

package manifold.ij.util;

import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Executes a task after a certain time delay. The task can be rescheduled which causes the old task to be cancelled
 * if it did not execute yet. Useful for reducing the frequency of refresh operations.
 * Tasks are executed on a timer thread.
 */
public class DelayedRunner
{
  private static final Logger LOG = Logger.getInstance(DelayedRunner.class);

  private final Timer timer = new Timer(true);
  private final Map<String, TimerTask> tasksByKey = Maps.newHashMap();

  public synchronized void scheduleTask(final String key, long millis, Runnable userTask) {
    TimerTask refreshTask = tasksByKey.get(key);
    if (refreshTask != null) {
      refreshTask.cancel();
    }
    refreshTask = new MyDelayedTask(key, userTask);
    tasksByKey.put(key, refreshTask);
    timer.schedule(refreshTask, millis);
  }

  private class MyDelayedTask extends TimerTask {
    private final String key;
    private final Runnable userTask;

    public MyDelayedTask(String key, Runnable userTask) {
      this.key = key;
      this.userTask = userTask;
    }

    public void run() {
      tasksByKey.remove(key);
      try {
        userTask.run();
      } catch (Throwable e) {
        LOG.error("DelayedRunner task threw an exception.", e);
      }
    }
  }
}
