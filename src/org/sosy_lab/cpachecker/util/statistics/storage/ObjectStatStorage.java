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

/** Stores objects and their amount, but ignores their order. */
public class ObjectStatStorage implements StatStorageStrategy {

  private static Set<String> methods = new HashSet<>();
  static {
    methods.add("hist");
    methods.add("count");
  }

  private final Multiset<Object> hist = ConcurrentHashMultiset.create();

  @Override
  public void update(Object value) {
    if (value != null) {
      hist.add(value);
    }
  }

  @Override
  public Set<String> getMethods() {
    return methods;
  }

  @Override
  public Object get(String method) {
    if (method.equals("hist")) {
      return hist;
    } else if (method.equals("count")) {
      return hist.size();
    }
    return null;
  }

  public int getTimesWithValue(Object value) {
    return hist.count(value);
  }

  public Set<Object> getValues() {
    return hist.elementSet();
  }

}

