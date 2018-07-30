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
package org.sosy_lab.cpachecker.util.statistics.output;


import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.sosy_lab.common.JSON;
import org.sosy_lab.cpachecker.util.statistics.StatisticsUtils;

/**
 * Retrieves the template for an statistic output, replaces the variables and writes it to multiple
 * output files/consumers.
 */
public class JSONOutput implements StatOutputStrategy {

  private final String variable;
  private final Path path;

  public JSONOutput(String variable, String file_name) {
    path = Paths.get(file_name);
    if (variable == null || variable.isEmpty() || variable.equals(".")) {
      this.variable = "raw";
    } else {
      variable = StatisticsUtils.escape(variable);
      variable = variable.split("(^|\\.)(value|time|raw|count)(\\.|$)")[0];
      this.variable = StatisticsUtils.escape(variable + ".raw");
    }
  }

  @Override
  public Collection<String> getRequiredVariables() {
    return Collections.singleton(variable);
  }

  @Override
  public void write(Map<String, Object> mapping) {
    try {
      JSON.writeJSONString(mapping.get(variable), path);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
