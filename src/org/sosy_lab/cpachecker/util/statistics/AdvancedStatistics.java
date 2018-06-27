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

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;

/**
 * A class to output statistics and results of an analysis in an advanced way.
 *
 * You usually want to implement {@link StatisticsProvider} and register your Statistics instances
 * so that they are actually called after CPAchecker finishes.
 */
public class AdvancedStatistics implements Statistics {

  private static final String LABEL_TOTALTIME = "Total";

  private final String name;
  private final Stopwatch baseTime = Stopwatch.createUnstarted();
  private Deque<StatEvent> openEvents = new ArrayDeque<>();

  private final StorageStrategy storage;

  public AdvancedStatistics(String name) {
    this.name = name;
    this.storage = new StorageStrategy(){

      List<StatEvent> events = new ArrayList<>();

      @Override
      public void store(StatEvent event) {
        events.add(event);
      }

      @Override
      public String getPrintableStatistics() {
        return Joiner.on("\n").join(events);
      }
    };
  }

  public AdvancedStatistics(String name, StorageStrategy store) {
    this.name = name;
    this.storage = store;
  }

  @Override
  public String getName() {
    return name;
  }

  /**
   * Starts the Timer to track events
   */
  public void startTracking() {
    if (!baseTime.isRunning()) {
      pushAndStore(new StatEvent(0, LABEL_TOTALTIME));
      baseTime.start();
    }
  }

  /**
   * Stops the Timer to track events
   */
  public void stopTracking() {
    if (baseTime.isRunning()) {
      while (!openEvents.isEmpty()) {
        StatEvent e = openEvents.pop();
        e.calcDuration(baseTime.elapsed().toNanos());
      }
      baseTime.stop();
    }
  }

  public void track(String label) {
    storage.store(new StatEvent(baseTime.elapsed().toNanos(), label));
  }

  public void track(String label, Object value) {
    storage.store(new StatEvent(baseTime.elapsed().toNanos(), label, value));
  }

  public void open(String label) {
    if(!label.equals(LABEL_TOTALTIME)){
      pushAndStore(new StatEvent(baseTime.elapsed().toNanos(), label));
    }
  }

  public void open(String label, Object value) {
    if(!label.equals(LABEL_TOTALTIME)){
    pushAndStore(new StatEvent(baseTime.elapsed().toNanos(), label, value));
    }
  }

  private void pushAndStore(StatEvent event) {
    openEvents.push(event);
    storage.store(event);
  }

  /**
   * If an open event with the same label exists, all open events are closed until this one is
   * reached (in negative order) Otherwise a new event will be tracked.
   */
  public void close(String label) {
    if (openEvents.stream().anyMatch(e -> e.label.equals(label))) {
      while (!openEvents.isEmpty()) {
        StatEvent e = openEvents.pop();
        e.calcDuration(baseTime.elapsed().toNanos());
        if (e.label.equals(label)) {
          break;
        }
      }
    } else {
      track(label);
    }
  }

  /**
   * If an open event with the same label exists, all open events are closed until this one is
   * reached (in negative order) Otherwise a new event will be tracked.
   */
  public void close(String label, Object value) {
    if (openEvents.stream().anyMatch(e -> e.label.equals(label))) {
      while (!openEvents.isEmpty()) {
        StatEvent e = openEvents.pop();
        e.value = value;
        e.calcDuration(baseTime.elapsed().toNanos());
        if (e.label.equals(label)) {
          break;
        }
      }
    }else{
      track(label, value);
    }
  }


  @Override
  public void printStatistics(PrintStream out, Result result, UnmodifiableReachedSet reached) {
    out.println(storage.getPrintableStatistics());
  }

  class StatEvent {

    final long time;
    private final String label;

    private long duration = 0;
    private Object value;

    public StatEvent(long time, String label) {
      this.time = time;
      this.label = label;
    }

    public StatEvent(long time, String label, Object value) {
      this.time = time;
      this.label = label;
      this.value = value;
    }

    /**
     * Calculates the duration of the event.
     */
    public void calcDuration(long end_time) {
      this.duration = end_time - time;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(time);

      if (label != null && !label.isEmpty()) {
        sb.append(" [" + label + "]");
      }

      sb.append(": ");
      if (value != null) {
        sb.append(value + ", ");
      }
      if (duration > 0) {
        sb.append("duration=" + duration + ", ");
      }
      sb.delete(sb.length() - 2, sb.length());
      return sb.toString();
    }
  }

  interface StorageStrategy {
    public void store(StatEvent event);
    public String getPrintableStatistics();
  }

}
