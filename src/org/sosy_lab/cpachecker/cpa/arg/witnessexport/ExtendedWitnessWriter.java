/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.arg.witnessexport;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.counterexample.CFAEdgeWithAdditionalInfo;
import org.sosy_lab.cpachecker.core.counterexample.CounterexampleInfo;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.KeyDef;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.WitnessType;
import org.sosy_lab.cpachecker.util.automaton.VerificationTaskMetaData;
import org.sosy_lab.cpachecker.util.expressions.ExpressionTreeFactory;
import org.sosy_lab.cpachecker.util.expressions.Simplifier;

public class ExtendedWitnessWriter extends WitnessWriter {
  Map<String, KeyDef> tagConverter;
  ExtendedWitnessWriter(
      WitnessOptions pOptions,
      CFA pCfa,
      VerificationTaskMetaData pMetaData,
      ExpressionTreeFactory<Object> pFactory,
      Simplifier<Object> pSimplifier,
      @Nullable String pDefaultSourceFileName,
      WitnessType pGraphType,
      InvariantProvider pInvariantProvider,
      Map<String, KeyDef> pTagConverter) {
    super(pOptions, pCfa, pMetaData, pFactory, pSimplifier, pDefaultSourceFileName, pGraphType,
        pInvariantProvider);
    tagConverter = pTagConverter;
  }

  @Override
  protected Map<ARGState, CFAEdgeWithAdditionalInfo> getAdditionalInfo(Optional<CounterexampleInfo> pCounterExample) {
    if (pCounterExample.isPresent()) {
      return pCounterExample.get().getAdditionalInfoMapping();
    }
    return ImmutableMap.of();
  }

  @Override
  protected TransitionCondition addAdditionalInfo(
      TransitionCondition pCondition, CFAEdgeWithAdditionalInfo pAdditionalInfo) {
    TransitionCondition result = pCondition;
    if (pAdditionalInfo != null) {
      for (Entry<String, ImmutableSet<Object>> addInfo : pAdditionalInfo.getInfos()) {
        String tag = addInfo.getKey();
        Set<Object> values = addInfo.getValue();
        for (Object value : values) {
          result = result.putAndCopy(tagConvert(tag), value.toString());
          result = result.putAndCopy(tagConvert(tag), pCondition.getMapping().get(KeyDef
              .SOURCECODE));
        }
      }
    }
    return result;
  }

  @Override
  protected boolean handleAsEpsilonEdge(CFAEdge pEdge) {
    return AutomatonGraphmlCommon.handleAsEpsilonEdge(pEdge);
  }

  private KeyDef tagConvert(String pTag) {
    return tagConverter.get(pTag);
  }
}