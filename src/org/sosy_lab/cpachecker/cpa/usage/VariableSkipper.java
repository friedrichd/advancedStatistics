/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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

import static com.google.common.collect.FluentIterable.from;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.util.identifiers.AbstractIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.SingleIdentifier;
import org.sosy_lab.cpachecker.util.identifiers.StructureIdentifier;

@Options(prefix = "cpa.usage.skippedvariables")
public class VariableSkipper {
  @Option(description = "variables, which will be filtered by its name", secure = true)
  private Set<String> byName = ImmutableSet.of();

  @Option(description = "variables, which will be filtered by its name prefix", secure = true)
  private Set<String> byNamePrefix = ImmutableSet.of();

  @Option(description = "variables, which will be filtered by its type", secure = true)
  private Set<String> byType = ImmutableSet.of();

  @Option(description = "variables, which will be filtered by function location", secure = true)
  private Set<String> byFunction = ImmutableSet.of();

  @Option(description = "variables, which will be filtered by function prefix", secure = true)
  private Set<String> byFunctionPrefix = ImmutableSet.of();

  public VariableSkipper(Configuration pConfig) throws InvalidConfigurationException {
    pConfig.inject(this);
  }

  public boolean shouldBeSkipped(AbstractIdentifier id, UsageInfo usage) {

    if (id instanceof SingleIdentifier) {
      SingleIdentifier singleId = (SingleIdentifier) id;
      if (checkId(singleId)) {
        return true;
      } else if (singleId instanceof StructureIdentifier) {
        AbstractIdentifier owner = singleId;
        while (owner instanceof StructureIdentifier) {
          owner = ((StructureIdentifier) owner).getOwner();
        }
        if (owner instanceof SingleIdentifier && checkId((SingleIdentifier) owner)) {
          return true;
        }
      }
    }

    // Check special functions like INIT_LIST_HEAD, in which we should skip all usages
    String functionName = usage.getLine().getNode().getFunctionName();
    if (byFunction.contains(functionName)) {
      return true;
    }

    if (from(byFunctionPrefix).anyMatch(functionName::startsWith)) {
      return true;
    }

    return false;
  }

  private boolean checkId(SingleIdentifier singleId) {
    String varName = singleId.getName();

    if (byName.contains(varName)) {
      return true;
    }
    if (from(byNamePrefix).anyMatch(varName::startsWith)) {
      return true;
    }
    if (!byType.isEmpty()) {
      CType idType = singleId.getType();
      if (idType instanceof CArrayType) {
        idType = ((CArrayType) idType).getType();
      }
      String typeString = idType.toString();
      typeString = typeString.replaceAll("\\(", "");
      typeString = typeString.replaceAll("\\)", "");
      if (byType.contains(typeString)) {
        return true;
      }
    }
    return false;
  }
}
