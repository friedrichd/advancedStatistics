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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.sosy_lab.common.time.TimeSpan;

/**
 * Saves basically everything and returns the raw data as a list of maps.</br>
 * There are no terminal operators available.
 */
public class CompleteStatStorage implements StatStorageStrategy {

  private final List<Map<String, Object>> complete_storage =
      Collections.synchronizedList(new ArrayList<>());

  @Override
  public void update(Object obj) {
    Map<String, Object> processed = new HashMap<>();
    if(obj instanceof Map<?,?>){
      for (Entry<?, ?> raw : ((Map<?, ?>) obj).entrySet()) {
        Object value = raw.getValue();
        if (value instanceof Duration) {
          value = ((Duration) value).toMillis();
        } else if (value instanceof TimeSpan) {
          value = ((TimeSpan) value).asMillis();
        }
        if(value instanceof Number || value instanceof CharSequence || value instanceof Boolean
            || value instanceof Map<?, ?>
            || value instanceof Iterable<?>) {
          processed.put(raw.getKey().toString(), value);
        }
      }
      if (!processed.isEmpty()) {
        complete_storage.add(Collections.unmodifiableMap(processed));
      }
    }
  }

  @Override
  public Object get(String method) {
    return Collections.unmodifiableList(complete_storage);
  }

}
