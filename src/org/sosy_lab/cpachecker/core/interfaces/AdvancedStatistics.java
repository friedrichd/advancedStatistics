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
package org.sosy_lab.cpachecker.core.interfaces;

import java.util.ArrayList;
import java.util.List;
import org.sosy_lab.common.time.TimeSpan;
import org.sosy_lab.common.time.Timer;

/**
 * A class to output statistics and results of an analysis in an advanced way.
 *
 * You usually want to implement {@link StatisticsProvider} and register your Statistics instances
 * so that they are actually called after CPAchecker finishes.
 */
public interface AdvancedStatistics extends Statistics {

  final Timer baseTimer = new Timer();
  final List<TimeSpan> timestamps = new ArrayList<>();
  final List<String> labels = new ArrayList<>();

  public default void startTracking() {
    baseTimer.start();
    this.trackEvent("Start");
  }

  public default void stopTracking() {
    this.trackEvent("Stop");
    baseTimer.stopIfRunning();
  }

  public default void trackEvent(String label) {
    timestamps.add(baseTimer.getSumTime());
    labels.add(label);
  }

}
