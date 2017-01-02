/*
 * CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cpa.summary.blocks;

import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.ast.ASimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.FunctionEntryNode;
import org.sosy_lab.cpachecker.cpa.livevar.LiveVariablesManager;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.CFATraversal;
import org.sosy_lab.cpachecker.util.CFATraversal.SummarizingVisitor;
import org.sosy_lab.cpachecker.util.CFATraversal.SummarizingVisitorForward;

/**
 * Dataflow analysis on CFA blocks.
 */
public class BlockManager {
  private final CFA cfa;
  private final LiveVariablesManager liveVariablesManager;
  private final ImmutableMap<FunctionEntryNode, Block> blockData;
  private final ImmutableMap<CFANode, Block> nodeToBlock;
  private final ImmutableMap<CFANode, Block> entryPredecessorsToBlock;
  private final ImmutableMap<CFANode, Block> exitSuccessorsToBlock;

  public BlockManager(
      CFA pCfa,
      Configuration pConfiguration,
      LogManager pLogManager
      ) throws InvalidConfigurationException, CPATransferException {

    // todo: probably really bad to throw CPATransferException in the constructor.
    // can we see what actual exceptions are thrown?..
    cfa = pCfa;
    liveVariablesManager = new LiveVariablesManager(
        cfa.getVarClassification(),
        pConfiguration,
        cfa.getLanguage(),
        cfa,
        pLogManager
    );
    blockData = computeBlocks();
    ImmutableMap.Builder<CFANode, Block> blockNodeMappingBuilder =
        ImmutableMap.builder();
    blockData.values().forEach(
        b -> b.getOwnNodes().forEach(
            n -> blockNodeMappingBuilder.put(n, b)
        )
    );
    nodeToBlock = blockNodeMappingBuilder.build();
    ImmutableMap.Builder<CFANode, Block> entryPredecessorsBuilder =
        ImmutableMap.builder();
    blockData.values().forEach(
        b -> b.getStartPredecessors().forEach(
            n -> entryPredecessorsBuilder.put(n, b)
        )
    );
    entryPredecessorsToBlock = entryPredecessorsBuilder.build();
    ImmutableMap.Builder<CFANode, Block> exitSuccessorsBuilder =
        ImmutableMap.builder();
    blockData.values().forEach(
        b -> b.getExitSuccessors().forEach(
            n -> exitSuccessorsBuilder.put(n, b)
        )
    );
    exitSuccessorsToBlock = exitSuccessorsBuilder.build();
  }

  public Optional<Block> blockToEnter(CFANode pNode) {
    return Optional.ofNullable(entryPredecessorsToBlock.get(pNode));
  }

  public Optional<Block> blockLeftBefore(CFANode pNode) {
    return Optional.ofNullable(exitSuccessorsToBlock.get(pNode));
  }

  public ImmutableMap<FunctionEntryNode, Block> getBlockData() {
    return blockData;
  }

  public Block getBlockForNode(CFANode pNode) {
    Block out = nodeToBlock.get(pNode);
    assert out != null;
    return out;
  }

  private ImmutableMap<FunctionEntryNode, Block> computeBlocks() throws CPATransferException {
    // todo: do not compute dataflow facts for "main".
    ImmutableMap.Builder<FunctionEntryNode, Block> out = ImmutableMap.builder();
    for (FunctionEntryNode e : cfa.getAllFunctionHeads()) {
      out.put(e, ofFunctionEntryNode(e));
    }
    return out.build();
  }

  private Block ofFunctionEntryNode(FunctionEntryNode entry) throws CPATransferException {
    String functionName = entry.getFunctionName();

    SummarizingVisitor visitor = new SummarizingVisitorForward();

    // All edges, including nested ones ones.
    CFATraversal.dfs().ignoreReturnOutsideOf(functionName).traverse(entry, visitor);

    ImmutableSet<CFAEdge> innerEdges = visitor.getVisitedEdges();

    boolean hasRecursion = innerEdges.stream().anyMatch(
        e -> e instanceof FunctionCallEdge
          && ((FunctionCallEdge) e).getSuccessor().getFunctionName().equals(functionName)
    );

    Set<CFANode> innerNodes = innerEdges.stream().map(
        e -> e.getPredecessor()
    ).collect(Collectors.toSet());

    Set<CFANode> ownNodes = cfa.getAllNodes().stream().filter(
        n -> n.getFunctionName().equals(functionName)
    ).collect(Collectors.toSet());

    Set<Wrapper<ASimpleDeclaration>> readVars = new HashSet<>();
    Set<Wrapper<ASimpleDeclaration>> modifiedVars = new HashSet<>();

    // todo: less computation repetition for blocks with deep nesting.
    // currently this is very inefficient.
    for (CFAEdge edge : innerEdges) {
      readVars.addAll(liveVariablesManager.getReadVars(edge));
      modifiedVars.addAll(liveVariablesManager.getKilledVars(edge));
    }

    CFANode exit = entry.getExitNode();

    Set<CFANode> startPredecessors = IntStream.range(0, entry.getNumEnteringEdges())
        .mapToObj(i -> entry.getEnteringEdge(i).getPredecessor()).collect(Collectors.toSet());
    Set<CFANode> exitSuccessors = IntStream.range(0, exit.getNumLeavingEdges())
        .mapToObj(i -> exit.getLeavingEdge(i).getSuccessor()).collect(Collectors .toSet());

    return new Block(
        innerNodes,
        ownNodes,
        modifiedVars,
        readVars,
        entry,
        exit,
        hasRecursion,
        startPredecessors,
        exitSuccessors);
  }

}