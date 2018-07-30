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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Interface for all nodes that provide printable values. */
public interface StatStorageStrategy {

  /** Returns all available terminal methods. */
  public default Set<String> getMethods() {
    return Collections.emptySet();
  }

  /** Validates if the given path could be stored. */
  public default boolean isValidPath(String path) {
    return path == null || path.isEmpty() || path.equals(".") || getMethods().contains(path);
  }

  /** Returns the inner or terminal node identified by this path. */
  public Object get(String path);

  /** Updates a node with an object value. */
  public void update(Object obj);

  /** Returns a map of all available values. */
  public default Map<String, Object> getVariableMap() {
    return getVariableMap("");
  }

  /** Returns a map of all available values and puts a prefix in front of all keys. */
  public default Map<String, Object> getVariableMap(String prefix) {
    Map<String, Object> result = new HashMap<>();
    if (prefix == null) {
      prefix = "";
    } else if (!prefix.isEmpty() && !prefix.endsWith(".")) {
      prefix += ".";
    }
    for (String key : getMethods()) {
      Object obj = get(key);
      if (obj != null) {
        result.put(prefix + key, obj);
        if (obj instanceof StatStorageStrategy) {
          result.putAll(((StatStorageStrategy) obj).getVariableMap(prefix + key));
        } else if (obj instanceof Collection<?>) {
          for (Object part : ((Collection<?>) obj)) {
            if (part instanceof StatStorageStrategy) {
              result.putAll(((StatStorageStrategy) part).getVariableMap(prefix + key));
            }
          }
        }
      }
    }
    return result;
  }

}
