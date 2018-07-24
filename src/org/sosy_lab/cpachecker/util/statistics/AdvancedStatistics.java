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
import java.io.File;
import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.statistics.output.BasicStatOutputStrategy;
import org.sosy_lab.cpachecker.util.statistics.output.StatOutputStrategy;
import org.sosy_lab.cpachecker.util.statistics.storage.StatStorage;

/**
 * A class to output statistics and results of an analysis in an advanced way.</br>
 * <strong>Notice:</strong> For any instance "stats" of AdvancedStatistics, both
 * <ol>
 * <li>&lt;StatsCollection&gt;.add(stats)) and</li>
 * <li>stats.collectStatistics(&lt;StatsCollection&gt;)</li>
 * </ol>
 * need to be called in the <code>collectStatistics</code> method of the surrounding
 * {@link StatisticsProvider}. Otherwise some statistics might not be printed.
 *
 */
public class AdvancedStatistics implements Statistics, StatisticsProvider {

  private static final String ERROR_START_TRACKING =
      "Please start tracking before trying to track something!";

  private final String name;
  private final Stopwatch baseTime = Stopwatch.createUnstarted();

  // Entry point for storing the statistics
  private final StatStorage baseStorage = new StatStorage();
  // Variants for printing statistics
  private final List<StatOutputStrategy> printStrategy =
      Collections.synchronizedList(new ArrayList<>());

  // contains the stack of open events for all threads
  private final Map<Long, Deque<StatEvent>> openEvents = new HashMap<>();
  // counter for detected errors
  private final LongAdder errors = new LongAdder();

  public AdvancedStatistics(String name) {
    this.name = name;
  }

  @Override
  public String getName() {
    return null;
  }

  /** Adds a new output to this statistic. */
  public AdvancedStatistics addOutputStrategy(StatOutputStrategy output) {
    assert !baseTime.isRunning()
        && baseTime.elapsed().isZero() : "Outputs have to be added before tracking is started!";
    printStrategy.add(output);
    return this;
  }

  /** Adds a new template for the default statistics file. */
  public AdvancedStatistics addBasicTemplate(Supplier<String> loadTemplate) {
    assert !baseTime.isRunning()
        && baseTime.elapsed().isZero() : "Outputs have to be added before tracking is started!";
    printStrategy.add(new BasicStatOutputStrategy(name, baseStorage, loadTemplate));
    return this;
  }

  /** Adds a new template for the default statistics file. */
  public AdvancedStatistics addBasicTemplate(File templateFile) {
    assert !baseTime.isRunning()
        && baseTime.elapsed().isZero() : "Outputs have to be added before tracking is started!";
    printStrategy.add(new BasicStatOutputStrategy(name, baseStorage, templateFile));
    return this;
  }

  /** Starts the overall timer and enables the tracking of events. */
  public synchronized void startTracking() {
    if (!baseTime.isRunning()) {
      if (baseTime.elapsed().isZero()) {
        Set<String> variables = new HashSet<>();
        for (StatOutputStrategy out : printStrategy) {
          variables.addAll(out.getRequiredVariables());
        }
        baseStorage.createVariables(variables);
      } else {
        baseTime.reset();
      }
      baseTime.start();
    }
  }

  /** Disables the tracking of events and stops the overall timer. */
  public synchronized void stopTracking() {
    if (baseTime.isRunning()) {
      baseTime.stop();
      baseStorage.update(baseTime.elapsed());

      // count all unclosed events as errors
      for (Deque<StatEvent> unclosedEvents : openEvents.values()) {
        errors.add(unclosedEvents.size());
      }
      openEvents.clear();
    }
  }

  /**
   * Tracks an event without duration or value.
   *
   * @param label representing the name of the event
   */
  public StatEvent track(String label) {
    return createEvent(label).store();
  }

  /**
   * Tracks an event without duration, but with some value.
   *
   * @param label The name of the event
   * @param value An additional value for categorization of the event
   */
  public StatEvent track(String label, Object value) {
    return createEvent(label).setValue(value).store();
  }

  /**
   * Opens an event without value.</br>
   * A value can be added either on opening, on closing or not at all.
   *
   * @param label The name of the event
   */
  public StatEvent open(String label) {
    return push(createEvent(label));
  }

  /**
   * Opens an event with value. A value can be added either on opening, on closing or not at all.
   *
   * @param label The name of the event
   * @param value An additional value for categorization of the event
   */
  public StatEvent open(String label, Object value) {
    return push(createEvent(label).setValue(value));
  }

  /** Creates a new event for the given label. */
  private StatEvent createEvent(String label) {
    assert baseTime.isRunning() : ERROR_START_TRACKING;
    return new StatEvent(label, baseTime.elapsed(), getCurrentStorage().getSubStorage(label));
  }

  /** Validates if an event is open on the current thread for given label. */
  public boolean hasOpenEvent(String label) {
    long id = Thread.currentThread().getId();
    return openEvents.containsKey(id)
        && !openEvents.get(id).isEmpty()
        && openEvents.get(id).stream().anyMatch(a -> a.label.equals(label));
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
    assert baseTime.isRunning() : ERROR_START_TRACKING;
    StatEvent stored_event = pop(e -> e.label.equals(label));
    if (stored_event != null) {
      stored_event.store(baseTime.elapsed());
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
    assert baseTime.isRunning() : ERROR_START_TRACKING;
    StatEvent stored_event = pop(e -> e.equals(event));
    if (stored_event != null) {
      stored_event.store(baseTime.elapsed());
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
    assert baseTime.isRunning() : ERROR_START_TRACKING;
    StatEvent stored_event = pop(e -> e.label.equals(label));
    if (stored_event != null) {
      stored_event.setValue(value);
      stored_event.store(baseTime.elapsed());
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
    assert baseTime.isRunning() : ERROR_START_TRACKING;
    StatEvent stored_event = pop(e -> e.equals(event));
    if (stored_event != null) {
      stored_event.setValue(value);
      stored_event.store(baseTime.elapsed());
    }
  }

  private synchronized StatEvent push(StatEvent event) {
    long id = Thread.currentThread().getId();
    if (!openEvents.containsKey(id)) {
      openEvents.put(id, new ArrayDeque<StatEvent>());
    }
    openEvents.get(id).push(event);
    return event;
  }

  private synchronized StatEvent pop(Predicate<StatEvent> pred) {
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
          errors.increment();
        }
      }
    } else {
      errors.increment();
    }
    return null;
  }

  private StatStorage getCurrentStorage() {
    long id = Thread.currentThread().getId();
    if (openEvents.containsKey(id) && !openEvents.get(id).isEmpty()) {
      return openEvents.get(id).getFirst().storage;
    }
    return baseStorage;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    for (StatOutputStrategy outStrategy : printStrategy) {
      if (outStrategy instanceof Statistics) {
        pStatsCollection.add((Statistics) outStrategy);
      }
    }
  }

  @Override
  public void printStatistics(PrintStream out, Result result, UnmodifiableReachedSet reached) {
    printStatistics();
  }

  public void printStatistics() {
    for (StatOutputStrategy outStrategy : printStrategy) {
      if (!(outStrategy instanceof Statistics)) {
        outStrategy.write(baseStorage.getVariableMap());
      }
    }
    // TODO: ERRORHANDLING
  }


  /**
   * Handle on an event until it is closed.
   */
  public static class StatEvent {

    private final String label;
    private final StatStorage storage;
    private final Duration start_time;
    private Object value = null;
    private boolean stored = false;

    public StatEvent(String label, Duration start_time, StatStorage storage) {
      this.label = label;
      this.start_time = start_time;
      this.storage = storage;
    }

    /**
     * Adds some value (e.g. for categorization) to the event.
     *
     * @param value An additional value
     */
    public synchronized StatEvent setValue(Object value) {
      assert !stored : "This event has already been stored!";
      this.value = value;
      return this;
    }

    /**
     * Stores the event without termination time (therefore without duration).
     */
    private synchronized StatEvent store() {
      assert !stored : "This event has already been stored!";
      if (value == null) {
        storage.update();
      } else {
        storage.update(value);
      }
      stored = true;
      return this;
    }

    /**
     * Stores the event.
     *
     * @param end_time The time when the event was terminated.
     */
    private synchronized StatEvent store(Duration end_time) {
      assert !stored : "This event has already been stored!";
      if (end_time == null || start_time.compareTo(end_time) > 0) {
        store();
      } else if(value == null){
        storage.update(end_time.minus(start_time));
      } else {
        storage.update(end_time.minus(start_time), value);
      }
      stored = true;
      return this;
    }

  }

}
