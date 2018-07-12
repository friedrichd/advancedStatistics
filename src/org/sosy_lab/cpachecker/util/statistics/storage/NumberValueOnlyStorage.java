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
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import org.sosy_lab.cpachecker.util.statistics.StatKind;

public class NumberValueOnlyStorage extends AbstractStatStorage {

  LongAdder count_events = new LongAdder();
  DoubleAdder value_total = new DoubleAdder(), value_squared = new DoubleAdder();
  DoubleAccumulator value_min = new DoubleAccumulator(Math::min, Double.MAX_VALUE);
  DoubleAccumulator value_max = new DoubleAccumulator(Math::max, Double.MIN_VALUE);

  public NumberValueOnlyStorage(String label) {
    super(label);
  }

  @Override
  public void update(Duration duration, Object value) {
    if (value instanceof Number) {
      double n = ((Number) value).doubleValue();
      count_events.increment();
      value_total.add(n);
      value_squared.add(Math.pow(n, 2));
      value_min.accumulate(n);
      value_max.accumulate(n);
    }
  }

  @Override
  protected Object getPrintableStatistics(StatKind type) {
    if (type == null && count_events.intValue() > 1) {
      return String
          .format(
              "%.2f (avg=%.2f, max=%.2f)",
              value_total.doubleValue(),
              value_total.doubleValue() / count_events.intValue(),
              value_max.doubleValue());
    }else{
      double printMe = value_total.doubleValue();
      if (type != null) {
        switch (type) {
          case AVG:
            printMe = value_total.doubleValue() / count_events.intValue();
            break;
          case COUNT:
            printMe = count_events.doubleValue();
            break;
          case SUM:
          default:
            printMe = value_total.doubleValue();
        }
      }
      return String.format((Math.floor(printMe) == Math.ceil(printMe)) ? "%.0f" : "%.2f", printMe);
    }
  }
}
