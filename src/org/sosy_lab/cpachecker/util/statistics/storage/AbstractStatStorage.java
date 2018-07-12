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

import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatisticsUtils;

public abstract class AbstractStatStorage {

  // name of the storage
  public final String label;
  // labeled, ordered list of all sub-storages
  private final Map<String, AbstractStatStorage> children =
      Collections.synchronizedMap(new LinkedHashMap<>());
  // ordered list of all display options
  private final Map<StatKind, String> printOptions =
      Collections.synchronizedMap(new LinkedHashMap<>());

  public AbstractStatStorage(String label) {
    this.label = label;
  }

  /**
   * Finds a sub-storage (child) by name or creates a new sub-storage, if the name doesn't exist.
   *
   * @param label The name of the sub-storage
   * @param c The type of the new sub-storage (must have a constructor with parameter
   *        {@link java.lang.String String} )
   * @return The (new) sub-storage
   */
  public AbstractStatStorage
      getChildOrDefault(String label, Class<? extends AbstractStatStorage> c) {
    if (children.containsKey(label)) {
      return children.get(label);
    } else {
      try {
        AbstractStatStorage newChild = c.getDeclaredConstructor(String.class).newInstance(label);
        children.put(label, newChild);
        return newChild;
      } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
          | InvocationTargetException | NoSuchMethodException | SecurityException e) {
        return null;
      }
    }
  }

  /**
   * Updates the storage with a new event without duration or additional value.
   */
  public void update() {
    update(Duration.ZERO, null);
  }

  /**
   * Updates the storage with a new event without additional value.
   *
   * @param duration The duration of the event
   */
  public void update(Duration duration) {
    update(duration, null);
  }

  /**
   * Updates the storage with a new event without duration.
   *
   * @param value An additional value of the event for categorization
   */
  public void update(Object value) {
    update(Duration.ZERO, value);
  }

  /**
   * Updates the storage with a new event.
   *
   * @param duration The duration of the event
   * @param value An additional value of the event for categorization
   */
  public abstract void update(Duration duration, Object value);

  /**
   * Prints the statistics storage with all sub-storages.
   *
   * @param out The output stream
   */
  public void printStatistics(PrintStream out) {
    printStatistics(out, 0);
  }

  /**
   * Prints the statistics storage with all sub-storages.
   *
   * @param out The output stream
   * @param level The amount of space in front of every line
   */
  public void printStatistics(PrintStream out, int level) {
    // Without options, use the default.
    if (printOptions.isEmpty()) {
      StatisticsUtils.write(out, level, 50, adaptLabel(label), getPrintableStatistics());
    } else {
      // Write a separate line for every option
      for (Entry<StatKind, String> option : printOptions.entrySet()) {
        StatisticsUtils
            .write(out, level, 50, option.getValue(), getPrintableStatistics(option.getKey()));
      }
    }
    synchronized (children) {
      for (AbstractStatStorage child : children.values()) {
        child.printStatistics(out, level + 1);
      }
    }
  }

  /**
   * All sub-classes may overwrite this method to customize the label, for example with prefixes.
   *
   * @param label The original name of the storage
   */
  protected String adaptLabel(String label) {
    return label;
  }

  public void setPrintFormat(StatKind type, String label) {
    printOptions.put(type, label);
  }

  /**
   * Returns the default value of the storage unit in a nice, printable way.
   */
  protected Object getPrintableStatistics() {
    return getPrintableStatistics(null);
  }

  /**
   * Returns the value of the storage unit in a nice, printable way.
   *
   * @param type The requested type
   */
  protected abstract Object getPrintableStatistics(StatKind type);

}
