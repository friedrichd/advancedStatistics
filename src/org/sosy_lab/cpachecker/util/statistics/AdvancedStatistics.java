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
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Supplier;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.UnmodifiableReachedSet;
import org.sosy_lab.cpachecker.util.statistics.output.BasicStatOutput;
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

  private final String name;
  private final Stopwatch baseTime = Stopwatch.createUnstarted();

  // Entry point for storing the statistics
  private final StatStorage baseStorage = new StatStorage();
  // Variants for printing statistics
  private final List<StatOutputStrategy> printStrategy =
      Collections.synchronizedList(new ArrayList<>());
  // contains the stack of open events for all threads
  private final Map<Long, Deque<StatEvent>> openEvents = new HashMap<>();

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
    printStrategy.add(new BasicStatOutput(name, baseStorage, loadTemplate));
    return this;
  }

  /** Adds a new template for the default statistics file. */
  public AdvancedStatistics addBasicTemplate(File templateFile) {
    assert !baseTime.isRunning()
        && baseTime.elapsed().isZero() : "Outputs have to be added before tracking is started!";
    printStrategy.add(new BasicStatOutput(name, baseStorage, templateFile));
    return this;
  }

  /** Starts the overall timer and enables the tracking of events. */
  public synchronized void startTracking() {
    assert !baseTime.isRunning() : "Tracking is allready running!";
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

  /** Disables the tracking of events and stops the overall timer. */
  public synchronized void stopTracking() {
    assert baseTime.isRunning() : "Tracking is not running, so it can't be stopped!";
    baseTime.stop();
    baseStorage.update(Collections.singletonMap("duration", baseTime.elapsed()));
    // count all unclosed events as errors
    for (Entry<Long, Deque<StatEvent>> unclosedEvents : openEvents.entrySet()) {
      assert unclosedEvents.getValue().isEmpty() : "There are still "
          + unclosedEvents.getValue().size()
          + " open events on the stack for Thread #"
          + unclosedEvents.getKey()
          + "!";
    }
    openEvents.clear();
  }

  /**
   * Tracks an event without duration or value.
   *
   * @param label representing the name of the event
   */
  public StatEvent track(String label) {
    return createEvent(label).store(null);
  }

  /**
   * Tracks an event without duration, but with some value.
   *
   * @param label The name of the event
   * @param value An additional value for categorization of the event
   */
  public StatEvent track(String label, Object value) {
    return createEvent(label).setValue(value).store(null);
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
    assert baseTime.isRunning() : "Please start tracking before trying to track something!";
    return new StatEvent(baseTime.elapsed(), getCurrentStorage().getSubStorage(label));
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
    StatEvent stored_event = pop(event);
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
   * @param value An additional value for categorization of the event
   */
  public void close(StatEvent event, Object value) {
    StatEvent stored_event = pop(event);
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

  private synchronized StatEvent pop(StatEvent event) {
    long id = Thread.currentThread().getId();
    assert openEvents.containsKey(id)
        && !openEvents.get(id).isEmpty() : "There are no open events for Thread #" + id + "!";
    assert openEvents.get(id).contains(event) : "There are is no open event for Thread #"
        + id
        + " that matches the predicate!";
    StatEvent e = openEvents.get(id).pop();
    assert e.equals(event) : "There last event for Thread #" + id + " doesn't match the predicate!";
    return e;
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
    if (!pStatsCollection.contains(this)) {
      pStatsCollection.add(this);
    }
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
    Map<String, Object> mapping = null;
    for (StatOutputStrategy outStrategy : printStrategy) {
      if (!(outStrategy instanceof Statistics)) {
        if (mapping == null) {
          mapping = baseStorage.getVariableMap();
        }
        outStrategy.write(mapping);
      }
    }
  }


  /**
   * Handle on an event until it is closed.
   */
  public static class StatEvent {

    private final StatStorage storage;
    private final Duration start_time;
    private Object value = null;
    private boolean stored = false;

    public StatEvent(Duration start_time, StatStorage storage) {
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
     * Stores the event.
     *
     * @param end_time The time when the event was terminated, or null
     */
    private synchronized StatEvent store(Duration end_time) {
      assert !stored : "This event has already been stored!";
      Map<String, Object> map = new HashMap<>();
      map.put("start_time", start_time);
      if (value != null) {
        map.put("value", value);
      }
      if (end_time != null && start_time.compareTo(end_time) <= 0) {
        map.put("end_time", end_time);
        map.put("duration", end_time.minus(start_time));
      }
      storage.update(Collections.unmodifiableMap(map));
      stored = true;
      return this;
    }
  }

}
