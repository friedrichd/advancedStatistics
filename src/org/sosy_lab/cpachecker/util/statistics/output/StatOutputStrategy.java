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

import java.util.Collection;
import java.util.Map;

public interface StatOutputStrategy {

  /** Returns all variables which are used in the template. */
  public Collection<String> getRequiredVariables();

  /**
   * Replaces all variables and writes the result.
   *
   * @param mapping A mapping of variables and replacement objects
   */
  public void write(Map<String, Object> mapping);

}
