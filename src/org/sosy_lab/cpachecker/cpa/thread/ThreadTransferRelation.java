/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.thread;

import java.util.Collection;
import java.util.Collections;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CThreadOperationStatement.CThreadCreateStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CThreadOperationStatement.CThreadJoinStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.cpa.thread.ThreadState.ThreadStateBuilder;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.HandleCodeException;


public class ThreadTransferRelation extends SingleEdgeTransferRelation {
  private final ThreadCPAStatistics threadStatistics;

  public ThreadTransferRelation() {
    threadStatistics = new ThreadCPAStatistics();
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(AbstractState pState,
      Precision pPrecision, CFAEdge pCfaEdge) throws CPATransferException, InterruptedException {

    threadStatistics.transfer.start();
    ThreadState tState = (ThreadState)pState;

    ThreadStateBuilder builder = tState.getBuilder();
    try {
      if (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {
        if (!handleFunctionCall((CFunctionCallEdge) pCfaEdge, builder)) {
          // Try to join non-created thread
          return Collections.emptySet();
        }
      } else if (pCfaEdge instanceof CFunctionSummaryStatementEdge) {
        CFunctionCall functionCall = ((CFunctionSummaryStatementEdge) pCfaEdge).getFunctionCall();
        if (isThreadCreateFunction(functionCall)) {
          builder.handleParentThread((CThreadCreateStatement) functionCall);
        }
      } else if (pCfaEdge.getEdgeType() == CFAEdgeType.StatementEdge) {
        CStatement stmnt = ((CStatementEdge) pCfaEdge).getStatement();
        if (stmnt instanceof CThreadJoinStatement) {
          threadStatistics.threadJoins.inc();
          if (!builder.joinThread((CThreadJoinStatement) stmnt)) {
            return Collections.emptySet();
          }
        }
      } else if (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionReturnEdge) {
        CFunctionCall functionCall =
            ((CFunctionReturnEdge) pCfaEdge).getSummaryEdge().getExpression();
        if (isThreadCreateFunction(functionCall)) {
          return Collections.emptySet();
        }
      }
      return Collections.singleton(builder.build());
    } catch (HandleCodeException e) {
      return Collections.emptySet();
    } finally {
      threadStatistics.transfer.stop();
    }
  }

  private boolean handleFunctionCall(CFunctionCallEdge pCfaEdge,
      ThreadStateBuilder builder) throws HandleCodeException {

    boolean success = true;
    CFunctionCall fCall = pCfaEdge.getSummaryEdge().getExpression();
    if (isThreadCreateFunction(fCall)) {
      builder.handleChildThread((CThreadCreateStatement) fCall);
      if (threadStatistics.createdThreads.add(pCfaEdge.getSuccessor().getFunctionName())) {
        threadStatistics.threadCreates.inc();
        // Just to statistics
        threadStatistics.maxNumberOfThreads.setNextValue(builder.getThreadSize());
      }
    } else if (isThreadJoinFunction(fCall)) {
      threadStatistics.threadJoins.inc();
      success = builder.joinThread((CThreadJoinStatement)fCall);
    }
    return success;
  }

  private boolean isThreadCreateFunction(CFunctionCall statement) {
    return (statement instanceof CThreadCreateStatement);
  }

  private boolean isThreadJoinFunction(CFunctionCall statement) {
    return (statement instanceof CThreadJoinStatement);
  }

  public Statistics getStatistics() {
    return threadStatistics;
  }
}
