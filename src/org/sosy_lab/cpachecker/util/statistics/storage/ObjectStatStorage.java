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

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.sosy_lab.cpachecker.util.statistics.StatisticsUtils;

/** Stores objects and their amount, but ignores their order. */

/**
 * Storage for object values and their amount (order will be ignored)</br>
 * <b>Terminal operators:</b> count, distinct (count), hist (all values and their amount),
 * &lt;value&gt; + _count/_perc/_both (count and/or percentage of this value)
 *
 */
public class ObjectStatStorage implements StatStorageStrategy {

  private static final Set<String> default_methods = ImmutableSet.of("count", "distinct", "hist");

  private final Set<String> methods = new HashSet<>(default_methods);
  private final Multiset<Object> hist = ConcurrentHashMultiset.create();

  @Override
  public void update(Object value) {
    if (value != null) {
      hist.add(value);
      updateMethods(value.toString());
    }
  }

  @Override
  public Set<String> getMethods() {
    return Collections.unmodifiableSet(methods);
  }

  private void updateMethods(String method) {
    if (method != null && !method.isEmpty() && !method.equals(".")) {
      method = StatisticsUtils.escape(method.substring(method.lastIndexOf(".") + 1));
      if (method.endsWith("_count") || method.endsWith("_perc") || method.endsWith("_both")) {
        method = method.substring(0, method.indexOf("_"));
      }
      methods.add(method + "_count");
      methods.add(method + "_perc");
      methods.add(method + "_both");
    }
  }

  @Override
  public boolean isValidPath(String path) {
    boolean existsInMethods = StatStorageStrategy.super.isValidPath(path);
    if (!existsInMethods
        && !path.contains(".")
        && (path.endsWith("_count") || path.endsWith("_perc") || path.endsWith("_both"))) {
      updateMethods(path);
      return true;
    }
    return existsInMethods;
  }

  @Override
  public Object get(String method) {
    if (method == null || method.isEmpty() || method.equals(".")) {
      return this;
    } else if (method.contains("_")) {
      String value = method.substring(0, method.lastIndexOf("_"));
      String type = method.substring(method.lastIndexOf("_") + 1);
      // Trying to find object/type in hist
      for (Object obj : hist.elementSet()) {
        if (value.equals(StatisticsUtils.escape(obj.toString()))) {
          switch (type) {
            case "count":
              return hist.count(obj);
            case "perc":
              return StatisticsUtils.toPercent(hist.count(obj), hist.size());
            case "both":
              return StatisticsUtils.valueWithPercentage(hist.count(obj), hist.size());
          }
        }
      }
      // If nothing was found, return default/null values
      switch (type) {
        case "count":
          return 0;
        case "perc":
          return StatisticsUtils.toPercent(0, 1);
        case "both":
          return StatisticsUtils.valueWithPercentage(0, 1);
      }
    }else{
      switch (method) {
        case "count":
          return hist.size();
        case "hist":
          return hist;
        case "distinct":
          return hist.elementSet().size();
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return hist.toString();
  }

}

