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
import java.util.Stack;
import java.util.concurrent.TimeUnit;
import org.sosy_lab.common.time.TimeSpan;
import org.sosy_lab.common.time.Timer;

public class StatTracker {

  private final String title;

  private final Timer baseTimer = new Timer();
  private final Stack<StatTrackerEvent> openEvents = new Stack<>();

  private final List<StatTrackerEvent> trackedEvents = new ArrayList<>();

  public StatTracker(String pTitle) {
    this.title = pTitle;
  }

  public String getTitle() {
    return title;
  }

  public void start() {
    if (baseTimer.isRunning()) {
      this.stop();
    }
    baseTimer.start();
  }

  public void stop() {
    while (!openEvents.isEmpty()) {
      StatTrackerEvent open = openEvents.pop();
      trackedEvents.add(
          new StatTrackerEvent(open.label, open.start, baseTimer.getSumTime()));
    }
    trackedEvents.add(new StatTrackerEvent("total", baseTimer.getSumTime()));
    baseTimer.stop();
  }

  public void trackEvent(String label) {
    trackedEvents.add(new StatTrackerEvent(label, baseTimer.getSumTime()));
  }

  public void openEvent(String label) {
    openEvents.push(new StatTrackerEvent(label, baseTimer.getSumTime()));
  }

  public void closeEvent() {
    if (!openEvents.isEmpty()) {
      StatTrackerEvent open = openEvents.pop();
      trackedEvents.add(new StatTrackerEvent(open.label, open.start, baseTimer.getSumTime()));
    }
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

    final String label;
    final TimeSpan start, dur;

    StatTrackerEvent(String label, TimeSpan start) {
      this.label = label;
      this.start = start;
      this.dur = TimeSpan.empty();
    }

    StatTrackerEvent(String label, TimeSpan start, TimeSpan end) {
      this.label = label;
      this.start = start;
      this.dur = TimeSpan.difference(end, start);
    }

    @Override
    public String toString() {
      return label + ": " + dur.formatAs(TimeUnit.MILLISECONDS);
    }
  }

}
