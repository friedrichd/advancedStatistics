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
package org.sosy_lab.cpachecker.util.statistics.storage;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import org.sosy_lab.cpachecker.util.statistics.StatisticsUtils;

/**
 * Stores and displays labeled durations and values (of statistic events) in a hierarchical
 * way.</br>
 * <b>Terminal operators:</b> count
 */
public class StatStorage implements StatStorageStrategy {

  static final Set<String> methods = new HashSet<>();
  static final List<Class<? extends StatStorageStrategy>> VALUETYPES = new ArrayList<>();
  static {
    methods.add("count");
    methods.add("value");
    methods.add("time");
    VALUETYPES.add(NumberStatStorage.class);
    VALUETYPES.add(ObjectStatStorage.class);
    VALUETYPES.add(ListStatStorage.class);
  }

  // labeled, ordered list of all sub-storages
  private final Map<String, StatStorage> children =
      Collections.synchronizedMap(new LinkedHashMap<>());

  // storage for duration and value
  private StatStorageStrategy storeDuration = null;
  private List<StatStorageStrategy> storeValue = new ArrayList<>();
  // counter for events
  private LongAdder countEvents = new LongAdder();

  public void createVariables(Collection<String> variables) {
    for (String path : variables) {
      if (path.contains("value.")) {
        String[] split = path.split("value\\.", 2);
        if (split.length > 1 && split[1] != null && !split[1].isEmpty()) {
          StatStorage innerNode = getSubStorage(split[0]);
          boolean success = false;
          for (StatStorageStrategy oldStrategy : innerNode.storeValue) {
            if (oldStrategy.isValidPath(split[1])) {
              success = true;
              break;
            }
          }
          if (success) {
            continue;
          }
          for (Class<? extends StatStorageStrategy> c : VALUETYPES) {
            if (innerNode.storeValue.stream().noneMatch(s -> s.getClass().equals(c))) {
            try {
                StatStorageStrategy newStrategy = c.getConstructor().newInstance();
                if (newStrategy.isValidPath(split[1])) {
                  success = true;
                  innerNode.storeValue.add(newStrategy);
                  break;
                }
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                | InvocationTargetException | NoSuchMethodException | SecurityException e) {
              e.printStackTrace();
              }
            }
          }
        }
      }
    }
  }


  /**
   * Finds a sub-storage (child) by name or creates a new sub-storage, if the name doesn't exist.
   *
   * @param label The name of the sub-storage (not "time" or "value")
   */
  public synchronized StatStorage getSubStorage(String label) {
    if (label == null || label.isEmpty() || label.equals(".")) {
      return this;
    }
    String prefix = label.contains(".") ? label.substring(0, label.indexOf(".")) : label;
    String escaped = StatisticsUtils.escape(prefix);
    String rest = label.substring(prefix.length());
    assert !getMethods().contains(escaped) : String
        .format("The label \"%s\" is already used as a method.", prefix);
    if (children.containsKey(escaped)) {
      return children.get(escaped).getSubStorage(rest);
    } else {
      StatStorage newChild = new StatStorage();
      children.put(escaped, newChild);
      return newChild.getSubStorage(rest);
    }
  }

  /** Updates the storage with a new event without duration or additional value. */
  @Override
  public void update() {
    countEvents.increment();
  }

  /**
   * Updates the storage with a new event without additional value.
   *
   * @param duration The duration of the event
   */
  public void update(Duration duration) {
    update();
    updateDuration(duration);
  }

  /**
   * Updates the storage with a new event without duration.
   *
   * @param value An additional value of the event for categorization
   */
  @Override
  public void update(Object value) {
    update();
    updateValue(value);
  }

  /**
   * Updates the storage with a new event.
   *
   * @param duration The duration of the event
   * @param value An additional value of the event for categorization
   */
  public void update(Duration duration, Object value) {
    update();
    updateDuration(duration);
    updateValue(value);
  }

  private void updateDuration(Duration duration) {
    if (storeDuration == null) {
      storeDuration = new DurationStatStorage();
    }
    storeDuration.update(duration);
  }

  private void updateValue(Object value) {
    if (storeValue.isEmpty()) {
      storeValue.add(new ObjectStatStorage());
    }
    storeValue.forEach(store -> store.update(value));
  }

  @Override
  public Set<String> getMethods() {
    return methods;
  }

  @Override
  public Object get(String path) {
    if (path == null || path.isEmpty() || path.equals(".")) {
      return this;
    }
    String prefix = path.contains(".") ? path.substring(0, path.indexOf(".")) : path;
    String rest = path.substring(prefix.length());
    switch (StatisticsUtils.escape(prefix)) {
      case "count":
        return countEvents.intValue();
      case "time":
        return storeDuration == null ? null : storeDuration.get(rest);
      case "value":
        return storeValue.isEmpty() ? null : storeValue;
      default:
        return getSubStorage(prefix).get(rest);
    }
  }

  @Override
  public Map<String, Object> getVariableMap(String prefix) {
    Map<String, Object> result = StatStorageStrategy.super.getVariableMap(prefix);
    for (Entry<String, StatStorage> entry : children.entrySet()) {
      result.putAll(entry.getValue().getVariableMap(prefix + entry.getKey()));
    }
    return result;
  }

  /** Returns the amount of processed <code>update</code> calls. */
  public int getUpdateCount() {
    return countEvents.intValue();
  }

}
