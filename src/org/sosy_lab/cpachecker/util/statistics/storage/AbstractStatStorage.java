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
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sosy_lab.cpachecker.util.statistics.StatisticsUtils;

public abstract class AbstractStatStorage {

  public final String label;
  private final List<AbstractStatStorage> children =
      Collections.synchronizedList(new ArrayList<>());

  public AbstractStatStorage(String label) {
    this.label = label;
  }

  public AbstractStatStorage getChild(String label, Class<? extends AbstractStatStorage> c) {
    synchronized (children) {
      for (AbstractStatStorage child : children) {
        if (child.label.equals(label)) {
          return child;
        }
      }
  /**
   * Finds a sub-storage (child) by name or creates a new sub-storage, if the name doesn't exist.
   *
   * @param label The name of the sub-storage
   * @param c The type of the new sub-storage (must have a constructor with parameter
   *        {@link java.lang.String String} )
   * @return The (new) sub-storage
   */
      try {
        AbstractStatStorage newChild = c.getDeclaredConstructor(String.class).newInstance(label);
        children.add(newChild);
        return newChild;
      } catch (Exception e) {
        return null;
      }
    }
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
    StatisticsUtils.write(out, level, 50, adaptLabel(label), getPrintableStatistics());
    synchronized (children) {
      for (AbstractStatStorage child : children) {
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

  /**
   * Returns the value of the storage unit in a nice, printable way.
   */
  protected abstract String getPrintableStatistics();

}
