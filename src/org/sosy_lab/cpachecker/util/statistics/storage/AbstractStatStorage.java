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
      try {
        AbstractStatStorage newChild = c.getDeclaredConstructor(String.class).newInstance(label);
        children.add(newChild);
        return newChild;
      } catch (Exception e) {
        return null;
      }
    }
  }

  public void update(Duration duration) {
    update(duration, null);
  }

  public void update(Object value) {
    update(Duration.ZERO, value);
  }

  public abstract void update(Duration duration, Object value);

  public void printStatistics(PrintStream out) {
    printStatistics(out, 0);
  }

  private void printStatistics(PrintStream out, int level) {
    StatisticsUtils.write(out, level, 50, adaptLabel(label), getPrintableStatistics());
    synchronized (children) {
      for (AbstractStatStorage child : children) {
        child.printStatistics(out, level + 1);
      }
    }
  }

  protected String adaptLabel(String label) {
    return label;
  }

  protected abstract String getPrintableStatistics();

}
