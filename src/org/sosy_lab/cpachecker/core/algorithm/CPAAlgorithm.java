/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.algorithm;

import com.google.common.base.Functions;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nullable;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.ClassOption;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.defaults.MergeSepOperator;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.ForcedCovering;
import org.sosy_lab.cpachecker.core.interfaces.MergeOperator;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustment;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult;
import org.sosy_lab.cpachecker.core.interfaces.PrecisionAdjustmentResult.Action;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.interfaces.StopOperator;
import org.sosy_lab.cpachecker.core.interfaces.TransferRelation;
import org.sosy_lab.cpachecker.core.reachedset.PartitionedReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.PseudoPartitionedReachedSet;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGMergeJoinCPAEnabledAnalysis;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.statistics.AbstractStatValue;
import org.sosy_lab.cpachecker.util.statistics.AdvancedStatistics;
import org.sosy_lab.cpachecker.util.statistics.AdvancedStatistics.StatEvent;
import org.sosy_lab.cpachecker.util.statistics.StatCounter;
import org.sosy_lab.cpachecker.util.statistics.StatInt;
import org.sosy_lab.cpachecker.util.statistics.StatisticsUtils;
import org.sosy_lab.cpachecker.util.statistics.output.JSONOutput;

public class CPAAlgorithm implements Algorithm, StatisticsProvider {

  private Map<String, AbstractStatValue> reachedSetStatistics = new HashMap<>();

  @Options(prefix = "cpa")
  public static class CPAAlgorithmFactory implements AlgorithmFactory {

    @Option(
      secure = true,
      description = "Which strategy to use for forced coverings (empty for none)",
      name = "forcedCovering"
    )
    @ClassOption(packagePrefix = "org.sosy_lab.cpachecker")
    private @Nullable ForcedCovering.Factory forcedCoveringClass = null;

    @Option(secure=true, description="Do not report 'False' result, return UNKNOWN instead. "
        + " Useful for incomplete analysis with no counterexample checking.")
    private boolean reportFalseAsUnknown = false;

    private final ForcedCovering forcedCovering;

    private final ConfigurableProgramAnalysis cpa;
    private final LogManager logger;
    private final ShutdownNotifier shutdownNotifier;

    public CPAAlgorithmFactory(ConfigurableProgramAnalysis cpa, LogManager logger,
        Configuration config, ShutdownNotifier pShutdownNotifier) throws InvalidConfigurationException {

      config.inject(this);
      this.cpa = cpa;
      this.logger = logger;
      this.shutdownNotifier = pShutdownNotifier;

      if (forcedCoveringClass != null) {
        forcedCovering = forcedCoveringClass.create(config, logger, cpa);
      } else {
        forcedCovering = null;
      }

    }

    @Override
    public CPAAlgorithm newInstance() {
      return new CPAAlgorithm(cpa, logger, shutdownNotifier, forcedCovering, reportFalseAsUnknown);
    }
  }

  public static CPAAlgorithm create(ConfigurableProgramAnalysis cpa, LogManager logger,
      Configuration config, ShutdownNotifier pShutdownNotifier) throws InvalidConfigurationException {

    return new CPAAlgorithmFactory(cpa, logger, config, pShutdownNotifier).newInstance();
  }


  private final ForcedCovering forcedCovering;

  private final AdvancedStatistics stats =
      new AdvancedStatistics("CPA algorithm").addBasicTemplate(() -> {
        StringBuilder sb = new StringBuilder();
        StatisticsUtils.write(sb, "Number of iterations", "$sizeofwaitlist.count$");

        StatisticsUtils.write(sb, "Max size of waitlist", "$sizeofwaitlist.value.max$");
        StatisticsUtils.write(sb, "Average size of waitlist", "$sizeofwaitlist.value.avg$");
        for (AbstractStatValue c : reachedSetStatistics.values()) {
          StatisticsUtils.write(sb, 1, 50, c.getTitle(), c);
        }
        sb.append(System.lineSeparator());

        StatisticsUtils.write(sb, "Number of computed successors", "$successors.value.sum$");
        StatisticsUtils.write(sb, "Max successors for one state", "$successors.value.max$");
        StatisticsUtils.write(sb, "Number of times merged", "$merge.value.merged_both$");
        StatisticsUtils.write(sb, "Number of times stopped", "$stopped.count$");
        StatisticsUtils.write(sb, "Number of times breaked", "$breaked.count$");
        sb.append(System.lineSeparator());

        StatisticsUtils.write(sb, "Total time for CPA algorithm", "$time.sum$ (max: $time.max$)");
        StatisticsUtils.write(sb, 1, 50, "Time for choose from waitlist", "$choose.time$");
        StatisticsUtils
            .writeIfExists(sb, 1, 50, "Time for forced covering", "$forcedcovering.time$");
        StatisticsUtils.write(sb, 1, 50, "Time for precision adjustment", "$precision.time$");
        StatisticsUtils.write(sb, 1, 50, "Time for transfer relation", "$transfer.time$");
        StatisticsUtils.write(sb, 1, 50, "Time for merge operator", "$merge.time$");
        StatisticsUtils.write(sb, 1, 50, "Time for stop operator", "$stop.time$");
        StatisticsUtils.write(sb, 1, 50, "Time for adding to reached set", "$add.time$");
        return sb.toString();
      }).addOutputStrategy(new JSONOutput("reachedset", "reachedset.json"));

  private final TransferRelation transferRelation;
  private final MergeOperator mergeOperator;
  private final StopOperator stopOperator;
  private final PrecisionAdjustment precisionAdjustment;

  private final LogManager                  logger;

  private final ShutdownNotifier                   shutdownNotifier;

  private final AlgorithmStatus status;

  private CPAAlgorithm(ConfigurableProgramAnalysis cpa, LogManager logger,
      ShutdownNotifier pShutdownNotifier,
      ForcedCovering pForcedCovering,
      boolean pIsImprecise) {

    transferRelation = cpa.getTransferRelation();
    mergeOperator = cpa.getMergeOperator();
    stopOperator = cpa.getStopOperator();
    precisionAdjustment = cpa.getPrecisionAdjustment();
    this.logger = logger;
    this.shutdownNotifier = pShutdownNotifier;
    this.forcedCovering = pForcedCovering;
    status = AlgorithmStatus.SOUND_AND_PRECISE.withPrecise(!pIsImprecise);
  }

  @Override
  public AlgorithmStatus run(final ReachedSet reachedSet) throws CPAException, InterruptedException {
    stats.startTracking();
    stats.track("ReachedSet", reachedSet.size(), null);
    try {
      return run0(reachedSet);
    } finally {
      stats.stopTracking();

      Map<String, ? extends AbstractStatValue> reachedSetStats;
      if (reachedSet instanceof PartitionedReachedSet) {
        reachedSetStats = ((PartitionedReachedSet) reachedSet).getStatistics();
      } else if (reachedSet instanceof PseudoPartitionedReachedSet) {
        reachedSetStats = ((PseudoPartitionedReachedSet) reachedSet).getStatistics();
      } else {
        reachedSetStats = null;
      }

      if (reachedSetStats != null) {
        for (Entry<String, ? extends AbstractStatValue> e : reachedSetStats.entrySet()) {
          String key = e.getKey();
          AbstractStatValue val = e.getValue();
          if (!reachedSetStatistics.containsKey(key)) {
            reachedSetStatistics.put(key, val);
          } else {
            AbstractStatValue newVal = reachedSetStatistics.get(key);

            if (newVal instanceof StatCounter) {
              assert val instanceof StatCounter;
              for (int i = 0; i < ((StatCounter) val).getValue(); i++) {
                ((StatCounter) newVal).inc();
              }
            } else if (newVal instanceof StatInt) {
              assert val instanceof StatInt;
              ((StatInt) newVal).add((StatInt) val);
            } else {
              assert false : "Can't handle " + val.getClass().getSimpleName();
            }
          }
        }
      }

    }
  }

  private AlgorithmStatus run0(final ReachedSet reachedSet) throws CPAException, InterruptedException {
    while (reachedSet.hasWaitingState()) {
      shutdownNotifier.shutdownIfNecessary();

      // Pick next state using strategy
      // BFS, DFS or top sort according to the configuration
      stats.track("SizeOfWaitlist", reachedSet.getWaitlist().size());

      StatEvent chooseTimer = stats.open("Choose");
      final AbstractState state = reachedSet.popFromWaitlist();
      final Precision precision = reachedSet.getPrecision(state);
      stats.close(chooseTimer);

      logger.log(Level.FINER, "Retrieved state from waitlist");
      try {
        if (handleState(state, precision, reachedSet)) {
          // Prec operator requested break
          return status;
        }
      } catch (Exception e) {
        // re-add the old state to the waitlist, there might be unhandled successors left
        // that otherwise would be forgotten (which would be unsound)
        reachedSet.reAddToWaitlist(state);
        throw e;
      }

    }

    return status;
  }

  /**
   * Handle one state from the waitlist, i.e., produce successors etc.
   * @param state The abstract state that was taken out of the waitlist
   * @param precision The precision for this abstract state.
   * @param reachedSet The reached set.
   * @return true if analysis should terminate, false if analysis should continue with next state
   */
  private boolean handleState(
      final AbstractState state, final Precision precision, final ReachedSet reachedSet)
      throws CPAException, InterruptedException {
    logger.log(Level.ALL, "Current state is", state, "with precision", precision);

    if (forcedCovering != null) {
      StatEvent forcedCoveringTimer = stats.open("forcedCovering");
      try {
        boolean stop = forcedCovering.tryForcedCovering(state, precision, reachedSet);

        if (stop) {
          // TODO: remove state from reached set?
          return false;
        }
      } finally {
        stats.close(forcedCoveringTimer);
      }
    }

    StatEvent transferTimer = stats.open("Transfer");
    Collection<? extends AbstractState> successors;
    try {
      successors = transferRelation.getAbstractSuccessors(state, precision);
    } finally {
      stats.close(transferTimer);
    }
    // TODO When we have a nice way to mark the analysis result as incomplete,
    // we could continue analysis on a CPATransferException with the next state from waitlist.

    int numSuccessors = successors.size();
    logger.log(Level.FINER, "Current state has", numSuccessors, "successors");
    stats.track("Successors", numSuccessors);

    for (Iterator<? extends AbstractState> it = successors.iterator(); it.hasNext();) {
      AbstractState successor = it.next();
      shutdownNotifier.shutdownIfNecessary();
      logger.log(Level.FINER, "Considering successor of current state");
      logger.log(Level.ALL, "Successor of", state, "\nis", successor);

      StatEvent precisionTimer = stats.open("Precision");
      PrecisionAdjustmentResult precAdjustmentResult;
      try {
        Optional<PrecisionAdjustmentResult> precAdjustmentOptional =
            precisionAdjustment.prec(
                successor, precision, reachedSet, Functions.identity(), successor);
        if (!precAdjustmentOptional.isPresent()) {
          continue;
        }
        precAdjustmentResult = precAdjustmentOptional.get();
      } finally {
        stats.close(precisionTimer);
      }

      successor = precAdjustmentResult.abstractState();
      Precision successorPrecision = precAdjustmentResult.precision();
      Action action = precAdjustmentResult.action();

      if (action == Action.BREAK) {
        StatEvent stopTimer = stats.open("Stop");
        boolean stop;
        try {
          stop = stopOperator.stop(successor, reachedSet.getReached(successor), successorPrecision);
        } finally {
          stats.close(stopTimer);
        }

        if (AbstractStates.isTargetState(successor) && stop) {
          // don't signal BREAK for covered states
          // no need to call merge and stop either, so just ignore this state
          // and handle next successor
          stats.track("Stopped");
          logger.log(Level.FINER, "Break was signalled but ignored because the state is covered.");
          continue;

        } else {
          stats.track("breaked");
          logger.log(Level.FINER, "Break signalled, CPAAlgorithm will stop.");

          // add the new state
          reachedSet.add(successor, successorPrecision);
          stats.track("ReachedSet", reachedSet.size(), null);

          if (it.hasNext()) {
            // re-add the old state to the waitlist, there are unhandled
            // successors left that otherwise would be forgotten
            reachedSet.reAddToWaitlist(state);
          }

          return true;
        }
      }
      assert action == Action.CONTINUE : "Enum Action has unhandled values!";

      Collection<AbstractState> reached = reachedSet.getReached(successor);

      // An optimization, we don't bother merging if we know that the
      // merge operator won't do anything (i.e., it is merge-sep).
      if (mergeOperator != MergeSepOperator.getInstance() && !reached.isEmpty()) {
        StatEvent mergeTimer = stats.open("Merge");
        try {
          List<AbstractState> toRemove = new ArrayList<>();
          List<Pair<AbstractState, Precision>> toAdd = new ArrayList<>();
          try {
            logger.log(
                Level.FINER, "Considering", reached.size(), "states from reached set for merge");
            for (AbstractState reachedState : reached) {
              shutdownNotifier.shutdownIfNecessary();
              AbstractState mergedState =
                  mergeOperator.merge(successor, reachedState, successorPrecision);

              mergeTimer.setValue(!mergedState.equals(reachedState) ? "merged" : "notmerged");

              if (!mergedState.equals(reachedState)) {
                logger.log(Level.FINER, "Successor was merged with state from reached set");
                logger.log(
                    Level.ALL, "Merged", successor, "\nand", reachedState, "\n-->", mergedState);

                toRemove.add(reachedState);
                toAdd.add(Pair.of(mergedState, successorPrecision));
              }
            }
          } finally {
            // If we terminate, we should still update the reachedSet if necessary
            // because ARGCPA doesn't like states in toRemove to be in the reachedSet.
            reachedSet.removeAll(toRemove);
            reachedSet.addAll(toAdd);
            if (!toRemove.isEmpty() || !toAdd.isEmpty()) {
              stats.track("ReachedSet", reachedSet.size(), null);
            }
          }

          if (mergeOperator instanceof ARGMergeJoinCPAEnabledAnalysis) {
            ((ARGMergeJoinCPAEnabledAnalysis) mergeOperator).cleanUp(reachedSet);
          }

        } finally {
          stats.close(mergeTimer);
        }
      }

      StatEvent stopTimer = stats.open("Stop");
      boolean stop;
      try {
        stop = stopOperator.stop(successor, reached, successorPrecision);
      } finally {
        stats.close(stopTimer);
      }

      if (stop) {
        logger.log(Level.FINER, "Successor is covered or unreachable, not adding to waitlist");
        stats.track("stopped");

      } else {
        logger.log(Level.FINER, "No need to stop, adding successor to waitlist");

        StatEvent addTimer = stats.open("Add");
        reachedSet.add(successor, successorPrecision);
        stats.track("ReachedSet", reachedSet.size(), null);
        stats.close(addTimer);
      }
    }

    return false;
  }

  @Override
  public void collectStatistics(Collection<Statistics> pStatsCollection) {
    if (forcedCovering instanceof StatisticsProvider) {
      ((StatisticsProvider)forcedCovering).collectStatistics(pStatsCollection);
    }
    stats.collectStatistics(pStatsCollection);
  }
}
