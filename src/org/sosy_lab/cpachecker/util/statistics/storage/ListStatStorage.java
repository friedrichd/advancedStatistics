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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores all objects in a list. Therefore all values and there order is saved.</br>
 * <b>Terminal operators:</b> count, list
 */
public class ListStatStorage implements StatStorageStrategy {

  private static final Set<String> methods = new HashSet<>();
  static {
    methods.add("list");
    methods.add("count");
  }

  private List<Object> list = Collections.synchronizedList(new ArrayList<>());

  @Override
  public void update(Object obj) {
    list.add(obj);
  }

  @Override
  public Set<String> getMethods() {
    return methods;
  }

  @Override
  public Object get(String method) {
    if (method == null || method.isEmpty() || method.equals(".")) {
      return this;
    } else {
      switch (method) {
        case "list":
          return Collections.unmodifiableList(list);
        case "count":
          return list.size();
        default:
          return null;
      }
    }
  }

  @Override
  public String toString() {
    return list.toString();
  }

}
