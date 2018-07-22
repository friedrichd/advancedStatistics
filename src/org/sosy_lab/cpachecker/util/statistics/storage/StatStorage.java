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

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

/** Stores and displays labeled durations and values (of statistic events) in a hierarchical way. */
public class StatStorage implements StatStorageStrategy {

  // labeled, ordered list of all sub-storages
  private final Map<String, StatStorage> children =
      Collections.synchronizedMap(new LinkedHashMap<>());
  // storage for duration and value
  private StatStorageStrategy storeDuration = null, storeValue = null;

  // counter for events
  private LongAdder countEvents = new LongAdder();

  @SuppressWarnings("unused")
  public void createVariables(Set<String> variables) {
    // TODO Auto-generated method stub
  }

  @Override
  public Set<String> getMethods() {
    return Collections.singleton("count");
  }

  @Override
  public Set<String> getSubStorages() {
    Set<String> res = new HashSet<>(children.keySet());
    res.add("time");
    res.add("value");
    return res;
  }

  @Override
  public Object get(String path) {
    if (path.equals("count")) {
      return countEvents.intValue();
    } else {
      String prefix = path.contains(".") ? path.substring(0, path.indexOf(".")) : path;
      String rest = path.substring(prefix.length());
      return getSubStorage(prefix).get(rest);
    }
  }

  /**
   * Finds a sub-storage (child) by name or creates a new sub-storage, if the name doesn't exist.
   *
   * @param label The name of the sub-storage
   */
  @Override
  public synchronized StatStorageStrategy getSubStorage(String label) {
    if (label.equals("time")) {
      if (storeDuration == null) {
        storeDuration = new DurationStatStorage();
      }
      return storeDuration;
    } else if (label.equals("value")) {
      if (storeValue == null) {
        storeValue = new ObjectStatStorage();
      }
      return storeValue;
    } else if (children.containsKey(label)) {
      return children.get(label);
    } else {
      StatStorage newChild = new StatStorage();
      children.put(label, newChild);
      return newChild;
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
    countEvents.increment();
    if (storeDuration == null) {
      storeDuration = new DurationStatStorage();
    }
    storeDuration.update(duration);
  }

  /**
   * Updates the storage with a new event without duration.
   *
   * @param value An additional value of the event for categorization
   */
  @Override
  public void update(Object value) {
    countEvents.increment();
    if (storeValue == null) {
      storeValue = new ObjectStatStorage();
    }
    storeValue.update(value);
  }

  /**
   * Updates the storage with a new event.
   *
   * @param duration The duration of the event
   * @param value An additional value of the event for categorization
   */
  public void update(Duration duration, Object value) {
    countEvents.increment();
    if (storeDuration == null) {
      storeDuration = new DurationStatStorage();
    }
    storeDuration.update(duration);

    if (storeValue == null) {
      storeValue = new ObjectStatStorage();
    }
    storeValue.update(value);
    // getChild(value.toString()).update(duration);
  }

  /** Returns the amount of processed <code>update</code> calls. */
  public int getUpdateCount() {
    return countEvents.intValue();
  }

}
