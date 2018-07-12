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
package org.sosy_lab.cpachecker.cpa.smg;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Optional;
import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.log.LogManagerWithoutDuplicates;
import org.sosy_lab.cpachecker.cfa.ast.c.CAssignment;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CDesignatedInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFieldDesignator;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializer;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CInitializerList;
import org.sosy_lab.cpachecker.cfa.ast.c.CIntegerLiteralExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CRightHandSide;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CVariableDeclaration;
import org.sosy_lab.cpachecker.cfa.model.BlankEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CDeclarationEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionEntryNode;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CReturnStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CArrayType;
import org.sosy_lab.cpachecker.cfa.types.c.CBitFieldType;
import org.sosy_lab.cpachecker.cfa.types.c.CComplexType.ComplexTypeKind;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType;
import org.sosy_lab.cpachecker.cfa.types.c.CCompositeType.CCompositeTypeMemberDeclaration;
import org.sosy_lab.cpachecker.cfa.types.c.CElaboratedType;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CStorageClass;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.defaults.ForwardingTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonState;
import org.sosy_lab.cpachecker.cpa.smg.SMGOptions.SMGExportLevel;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.AssumeVisitor;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.LValueAssignmentVisitor;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGAbstractObjectAndState.SMGAddressAndState;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGAbstractObjectAndState.SMGExplicitValueAndState;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGAbstractObjectAndState.SMGValueAndState;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGExpressionEvaluator;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGRightHandSideEvaluator;
import org.sosy_lab.cpachecker.cpa.smg.graphs.PredRelation;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGRegion;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGAddress;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGExplicitValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownExpValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownSymValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownSymbolicValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGSymbolicValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGUnknownValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGZeroValue;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.predicates.BlockOperator;
import org.sosy_lab.java_smt.api.BooleanFormula;
import org.sosy_lab.java_smt.api.SolverException;

public class SMGTransferRelation
    extends ForwardingTransferRelation<Collection<SMGState>, SMGState, Precision> {

  private final static AtomicInteger ID_COUNTER = new AtomicInteger(0);

  private final LogManagerWithoutDuplicates logger;
  private final MachineModel machineModel;
  private final SMGOptions options;
  private final SMGExportDotOption exportSMGOptions;
  private final BlockOperator blockOperator;
  private final SMGPredicateManager smgPredicateManager;

  final SMGRightHandSideEvaluator expressionEvaluator;

  public SMGTransferRelationKind kind;

  public final SMGBuiltins builtins;

  private SMGTransferRelation(LogManager pLogger,
      MachineModel pMachineModel, SMGExportDotOption pExportOptions, SMGTransferRelationKind pKind,
      SMGPredicateManager pSMGPredicateManager, BlockOperator pBlockOperator, SMGOptions pOptions) {
    logger = new LogManagerWithoutDuplicates(pLogger);
    machineModel = pMachineModel;
    expressionEvaluator = new SMGRightHandSideEvaluator(this, logger, machineModel, pOptions);
    smgPredicateManager = pSMGPredicateManager;
    blockOperator = pBlockOperator;
    options = pOptions;
    exportSMGOptions = pExportOptions;
    kind = pKind;
    builtins = new SMGBuiltins(this, options, exportSMGOptions, machineModel, logger);
  }

  public static SMGTransferRelation createTransferRelationForCEX(
      LogManager pLogger, MachineModel pMachineModel, SMGPredicateManager pSMGPredicateManager,
      BlockOperator pBlockOperator, SMGOptions pOptions) {
    return new SMGTransferRelation( pLogger, pMachineModel,
            SMGExportDotOption.getNoExportInstance(), SMGTransferRelationKind.STATIC,
            pSMGPredicateManager, pBlockOperator, pOptions);
  }

  public static SMGTransferRelation createTransferRelation(LogManager pLogger,
      MachineModel pMachineModel, SMGExportDotOption pExportOptions,
      SMGPredicateManager pSMGPredicateManager,
      BlockOperator pBlockOperator, SMGOptions pOptions) {
    return new SMGTransferRelation( pLogger, pMachineModel, pExportOptions,
        SMGTransferRelationKind.STATIC, pSMGPredicateManager, pBlockOperator, pOptions);
  }

  public static SMGTransferRelation createTransferRelationForInterpolation(
      LogManager pLogger,
      MachineModel pMachineModel, SMGPredicateManager pSMGPredicateManager,
      BlockOperator pBlockOperator, SMGOptions pOptions) {
    return new SMGTransferRelation(pLogger, pMachineModel,
            SMGExportDotOption.getNoExportInstance(), SMGTransferRelationKind.REFINEMENT,
            pSMGPredicateManager, pBlockOperator, pOptions);
  }

  @Override
  protected Collection<SMGState> postProcessing(Collection<SMGState> successors, CFAEdge edge) {
    plotWhenConfigured(successors, edge.getDescription(), SMGExportLevel.INTERESTING);
    successors =
        Collections2.transform(
            successors, s -> s.copyWithBlockEnd(blockOperator.isBlockEnd(edge.getSuccessor(), 0)));
    logger.log(
        Level.ALL,
        "state with id",
        state.getId(),
        "has successors with ids",
        Collections2.transform(successors, UnmodifiableSMGState::getId));
    // Verify predicate on error feasibility
    successors = Collections2.transform(successors, this::checkAndSetErrorRelation);
    return successors;
  }

  private void plotWhenConfigured(
      Collection<? extends UnmodifiableSMGState> pStates, String pLocation, SMGExportLevel pLevel) {
    for (UnmodifiableSMGState s : pStates) {
      SMGUtils.plotWhenConfigured(
          getDotExportFileName(s), s, pLocation, logger, pLevel, exportSMGOptions);
    }
  }

  private String getDotExportFileName(UnmodifiableSMGState pState) {
    if (pState.getPredecessorId() == 0) {
      return String.format("initial-%03d", pState.getId());
    } else {
      return String.format("%03d-%03d-%03d", pState.getPredecessorId(), pState.getId(),
          ID_COUNTER.getAndIncrement());
    }
  }

  @Override
  protected Set<SMGState> handleBlankEdge(BlankEdge cfaEdge) {
    return Collections.singleton(state);
  }

  @Override
  protected Collection<SMGState> handleReturnStatementEdge(CReturnStatementEdge returnEdge)
      throws CPATransferException {
    SMGState smgState = state.copyOf();
    Collection<SMGState> successors;
    SMGObject tmpFieldMemory = smgState.getHeap().getFunctionReturnObject();
    if (tmpFieldMemory != null) {
      CExpression returnExp =
          returnEdge.getExpression().or(CIntegerLiteralExpression.ZERO); // 0 is the default in C
      CType expType = TypeUtils.getRealExpressionType(returnExp);
      Optional<CAssignment> returnAssignment = returnEdge.asAssignment();
      if (returnAssignment.isPresent()) {
        expType = returnAssignment.get().getLeftHandSide().getExpressionType();
      }
      successors =
          handleAssignmentToField(smgState, returnEdge, tmpFieldMemory, 0, expType, returnExp);
    } else {
      successors = ImmutableList.of(smgState);
    }

    // if this is the entry function, there is no FunctionReturnEdge
    // so we have to check for memleaks here
    if (returnEdge.getSuccessor().getNumLeavingEdges() == 0) {
      // Ugly, but I do not know how to do better
      // TODO: Handle leaks at any program exit point (abort, etc.)
      for (SMGState successor : successors) {
        if (options.isHandleNonFreedMemoryInMainAsMemLeak()) {
          successor.dropStackFrame();
        }
        successor.pruneUnreachable();
      }
    }
    return successors;
  }

  @Override
  protected Collection<SMGState> handleFunctionReturnEdge(
      CFunctionReturnEdge functionReturnEdge,
      CFunctionSummaryEdge fnkCall,
      CFunctionCall summaryExpr,
      String callerFunctionName)
      throws CPATransferException {
    Collection<SMGState> successors = handleFunctionReturn(state, functionReturnEdge);
    if (options.isCheckForMemLeaksAtEveryFrameDrop()) {
      for (SMGState successor : successors) {
        String name =
            String.format(
                "%03d-%03d-%03d",
                successor.getPredecessorId(), successor.getId(), ID_COUNTER.getAndIncrement());
        SMGUtils.plotWhenConfigured(
            "beforePrune" + name,
            successor,
            functionReturnEdge.getDescription(),
            logger,
            SMGExportLevel.INTERESTING,
            exportSMGOptions);
        successor.pruneUnreachable();
      }
    }
    return successors;
  }

  private List<SMGState> handleFunctionReturn(
      UnmodifiableSMGState smgState, CFunctionReturnEdge functionReturnEdge)
      throws CPATransferException {

    CFunctionSummaryEdge summaryEdge = functionReturnEdge.getSummaryEdge();
    CFunctionCall exprOnSummary = summaryEdge.getExpression();
    SMGState newState = smgState.copyOf();

    assert Iterables.getLast(newState.getHeap().getStackFrames())
        .getFunctionDeclaration()
        .equals(functionReturnEdge.getFunctionEntry().getFunctionDefinition());

    if (exprOnSummary instanceof CFunctionCallAssignmentStatement) {

      // Assign the return value to the lValue of the functionCallAssignment
      CExpression lValue = ((CFunctionCallAssignmentStatement) exprOnSummary).getLeftHandSide();
      CType rValueType =
          TypeUtils.getRealExpressionType(
              ((CFunctionCallAssignmentStatement) exprOnSummary).getRightHandSide());
      SMGObject tmpMemory = newState.getHeap().getFunctionReturnObject();
      SMGSymbolicValue rValue =
          expressionEvaluator
              .readValue(
                  newState, tmpMemory, SMGZeroValue.INSTANCE, rValueType, functionReturnEdge)
              .getObject();
      SMGAddress address = null;

      // Lvalue is one frame above
      newState.dropStackFrame();
      LValueAssignmentVisitor visitor = expressionEvaluator.getLValueAssignmentVisitor(functionReturnEdge, newState);
      List<SMGAddressAndState> addressAndValues = lValue.accept(visitor);
      List<SMGState> result = new ArrayList<>(addressAndValues.size());

      for (SMGAddressAndState addressAndValue : addressAndValues) {
        address = addressAndValue.getObject();
        newState = addressAndValue.getSmgState();

        if (!address.isUnknown()) {
          if (rValue.isUnknown()) {
            rValue = SMGKnownSymValue.of();
          }

          SMGObject object = address.getObject();
          long offset = address.getOffset().getAsLong();

          // TODO cast value
          rValueType = TypeUtils.getRealExpressionType(lValue);

          SMGState resultState =
              expressionEvaluator.assignFieldToState(
                  newState, functionReturnEdge, object, offset, rValue, rValueType);
          result.add(resultState);
        } else {
          //TODO missingInformation, exception
          result.add(newState);
        }
      }

      return result;
    } else {
      newState.dropStackFrame();
      return ImmutableList.of(newState);
    }
  }

  @Override
  protected Collection<SMGState> handleFunctionCallEdge(
      CFunctionCallEdge callEdge,
      List<CExpression> arguments,
      List<CParameterDeclaration> paramDecl,
      String calledFunctionName)
      throws CPATransferException {

    CFunctionEntryNode functionEntryNode = callEdge.getSuccessor();
    SMGState initialNewState = state.copyOf();
    CFunctionDeclaration functionDeclaration = functionEntryNode.getFunctionDefinition();

    if (!callEdge.getSuccessor().getFunctionDefinition().getType().takesVarArgs()) {
      //TODO Parameter with varArgs
      assert (paramDecl.size() == arguments.size());
    }

    Map<UnmodifiableSMGState, List<Pair<SMGRegion, SMGSymbolicValue>>> valuesMap = new HashMap<>();

    //TODO Refactor, ugly

    List<SMGState> newStates = new ArrayList<>(4);
    newStates.add(initialNewState);

    List<Pair<SMGRegion, SMGSymbolicValue>> initialValuesList = new ArrayList<>(paramDecl.size());
    valuesMap.put(initialNewState, initialValuesList);

    // get value of actual parameter in caller function context
    for (int i = 0; i < paramDecl.size(); i++) {

      CExpression exp = arguments.get(i);

      String varName = paramDecl.get(i).getName();
      CType cParamType = TypeUtils.getRealExpressionType(paramDecl.get(i));

      SMGRegion paramObj;
      // If parameter is a array, convert to pointer
      if (cParamType instanceof CArrayType) {
        int size = machineModel.getSizeofPtrInBits();
        paramObj = new SMGRegion(size, varName);
      } else {
        int size = expressionEvaluator.getBitSizeof(callEdge, cParamType, initialNewState);
        paramObj = new SMGRegion(size, varName);
      }

      List<SMGState> result = new ArrayList<>(4);

      for (SMGState newState : newStates) {
        // We want to write a possible new Address in the new State, but
        // explore the old state for the parameters
        for (SMGValueAndState stateValue : readValueToBeAssiged(newState, callEdge, exp)) {
          SMGState newStateWithReadSymbolicValue = stateValue.getSmgState();
          SMGSymbolicValue value = stateValue.getObject();

          for (Pair<SMGState, SMGKnownSymbolicValue> newStateWithExpVal :
              assignExplicitValueToSymbolicValue(
                  newStateWithReadSymbolicValue, callEdge, value, exp)) {

            SMGState curState = newStateWithExpVal.getFirst();
            if (!valuesMap.containsKey(curState)) {
              List<Pair<SMGRegion, SMGSymbolicValue>> newValues = new ArrayList<>(paramDecl.size());
              newValues.addAll(valuesMap.get(newState));
              valuesMap.put(curState, newValues);
            }

            Pair<SMGRegion, SMGSymbolicValue> lhsValuePair = Pair.of(paramObj, value);
            valuesMap.get(curState).add(i, lhsValuePair);
            result.add(curState);

            //Check that previous values are not merged with new one
            if (newStateWithExpVal.getSecond() != null) {
              for (int j = i - 1; j >= 0; j--) {
                Pair<SMGRegion, SMGSymbolicValue> lhsCheckValuePair = valuesMap.get(curState).get(j);
                SMGSymbolicValue symbolicValue = lhsCheckValuePair.getSecond();
                if (newStateWithExpVal.getSecond().equals(symbolicValue)) {
                  //Previous value was merged, replace with new value
                  Pair<SMGRegion, SMGSymbolicValue> newLhsValuePair = Pair.of(lhsCheckValuePair.getFirst(), value);
                  valuesMap.get(curState).remove(j);
                  valuesMap.get(curState).add(j, newLhsValuePair);
                }
              }
            }
          }
        }
      }

      newStates = result;
    }

    for (SMGState newState : newStates) {
      newState.addStackFrame(functionDeclaration);

      // get value of actual parameter in caller function context
      for (int i = 0; i < paramDecl.size(); i++) {

        CExpression exp = arguments.get(i);

        String varName = paramDecl.get(i).getName();
        CType cParamType = TypeUtils.getRealExpressionType(paramDecl.get(i));
        CType rValueType = TypeUtils.getRealExpressionType(exp.getExpressionType());
        // if function declaration is in form 'int foo(char b[32])' then omit array length
        if (rValueType instanceof CArrayType) {
          rValueType = new CPointerType(rValueType.isConst(), rValueType.isVolatile(), ((CArrayType)rValueType).getType());
        }

        if (cParamType instanceof CArrayType) {
          cParamType = new CPointerType(cParamType.isConst(), cParamType.isVolatile(), ((CArrayType) cParamType).getType());
        }

        List<Pair<SMGRegion, SMGSymbolicValue>> values = valuesMap.get(newState);
        SMGRegion newObject = values.get(i).getFirst();
        SMGSymbolicValue symbolicValue = values.get(i).getSecond();

        int typeSize = expressionEvaluator.getBitSizeof(callEdge, cParamType, newState);

        newState.addLocalVariable(typeSize, varName, newObject);

        //TODO (  cast expression)

        //6.5.16.1 right operand is converted to type of assignment expression
        // 6.5.26 The type of an assignment expression is the type the left operand would have after lvalue conversion.
        rValueType = cParamType;

        // We want to write a possible new Address in the new State, but
        // explore the old state for the parameters
        newState =
            expressionEvaluator.assignFieldToState(
                newState, callEdge, newObject, 0, symbolicValue, rValueType);
      }
    }

    return newStates;
  }

  // Current SMGState is not fully persistent
  @Override
  protected void setInfo(
      AbstractState abstractState, Precision abstractPrecision, CFAEdge cfaEdge) {
    super.setInfo(abstractState, abstractPrecision, cfaEdge);
    state = state.copyOf();
    state.cleanCurrentChain();
  }

  @Override
  protected Collection<SMGState> handleAssumption(
      CAssumeEdge cfaEdge, CExpression expression, boolean truthAssumption)
      throws CPATransferException {
    return handleAssumption(state, expression, cfaEdge, truthAssumption, true);
  }

  private List<SMGState> handleAssumption(SMGState smgState, CExpression expression, CFAEdge cfaEdge,
      boolean truthValue, boolean createNewStateIfNecessary) throws CPATransferException {

    // FIXME Quickfix, simplify expressions for sv-comp, later assumption handling has to be refactored to be able to handle complex expressions
    expression = eliminateOuterEquals(expression);

    // get the value of the expression (either true[-1], false[0], or unknown[null])
    AssumeVisitor visitor = expressionEvaluator.getAssumeVisitor(cfaEdge, smgState);
    List<SMGState> result = new ArrayList<>();
    for (SMGValueAndState valueAndState : expression.accept(visitor)) {

      SMGSymbolicValue value = valueAndState.getObject();
      smgState = valueAndState.getSmgState();

      if (!value.isUnknown()) {
        if ((truthValue && value.equals(SMGKnownSymValue.TRUE))
            || (!truthValue && value.equals(SMGZeroValue.INSTANCE))) {
          result.add(smgState);
        } else {
          // This signals that there are no new States reachable from this State i. e. the
          // Assumption does not hold.
        }
      } else {
        result.addAll(
            deriveFurtherInformationFromAssumption(smgState, visitor, cfaEdge, truthValue, expression,
                createNewStateIfNecessary));
      }
    }

    return result;
  }

  private SMGState checkAndSetErrorRelation(SMGState smgState) {
    if (smgPredicateManager.isErrorPathFeasible(smgState)) {
      smgState =
          smgState.withInvalidRead().withInvalidWrite().withErrorDescription("Possible overflow");
    }
    return smgState.resetErrorRelation();
  }

  private List<SMGState> deriveFurtherInformationFromAssumption(
      SMGState pSmgState,
      AssumeVisitor visitor,
      CFAEdge cfaEdge,
      boolean truthValue,
      CExpression expression,
      boolean createNewStateIfNecessary)
      throws CPATransferException {

    SMGState smgState = pSmgState;

    boolean impliesEqOn = visitor.impliesEqOn(truthValue, smgState);
    boolean impliesNeqOn = visitor.impliesNeqOn(truthValue, smgState);

    SMGSymbolicValue val1ImpliesOn;
    SMGSymbolicValue val2ImpliesOn;

    if(impliesEqOn || impliesNeqOn ) {
      val1ImpliesOn = visitor.impliesVal1(smgState);
      val2ImpliesOn = visitor.impliesVal2(smgState);
    } else {
      val1ImpliesOn = SMGUnknownValue.INSTANCE;
      val2ImpliesOn = SMGUnknownValue.INSTANCE;
    }

    List<SMGState> result = new ArrayList<>();

    for (SMGExplicitValueAndState explicitValueAndState :
        expressionEvaluator.evaluateExplicitValue(smgState, cfaEdge, expression)) {

      SMGExplicitValue explicitValue = explicitValueAndState.getObject();
      SMGState explicitSmgState = explicitValueAndState.getSmgState();

      if (explicitValue.isUnknown()) {

        // Don't continuously create new states when strengthening.
        SMGState newState =
            createNewStateIfNecessary ? explicitSmgState.copyOf() : explicitSmgState;

        if (!val1ImpliesOn.isUnknown() && !val2ImpliesOn.isUnknown()) {
          if (impliesEqOn) {
            newState.identifyEqualValues(
                (SMGKnownSymbolicValue) val1ImpliesOn, (SMGKnownSymbolicValue) val2ImpliesOn);
          } else if (impliesNeqOn) {
            newState.identifyNonEqualValues(
                (SMGKnownSymbolicValue) val1ImpliesOn, (SMGKnownSymbolicValue) val2ImpliesOn);
          }
        }

        newState = expressionEvaluator.deriveFurtherInformation(newState, truthValue, cfaEdge, expression);
        PredRelation pathPredicateRelation = newState.getPathPredicateRelation();
        BooleanFormula predicateFormula = smgPredicateManager.getPredicateFormula(pathPredicateRelation);
        try {
          if (newState.hasMemoryErrors() || !smgPredicateManager.isUnsat(predicateFormula)) {
            result.add(newState);
          }
        } catch (SolverException pE) {
          result.add(newState);
          logger.log(Level.WARNING, "Solver Exception: ", pE, " on predicate ", predicateFormula);
        } catch (InterruptedException pE) {
          result.add(newState);
          logger.log(Level.WARNING, "Solver Interrupted Exception: ", pE, " on predicate ", predicateFormula);
        }
      } else if ((truthValue && !explicitValue.equals(SMGZeroValue.INSTANCE))
          || (!truthValue && explicitValue.equals(SMGZeroValue.INSTANCE))) {
        result.add(explicitSmgState);
      } else {
        // This signals that there are no new States reachable from this State i. e. the
        // Assumption does not hold.
      }
    }

    return result;
  }

  /**
   * This method simplifies an expression, if possible, else returns it unchanged.
   * (a==b)==0 --> a!=b.
   * (a!=b)==0 --> a==b.
   * (a==b)!=0 --> a==b.
   * (a!=b)!=0 --> a!=b.
   */
  // TODO implement as CFA-preprocessing?
  private static CExpression eliminateOuterEquals(CExpression pExpression) {

    if (!(pExpression instanceof CBinaryExpression)) {
      return pExpression;
    }

    CBinaryExpression binExp = (CBinaryExpression) pExpression;
    CExpression op1 = binExp.getOperand1();
    CExpression op2 = binExp.getOperand2();
    BinaryOperator op = binExp.getOperator();

    if (!(op1 instanceof CBinaryExpression
        && op2 instanceof CIntegerLiteralExpression
        && ((CIntegerLiteralExpression) op2).getValue().equals(BigInteger.ZERO)
        && (op == BinaryOperator.EQUALS || op == BinaryOperator.NOT_EQUALS))) {
      return pExpression;
    }

    CBinaryExpression binExpOp1 = (CBinaryExpression) op1;
    switch (binExpOp1.getOperator()) {
    case EQUALS:
      return new CBinaryExpression(binExpOp1.getFileLocation(), binExpOp1.getExpressionType(),
          binExpOp1.getCalculationType(), binExpOp1.getOperand1(), binExpOp1.getOperand2(),
          op == BinaryOperator.EQUALS ? BinaryOperator.NOT_EQUALS : BinaryOperator.EQUALS);
    case NOT_EQUALS:
      return new CBinaryExpression(binExpOp1.getFileLocation(), binExpOp1.getExpressionType(),
          binExpOp1.getCalculationType(), binExpOp1.getOperand1(), binExpOp1.getOperand2(),
          op == BinaryOperator.EQUALS ? BinaryOperator.EQUALS : BinaryOperator.NOT_EQUALS);
    default:
      return pExpression;
    }
  }

  @Override
  protected Collection<SMGState> handleStatementEdge(CStatementEdge pCfaEdge, CStatement cStmt)
      throws CPATransferException {
    if (cStmt instanceof CAssignment) {
      CAssignment cAssignment = (CAssignment) cStmt;
      CExpression lValue = cAssignment.getLeftHandSide();
      CRightHandSide rValue = cAssignment.getRightHandSide();

      return handleAssignment(state, pCfaEdge, lValue, rValue);
    } else if (cStmt instanceof CFunctionCallStatement) {

      CFunctionCallStatement cFCall = (CFunctionCallStatement) cStmt;
      CFunctionCallExpression cFCExpression = cFCall.getFunctionCallExpression();
      CExpression fileNameExpression = cFCExpression.getFunctionNameExpression();
      String calledFunctionName = fileNameExpression.toASTString();

      if (builtins.isABuiltIn(calledFunctionName)) {
        SMGState newState = state.copyOf();

        if (builtins.isConfigurableAllocationFunction(calledFunctionName)) {
          logger.logf(
              Level.INFO,
              "%s: Calling '%s' and not using the result, resulting in memory leak.",
              pCfaEdge.getFileLocation(),
              calledFunctionName);
          List<SMGState> newStates =
              asSMGStateList(
                  builtins.evaluateConfigurableAllocationFunction(
                      cFCExpression, newState, pCfaEdge));
          for (SMGState s : newStates) {
            s.setMemLeak(
                "Calling '"
                    + calledFunctionName
                    + "' and not using the result, resulting in memory leak.",
                Collections.emptyList());
          }
          return newStates;
        }
        if (builtins.isDeallocationFunction(calledFunctionName)) {
          return builtins.evaluateFree(cFCExpression, newState, pCfaEdge);
        }
        return asSMGStateList(
            builtins.handleBuiltinFunctionCall(
                pCfaEdge, cFCExpression, calledFunctionName, newState));

      } else {
        return asSMGStateList(
            builtins.handleUnknownFunction(pCfaEdge, cFCExpression, calledFunctionName, state));
      }
    } else {
      return ImmutableList.of(state);
    }
  }

  private List<SMGState> handleAssignment(
      SMGState pState, CFAEdge cfaEdge, CExpression lValue, CRightHandSide rValue)
      throws CPATransferException {

    List<SMGState> result = new ArrayList<>(4);
    LValueAssignmentVisitor visitor =
        expressionEvaluator.getLValueAssignmentVisitor(cfaEdge, pState);
    for (SMGAddressAndState addressOfFieldAndState : lValue.accept(visitor)) {
      SMGAddress addressOfField = addressOfFieldAndState.getObject();
      pState = addressOfFieldAndState.getSmgState();

      CType fieldType = TypeUtils.getRealExpressionType(lValue);

      if (addressOfField.isUnknown()) {
        SMGState resultState = pState.copyOf();
        /*Check for dereference errors in rValue*/
        List<SMGState> newStates =
            asSMGStateList(readValueToBeAssiged(resultState, cfaEdge, rValue));
        newStates.forEach(smgState -> smgState.unknownWrite());
        result.addAll(newStates);
      } else {
        result.addAll(handleAssignmentToField(
            pState,
            cfaEdge,
            addressOfField.getObject(),
            addressOfField.getOffset().getAsLong(),
            fieldType,
            rValue));
      }
    }

    return result;
  }

  /*
   * Creates value to be assigned to given field, by either reading it from the state,
   * or creating it, if an unknown value is returned, and marking it in missing Information.
   * Note that this read may modify the state.
   *
   */
  private List<SMGValueAndState> readValueToBeAssiged(
      SMGState pNewState, CFAEdge cfaEdge, CRightHandSide rValue) throws CPATransferException {

    List<SMGValueAndState> resultValueAndStates = new ArrayList<>();
    for (SMGValueAndState valueAndState :
        expressionEvaluator.evaluateExpressionValue(pNewState, cfaEdge, rValue)) {
      SMGSymbolicValue value = valueAndState.getObject();

      if (value.isUnknown()) {
        value = SMGKnownSymValue.of();
        valueAndState = SMGValueAndState.of(valueAndState.getSmgState(), value);
      }
      resultValueAndStates.add(valueAndState);
    }
    return resultValueAndStates;
  }

  // assign value of given expression to State at given location
  private List<SMGState> assignFieldToState(SMGState pNewState, CFAEdge cfaEdge,
      SMGObject memoryOfField, long fieldOffset, CType pLFieldType, CRightHandSide rValue)
          throws CPATransferException {

    List<SMGState> result = new ArrayList<>(4);
    CType rValueType = TypeUtils.getRealExpressionType(rValue);
    for (SMGValueAndState valueAndState : readValueToBeAssiged(pNewState, cfaEdge, rValue)) {
      SMGSymbolicValue value = valueAndState.getObject();
      SMGState newState = valueAndState.getSmgState();

      //TODO (  cast expression)

      //6.5.16.1 right operand is converted to type of assignment expression
      // 6.5.26 The type of an assignment expression is the type the left operand would have after lvalue conversion.
      rValueType = pLFieldType;

      for (Pair<SMGState, SMGKnownSymbolicValue> currentNewStateWithMergedValue :
          assignExplicitValueToSymbolicValue(newState, cfaEdge, value, rValue)) {
        result.add(
            expressionEvaluator.assignFieldToState(
                currentNewStateWithMergedValue.getFirst(),
                cfaEdge,
                memoryOfField,
                fieldOffset,
                value,
                rValueType));
      }
    }

    return result;
  }

  // Assign symbolic value to the explicit value calculated from pRvalue
  private List<Pair<SMGState, SMGKnownSymbolicValue>> assignExplicitValueToSymbolicValue(
      SMGState pNewState, CFAEdge pCfaEdge, SMGSymbolicValue value, CRightHandSide pRValue)
      throws CPATransferException {

    SMGExpressionEvaluator expEvaluator = new SMGExpressionEvaluator(logger, machineModel);

    List<Pair<SMGState, SMGKnownSymbolicValue>> result = new ArrayList<>();

    for (SMGExplicitValueAndState expValueAndState :
        expEvaluator.evaluateExplicitValue(pNewState, pCfaEdge, pRValue)) {
      SMGExplicitValue expValue = expValueAndState.getObject();
      SMGState newState = expValueAndState.getSmgState();

      if (!expValue.isUnknown()) {
        SMGKnownSymbolicValue mergedSymValue =
            newState.putExplicit((SMGKnownSymbolicValue) value, (SMGKnownExpValue) expValue);
        result.add(Pair.of(newState, mergedSymValue));
      } else {
        result.add(Pair.of(newState, null));
      }
    }

    return result;
  }

  private List<SMGState> handleAssignmentToField(
      UnmodifiableSMGState pState,
      CFAEdge cfaEdge,
      SMGObject memoryOfField,
      long fieldOffset,
      CType pLFieldType,
      CRightHandSide rValue)
      throws CPATransferException {

    return assignFieldToState(
        pState.copyOf(), cfaEdge, memoryOfField, fieldOffset, pLFieldType, rValue);
  }

  private List<SMGState> handleVariableDeclaration(SMGState pState, CVariableDeclaration pVarDecl, CDeclarationEdge pEdge) throws CPATransferException {
    String varName = pVarDecl.getName();
    CType cType = TypeUtils.getRealExpressionType(pVarDecl);

    if (cType.isIncomplete() && cType instanceof CElaboratedType) {
      // for incomplete types, we do not add variables.
      // we are not allowed to read or write them, dereferencing is possible.
      // example: "struct X; extern struct X var; void main() { }"
      // TODO currently we assume that only CElaboratedTypes are unimportant when incomplete.
      return ImmutableList.of(pState);
    }

    SMGObject newObject = pState.getHeap().getObjectForVisibleVariable(varName);
      /*
     *  The variable is not null if we seen the declaration already, for example in loops. Invalid
     *  occurrences (variable really declared twice) should be caught for us by the parser. If we
     *  already processed the declaration, we do nothing.
     */
    if (newObject == null) {
      int typeSize = expressionEvaluator.getBitSizeof(pEdge, cType, pState);

      // Handle incomplete type of extern variables as externally allocated
      if (options.isHandleExternVariableAsExternalAllocation() && cType.isIncomplete() &&
          pVarDecl.getCStorageClass().equals(CStorageClass.EXTERN)) {
        typeSize = options.getExternalAllocationSize();
      }
      if (pVarDecl.isGlobal()) {
        newObject = pState.addGlobalVariable(typeSize, varName);
      } else {
        java.util.Optional<SMGObject> addedLocalVariable =
            pState.addLocalVariable(typeSize, varName);
        if (!addedLocalVariable.isPresent()) {
          throw new SMGInconsistentException("Cannot add a local variable to an empty stack.");
        }
        newObject = addedLocalVariable.get();
      }
    }

    return handleInitializerForDeclaration(pState, newObject, pVarDecl, pEdge);
  }

  @Override
  protected List<SMGState> handleDeclarationEdge(CDeclarationEdge edge, CDeclaration cDecl)
      throws CPATransferException {
    if (!(cDecl instanceof CVariableDeclaration)) {
      return ImmutableList.of(state);
    }

    SMGState newState = state.copyOf();
    return handleVariableDeclaration(newState, (CVariableDeclaration)cDecl, edge);
  }

  private List<SMGState> handleInitializerForDeclaration(SMGState pState, SMGObject pObject, CVariableDeclaration pVarDecl, CDeclarationEdge pEdge) throws CPATransferException {
    CInitializer newInitializer = pVarDecl.getInitializer();
    CType cType = TypeUtils.getRealExpressionType(pVarDecl);

    if (newInitializer != null) {
      return handleInitializer(pState, pVarDecl, pEdge, pObject, 0, cType, newInitializer);
    } else if (pVarDecl.isGlobal()) {
      // Don't nullify extern variables
      if (pVarDecl.getCStorageClass().equals(CStorageClass.EXTERN)) {
        if (options.isHandleExternVariableAsExternalAllocation()) {
          pState.setExternallyAllocatedFlag(pObject);
        }
      } else {
        // Global variables without initializer are nullified in C
        pState =
            expressionEvaluator.writeValue(pState, pObject, 0, cType, SMGZeroValue.INSTANCE, pEdge);
      }
    }

    return ImmutableList.of(pState);
  }

  private List<SMGState> handleInitializer(
      SMGState pNewState,
      CVariableDeclaration pVarDecl,
      CFAEdge pEdge,
      SMGObject pNewObject,
      long pOffset,
      CType pLValueType,
      CInitializer pInitializer)
      throws CPATransferException {

    if (pInitializer instanceof CInitializerExpression) {
       return assignFieldToState(pNewState, pEdge, pNewObject,
          pOffset, pLValueType,
          ((CInitializerExpression) pInitializer).getExpression());

    } else if (pInitializer instanceof CInitializerList) {

      return handleInitializerList(pNewState, pVarDecl, pEdge,
          pNewObject, pOffset, pLValueType, ((CInitializerList) pInitializer));
    } else if (pInitializer instanceof CDesignatedInitializer) {
      throw new AssertionError("Error in handling initializer, designated Initializer " + pInitializer.toASTString()
          + " should not appear at this point.");

    } else {
      throw new UnrecognizedCodeException("Did not recognize Initializer", pInitializer);
    }
  }

  private List<SMGState> handleInitializerList(
      SMGState pNewState,
      CVariableDeclaration pVarDecl,
      CFAEdge pEdge,
      SMGObject pNewObject,
      long pOffset,
      CType pLValueType,
      CInitializerList pNewInitializer)
      throws CPATransferException {

    CType realCType = pLValueType.getCanonicalType();

    if (realCType instanceof CArrayType) {
      CArrayType arrayType = (CArrayType) realCType;
      return handleInitializerList(pNewState, pVarDecl, pEdge,
          pNewObject, pOffset, arrayType, pNewInitializer);

    } else if (realCType instanceof CCompositeType) {
      CCompositeType structType = (CCompositeType) realCType;
      return handleInitializerList(pNewState, pVarDecl, pEdge,
          pNewObject, pOffset, structType, pNewInitializer);
    }

    // Type cannot be resolved
    logger.log(Level.INFO,() ->
              String.format("Type %s cannot be resolved sufficiently to handle initializer %s",
                  realCType.toASTString(""), pNewInitializer));
    return ImmutableList.of(pNewState);
  }

  @SuppressWarnings("deprecation") // replace with machineModel.getAllFieldOffsetsInBits
  private Pair<Long, Integer> calculateOffsetAndPositionOfFieldFromDesignator(
      long offsetAtStartOfStruct,
      List<CCompositeTypeMemberDeclaration> pMemberTypes,
      CDesignatedInitializer pInitializer,
      CCompositeType pLValueType)
      throws UnrecognizedCodeException {

    // TODO More Designators?
    assert pInitializer.getDesignators().size() == 1;

    String fieldDesignator = ((CFieldDesignator) pInitializer.getDesignators().get(0)).getFieldName();

    long offset = offsetAtStartOfStruct;
    int sizeOfByte = machineModel.getSizeofCharInBits();
    for (int listCounter = 0; listCounter < pMemberTypes.size(); listCounter++) {

      CCompositeTypeMemberDeclaration memberDcl = pMemberTypes.get(listCounter);

      if (memberDcl.getName().equals(fieldDesignator)) {
        return Pair.of(offset, listCounter);
      } else {
        if (pLValueType.getKind() == ComplexTypeKind.STRUCT) {
          int memberSize = machineModel.getSizeofInBits(memberDcl.getType());
          if (!(memberDcl.getType() instanceof CBitFieldType)) {
            offset += memberSize;
            long overByte = offset % machineModel.getSizeofCharInBits();
            if (overByte > 0) {
              offset += machineModel.getSizeofCharInBits() - overByte;
            }
            offset +=
                machineModel.getPadding(offset / sizeOfByte, memberDcl.getType()) * sizeOfByte;
          } else {
            // Cf. implementation of {@link MachineModel#getFieldOffsetOrSizeOrFieldOffsetsMappedInBits(...)}
            CType innerType = ((CBitFieldType) memberDcl.getType()).getType();

            if (memberSize == 0) {
              offset = machineModel.calculatePaddedBitsize(0, offset, innerType, sizeOfByte);
            } else {
              offset =
                  machineModel.calculateNecessaryBitfieldOffset(
                      offset, innerType, sizeOfByte, memberSize);
              offset += memberSize;
            }
          }
        }
      }
    }
    throw new UnrecognizedCodeException("CDesignator field name not in struct.", pInitializer);
  }

  private List<SMGState> handleInitializerList(
      SMGState pNewState,
      CVariableDeclaration pVarDecl,
      CFAEdge pEdge,
      SMGObject pNewObject,
      long pOffset,
      CCompositeType pLValueType,
      CInitializerList pNewInitializer)
      throws CPATransferException {

    int listCounter = 0;

    List<CCompositeType.CCompositeTypeMemberDeclaration> memberTypes = pLValueType.getMembers();
    Pair<SMGState, Long> startOffsetAndState = Pair.of(pNewState, pOffset);
    List<Pair<SMGState, Long>> offsetAndStates = new ArrayList<>();
    offsetAndStates.add(startOffsetAndState);

    // Move preinitialization of global variable because of unpredictable fields' order within CDesignatedInitializer
    if (pVarDecl.isGlobal()) {
      List<Pair<SMGState, Long>> result = new ArrayList<>(offsetAndStates.size());

      for (Pair<SMGState, Long> offsetAndState : offsetAndStates) {
        long offset = offsetAndState.getSecond();
        SMGState newState = offsetAndState.getFirst();
        int sizeOfType = expressionEvaluator.getBitSizeof(pEdge, pLValueType, pNewState);

        if (offset - pOffset < sizeOfType) {
          newState =
              expressionEvaluator.writeValue(
                  newState,
                  pNewObject,
                  offset,
                  TypeUtils.createTypeWithLength(
                      Math.toIntExact((sizeOfType - (offset - pOffset)))),
                  SMGZeroValue.INSTANCE,
                  pEdge);
        }

        result.add(Pair.of(newState, offset));
      }

      offsetAndStates = result;
    }

    for (CInitializer initializer : pNewInitializer.getInitializers()) {
      if (initializer instanceof CDesignatedInitializer) {
        Pair<Long, Integer> offsetAndPosition =
            calculateOffsetAndPositionOfFieldFromDesignator(pOffset, memberTypes,
                (CDesignatedInitializer) initializer, pLValueType);
        long offset = offsetAndPosition.getFirst();
        listCounter = offsetAndPosition.getSecond();
        initializer = ((CDesignatedInitializer) initializer).getRightHandSide();

        List<Pair<SMGState, Long>> resultOffsetAndStatesDesignated = new ArrayList<>();
        resultOffsetAndStatesDesignated.add(Pair.of(pNewState, offset));

        offsetAndStates = resultOffsetAndStatesDesignated;
      }

      if (listCounter >= memberTypes.size()) {
        throw new UnrecognizedCodeException(
            "More Initializer in initializer list "
                + pNewInitializer.toASTString()
                + " than fit in type "
                + pLValueType.toASTString(""),
            pEdge);
      }

      CType memberType = memberTypes.get(listCounter).getType();
      List<Pair<SMGState, Long>> resultOffsetAndStates = new ArrayList<>();

      for (Pair<SMGState, Long> offsetAndState : offsetAndStates) {
        long offset = offsetAndState.getSecond();
        if (!(memberType instanceof CBitFieldType)) {
          int overByte = Math.toIntExact(offset % machineModel.getSizeofCharInBits());
          if (overByte > 0) {
            offset += machineModel.getSizeofCharInBits() - overByte;
          }
          @SuppressWarnings("deprecation") // replace with machineModel.getAllFieldOffsetsInBits
          long padding =
              machineModel.getPadding(offset / machineModel.getSizeofCharInBits(), memberType);
          offset += padding * machineModel.getSizeofCharInBits();
        }
        SMGState newState = offsetAndState.getFirst();

        List<SMGState> pNewStates =
            handleInitializer(newState, pVarDecl, pEdge, pNewObject, offset, memberType, initializer);

        offset = offset + machineModel.getSizeofInBits(memberType);

        final long currentOffset = offset;
        resultOffsetAndStates.addAll(Lists.transform(pNewStates, s -> Pair.of(s, currentOffset)));
      }

      offsetAndStates = resultOffsetAndStates;
      listCounter++;
    }

    return Lists.transform(offsetAndStates, Pair::getFirst);
  }

  private List<SMGState> handleInitializerList(
      SMGState pNewState,
      CVariableDeclaration pVarDecl,
      CFAEdge pEdge,
      SMGObject pNewObject,
      long pOffset,
      CArrayType pLValueType,
      CInitializerList pNewInitializer)
      throws CPATransferException {

    int listCounter = 0;

    CType elementType = pLValueType.getType();

    int sizeOfElementType = expressionEvaluator.getBitSizeof(pEdge, elementType, pNewState);

    List<SMGState> newStates = new ArrayList<>(4);
    newStates.add(pNewState);

    for (CInitializer initializer : pNewInitializer.getInitializers()) {

      long offset = pOffset + listCounter * sizeOfElementType;

      List<SMGState> result = new ArrayList<>();

      for (SMGState newState : newStates) {
        result.addAll(handleInitializer(newState, pVarDecl, pEdge,
            pNewObject, offset, pLValueType.getType(), initializer));
      }

      newStates = result;
      listCounter++;
    }

    if (pVarDecl.isGlobal()) {
      List<SMGState> result = new ArrayList<>(newStates.size());

      for (SMGState newState : newStates) {
        if (!options.isGCCZeroLengthArray() || pLValueType.getLength() != null) {
          int sizeOfType = expressionEvaluator.getBitSizeof(pEdge, pLValueType, pNewState);

          long offset = pOffset + listCounter * sizeOfElementType;
          if (offset - pOffset < sizeOfType) {
            newState =
                expressionEvaluator.writeValue(
                    newState,
                    pNewObject,
                    offset,
                    TypeUtils.createTypeWithLength(
                        Math.toIntExact(sizeOfType - (offset - pOffset))),
                    SMGZeroValue.INSTANCE,
                    pEdge);
          }
        }
        result.add(newState);
      }
      newStates = result;
    }

    return ImmutableList.copyOf(newStates);
  }

  @Override
  public Collection<? extends AbstractState> strengthen(AbstractState element, List<AbstractState> elements,
      CFAEdge cfaEdge, Precision pPrecision) throws CPATransferException, InterruptedException {

    ArrayList<SMGState> toStrengthen = new ArrayList<>();
    ArrayList<SMGState> result = new ArrayList<>();
    toStrengthen.add((SMGState) element);
    result.add((SMGState) element);

    for (AbstractState ae : elements) {
      if (ae instanceof AutomatonState) {
        // New result
        result.clear();
        for (SMGState stateToStrengthen : toStrengthen) {
          Collection<SMGState> ret = strengthen((AutomatonState) ae, stateToStrengthen, cfaEdge);
          if (ret == null) {
            result.add(stateToStrengthen);
          } else {
            result.addAll(ret);
          }
        }
        toStrengthen.clear();
        toStrengthen.addAll(result);
      }
    }

    return result;
  }

  private Collection<SMGState> strengthen(AutomatonState pAutomatonState, SMGState pElement,
      CFAEdge pCfaEdge) throws CPATransferException {

    FluentIterable<CExpression> assumptions =
        from(pAutomatonState.getAssumptions()).filter(CExpression.class);

    if(assumptions.isEmpty()) {
      return Collections.singleton(pElement);
    }

    StringBuilder assumeDesc = new StringBuilder();
    SMGState newElement = pElement;

    for (CExpression assume : assumptions) {
      assumeDesc.append(assume.toASTString());

      // only create new SMGState if necessary
      List<SMGState> newElements =
          handleAssumption(newElement, assume, pCfaEdge, true, pElement == newElement);

      assert newElements.size() < 2;

      if (newElements.isEmpty()) {
        newElement = null;
        break;
      } else {
        newElement = newElements.get(0).withViolationsOf(newElement);
      }
    }

    if (newElement == null) {
      return Collections.emptyList();
    } else {
      SMGUtils.plotWhenConfigured(getDotExportFileName(newElement), newElement, assumeDesc.toString(), logger, SMGExportLevel.EVERY, exportSMGOptions);
      return Collections.singleton(newElement);
    }
  }

  public void changeKindToRefinment() {
    kind = SMGTransferRelationKind.REFINEMENT;
  }

  static List<SMGState> asSMGStateList(List<? extends SMGValueAndState> valueAndStateList) {
    return Lists.transform(valueAndStateList, SMGValueAndState::getSmgState);
  }
}
