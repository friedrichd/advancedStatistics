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
package org.sosy_lab.cpachecker.cpa.usage;

import java.util.Collection;
import java.util.Collections;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.exceptions.CPAException;

public class UsageStopOperator implements StopOperator {

  private final StopOperator wrappedStop;

  UsageStopOperator(StopOperator pWrappedStop) {
    wrappedStop = pWrappedStop;
  }

  @Override
  public boolean stop(
      AbstractState pState, Collection<AbstractState> pReached, Precision pPrecision)
      throws CPAException, InterruptedException {

    UsageState usageState = (UsageState) pState;
    UsagePrecision usagePrecision = (UsagePrecision) pPrecision;

    for (AbstractState reached : pReached) {
      UsageState reachedUsageState = (UsageState) reached;
      boolean result = usageState.isLessOrEqual(reachedUsageState);
      if (!result) {
        continue;
      }
      result =
          wrappedStop.stop(
              usageState.getWrappedState(),
              Collections.singleton(reachedUsageState.getWrappedState()),
              usagePrecision.getWrappedPrecision());
      if (result) {
        return true;
      }
    }
    return false;
  }
}
