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

import com.google.common.base.Stopwatch;
import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.statistics.storage.AbstractStatStorage;
import org.sosy_lab.cpachecker.util.statistics.storage.NumberValueOnlyStorage;
import org.sosy_lab.cpachecker.util.statistics.storage.TimeOnlyStorage;
import org.sosy_lab.cpachecker.util.statistics.storage.ValueOnlyStorage;

/**
 * A class to output statistics and results of an analysis in an advanced way.
 *
 * You usually want to implement {@link StatisticsProvider} and register your Statistics instances
 * so that they are actually called after CPAchecker finishes.
 */
public class AdvancedStatistics implements Statistics {

  private static final String LABEL_TOTALTIME = "Total time for ";

  private final String name;
  private final Stopwatch baseTime = Stopwatch.createUnstarted();

  // root of the storage tree
  private final AbstractStatStorage baseStorage;
  // contains the stack of open events for all threads
  private final Map<Long, Deque<StatEvent>> openEvents = new HashMap<>();

  // counter for detected errors
  private volatile int errors = 0;

  public AdvancedStatistics(String name) {
    this.name = name;
    this.baseStorage = new TimeOnlyStorage(LABEL_TOTALTIME + name);
  }

  @Override
  public String getName() {
    return name;
  }

  /**
   * Starts the overall timer and enables the tracking of events.</br>
   * <b>Notice:</b> Tracking can not be paused or restarted again!
   */
  public synchronized void startTracking() {
    if (!baseTime.isRunning() && baseTime.elapsed().isZero()) {
      baseTime.start();
    }
  }

  /**
   * Disables the tracking of events and stops the overall timer.</br>
   * <b>Notice:</b> Tracking can not be paused or restarted again!
   */
  public synchronized void stopTracking() {
    if (baseTime.isRunning()) {
      // count all unclosed events as errors
      for (Deque<StatEvent> unclosedEvents : openEvents.values()) {
        errors += unclosedEvents.size();
      }
      openEvents.clear();

      baseStorage.update(baseTime.elapsed());
      baseTime.stop();
    }
  }

  /**
   * Tracks an event without duration or value.
   *
   * @param label representing the name of the event
   */
  public void track(String label) {
    assert baseTime.isRunning() : "Please start tracking before trying to track something!";
    getCurrentStorage().getChildOrDefault(label, ValueOnlyStorage.class).update();
  }

  /**
   * Tracks an event without duration, but with some value.
   *
   * @param label The name of the event
   * @param value An additional value for categorization of the event
   */
  public void track(String label, Object value) {
    assert baseTime.isRunning() : "Please start tracking before trying to track something!";
    if (value instanceof Number) {
      getCurrentStorage().getChildOrDefault(label, NumberValueOnlyStorage.class).update(value);
    } else {
      getCurrentStorage().getChildOrDefault(label, ValueOnlyStorage.class).update(value);
    }
  }

  /**
   * Opens an event without value.</br>
   * A value can be added either on opening, on closing or not at all.
   *
   * @param label The name of the event
   */
  public StatEvent open(String label) {
    assert baseTime.isRunning() : "Please start tracking before trying to open an event!";
    AbstractStatStorage current =
        getCurrentStorage().getChildOrDefault(label, TimeOnlyStorage.class);
    return push(new StatEvent(baseTime.elapsed(), current));
  }

  /**
   * Opens an event with value. A value can be added either on opening, on closing or not at all.
   *
   * @param label The name of the event
   * @param value An additional value for categorization of the event
   */
  public StatEvent open(String label, Object value) {
    assert baseTime.isRunning() : "Please start tracking before trying to open an event!";
    AbstractStatStorage current =
        getCurrentStorage().getChildOrDefault(label, TimeOnlyStorage.class);
    return push(new StatEvent(baseTime.elapsed(), current, value));
  }

  /**
   * Closes the last open event with the same label.</br>
   * <b>Notice:</b> Some behavior is not considered reasonable and will increment the error count:
   * <ul>
   * <li>Closing an event, that has not been opened or was already closed</li>
   * <li>Not closing another event, that has been opened after opening this event</li>
   * </ul>
   *
   * @param label The name of the event
   */
  public void close(String label) {
    StatEvent stored_event = pop(e -> e.storage.label.equals(label));
    if (stored_event != null) {
      stored_event.storage.update(baseTime.elapsed().minus(stored_event.time), stored_event.value);
    }
  }

  /**
   * Closes the event.</br>
   * <b>Notice:</b> Some behavior is not considered reasonable and will increment the error count:
   * <ul>
   * <li>Closing an event, that has not been opened or was already closed</li>
   * <li>Not closing another event, that has been opened after opening this event</li>
   * </ul>
   *
   * @param event The handle on the event
   */
  public void close(StatEvent event) {
    StatEvent stored_event = pop(e -> e.equals(event));
    if (stored_event != null) {
      stored_event.storage.update(baseTime.elapsed().minus(stored_event.time), stored_event.value);
    }
  }

  /**
   * Closes the last open event with the same label.</br>
   * <b>Notice:</b> Some behavior is not considered reasonable and will increment the error count:
   * <ul>
   * <li>Closing an event, that has not been opened or was already closed</li>
   * <li>Not closing another event, that has been opened after opening this event</li>
   * </ul>
   *
   * @param label The name of the event
   * @param value An additional value for categorization of the event
   */
  public void close(String label, Object value) {
    StatEvent stored_event = pop(e -> e.storage.label.equals(label));
    if (stored_event != null) {
      stored_event.storage.update(baseTime.elapsed().minus(stored_event.time), value);
    }
  }

  /**
   * Closes the event.</br>
   * <b>Notice:</b> Some behavior is not considered reasonable and will increment the error count:
   * <ul>
   * <li>Closing an event, that has not been opened or was already closed</li>
   * <li>Not closing another event, that has been opened after opening this event</li>
   * </ul>
   *
   * @param event The handle on the event
   * @param value An additional value for categorization of the event
   */
  public void close(StatEvent event, Object value) {
    StatEvent stored_event = pop(e -> e.equals(event));
    if (stored_event != null) {
      stored_event.storage.update(baseTime.elapsed().minus(stored_event.time), value);
    }
  }

  private StatEvent push(StatEvent event) {
    long id = Thread.currentThread().getId();
    if (!openEvents.containsKey(id)) {
      openEvents.put(id, new ArrayDeque<StatEvent>());
    }
    openEvents.get(id).push(event);
    return event;
  }

  private StatEvent pop(Predicate<StatEvent> pred) {
    long id = Thread.currentThread().getId();
    if (openEvents.containsKey(id)
        && !openEvents.get(id).isEmpty()
        && openEvents.get(id).stream().anyMatch(pred)) {
      // Remove events until the right one is reached
      while (!openEvents.get(id).isEmpty()) {
        StatEvent e = openEvents.get(id).pop();
        if (pred.test(e)) {
          return e;
        } else {
          errors++;
        }
      }
    } else {
      errors++;
    }
    return null;
  }

  private AbstractStatStorage getCurrentStorage() {
    long id = Thread.currentThread().getId();
    if (openEvents.containsKey(id) && !openEvents.get(id).isEmpty()) {
      return openEvents.get(id).getFirst().storage;
    }
    return baseStorage;
  }

  @Override
  public void printStatistics(PrintStream out, Result result, UnmodifiableReachedSet reached) {
    baseStorage.printStatistics(out);
    if (errors > 0) {
      put(out, 0, "Defect StatEvents in " + name + " statistics", errors);
    }
  }

  /**
   * Handle on an event until it is closed.
   */
  public class StatEvent {

    final AbstractStatStorage storage;
    final Duration time;
    Object value;

    public StatEvent(Duration start_time, AbstractStatStorage storage) {
      this.time = start_time;
      this.storage = storage;
    }

    public StatEvent(Duration start_time, AbstractStatStorage storage, Object value) {
      this.time = start_time;
      this.storage = storage;
      this.value = value;
    }

    public void setValue(Object value) {
      this.value = value;
    }

  }

}
