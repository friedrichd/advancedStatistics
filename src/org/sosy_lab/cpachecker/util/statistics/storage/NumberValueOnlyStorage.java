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

public class NumberValueOnlyStorage extends AbstractStatStorage {

  int count_events = 0;
  double value_total = 0, value_squared = 0;
  double value_min = Double.MAX_VALUE, value_max = Double.MIN_VALUE;

  public NumberValueOnlyStorage(String label) {
    super(label);
  }

  @Override
  public synchronized void update(Duration duration, Object value) {

    if (value instanceof Number) {
      Number n = (Number) value;
      count_events++;
      value_total += n.doubleValue();
      value_squared += Math.pow(n.doubleValue(), 2);
      value_min = Math.min(value_min, n.doubleValue());
      value_max = Math.max(value_max, n.doubleValue());
    }
  }

  @Override
  public String getPrintableStatistics() {
    if (count_events < 2) {
      return String.format(
          (Math.floor(value_total) == Math.ceil(value_total)) ? "%.0f" : "%.2f",
          value_total);
    } else {
      return String.format(
          "%.2f (avg=%.2f, max=%.2f)",
          value_total,
          value_total / count_events,
          value_max);
    }

  }

}
