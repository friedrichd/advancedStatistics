/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2018  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.util.statistics;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.sosy_lab.common.time.TimeSpan;
import org.sosy_lab.common.time.Timer;

public class StatTracker {

  private final String title;
  private final Timer baseTimer = new Timer();
  private List<StatTrackerEvent> trackedEvents = new ArrayList<>();

  public StatTracker(String pTitle) {
    this.title = pTitle;
  }

  public String getTitle() {
    return title;
  }

  public void startTracking() {
    if (!baseTimer.isRunning()) {
      baseTimer.start();
      this.trackEvent("Start Tracking");
    }
  }

  public void stopTracking() {
    if (baseTimer.isRunning()) {
      this.trackEvent("Stop Tracking");
      baseTimer.stop();
    }
  }

  public void trackEvent(String label) {
    trackedEvents.add(new StatTrackerEvent(baseTimer.getSumTime(), label));
  }

  @Override
  public String toString() {
    String r = title + "\n";
    for (StatTrackerEvent e : trackedEvents) {
      r += e.toString() + "\n";
    }
    return r;
  }

  class StatTrackerEvent {
    final TimeSpan time;
    final String label;

    StatTrackerEvent(TimeSpan time, String label) {
      this.time = time;
      this.label = label;
    }

    @Override
    public String toString() {
      return label + ": " + time.formatAs(TimeUnit.MILLISECONDS);
    }

  }

}
