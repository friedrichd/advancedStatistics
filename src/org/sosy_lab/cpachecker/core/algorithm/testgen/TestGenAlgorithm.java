/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2013  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.algorithm.testgen;

import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.Pair;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.ShutdownNotifier;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.algorithm.testgen.dummygen.ARGStateDummyCreator;
import org.sosy_lab.cpachecker.core.algorithm.testgen.predicates.formula.TestHelper;
import org.sosy_lab.cpachecker.core.algorithm.testgen.predicates.formula.TestHelper.StartupConfig;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSetFactory;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.PredicatedAnalysisPropertyViolationException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.CFAUtils;
import org.sosy_lab.cpachecker.util.predicates.FormulaManagerFactory;
import org.sosy_lab.cpachecker.util.predicates.PathChecker;
import org.sosy_lab.cpachecker.util.predicates.Solver;
import org.sosy_lab.cpachecker.util.predicates.interfaces.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.view.FormulaManagerView;
import org.sosy_lab.cpachecker.util.predicates.interpolation.CounterexampleTraceInfo;
import org.sosy_lab.cpachecker.util.predicates.pathformula.PathFormulaManagerImpl;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;

@Options(prefix = "testgen")
public class TestGenAlgorithm implements Algorithm {

  private Algorithm explicitAlg;
  private LogManager logger;
  private CFA cfa;
  private ConfigurableProgramAnalysis cpa;
  private ARGStateDummyCreator dummyCreator;

  private PathFormulaManager pfMgr;
  private Solver solver;
  PathChecker pathChecker;
  private ReachedSetFactory reachedSetFactory;


  //  ConfigurationBuilder singleConfigBuilder = Configuration.builder();
  //  singleConfigBuilder.copyFrom(globalConfig);
  //  singleConfigBuilder.clearOption("restartAlgorithm.configFiles");
  //  singleConfigBuilder.clearOption("analysis.restartAfterUnknown");


  public TestGenAlgorithm(Algorithm pAlgorithm, ConfigurableProgramAnalysis pCpa,
      ShutdownNotifier pShutdownNotifier, CFA pCfa, String filename,
      Configuration config, LogManager pLogger) throws InvalidConfigurationException, CPAException {
    TestHelper helper = new TestHelper();
    StartupConfig predConfig = helper.createPredicateConfig();
    FormulaManagerFactory formulaManagerFactory =
        new FormulaManagerFactory(predConfig.getConfig(), pLogger, ShutdownNotifier.createWithParent(pShutdownNotifier));
    FormulaManagerView formulaManager =
        new FormulaManagerView(formulaManagerFactory.getFormulaManager(), config, logger);
    pfMgr = new PathFormulaManagerImpl(formulaManager, config, logger, cfa);
    solver = new Solver(formulaManager, formulaManagerFactory);
    pathChecker = new PathChecker(pLogger, pfMgr, solver);
    reachedSetFactory = new ReachedSetFactory(config, logger);

    cfa = pCfa;
    cpa = pCpa;
    config.inject(this);
    this.explicitAlg = pAlgorithm;
    this.logger = pLogger;

    dummyCreator = new ARGStateDummyCreator(pCpa, logger);
    /*TODO change the config file, so we can configure 'dfs'*/
    //    Configuration testCaseConfig = Configuration.copyWithNewPrefix(config, "testgen.");
    //    explicitAlg = new ExplicitTestcaseGenerator(config, logger, pShutdownNotifier, cfa, filename);
  }


  @Override
  public boolean run(ReachedSet pReachedSet) throws CPAException, InterruptedException,
      PredicatedAnalysisPropertyViolationException {
    ReachedSet globalReached = pReachedSet;
    ReachedSet currentReached = reachedSetFactory.create();

//    currentReached.add(globalReached. precision);
    while(globalReached.hasWaitingState()){
      //explicit, DFS, PRECISION=TRACK_ALL; with automaton of new path created in previous iteration OR custom CPA
      boolean sound = explicitAlg.run(currentReached);
      //sound should normally be unsound for us.
      //check if reachedSet contains error
      if(AbstractStates.isTargetState(currentReached.getLastState()))
      {
        /*
         * target state means error state.
         * we found an error path and leave the analysis to the surrounding alg.
         */
        return true;
      }
      /*
       * not an error path. selecting new path to traverse.
       */
      if(!(currentReached.getLastState() instanceof ARGState)) {
        throw new IllegalStateException("wrong configuration of explicit cpa, because concolicAlg needs ARGState");
      }
      ARGState pseudoTarget = (ARGState)currentReached.getLastState();
      ARGPath executedPath = ARGUtils.getOnePathTo(pseudoTarget);

      CounterexampleTraceInfo newPath = findNewFeasiblePathUsingPredicates(executedPath);
      if(newPath == null){
        return false; //true = sound or false = unsound. Which case is it here??
      }else
      {
        AbstractState newInitialState = createNewInitialState(newPath);
        currentReached = reachedSetFactory.create();
        currentReached.add(newInitialState, null);
      }

//ARGUtils.producePathAutomaton(sb, pRootState, pPathStates, name, pCounterExample);
      //traceInfo.getModel()
//      globalReached.add(currentReached.getAllReached);
    }
    return false;
  }

  private AbstractState createNewInitialState(CounterexampleTraceInfo pNewPath) {
    //TODO implement me
    throw new UnsupportedOperationException("Not yet implemented");
  }


  private CounterexampleTraceInfo findNewFeasiblePathUsingPredicates( ARGPath pExecutedPath) throws CPATransferException, InterruptedException {
    List<CFAEdge> newPath = Lists.newArrayList(pExecutedPath.asEdgesList());
    Iterator<Pair<ARGState,CFAEdge>> branchingEdges =
    Iterators.filter(Iterators.consumingIterator(pExecutedPath.descendingIterator()), new Predicate<Pair<ARGState,CFAEdge>>() {

      @Override
      public boolean apply(Pair<ARGState, CFAEdge> pInput) {
        CFAEdge lastEdge = pInput.getSecond();
        if(lastEdge == null) {
          return false;
        }
        CFANode decidingNode = lastEdge.getPredecessor();
        //num of leaving edges does not include a summary edge, so the check is valid.
        if(decidingNode.getNumLeavingEdges()==2) {
          return true;
        }
        return false;
      }});
    while(branchingEdges.hasNext())
    {
      Pair<ARGState, CFAEdge> branchingPair = branchingEdges.next();
      CFAEdge wrongEdge = branchingPair.getSecond();
      CFANode decidingNode = wrongEdge.getPredecessor();
      CFAEdge otherEdge = null;
      for (CFAEdge cfaEdge : CFAUtils.leavingEdges(decidingNode)) {
        if(cfaEdge.equals(wrongEdge)) {
          continue;
        } else {
          otherEdge = cfaEdge;
          break;
        }
      }
      //should not happen; If it does make it visible.
      assert otherEdge != null;
      newPath = Lists.newArrayList(pExecutedPath.asEdgesList());
      newPath.add(otherEdge);
      CounterexampleTraceInfo traceInfo = pathChecker.checkPath(newPath);
//      traceInfo.
      if(!traceInfo.isSpurious())
      {
        return traceInfo;
      }

    }
    return null;
  }

  private ReachedSet createNextReachedSet(
      ConfigurableProgramAnalysis cpa,
      CFANode mainFunction,
      ReachedSetFactory pReachedSetFactory) {
    logger.log(Level.FINE, "Creating initial reached set");

    AbstractState initialState = cpa.getInitialState(mainFunction);
    Precision initialPrecision = cpa.getInitialPrecision(mainFunction);

    ReachedSet reached = pReachedSetFactory.create();
    reached.add(initialState, initialPrecision);
    return reached;
  }


  //  boolean pathToExplore = true;
  //  boolean success = true;
  //  /* run the given alg ones using the "config/specification/onepathloopautomaton.spc" and DFS */
  //  ReachedSet currentReachedSet = pReachedSet;
  //  success &= algorithm.run(currentReachedSet);
  //  do{
  ////    combinedExplPredAlg.run(currentReachedSet);
  //  }while(success);
  //
  //  do {
  ////    pReachedSet.get
  ////    AbstractStates.isTargetState(as)
  //    /**/
  //    ARGState currentRootState =(ARGState) currentReachedSet.getFirstState();
  //    ARGState lastState = (ARGState) currentReachedSet.getLastState();
  //    ARGPath path = ARGUtils.getOnePathTo(lastState);
  //
  ////    for (AbstractState s : from(reached).filter(IS_TARGET_STATE)) {
  //    currentReachedSet = explicitAlg.analysePath(currentRootState, lastState, path.getStateSet());
  ////    dummyCreator.computeOtherSuccessor(pState, pNotToChildState)
  ////    dummyCreator.computeOtherSuccessor(pState, pNotToChildState);
  //    /**/
  ////    pReachedSet.getFirstState()
  ////    ARGUtils.getPathFromBranchingInformation(root, arg, branchingInformation)
  ////    explicitAlg.analysePath(currentRootState, null, errorPathStates);
  //  }while(pathToExplore & success);
  //  return false;

}
