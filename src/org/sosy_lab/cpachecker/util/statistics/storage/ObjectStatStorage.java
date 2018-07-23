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
import com.google.common.collect.Multiset;
import java.util.HashSet;
import java.util.Set;
import org.sosy_lab.cpachecker.util.statistics.StatisticsUtils;

/** Stores objects and their amount, but ignores their order. */

/**
 * Storage for object values and their amount (order will be ignored)</br>
 * <b>Terminal operators:</b> count, distinct (count), &lt;value&gt; (count of this value)
 */
public class ObjectStatStorage implements StatStorageStrategy {

  private final Set<String> methods = new HashSet<>();
  {
    methods.add("count");
    methods.add("distinct");
    methods.add("hist");
  }
  private final Multiset<Object> hist = ConcurrentHashMultiset.create();

  @Override
  public void update(Object value) {
    if (value != null) {
      hist.add(value);
      methods.add(StatisticsUtils.escape(value.toString()));
    }
  }

  @Override
  public Set<String> getMethods() {
    return methods;
  }

  @Override
  public boolean isValidPath(String path) {
    boolean result = path == null || !path.contains(".") || path.equals(".");
    if (result) {
      methods.add(StatisticsUtils.escape(path));
    }
    return result;
  }

  @Override
  public Object get(String method) {
    if (method == null || method.isEmpty() || method.equals(".")) {
      return this;
    } else if (method.equals("count")) {
      return hist.size();
    } else if (method.equals("hist")) {
      return hist;
    } else if (method.equals("distinct")) {
      return hist.elementSet().size();
    } else {
      for (Object obj : hist.elementSet()) {
        if (method.equals(StatisticsUtils.escape(obj.toString()))) {
          return hist.count(obj);
        }
      }
      return 0;
    }
  }

  @Override
  public String toString() {
    return hist.toString();
  }

}

