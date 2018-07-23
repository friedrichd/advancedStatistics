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
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.sosy_lab.common.time.TimeSpan;

/**
 * Works like a {@link NumberStatStorage}, but allows to work with TimeSpan and Duration.</br>
 * <b>Terminal operators:</b> all from {@link NumberStatStorage}
 */
public class DurationStatStorage implements StatStorageStrategy {

  NumberStatStorage ns = new NumberStatStorage();

  @Override
  public synchronized void update(Object obj) {
    if (obj instanceof Number) {
      ns.update(obj);
    } else if (obj instanceof Duration) {
      ns.update(((Duration) obj).toMillis());
    } else if (obj instanceof TimeSpan) {
      ns.update(((TimeSpan) obj).asMillis());
    }
  }

  @Override
  public Set<String> getMethods() {
    return ns.getMethods();
  }

  @Override
  public Object get(String method) {
    if (method == null || method.isEmpty() || method.equals(".")) {
      return this;
    }
    Object ret = ns.get(method);
    if (ret != null
        && !method.equals("count")
        && ret instanceof Double
        && !ret.equals(Double.NaN)) {
      return TimeSpan.ofMillis(((Double) ret).longValue()).formatAs(TimeUnit.SECONDS);
    } else {
      return ret;
    }
  }

  @Override
  public String toString() {
    return String.format(
        "%s (count: %2d, avg: %s, max: %s)",
        get("count"),
        get("sum").toString().trim(),
        get("avg").toString().trim(),
        get("max").toString().trim());
  }

}
