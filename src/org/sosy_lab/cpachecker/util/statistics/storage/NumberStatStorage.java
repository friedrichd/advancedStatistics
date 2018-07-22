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
 */
package org.sosy_lab.cpachecker.util.statistics.storage;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

public class NumberStatStorage implements StatStorageStrategy {

  private static Set<String> methods = new HashSet<>();
  static {
    methods.add("count");
    methods.add("sum");
    methods.add("min");
    methods.add("max");
    methods.add("avg");
    methods.add("std");
  }

  private LongAdder count = new LongAdder();
  private DoubleAdder sum = new DoubleAdder(), sum_squared = new DoubleAdder();
  private DoubleAccumulator min = new DoubleAccumulator(Math::min, Double.MAX_VALUE);
  private DoubleAccumulator max = new DoubleAccumulator(Math::max, Double.MIN_VALUE);

  @Override
  public void update(Object obj) {
    if (obj instanceof Number) {
      double d = ((Number) obj).doubleValue();
      count.increment();
      sum.add(d);
      sum_squared.add(Math.pow(d, 2));
      min.accumulate(d);
      max.accumulate(d);
    }
  }

  @Override
  public Set<String> getMethods() {
    return methods;
  }

  @Override
  public Object get(String method) {
    switch (method) {
      case "count":
        return count.intValue();
      case "sum":
        return sum.doubleValue();
      case "min":
        return min.doubleValue();
      case "max":
        return max.doubleValue();
      case "avg":
        return count.intValue() > 0 ? sum.doubleValue() / count.doubleValue() : Double.NaN;
      case "std":
        return count.intValue() > 0
            ? Math.sqrt(
                (sum_squared.doubleValue() - Math.pow(sum.doubleValue(), 2) / count.doubleValue())
                    / count.doubleValue())
            : Double.NaN;
      default:
        return null;
    }
  }

}
