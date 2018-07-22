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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public interface StatStorageStrategy {

  public default Set<String> getMethods(){
    return Collections.emptySet();
  }

  public default Set<String> getSubStorages() {
    return Collections.emptySet();
  }

  public Object get(String method);

  public default StatStorageStrategy getSubStorage(@SuppressWarnings("unused") String path) {
    return null;
  }

  public default void update() {}

  public void update(Object obj);

  public default Map<String, Object> getVariableMap() {
    return getVariableMap("");
  }

  public default Map<String, Object> getVariableMap(String prefix) {
    Map<String, Object> result = new HashMap<>();
    if (prefix == null) {
      prefix = "";
    } else if (!prefix.isEmpty() && !prefix.endsWith(".")) {
      prefix += ".";
    }
    for (String key : getMethods()) {
      result.put(prefix + key, get(key));
    }
    for (String key : getSubStorages()) {
      StatStorageStrategy child = getSubStorage(key);
      if (child != null) {
        result.putAll(child.getVariableMap(prefix + key));
      }
    }
    return result;
  }

}
