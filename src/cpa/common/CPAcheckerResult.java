/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker. 
 *
 *  Copyright (C) 2007-2008  Dirk Beyer and Erkan Keremoglu.
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
 *    http://www.cs.sfu.ca/~dbeyer/CPAchecker/
 */
package cpa.common;

import java.io.PrintWriter;

import com.google.common.base.Preconditions;

import cpa.common.interfaces.Statistics;

/**
 * Class that represents the result of a CPAchecker analysis.
 */
public class CPAcheckerResult {

  /**
   * Enum for the possible outcomes of a CPAchecker analysis:
   * - UNKNOWN: analysis did not terminate
   * - UNSAFE: bug found
   * - SAFE: no bug found
   */
  public static enum Result { UNKNOWN, UNSAFE, SAFE }

  private final Result result;
  
  private final ReachedElements reached;
  
  private final Statistics stats;
  
  CPAcheckerResult(Result result, ReachedElements reached, Statistics stats) {
    Preconditions.checkNotNull(result);
    this.result = result;
    this.reached = reached;
    this.stats = stats;
  }
  
  /**
   * Return the result of the analysis.
   */
  public Result getResult() {
    return result;
  }
  
  /**
   * Return the final reached set.
   */
  public UnmodifiableReachedElements getReached() {
    return reached;
  }
  
  /**
   * Write the statistics to a given PrintWriter. Additionally some output files
   * may be written here, if configuration says so.
   */
  public void printStatistics(PrintWriter target) {
    if (stats != null && reached != null) {
      stats.printStatistics(target, result, reached);
    }
  }
}
