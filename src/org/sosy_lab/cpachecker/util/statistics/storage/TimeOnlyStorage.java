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
import org.sosy_lab.common.time.TimeSpan;
import org.sosy_lab.cpachecker.util.statistics.StatKind;

public class TimeOnlyStorage extends AbstractStatStorage {

  int count_events = 0;
  long duration_total = 0, duration_squared = 0;
  long duration_min = Long.MAX_VALUE, duration_max = Long.MIN_VALUE;

  public TimeOnlyStorage(String label) {
    super(label);
  }

  @Override
  public synchronized void update(Duration duration, Object value) {
    long dur = duration.toMillis();
    count_events++;
    duration_total += dur;
    duration_squared += Math.multiplyExact(dur, dur);
    duration_min = Math.min(duration_min, dur);
    duration_max = Math.max(duration_max, dur);
  }

  @Override
  protected String adaptLabel(String label) {
    if (!label.toLowerCase().contains("time")) {
      return "Time for " + label.toLowerCase() + "s";
    } else {
      return label;
    }
  }

  @Override
  protected Object getPrintableStatistics(StatKind type) {
    if (type == null && count_events > 1) {
      return String.format(
          "%s (avg=%s, max=%s)",
          TimeSpan.ofMillis(duration_total),
          TimeSpan.ofMillis(duration_total).divide(count_events),
          TimeSpan.ofMillis(duration_max));
    } else if (type == null) {
      return TimeSpan.ofMillis(duration_total);
    } else {
      switch (type) {
        case AVG:
          return TimeSpan.ofMillis(duration_total).divide(count_events);
        case COUNT:
          return count_events;
        case SUM:
        default:
          return TimeSpan.ofMillis(duration_total);
      }
      }
    }
}
