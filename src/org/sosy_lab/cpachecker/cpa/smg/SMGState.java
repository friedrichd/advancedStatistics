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
package org.sosy_lab.cpachecker.cpa.smg;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CBinaryExpression.BinaryOperator;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CParameterDeclaration;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CAssumeEdge;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.interfaces.AbstractQueryableState;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGAbstractObjectAndState.SMGAddressValueAndState;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGAbstractObjectAndState.SMGValueAndState;
import org.sosy_lab.cpachecker.cpa.smg.graphs.CLangSMG;
import org.sosy_lab.cpachecker.cpa.smg.graphs.CLangSMGConsistencyVerifier;
import org.sosy_lab.cpachecker.cpa.smg.graphs.PredRelation;
import org.sosy_lab.cpachecker.cpa.smg.graphs.UnmodifiableCLangSMG;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgeHasValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgeHasValueFilter;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgePointsTo;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgePointsToFilter;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGAbstractObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGRegion;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.dll.SMGDoublyLinkedList;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.optional.SMGOptionalObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.sll.SMGSingleLinkedList;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGAddress;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGAddressValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGExplicitValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownAddressValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownExpValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownSymValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownSymbolicValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGSymbolicValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGZeroValue;
import org.sosy_lab.cpachecker.cpa.smg.join.SMGIsLessOrEqual;
import org.sosy_lab.cpachecker.cpa.smg.join.SMGJoin;
import org.sosy_lab.cpachecker.cpa.smg.join.SMGJoinStatus;
import org.sosy_lab.cpachecker.cpa.smg.refiner.SMGMemoryPath;
import org.sosy_lab.cpachecker.exceptions.InvalidQueryException;
import org.sosy_lab.cpachecker.util.states.MemoryLocation;

public class SMGState implements UnmodifiableSMGState, AbstractQueryableState, Graphable {

  // Properties:
  private static final String HAS_INVALID_FREES = "has-invalid-frees";
  private static final String HAS_INVALID_READS = "has-invalid-reads";
  private static final String HAS_INVALID_WRITES = "has-invalid-writes";
  private static final String HAS_LEAKS = "has-leaks";

  private static final Pattern externalAllocationRecursivePattern = Pattern.compile("^(r_)(\\d+)(_.*)$");

  // use 'id' and 'precessorId' only for debugging or logging, never for important stuff!
  // TODO remove to avoid problems?
  private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);
  private final int predecessorId;
  private final int id;

  private final BiMap<SMGKnownSymbolicValue, SMGKnownExpValue> explicitValues = HashBiMap.create();
  private final CLangSMG heap;

  private final boolean blockEnded;

  private SMGErrorInfo errorInfo;

  private final LogManager logger;
  private final SMGOptions options;

  private void issueMemoryError(String pMessage, boolean pUndefinedBehavior) {
    if (options.isMemoryErrorTarget()) {
      logger.log(Level.FINE, pMessage);
    } else if (pUndefinedBehavior) {
      logger.log(Level.FINE, pMessage);
      logger.log(Level.FINE,
          "Non-target undefined behavior detected. The verification result is unreliable.");
    }
  }

  @Override
  public String getErrorDescription() {
    return errorInfo.getErrorDescription();
  }

  @Override
  public SMGState withErrorDescription(String pErrorDescription) {
    return new SMGState(
        logger,
        options,
        heap.copyOf(),
        id,
        explicitValues,
        errorInfo.withErrorMessage(pErrorDescription),
        blockEnded);
  }

  /**
   * Constructor.
   *
   * Keeps consistency: yes
   *
   * @param pLogger A logger to log any messages
   * @param pMachineModel A machine model for the underlying SMGs
   */
  public SMGState(LogManager pLogger, MachineModel pMachineModel, SMGOptions pOptions) {
    this(
        pLogger,
        pOptions,
        new CLangSMG(pMachineModel),
        ID_COUNTER.getAndIncrement(),
        Collections.emptyMap());
  }

  public SMGState(
      LogManager pLogger,
      SMGOptions pOptions,
      CLangSMG pHeap,
      int pPredId,
      Map<SMGKnownSymbolicValue, SMGKnownExpValue> pMergedExplicitValues) {
    this(pLogger, pOptions, pHeap, pPredId, pMergedExplicitValues, SMGErrorInfo.of(), false);
  }

  /** Copy constructor. */
  private SMGState(
      LogManager pLogger,
      SMGOptions pOptions,
      CLangSMG pHeap,
      int pPredId,
      Map<SMGKnownSymbolicValue, SMGKnownExpValue> pExplicitValues,
      SMGErrorInfo pErrorInfo,
      boolean pBlockEnded) {
    options = pOptions;
    heap = pHeap;
    logger = pLogger;
    predecessorId = pPredId;
    id = ID_COUNTER.getAndIncrement();
    Preconditions.checkArgument(!pExplicitValues.containsKey(null));
    Preconditions.checkArgument(!pExplicitValues.containsValue(null));
    explicitValues.putAll(pExplicitValues);
    errorInfo = pErrorInfo;
    blockEnded = pBlockEnded;
  }

  private SMGState(SMGState pOriginalState, Property pProperty) {
    heap = pOriginalState.heap.copyOf();
    logger = pOriginalState.logger;
    options = pOriginalState.options;
    predecessorId = pOriginalState.getId();
    id = ID_COUNTER.getAndIncrement();
    explicitValues.putAll(pOriginalState.explicitValues);
    blockEnded = pOriginalState.blockEnded;
    errorInfo = pOriginalState.errorInfo.withProperty(pProperty);
  }

  @Override
  public SMGState copyOf() {
    return new SMGState(logger, options, heap.copyOf(), id, explicitValues, errorInfo, blockEnded);
  }

  @Override
  public SMGState copyWith(CLangSMG pSmg, BiMap<SMGKnownSymbolicValue, SMGKnownExpValue> pValues) {
    return new SMGState(logger, options, pSmg, id, pValues, errorInfo, blockEnded);
  }

  @Override
  public SMGState copyWithBlockEnd(boolean isBlockEnd) {
    return new SMGState(logger, options, heap.copyOf(), id, explicitValues, errorInfo, isBlockEnd);
  }

  @Override
  public SMGState withViolationsOf(SMGState pOther) {
    if (errorInfo.equals(pOther.errorInfo)) {
      return this;
    }
    SMGState result =
        new SMGState(logger, options, heap, ID_COUNTER.getAndIncrement(), explicitValues);
    result.errorInfo = result.errorInfo.mergeWith(pOther.errorInfo);
    return result;
  }

  /**
   * Makes SMGState create a new object and put it into the global namespace
   *
   * Keeps consistency: yes
   *
   * @param pTypeSize Size of the type of the new global variable
   * @param pVarName Name of the global variable
   * @return Newly created object
   *
   * @throws SMGInconsistentException when resulting SMGState is inconsistent
   * and the checks are enabled
   */
  public SMGObject addGlobalVariable(int pTypeSize, String pVarName)
      throws SMGInconsistentException {
    SMGRegion new_object = new SMGRegion(pTypeSize, pVarName);

    heap.addGlobalObject(new_object);
    performConsistencyCheck(SMGRuntimeCheck.HALF);
    return new_object;
  }

  /**
   * Makes SMGState create a new object and put it into the current stack
   * frame.
   *
   * Keeps consistency: yes
   *
   * @param pTypeSize Size of the type the new local variable
   * @param pVarName Name of the local variable
   * @return Newly created object
   * @throws SMGInconsistentException when resulting SMGState is inconsistent
   * and the checks are enabled
   */
  public Optional<SMGObject> addLocalVariable(int pTypeSize, String pVarName)
      throws SMGInconsistentException {
    if (heap.isStackEmpty()) {
      return Optional.empty();
    }

    SMGRegion new_object = new SMGRegion(pTypeSize, pVarName);

    heap.addStackObject(new_object);
    performConsistencyCheck(SMGRuntimeCheck.HALF);
    return Optional.of(new_object);
  }

  /**
   * Makes SMGState create a new object, compares it with the given object, and puts the given object into the current stack
   * frame.
   *
   * Keeps consistency: yes
   *
   * @param pTypeSize Size of the type of the new variable
   * @param pVarName Name of the local variable
   * @param smgObject object of local variable
   *
   * @throws SMGInconsistentException when resulting SMGState is inconsistent
   * and the checks are enabled
   */
  public void addLocalVariable(int pTypeSize, String pVarName, SMGRegion smgObject)
      throws SMGInconsistentException {
    SMGRegion new_object2 = new SMGRegion(pTypeSize, pVarName);

    assert smgObject.getLabel().equals(new_object2.getLabel());

    // arrays are converted to pointers
    assert smgObject.getSize() == pTypeSize
        || smgObject.getSize() == heap.getMachineModel().getSizeofPtrInBits();

    heap.addStackObject(smgObject);
    performConsistencyCheck(SMGRuntimeCheck.HALF);
  }

  /**
   * Adds a new frame for the function.
   *
   * Keeps consistency: yes
   *
   * @param pFunctionDefinition A function for which to create a new stack frame
   */
  public void addStackFrame(CFunctionDeclaration pFunctionDefinition)
      throws SMGInconsistentException {
    heap.addStackFrame(pFunctionDefinition);
    performConsistencyCheck(SMGRuntimeCheck.HALF);
  }

  /* ********************************************* */
  /* Non-modifying functions: getters and the like */
  /* ********************************************* */

  /**
   * Constant.
   *
   * @return The ID of this SMGState
   */
  @Override
  final public int getId() {
    return id;
  }

  /**
   * Constant.
   * .
   * @return The predecessor state, i.e. one from which this one was copied
   */
  @Override
  final public int getPredecessorId() {
    return predecessorId;
  }

  /**
   * Based on the current setting of runtime check level, it either performs a full consistency
   * check or not. If the check is performed and the state is deemed inconsistent, a {@link
   * SMGInconsistentException} is thrown.
   *
   * <p>Constant.
   *
   * @param pLevel A level of the check request. When e.g. HALF is passed, it means "perform the
   *     check if the setting is HALF or finer.
   */
  public final void performConsistencyCheck(SMGRuntimeCheck pLevel)
      throws SMGInconsistentException {
    if (pLevel == null || options.getRuntimeCheck().isFinerOrEqualThan(pLevel)) {
      if (!CLangSMGConsistencyVerifier.verifyCLangSMG(logger,
          heap)) { throw new SMGInconsistentException(
              "SMG was found inconsistent during a check on state id " + this.getId()); }
    }
  }

  /**
   * Returns a DOT representation of the SMGState.
   *
   * Constant.
   *
   * @param pName A name of the graph.
   * @param pLocation A location in the program.
   * @return String containing a DOT graph corresponding to the SMGState.
   */
  @Override
  public String toDot(String pName, String pLocation) {
    SMGPlotter plotter = new SMGPlotter();
    return plotter.smgAsDot(heap, pName, pLocation, explicitValues);
  }

  /**
   * @return A string representation of the SMGState.
   */
  @Override
  public String toString() {
    String parent =
        getPredecessorId() == 0
            ? "no parent, initial state"
            : "parent [" + getPredecessorId() + "]";
    return String.format("SMGState [%d] <-- %s: %s", getId(), parent, heap);
  }

  /**
   * Returns a address leading from a value. If the target is an abstract heap segment, materialize
   * heap segment.
   *
   * <p>Constant.
   *
   * @param pValue A value for which to return the address.
   * @return the address represented by the passed value. The value needs to be a pointer, i.e. it
   *     needs to have a points-to edge. If it does not have it, the method raises an exception.
   * @throws SMGInconsistentException When the value passed does not have a Points-To edge.
   */
  @Override
  public List<SMGAddressValueAndState> getPointerFromValue(SMGValue pValue)
      throws SMGInconsistentException {
    if (heap.isPointer(pValue)) {
      SMGEdgePointsTo addressValue = heap.getPointer(pValue);
      SMGAddressValue address = SMGKnownAddressValue.valueOf(addressValue);
      SMGObject obj = address.getObject();

      if (obj.isAbstract()) {
        performConsistencyCheck(SMGRuntimeCheck.HALF);
        return handleMaterilisation(addressValue, ((SMGAbstractObject) obj));
      }

      return Collections.singletonList(SMGAddressValueAndState.of(this, address));
    }

    throw new SMGInconsistentException("Asked for a Points-To edge for a non-pointer value");
  }

  private List<SMGAddressValueAndState> handleMaterilisation(
      SMGEdgePointsTo pointerToAbstractObject, SMGAbstractObject pSmgAbstractObject)
      throws SMGInconsistentException {

    List<SMGAddressValueAndState> result = new ArrayList<>(2);
    switch (pSmgAbstractObject.getKind()) {
      case DLL:
        SMGDoublyLinkedList dllListSeg = (SMGDoublyLinkedList) pSmgAbstractObject;
        if (dllListSeg.getMinimumLength() == 0) {
          result.addAll(copyOf().removeDls(dllListSeg, pointerToAbstractObject));
        }
        result.add(materialiseDls(dllListSeg, pointerToAbstractObject));
        break;
      case SLL:
        SMGSingleLinkedList sllListSeg = (SMGSingleLinkedList) pSmgAbstractObject;
        if (sllListSeg.getMinimumLength() == 0) {
          result.addAll(copyOf().removeSll(sllListSeg, pointerToAbstractObject));
        }
        result.add(materialiseSll(sllListSeg, pointerToAbstractObject));
        break;
      case OPTIONAL:
        SMGOptionalObject optionalObject = (SMGOptionalObject) pSmgAbstractObject;
        result.addAll(copyOf().removeOptionalObject(optionalObject));
        result.add(materialiseOptionalObject(optionalObject, pointerToAbstractObject));
        break;
      default:
        throw new UnsupportedOperationException(
            "Materilization of abstraction" + pSmgAbstractObject + " not yet implemented.");
    }
    return result;
  }

  private List<SMGAddressValueAndState> removeOptionalObject(SMGOptionalObject pOptionalObject)
      throws SMGInconsistentException {

    logger.log(Level.ALL, "Remove ", pOptionalObject, " in state id ", this.getId());

    /*Just remove the optional Object and merge all incoming pointer
     * with the one pointer in all fields of the optional edge.
     * If there is no pointer besides zero in the fields of the
     * optional object, use zero.*/

    Set<SMGEdgePointsTo> pointer = SMGUtils.getPointerToThisObject(pOptionalObject, heap);

    Set<SMGEdgeHasValue> fields = getHVEdges(SMGEdgeHasValueFilter.objectFilter(pOptionalObject));

    heap.removeHeapObjectAndEdges(pOptionalObject);

    SMGValue pointerValue = SMGZeroValue.INSTANCE;

    for (SMGEdgeHasValue field : fields) {
      if (heap.isPointer(field.getValue()) && !field.getValue().isZero()) {
        pointerValue = field.getValue();
        break;
      }
    }

    for (SMGEdgePointsTo edge : pointer) {
      heap.removePointsToEdge(edge.getValue());
      heap.replaceValue(pointerValue, edge.getValue());
    }

    return getPointerFromValue(pointerValue);
  }

  private SMGAddressValueAndState materialiseOptionalObject(SMGOptionalObject pOptionalObject,
      SMGEdgePointsTo pPointerToAbstractObject) {

    /*Just replace the optional object with a region*/
    logger.log(Level.ALL,
        "Materialise ", pOptionalObject, " in state id ", this.getId());

    Set<SMGEdgePointsTo> pointer = SMGUtils.getPointerToThisObject(pOptionalObject, heap);

    Set<SMGEdgeHasValue> fields = getHVEdges(SMGEdgeHasValueFilter.objectFilter(pOptionalObject));

    SMGObject newObject = new SMGRegion(pOptionalObject.getSize(),
        "Concrete object of " + pOptionalObject.toString(), pOptionalObject.getLevel());

    heap.addHeapObject(newObject);
    heap.setValidity(newObject, heap.isObjectValid(pOptionalObject));

    heap.removeHeapObjectAndEdges(pOptionalObject);

    for (SMGEdgeHasValue edge : fields) {
      heap.addHasValueEdge(
          new SMGEdgeHasValue(edge.getType(), edge.getOffset(), newObject, edge.getValue()));
    }

    for (SMGEdgePointsTo edge : pointer) {
      heap.removePointsToEdge(edge.getValue());
      heap.addPointsToEdge(new SMGEdgePointsTo(edge.getValue(), newObject, edge.getOffset()));
    }

    return SMGAddressValueAndState.of(
        this,
        SMGKnownAddressValue.valueOf(
            (SMGKnownSymbolicValue) pPointerToAbstractObject.getValue(),
            newObject,
            SMGKnownExpValue.valueOf(pPointerToAbstractObject.getOffset())));
  }

  private List<SMGAddressValueAndState> removeSll(
      SMGSingleLinkedList pListSeg, SMGEdgePointsTo pPointerToAbstractObject)
      throws SMGInconsistentException {

    logger.log(Level.ALL, "Remove ", pListSeg, " in state id ", this.getId());

    /*First, set all sub smgs of sll to be removed to invalid.*/
    Set<Long> restriction = ImmutableSet.of(pListSeg.getNfo());

    removeRestrictedSubSmg(pListSeg, restriction);

    /*When removing sll, connect target specifier first pointer to next field*/

    long nfo = pListSeg.getNfo();
    long hfo = pListSeg.getHfo();

    SMGEdgeHasValue nextEdge = Iterables.getOnlyElement(
        heap.getHVEdges(SMGEdgeHasValueFilter.objectFilter(pListSeg).filterAtOffset(nfo)));

    SMGEdgePointsTo nextPointerEdge = heap.getPointer(nextEdge.getValue());

    SMGValue firstPointer = getAddress(pListSeg, hfo, SMGTargetSpecifier.FIRST);

    heap.removeHeapObjectAndEdges(pListSeg);

    heap.replaceValue(nextEdge.getValue(), firstPointer);

    if (firstPointer.equals(pPointerToAbstractObject.getValue())) {
      return getPointerFromValue(nextPointerEdge.getValue());
    } else {
      throw new AssertionError(
          "Unexpected dereference of pointer " + pPointerToAbstractObject.getValue()
              + " pointing to abstraction " + pListSeg.toString());
    }
  }

  private List<SMGAddressValueAndState> removeDls(
      SMGDoublyLinkedList pListSeg, SMGEdgePointsTo pPointerToAbstractObject)
      throws SMGInconsistentException {

    logger.log(Level.ALL, "Remove ", pListSeg, " in state id ", this.getId());

    /*First, set all sub smgs of dll to be removed to invalid.*/
    Set<Long> restriction = ImmutableSet.of(pListSeg.getNfo(), pListSeg.getPfo());

    removeRestrictedSubSmg(pListSeg, restriction);

    /*When removing dll, connect target specifier first pointer to next field,
     * and target specifier last to prev field*/

    long nfo = pListSeg.getNfo();
    long pfo = pListSeg.getPfo();
    long hfo = pListSeg.getHfo();

    SMGEdgeHasValue nextEdge = Iterables.getOnlyElement(
        heap.getHVEdges(SMGEdgeHasValueFilter.objectFilter(pListSeg).filterAtOffset(nfo)));
    SMGEdgeHasValue prevEdge = Iterables.getOnlyElement(
        heap.getHVEdges(SMGEdgeHasValueFilter.objectFilter(pListSeg).filterAtOffset(pfo)));

    SMGEdgePointsTo nextPointerEdge = heap.getPointer(nextEdge.getValue());
    SMGEdgePointsTo prevPointerEdge = heap.getPointer(prevEdge.getValue());

    SMGSymbolicValue firstPointer = getAddress(pListSeg, hfo, SMGTargetSpecifier.FIRST);
    SMGSymbolicValue lastPointer = getAddress(pListSeg, hfo, SMGTargetSpecifier.LAST);

    heap.removeHeapObjectAndEdges(pListSeg);

    /* We may not have pointers to the beginning/end to this list.
     *  */

    if (firstPointer != null) {
      heap.replaceValue(nextEdge.getValue(), firstPointer);
    }

    if (lastPointer != null) {
      heap.replaceValue(prevEdge.getValue(), lastPointer);
    }

    if (firstPointer != null && firstPointer.equals(pPointerToAbstractObject.getValue())) {
      return getPointerFromValue(nextPointerEdge.getValue());
    } else if (lastPointer != null && lastPointer.equals(pPointerToAbstractObject.getValue())) {
      return getPointerFromValue(prevPointerEdge.getValue());
    } else {
      throw new AssertionError(
          "Unexpected dereference of pointer " + pPointerToAbstractObject.getValue()
              + " pointing to abstraction " + pListSeg.toString());
    }
  }

  private SMGAddressValueAndState materialiseSll(SMGSingleLinkedList pListSeg,
      SMGEdgePointsTo pPointerToAbstractObject) throws SMGInconsistentException {

    logger.log(Level.ALL, "Materialise ", pListSeg, " in state id ", this.getId());

    if (pPointerToAbstractObject
        .getTargetSpecifier() != SMGTargetSpecifier.FIRST) { throw new SMGInconsistentException(
            "Target specifier of pointer " + pPointerToAbstractObject.getValue()
                + "that leads to a sll has unexpected target specifier "
                + pPointerToAbstractObject.getTargetSpecifier().toString()); }

    SMGRegion newConcreteRegion =
        new SMGRegion(pListSeg.getSize(), "concrete sll segment ID " + SMGCPA.getNewValue(), 0);
    heap.addHeapObject(newConcreteRegion);

    Set<Long> restriction = ImmutableSet.of(pListSeg.getNfo());

    copyRestrictedSubSmgToObject(pListSeg, newConcreteRegion, restriction);

    long hfo = pListSeg.getHfo();
    long nfo = pListSeg.getNfo();

    SMGEdgeHasValue oldSllFieldToOldRegion =
        Iterables.getOnlyElement(
            heap.getHVEdges(SMGEdgeHasValueFilter.objectFilter(pListSeg).filterAtOffset(nfo)));

    SMGValue oldPointerToSll = pPointerToAbstractObject.getValue();

    Set<SMGEdgeHasValue> oldFieldsEdges =
        heap.getHVEdges(SMGEdgeHasValueFilter.objectFilter(pListSeg));
    Set<SMGEdgePointsTo> oldPtEdges = SMGUtils.getPointerToThisObject(pListSeg, heap);

    heap.removeHasValueEdge(oldSllFieldToOldRegion);
    heap.removePointsToEdge(oldPointerToSll);

    heap.removeHeapObjectAndEdges(pListSeg);

    SMGSingleLinkedList newSll = new SMGSingleLinkedList(pListSeg.getSize(), pListSeg.getHfo(),
        pListSeg.getNfo(), pListSeg.getMinimumLength() > 0 ? pListSeg.getMinimumLength() - 1 : 0,
        0);

    heap.addHeapObject(newSll);
    heap.setValidity(newSll, true);

    /*Check if pointer was already created due to All target Specifier*/
    SMGValue newPointerToNewRegion = getAddress(newConcreteRegion, hfo);

    if (newPointerToNewRegion != null) {
      heap.removePointsToEdge(newPointerToNewRegion);
      heap.replaceValue(oldPointerToSll, newPointerToNewRegion);
    }

    SMGEdgePointsTo newPtEdgeToNewRegionFromOutsideSMG =
        new SMGEdgePointsTo(oldPointerToSll, newConcreteRegion, hfo);

    SMGValue newPointerToSll = SMGKnownSymValue.of();

    /*If you can't find the pointer, use generic pointer type*/
    CType typeOfPointerToSll;

    Set<SMGEdgeHasValue> fieldsContainingOldPointerToSll =
        heap.getHVEdges(SMGEdgeHasValueFilter.valueFilter(oldPointerToSll));

    if (fieldsContainingOldPointerToSll.isEmpty()) {
      typeOfPointerToSll = CPointerType.POINTER_TO_VOID;
    } else {
      typeOfPointerToSll = fieldsContainingOldPointerToSll.iterator().next().getType();
    }

    SMGEdgeHasValue newFieldFromNewRegionToSll = new SMGEdgeHasValue(
        typeOfPointerToSll, nfo, newConcreteRegion, newPointerToSll);
    SMGEdgePointsTo newPtEToSll =
        new SMGEdgePointsTo(newPointerToSll, newSll, hfo, SMGTargetSpecifier.FIRST);

    for (SMGEdgeHasValue hve : oldFieldsEdges) {
      heap.addHasValueEdge(
          new SMGEdgeHasValue(hve.getType(), hve.getOffset(), newSll, hve.getValue()));
    }

    for (SMGEdgePointsTo ptE : oldPtEdges) {
      heap.addPointsToEdge(
          new SMGEdgePointsTo(ptE.getValue(), newSll, ptE.getOffset(), ptE.getTargetSpecifier()));
    }

    heap.addPointsToEdge(newPtEdgeToNewRegionFromOutsideSMG);

    heap.addValue(newPointerToSll);
    heap.addHasValueEdge(newFieldFromNewRegionToSll);
    heap.addPointsToEdge(newPtEToSll);

    return SMGAddressValueAndState.of(
        this, SMGKnownAddressValue.valueOf(newPtEdgeToNewRegionFromOutsideSMG));
  }

  private SMGAddressValueAndState materialiseDls(SMGDoublyLinkedList pListSeg,
      SMGEdgePointsTo pPointerToAbstractObject) throws SMGInconsistentException {

    logger.log(Level.ALL, "Materialise ", pListSeg, " in state id ", this.getId());

    SMGRegion newConcreteRegion =
        new SMGRegion(pListSeg.getSize(), "concrete dll segment ID " + SMGCPA.getNewValue(), 0);
    heap.addHeapObject(newConcreteRegion);

    Set<Long> restriction = ImmutableSet.of(pListSeg.getNfo(), pListSeg.getPfo());

    copyRestrictedSubSmgToObject(pListSeg, newConcreteRegion, restriction);

    SMGTargetSpecifier tg = pPointerToAbstractObject.getTargetSpecifier();

    long offsetPointingToDll;
    long offsetPointingToRegion;

    switch (tg) {
      case FIRST:
        offsetPointingToDll = pListSeg.getNfo();
        offsetPointingToRegion = pListSeg.getPfo();
        break;
      case LAST:
        offsetPointingToDll = pListSeg.getPfo();
        offsetPointingToRegion = pListSeg.getNfo();
        break;
      default:
        throw new SMGInconsistentException(
            "Target specifier of pointer " + pPointerToAbstractObject.getValue()
                + "that leads to a dll has unexpected target specifier " + tg.toString());
    }

    long hfo = pListSeg.getHfo();

    SMGEdgeHasValue oldDllFieldToOldRegion =
        Iterables.getOnlyElement(heap.getHVEdges(
            SMGEdgeHasValueFilter.objectFilter(pListSeg).filterAtOffset(offsetPointingToRegion)));

    SMGKnownSymbolicValue oldPointerToDll =
        (SMGKnownSymbolicValue) pPointerToAbstractObject.getValue();

    heap.removeHasValueEdge(oldDllFieldToOldRegion);
    heap.removePointsToEdge(oldPointerToDll);

    Set<SMGEdgeHasValue> oldFieldsEdges =
        heap.getHVEdges(SMGEdgeHasValueFilter.objectFilter(pListSeg));
    Set<SMGEdgePointsTo> oldPtEdges = SMGUtils.getPointerToThisObject(pListSeg, heap);

    heap.removeHeapObjectAndEdges(pListSeg);

    SMGDoublyLinkedList newDll = new SMGDoublyLinkedList(pListSeg.getSize(), pListSeg.getHfo(),
        pListSeg.getNfo(), pListSeg.getPfo(),
        pListSeg.getMinimumLength() > 0 ? pListSeg.getMinimumLength() - 1 : 0, 0);

    heap.addHeapObject(newDll);
    heap.setValidity(newDll, true);

    /*Check if pointer was already created due to All target Specifier*/
    SMGValue newPointerToNewRegion = getAddress(newConcreteRegion, hfo);

    if (newPointerToNewRegion != null) {
      heap.removePointsToEdge(newPointerToNewRegion);
      heap.replaceValue(oldPointerToDll, newPointerToNewRegion);
    }

    SMGEdgePointsTo newPtEdgeToNewRegionFromOutsideSMG =
        new SMGEdgePointsTo(oldPointerToDll, newConcreteRegion, hfo);
    SMGEdgeHasValue newFieldFromNewRegionToOutsideSMG =
        new SMGEdgeHasValue(oldDllFieldToOldRegion.getType(), offsetPointingToRegion,
            newConcreteRegion, oldDllFieldToOldRegion.getValue());

    SMGValue newPointerToDll = SMGKnownSymValue.of();

    CType typeOfPointerToDll;

    Set<SMGEdgeHasValue> fieldsContainingOldPointerToDll =
        heap.getHVEdges(SMGEdgeHasValueFilter.valueFilter(oldPointerToDll));

    if (fieldsContainingOldPointerToDll.isEmpty()) {
      typeOfPointerToDll = CPointerType.POINTER_TO_VOID;
    } else {
      typeOfPointerToDll = fieldsContainingOldPointerToDll.iterator().next().getType();
    }

    SMGEdgeHasValue newFieldFromNewRegionToDll = new SMGEdgeHasValue(typeOfPointerToDll,
        offsetPointingToDll, newConcreteRegion, newPointerToDll);
    SMGEdgePointsTo newPtEToDll = new SMGEdgePointsTo(newPointerToDll, newDll, hfo, tg);

    SMGEdgeHasValue newFieldFromDllToNewRegion = new SMGEdgeHasValue(
        oldDllFieldToOldRegion.getType(), offsetPointingToRegion, newDll, oldPointerToDll);

    for (SMGEdgeHasValue hve : oldFieldsEdges) {
      heap.addHasValueEdge(
          new SMGEdgeHasValue(hve.getType(), hve.getOffset(), newDll, hve.getValue()));
    }

    for (SMGEdgePointsTo ptE : oldPtEdges) {
      heap.addPointsToEdge(
          new SMGEdgePointsTo(ptE.getValue(), newDll, ptE.getOffset(), ptE.getTargetSpecifier()));
    }

    heap.addPointsToEdge(newPtEdgeToNewRegionFromOutsideSMG);
    heap.addHasValueEdge(newFieldFromNewRegionToOutsideSMG);

    heap.addValue(newPointerToDll);
    heap.addHasValueEdge(newFieldFromNewRegionToDll);
    heap.addPointsToEdge(newPtEToDll);

    heap.addHasValueEdge(newFieldFromDllToNewRegion);

    return SMGAddressValueAndState.of(
        this, SMGKnownAddressValue.valueOf(newPtEdgeToNewRegionFromOutsideSMG));
  }

  private void copyRestrictedSubSmgToObject(SMGObject pRoot, SMGRegion pNewRegion,
      Set<Long> pRestriction) {

    Set<SMGObject> toBeChecked = new HashSet<>();
    Map<SMGObject, SMGObject> newObjectMap = new HashMap<>();
    Map<SMGValue, SMGValue> newValueMap = new HashMap<>();

    newObjectMap.put(pRoot, pNewRegion);

    for (SMGEdgeHasValue hve : heap.getHVEdges(SMGEdgeHasValueFilter.objectFilter(pRoot))) {
      if (!pRestriction.contains(hve.getOffset())) {

        SMGValue subDlsValue = hve.getValue();
        SMGValue newVal = subDlsValue;

        if (heap.isPointer(subDlsValue)) {
          SMGEdgePointsTo reachedObjectSubSmgPTEdge = heap.getPointer(subDlsValue);
          SMGObject reachedObjectSubSmg = reachedObjectSubSmgPTEdge.getObject();
          int level = reachedObjectSubSmg.getLevel();
          SMGTargetSpecifier tg = reachedObjectSubSmgPTEdge.getTargetSpecifier();

          if ((level != 0 || tg == SMGTargetSpecifier.ALL) && !newVal.isZero()) {

            SMGObject copyOfReachedObject;

            if (!newObjectMap.containsKey(reachedObjectSubSmg)) {
              assert level > 0;
              copyOfReachedObject = reachedObjectSubSmg.copy(reachedObjectSubSmg.getLevel() - 1);
              newObjectMap.put(reachedObjectSubSmg, copyOfReachedObject);
              heap.addHeapObject(copyOfReachedObject);
              heap.setValidity(copyOfReachedObject, heap.isObjectValid(reachedObjectSubSmg));
              toBeChecked.add(reachedObjectSubSmg);
            } else {
              copyOfReachedObject = newObjectMap.get(reachedObjectSubSmg);
            }

            if (newValueMap.containsKey(subDlsValue)) {
              newVal = newValueMap.get(subDlsValue);
            } else {
              newVal = SMGKnownSymValue.of();
              heap.addValue(newVal);
              newValueMap.put(subDlsValue, newVal);

              SMGTargetSpecifier newTg;

              if (copyOfReachedObject instanceof SMGRegion) {
                newTg = SMGTargetSpecifier.REGION;
              } else {
                newTg = reachedObjectSubSmgPTEdge.getTargetSpecifier();
              }

              SMGEdgePointsTo newPtEdge = new SMGEdgePointsTo(newVal, copyOfReachedObject,
                  reachedObjectSubSmgPTEdge.getOffset(), newTg);
              heap.addPointsToEdge(newPtEdge);
            }
          }
        }
        heap.addHasValueEdge(
            new SMGEdgeHasValue(hve.getType(), hve.getOffset(), pNewRegion, newVal));
      } else {
        MachineModel model = heap.getMachineModel();
        int sizeOfHveInBits = hve.getSizeInBits(model);
        /*If a restricted field is 0, and bigger than a pointer, add 0*/
        if (sizeOfHveInBits > model.getSizeofPtrInBits() && hve.getValue().isZero()) {
          long offset = hve.getOffset() + model.getSizeofPtrInBits();
          int sizeInBits = sizeOfHveInBits - model.getSizeofPtrInBits();
          SMGEdgeHasValue expandedZeroEdge =
              new SMGEdgeHasValue(sizeInBits, offset, pNewRegion, SMGZeroValue.INSTANCE);
          heap.addHasValueEdge(expandedZeroEdge);
        }
      }
    }

    Set<SMGObject> toCheck = new HashSet<>();

    while (!toBeChecked.isEmpty()) {
      toCheck.clear();
      toCheck.addAll(toBeChecked);
      toBeChecked.clear();

      for (SMGObject objToCheck : toCheck) {
        copyObjectAndNodesIntoDestSMG(objToCheck, toBeChecked, newObjectMap, newValueMap);
      }
    }
  }

  private void copyObjectAndNodesIntoDestSMG(
      SMGObject pObjToCheck,
      Set<SMGObject> pToBeChecked,
      Map<SMGObject, SMGObject> newObjectMap,
      Map<SMGValue, SMGValue> newValueMap) {

    SMGObject newObj = newObjectMap.get(pObjToCheck);
    for (SMGEdgeHasValue hve : heap.getHVEdges(SMGEdgeHasValueFilter.objectFilter(pObjToCheck))) {
      SMGValue subDlsValue = hve.getValue();
      SMGValue newVal = subDlsValue;

      if (heap.isPointer(subDlsValue)) {
        SMGEdgePointsTo reachedObjectSubSmgPTEdge = heap.getPointer(subDlsValue);
        SMGObject reachedObjectSubSmg = reachedObjectSubSmgPTEdge.getObject();
        int level = reachedObjectSubSmg.getLevel();
        SMGTargetSpecifier tg = reachedObjectSubSmgPTEdge.getTargetSpecifier();

        if ((level != 0 || tg == SMGTargetSpecifier.ALL) && !newVal.isZero()) {

          SMGObject copyOfReachedObject;

          if (!newObjectMap.containsKey(reachedObjectSubSmg)) {
            assert level > 0;
            copyOfReachedObject = reachedObjectSubSmg.copy(reachedObjectSubSmg.getLevel() - 1);
            newObjectMap.put(reachedObjectSubSmg, copyOfReachedObject);
            heap.addHeapObject(copyOfReachedObject);
            heap.setValidity(copyOfReachedObject, heap.isObjectValid(reachedObjectSubSmg));
            pToBeChecked.add(reachedObjectSubSmg);
          } else {
            copyOfReachedObject = newObjectMap.get(reachedObjectSubSmg);
          }

          if (newValueMap.containsKey(subDlsValue)) {
            newVal = newValueMap.get(subDlsValue);
          } else {
            newVal = SMGKnownSymValue.of();
            heap.addValue(newVal);
            newValueMap.put(subDlsValue, newVal);

            SMGTargetSpecifier newTg;

            if (copyOfReachedObject instanceof SMGRegion) {
              newTg = SMGTargetSpecifier.REGION;
            } else {
              newTg = reachedObjectSubSmgPTEdge.getTargetSpecifier();
            }

            SMGEdgePointsTo newPtEdge = new SMGEdgePointsTo(newVal, copyOfReachedObject,
                reachedObjectSubSmgPTEdge.getOffset(),
                newTg);
            heap.addPointsToEdge(newPtEdge);
          }
        }
      }
      heap.addHasValueEdge(new SMGEdgeHasValue(hve.getType(), hve.getOffset(), newObj, newVal));
    }
  }

  private void removeRestrictedSubSmg(SMGObject pRoot, Set<Long> pRestriction) {

    Set<SMGObject> toBeChecked = new HashSet<>();
    Set<SMGObject> reached = new HashSet<>();

    reached.add(pRoot);

    for (SMGEdgeHasValue hve : heap.getHVEdges(SMGEdgeHasValueFilter.objectFilter(pRoot))) {
      if (!pRestriction.contains(hve.getOffset())) {

        SMGValue subDlsValue = hve.getValue();

        if (heap.isPointer(subDlsValue)) {
          SMGEdgePointsTo reachedObjectSubSmgPTEdge = heap.getPointer(subDlsValue);
          SMGObject reachedObjectSubSmg = reachedObjectSubSmgPTEdge.getObject();
          int level = reachedObjectSubSmg.getLevel();
          SMGTargetSpecifier tg = reachedObjectSubSmgPTEdge.getTargetSpecifier();

          if ((!reached.contains(reachedObjectSubSmg))
              && (level != 0 || tg == SMGTargetSpecifier.ALL)
              && !subDlsValue.isZero()) {
            assert level > 0;
            reached.add(reachedObjectSubSmg);
            heap.setValidity(reachedObjectSubSmg, false);
            toBeChecked.add(reachedObjectSubSmg);
          }
        }
      }
    }

    Set<SMGObject> toCheck = new HashSet<>();

    while (!toBeChecked.isEmpty()) {
      toCheck.clear();
      toCheck.addAll(toBeChecked);
      toBeChecked.clear();

      for (SMGObject objToCheck : toCheck) {
        removeRestrictedSubSmg(objToCheck, toBeChecked, reached);
      }
    }

    for (SMGObject toBeRemoved : reached) {
      if (toBeRemoved != pRoot) {
        heap.removeHeapObjectAndEdges(toBeRemoved);
      }
    }
  }

  private void removeRestrictedSubSmg(SMGObject pObjToCheck,
      Set<SMGObject> pToBeChecked, Set<SMGObject> reached) {

    for (SMGEdgeHasValue hve : heap.getHVEdges(SMGEdgeHasValueFilter.objectFilter(pObjToCheck))) {
      SMGValue subDlsValue = hve.getValue();

      if (heap.isPointer(subDlsValue)) {
        SMGEdgePointsTo reachedObjectSubSmgPTEdge = heap.getPointer(subDlsValue);
        SMGObject reachedObjectSubSmg = reachedObjectSubSmgPTEdge.getObject();
        int level = reachedObjectSubSmg.getLevel();
        SMGTargetSpecifier tg = reachedObjectSubSmgPTEdge.getTargetSpecifier();

        if ((!reached.contains(reachedObjectSubSmg))
            && (level != 0 || tg == SMGTargetSpecifier.ALL)
            && !subDlsValue.isZero()) {
          assert level > 0;
          reached.add(reachedObjectSubSmg);
          heap.setValidity(reachedObjectSubSmg, false);
          pToBeChecked.add(reachedObjectSubSmg);
        }
      }
    }
  }

  /**
   * Read Value in field (object, type) of an Object. If a Value cannot be determined,
   * but the given object and field is a valid place to read a value, a new value will be
   * generated and returned. (Does not create a new State but modifies this state).
   *
   * @param pObject SMGObject representing the memory the field belongs to.
   * @param pOffset offset of field being read.
   * @param pType type of field
   * @return the value and the state (may be the given state)
   */
  public SMGValueAndState forceReadValue(SMGObject pObject, long pOffset, CType pType)
      throws SMGInconsistentException {
    SMGValueAndState valueAndState = readValue(pObject, pOffset, pType);

    // Do not create a value if the read is invalid.
    if (valueAndState.getObject().isUnknown()
        && !valueAndState.getSmgState().errorInfo.isInvalidRead()) {
      SMGStateEdgePair stateAndNewEdge;
      if (valueAndState.getSmgState().getHeap().isObjectExternallyAllocated(pObject)
          && pType.getCanonicalType() instanceof CPointerType) {
        SMGAddressValue new_address = valueAndState.getSmgState().addExternalAllocation(genRecursiveLabel(pObject.getLabel()));
        stateAndNewEdge = writeValue(pObject, pOffset, pType, new_address);
      } else {
        SMGValue newValue = SMGKnownSymValue.of();
        stateAndNewEdge = writeValue0(pObject, pOffset, pType, newValue);
      }
      return SMGValueAndState.of(
          stateAndNewEdge.getState(), (SMGSymbolicValue) stateAndNewEdge.getNewEdge().getValue());
    } else {
      return valueAndState;
    }
  }

  private String genRecursiveLabel(String pLabel) {
    Matcher result = externalAllocationRecursivePattern.matcher(pLabel);
    if (result.matches()) {
      String in = result.group(2);
      Integer level = Integer.parseInt(in) + 1;
      return result.replaceFirst("$1" + level + "$3");
    } else {
      return "r_1_" + pLabel;
    }
  }

  /**
   * Read Value in field (object, type) of an Object.
   *
   * @param pObject SMGObject representing the memory the field belongs to.
   * @param pOffset offset of field being read.
   * @param pType type of field
   * @return the value and the state (may be the given state)
   */
  public SMGValueAndState readValue(SMGObject pObject, long pOffset, CType pType)
      throws SMGInconsistentException {
    if (!heap.isObjectValid(pObject) && !heap.isObjectExternallyAllocated(pObject)) {
      SMGState newState =
          withInvalidRead().withErrorDescription("Try to read from deallocated object");
      newState.addInvalidObject(pObject);
      return SMGValueAndState.of(newState);
    }

    SMGEdgeHasValue edge = new SMGEdgeHasValue(pType, pOffset, pObject, SMGZeroValue.INSTANCE);

    SMGEdgeHasValueFilter filter =
        SMGEdgeHasValueFilter.objectFilter(pObject).filterAtOffset(pOffset);
    for (SMGEdgeHasValue object_edge : heap.getHVEdges(filter)) {
      if (edge.isCompatibleFieldOnSameObject(object_edge, heap.getMachineModel())) {
        performConsistencyCheck(SMGRuntimeCheck.HALF);
        addElementToCurrentChain(object_edge);
        return SMGValueAndState.of(this, (SMGSymbolicValue) object_edge.getValue());
      }
    }

    if (heap.isCoveredByNullifiedBlocks(edge)) {
      return SMGValueAndState.of(this, SMGZeroValue.INSTANCE);
    }

    performConsistencyCheck(SMGRuntimeCheck.HALF);
    return SMGValueAndState.of(this);
  }

  @Override
  public SMGState withInvalidRead() {
    SMGState smgState = new SMGState(this, Property.INVALID_READ);
    smgState.moveCurrentChainToInvalidChain();
    return smgState;
  }

  /**
   * Write a value into a field (offset, type) of an Object.
   * Additionally, this method writes a points-to edge into the
   * SMG, if the given symbolic value points to an address, and
   *
   *
   * @param pObject SMGObject representing the memory the field belongs to.
   * @param pOffset offset of field written into.
   * @param pType type of field written into.
   * @param pValue value to be written into field.
   * @return the edge and the new state (may be this state)
   */
  public SMGStateEdgePair writeValue(SMGObject pObject, long pOffset,
      CType pType, SMGSymbolicValue pValue) throws SMGInconsistentException {

    SMGSymbolicValue value;

    // If the value is not yet known by the SMG
    // create a unconstrained new symbolic value
    if (pValue.isUnknown()) {
      value = SMGKnownSymValue.of();
    } else {
      value = pValue;
    }

    // If the value represents an address, and the address is known,
    // add the necessary points-To edge.
    if (pValue instanceof SMGAddressValue) {
      SMGAddress address = ((SMGAddressValue) pValue).getAddress();

      if (!address.isUnknown()) {
        addPointsToEdge(
            address.getObject(),
            address.getOffset().getAsLong(),
            value);
      }
    }

    return writeValue0(pObject, pOffset, pType, value);
  }

  public void addPointsToEdge(SMGObject pObject, long pOffset, SMGValue pValue) {
    heap.addValue(pValue);
    heap.addPointsToEdge(new SMGEdgePointsTo(pValue, pObject, pOffset));
  }

  /**
   * Write a value into a field (offset, type) of an Object.
   *
   * @param pObject SMGObject representing the memory the field belongs to.
   * @param pOffset offset of field written into.
   * @param pType type of field written into.
   * @param pValue value to be written into field.
   */
  private SMGStateEdgePair writeValue0(
      SMGObject pObject, long pOffset, CType pType, SMGValue pValue)
      throws SMGInconsistentException {
    // vgl Algorithm 1 Byte-Precise Verification of Low-Level List Manipulation FIT-TR-2012-04

    if (!heap.isObjectValid(pObject) && !heap.isObjectExternallyAllocated(pObject)) {
      // Attempt to write to invalid object
      SMGState newState = withInvalidWrite();
      newState
          .withErrorDescription("Attempt to write to deallocated object")
          .addInvalidObject(pObject);
      return new SMGStateEdgePair(newState);
    }

    SMGEdgeHasValue new_edge = new SMGEdgeHasValue(pType, pOffset, pObject, pValue);

    // Check if the edge is  not present already
    SMGEdgeHasValueFilter filter = SMGEdgeHasValueFilter.objectFilter(pObject);

    Set<SMGEdgeHasValue> edges = heap.getHVEdges(filter);
    if (edges.contains(new_edge)) {
      performConsistencyCheck(SMGRuntimeCheck.HALF);
      return new SMGStateEdgePair(this, new_edge);
    }

    heap.addValue(pValue);

    Set<SMGEdgeHasValue> overlappingZeroEdges = new HashSet<>();

    /* We need to remove all non-zero overlapping edges
     * and remember all overlapping zero edges to shrink them later
     */
    for (SMGEdgeHasValue hv : edges) {

      boolean hvEdgeOverlaps = new_edge.overlapsWith(hv, heap.getMachineModel());
      boolean hvEdgeIsZero = hv.getValue() == SMGZeroValue.INSTANCE;

      if (hvEdgeOverlaps) {
        if (hvEdgeIsZero) {
          overlappingZeroEdges.add(hv);
        } else {
          heap.removeHasValueEdge(hv);
        }
      }
    }

    shrinkOverlappingZeroEdges(new_edge, overlappingZeroEdges);

    heap.addHasValueEdge(new_edge);
    performConsistencyCheck(SMGRuntimeCheck.HALF);

    return new SMGStateEdgePair(this, new_edge);
  }

  @Override
  public boolean isBlockEnded() {
    return blockEnded;
  }

  public static class SMGStateEdgePair {

    private final SMGState smgState;
    private final SMGEdgeHasValue edge;

    private SMGStateEdgePair(SMGState pState, SMGEdgeHasValue pEdge) {
      smgState = pState;
      edge = pEdge;
    }

    private SMGStateEdgePair(SMGState pNewState) {
      smgState = pNewState;
      edge = null;
    }

    public boolean smgStateHasNewEdge() {
      return edge != null;
    }

    public SMGEdgeHasValue getNewEdge() {
      return edge;
    }

    public SMGState getState() {
      return smgState;
    }
  }

  private void shrinkOverlappingZeroEdges(SMGEdgeHasValue pNew_edge,
      Set<SMGEdgeHasValue> pOverlappingZeroEdges) {

    SMGObject object = pNew_edge.getObject();
    long offset = pNew_edge.getOffset();

    MachineModel maModel = heap.getMachineModel();
    int sizeOfType = pNew_edge.getSizeInBits(maModel);

    // Shrink overlapping zero edges
    for (SMGEdgeHasValue zeroEdge : pOverlappingZeroEdges) {
      heap.removeHasValueEdge(zeroEdge);

      long zeroEdgeOffset = zeroEdge.getOffset();

      long offset2 = offset + sizeOfType;
      long zeroEdgeOffset2 = zeroEdgeOffset + zeroEdge.getSizeInBits(maModel);

      if (zeroEdgeOffset < offset) {
        SMGEdgeHasValue newZeroEdge =
            new SMGEdgeHasValue(
                Math.toIntExact(offset - zeroEdgeOffset),
                zeroEdgeOffset,
                object,
                SMGZeroValue.INSTANCE);
        heap.addHasValueEdge(newZeroEdge);
      }

      if (offset2 < zeroEdgeOffset2) {
        SMGEdgeHasValue newZeroEdge =
            new SMGEdgeHasValue(
                Math.toIntExact(zeroEdgeOffset2 - offset2), offset2, object, SMGZeroValue.INSTANCE);
        heap.addHasValueEdge(newZeroEdge);
      }
    }
  }

  @Override
  public SMGState withInvalidWrite() {
    SMGState smgState = new SMGState(this, Property.INVALID_WRITE);
    smgState.moveCurrentChainToInvalidChain();
    return smgState;
  }

  /**
   * Computes the join of this abstract State and the reached abstract State,
   * or returns the reached state, if no join is defined.
   *
   * @param reachedState the abstract state this state will be joined to.
   * @return the join of the two states or reached state.
   * @throws SMGInconsistentException inconsistent smgs while
   */
  @Override
  public UnmodifiableSMGState join(UnmodifiableSMGState reachedState)
      throws SMGInconsistentException {
    // Not necessary if merge_SEP and stop_SEP is used.

    SMGJoin join = new SMGJoin(this.heap, reachedState.getHeap(), this, reachedState);

    if(join.getStatus() != SMGJoinStatus.INCOMPARABLE) {
      return reachedState;
    }

    if (!join.isDefined()) {
      return reachedState;
    }

    CLangSMG destHeap = join.getJointSMG();

    // join explicit values
    Map<SMGKnownSymbolicValue, SMGKnownExpValue> mergedExplicitValues = new HashMap<>();
    for (Entry<SMGKnownSymbolicValue, SMGKnownExpValue> entry : explicitValues.entrySet()) {
      if (destHeap.getValues().contains(entry.getKey())) {
        mergedExplicitValues.put(entry.getKey(), entry.getValue());
      }
    }
    for (Entry<SMGKnownSymbolicValue, SMGKnownExpValue> entry : reachedState.getExplicitValues()) {
      mergedExplicitValues.put(entry.getKey(), entry.getValue());
    }

    return new SMGState(logger, options, destHeap, predecessorId, mergedExplicitValues);
  }

  /**
   * Computes whether this abstract state is covered by the given abstract state.
   * A state is covered by another state, if the set of concrete states
   * a state represents is a subset of the set of concrete states the other
   * state represents.
   *
   *
   * @param reachedState already reached state, that may cover this state already.
   * @return True, if this state is covered by the given state, false otherwise.
   */
  @Override
  public boolean isLessOrEqual(UnmodifiableSMGState reachedState) throws SMGInconsistentException {

    if(!getErrorPredicateRelation().isEmpty() || !reachedState.getErrorPredicateRelation().isEmpty()) {
      return false;
    }

    if (options.isHeapAbstractionEnabled()) {
      SMGJoin join = new SMGJoin(heap, reachedState.getHeap(), this, reachedState);

      if (!join.isDefined()) {
        return false;
      }

      SMGJoinStatus jss = join.getStatus();
      if (jss != SMGJoinStatus.EQUAL && jss != SMGJoinStatus.RIGHT_ENTAIL) {
        return false;
      }

      // Only stop if either reached has memleak or this state has no memleak
      // to avoid losing memleak information.
      SMGState s1 = reachedState.copyOf();
      SMGState s2 = this.copyOf();
      s1.pruneUnreachable();
      s2.pruneUnreachable();
      logger.log(Level.ALL, this.getId(), " is Less or Equal ", reachedState.getId());
      return s1.errorInfo.hasMemoryLeak() == s2.errorInfo.hasMemoryLeak();

    } else {
      return SMGIsLessOrEqual.isLessOrEqual(reachedState.getHeap(), heap);
    }
  }

  @Override
  public String getCPAName() {
    return "SMGCPA";
  }

  @Override
  public boolean checkProperty(String pProperty) throws InvalidQueryException {
    // SMG Properties:
    // has-leaks:boolean

    switch (pProperty) {
      case HAS_LEAKS:
        if (errorInfo.hasMemoryLeak()) {
          //TODO: Give more information
          issueMemoryError("Memory leak found", false);
          return true;
        }
        return false;
      case HAS_INVALID_WRITES:
        if (errorInfo.isInvalidWrite()) {
          //TODO: Give more information
          issueMemoryError("Invalid write found", true);
          return true;
        }
        return false;
      case HAS_INVALID_READS:
        if (errorInfo.isInvalidRead()) {
          //TODO: Give more information
          issueMemoryError("Invalid read found", true);
          return true;
        }
        return false;
      case HAS_INVALID_FREES:
        if (errorInfo.isInvalidFree()) {
          //TODO: Give more information
          issueMemoryError("Invalid free found", true);
          return true;
        }
        return false;
      default:
        throw new InvalidQueryException("Query '" + pProperty + "' is invalid.");
    }
  }

  public void addGlobalObject(SMGRegion newObject) {
    heap.addGlobalObject(newObject);
  }

  /** memory allocated in the heap has to be freed by the user, otherwise this is a memory-leak. */
  public SMGAddressValue addNewHeapAllocation(int pSize, String pLabel)
      throws SMGInconsistentException {
    return addHeapAllocation(pLabel, pSize, 0, false);
  }

  /** memory externally allocated could be freed by the user */
  public SMGAddressValue addExternalAllocation(String pLabel) throws SMGInconsistentException {
    return addHeapAllocation(
        pLabel, options.getExternalAllocationSize(), options.getExternalAllocationSize() / 2, true);
  }

  private SMGAddressValue addHeapAllocation(String label, int size, int offset, boolean external)
      throws SMGInconsistentException {
    SMGRegion new_object = new SMGRegion(size, label);
    SMGKnownSymbolicValue new_value = SMGKnownSymValue.of();
    heap.addHeapObject(new_object);
    heap.addValue(new_value);
    SMGEdgePointsTo pointsTo = new SMGEdgePointsTo(new_value, new_object, offset);
    heap.addPointsToEdge(pointsTo);
    heap.setExternallyAllocatedFlag(new_object, external);
    performConsistencyCheck(SMGRuntimeCheck.HALF);
    return SMGKnownAddressValue.valueOf(pointsTo);
  }

  public void setExternallyAllocatedFlag(SMGObject pObject) {
    heap.setExternallyAllocatedFlag(pObject, true);
  }

  /** memory allocated on the stack is automatically freed when leaving the current function scope */
  public SMGAddressValue addNewStackAllocation(int pSize, String pLabel)
      throws SMGInconsistentException {
    SMGRegion new_object = new SMGRegion(pSize, pLabel);
    SMGKnownSymbolicValue new_value = SMGKnownSymValue.of();
    heap.addStackObject(new_object);
    heap.addValue(new_value);
    SMGEdgePointsTo pointsTo = new SMGEdgePointsTo(new_value, new_object, 0);
    heap.addPointsToEdge(pointsTo);
    performConsistencyCheck(SMGRuntimeCheck.HALF);
    return SMGKnownAddressValue.valueOf(pointsTo);
  }

  /** Sets a flag indicating this SMGState is a successor over an edge causing a memory leak. */
  public void setMemLeak(String errorMsg, Collection<SMGObject> pUnreachableObjects) {
    errorInfo =
        errorInfo
            .withProperty(Property.INVALID_HEAP)
            .withErrorMessage(errorMsg)
            .withInvalidObjects(pUnreachableObjects);
  }

  @Override
  @Nullable
  public SMGSymbolicValue getAddress(SMGObject memory, long offset, SMGTargetSpecifier tg) {

    SMGEdgePointsToFilter filter =
        SMGEdgePointsToFilter.targetObjectFilter(memory).filterAtTargetOffset(offset)
            .filterByTargetSpecifier(tg);

    Set<SMGEdgePointsTo> edges = heap.getPtEdges(filter);

    if (edges.isEmpty()) {
      return null;
    } else {
      return (SMGSymbolicValue) Iterables.getOnlyElement(edges).getValue();
    }
  }

  /**
   * This method simulates a free invocation. It checks, whether the call is valid, and invalidates
   * the Memory the given address points to. The address (address, offset, smgObject) is the
   * argument of the free invocation. It does not need to be part of the SMG.
   *
   * @param offset The offset of the address relative to the beginning of smgObject.
   * @param smgObject The memory the given Address belongs to.
   * @return returns a possible new State
   */
  protected SMGState free(Integer offset, SMGObject smgObject) throws SMGInconsistentException {

    if (!heap.isHeapObject(smgObject) && !heap.isObjectExternallyAllocated(smgObject)) {
      // You may not free any objects not on the heap.
      SMGState newState =
          withInvalidFree().withErrorDescription("Invalid free of unallocated object is found");
      newState.addInvalidObject(smgObject);
      return newState;
    }

    if (!heap.isObjectValid(smgObject)) {
      // you may not invoke free multiple times on
      // the same object

      SMGState newState = withInvalidFree().withErrorDescription("Double free is found");
      newState.addInvalidObject(smgObject);
      return newState;
    }

    if (!(offset == 0) && !heap.isObjectExternallyAllocated(smgObject)) {
      // you may not invoke free on any address that you
      // didn't get through a malloc invocation.
      // TODO: externally allocated memory could be freed partially

      SMGState newState = withInvalidFree();
      newState.addInvalidObject(smgObject);
      final String description;
      if (offset % 8 != 0) {
        description = "Invalid free at " + offset + " bit offset from allocated is found";
      } else {
        description = "Invalid free at " + offset / 8 + " byte offset from allocated is found";
      }
      return newState.withErrorDescription(description);
    }

    heap.setValidity(smgObject, false);
    heap.setExternallyAllocatedFlag(smgObject, false);
    SMGEdgeHasValueFilter filter = SMGEdgeHasValueFilter.objectFilter(smgObject);

    for (SMGEdgeHasValue edge : heap.getHVEdges(filter)) {
      heap.removeHasValueEdge(edge);
    }

    performConsistencyCheck(SMGRuntimeCheck.HALF);
    return this;
  }

  @Override
  public Collection<Object> getInvalidChain() {
    return Collections.unmodifiableList(errorInfo.getInvalidChain());
  }

  public void addInvalidObject(SMGObject pSmgObject) {
    errorInfo = errorInfo.withInvalidObject(pSmgObject);
  }

  public void addElementToCurrentChain(Object elem) {
    // Avoid to add Null element
    if (elem instanceof SMGValue && ((SMGValue) elem).isZero()) {
      return;
    }
    errorInfo = errorInfo.withObject(elem);
  }

  @Override
  public Collection<Object> getCurrentChain() {
    return Collections.unmodifiableList(errorInfo.getCurrentChain());
  }

  protected void cleanCurrentChain() {
    errorInfo = errorInfo.withClearChain();
  }

  private void moveCurrentChainToInvalidChain() {
    errorInfo = errorInfo.moveCurrentChainToInvalidChain();
  }

  /**
   * Drop the stack frame representing the stack of
   * the function with the given name
   */
  public void dropStackFrame() throws SMGInconsistentException {
    heap.dropStackFrame();
    performConsistencyCheck(SMGRuntimeCheck.FULL);
  }

  public void pruneUnreachable() throws SMGInconsistentException {
    Set<SMGObject> unreachable = heap.pruneUnreachable();
    if (!unreachable.isEmpty()) {
      setMemLeak("Memory leak is detected", unreachable);
    }
    //TODO: Explicit values pruning
    performConsistencyCheck(SMGRuntimeCheck.HALF);
  }

  @Override
  public SMGState withInvalidFree() {
    return new SMGState(this, Property.INVALID_FREE);
  }

  @VisibleForTesting
  Set<SMGEdgeHasValue> getHVEdges(SMGEdgeHasValueFilter pFilter) {
    return heap.getHVEdges(pFilter);
  }

  /**
   * Copys (shallow) the hv-edges of source in the given source range
   * to the target at the given target offset. Note that the source
   * range (pSourceLastCopyBitOffset - pSourceOffset) has to fit into
   * the target range ( size of pTarget - pTargetOffset).
   * Also, pSourceOffset has to be less or equal to the size
   * of the source Object.
   *
   * This method is mainly used to assign struct variables.
   *
   * @param pSource the SMGObject providing the hv-edges
   * @param pTarget the target of the copy process
   * @param pTargetOffset begin the copy of source at this offset
   * @param pSourceLastCopyBitOffset the size of the copy of source (not the size of the copy, but the size to the last bit of the source which should be copied).
   * @param pSourceOffset insert the copy of source into target at this offset
   * @throws SMGInconsistentException thrown if the copying leads to an inconsistent SMG.
   */
  public SMGState copy(SMGObject pSource, SMGObject pTarget, long pSourceOffset,
      long pSourceLastCopyBitOffset, long pTargetOffset) throws SMGInconsistentException {

    SMGState newSMGState = this;

    long copyRange = pSourceLastCopyBitOffset - pSourceOffset;

    assert pSource.getSize() >= pSourceLastCopyBitOffset;
    assert pSourceOffset >= 0;
    assert pTargetOffset >= 0;
    assert copyRange >= 0;
    assert copyRange <= pTarget.getSize();

    // If copy range is 0, do nothing
    if (copyRange == 0) { return newSMGState; }

    long targetRangeSize = pTargetOffset + copyRange;

    SMGEdgeHasValueFilter filterSource = SMGEdgeHasValueFilter.objectFilter(pSource);
    SMGEdgeHasValueFilter filterTarget = SMGEdgeHasValueFilter.objectFilter(pTarget);

    // Remove all Target edges in range
    for (SMGEdgeHasValue edge : getHVEdges(filterTarget)) {
      if (edge.overlapsWith(pTargetOffset, targetRangeSize, heap.getMachineModel())) {
        boolean hvEdgeIsZero = edge.getValue() == SMGZeroValue.INSTANCE;
        heap.removeHasValueEdge(edge);
        if (hvEdgeIsZero) {
          SMGObject object = edge.getObject();

          MachineModel maModel = heap.getMachineModel();

          // Shrink overlapping zero edge
          long zeroEdgeOffset = edge.getOffset();

          long zeroEdgeOffset2 = zeroEdgeOffset + edge.getSizeInBits(maModel);

          if (zeroEdgeOffset < pTargetOffset) {
            SMGEdgeHasValue newZeroEdge =
                new SMGEdgeHasValue(
                    Math.toIntExact(pTargetOffset - zeroEdgeOffset),
                    zeroEdgeOffset,
                    object,
                    SMGZeroValue.INSTANCE);
            heap.addHasValueEdge(newZeroEdge);
          }

          if (targetRangeSize < zeroEdgeOffset2) {
            SMGEdgeHasValue newZeroEdge =
                new SMGEdgeHasValue(
                    Math.toIntExact(zeroEdgeOffset2 - targetRangeSize),
                    targetRangeSize,
                    object,
                    SMGZeroValue.INSTANCE);
            heap.addHasValueEdge(newZeroEdge);
          }
        }
      }
    }

    // Copy all Source edges
    Set<SMGEdgeHasValue> sourceEdges = getHVEdges(filterSource);

    // Shift the source edge offset depending on the target range offset
    long copyShift = pTargetOffset - pSourceOffset;

    for (SMGEdgeHasValue edge : sourceEdges) {
      if (edge.overlapsWith(pSourceOffset, pSourceLastCopyBitOffset, heap.getMachineModel())) {
        long offset = edge.getOffset() + copyShift;
        newSMGState = writeValue0(pTarget, offset, edge.getType(), edge.getValue()).getState();
      }
    }

    performConsistencyCheck(SMGRuntimeCheck.FULL);
    // TODO Why do I do this here?
    Set<SMGObject> unreachable = heap.pruneUnreachable();
    if (!unreachable.isEmpty()) {
      setMemLeak("Memory leak is detected", unreachable);
    }
    performConsistencyCheck(SMGRuntimeCheck.FULL);
    return newSMGState;
  }

  @Override
  public SMGState withUnknownDereference() {
    // TODO: accurate define SMG change on unknown dereference with predicate knowledge
    if (options.isHandleUnknownDereferenceAsSafe() && isTrackPredicatesEnabled()) {
      // doesn't stop analysis on unknown dereference
      return this;
    }

    // TODO: This can actually be an invalid read too
    //      The flagging mechanism should be improved
    SMGState smgState =
        new SMGState(this, Property.INVALID_WRITE).withErrorDescription("Unknown dereference");
    return smgState;
  }

  public void identifyEqualValues(SMGKnownSymbolicValue pKnownVal1, SMGKnownSymbolicValue pKnownVal2) {

    assert !isInNeq(pKnownVal1, pKnownVal2);
    assert !(explicitValues.get(pKnownVal1) != null &&
        explicitValues.get(pKnownVal1).equals(explicitValues.get(pKnownVal2)));

    // Avoid remove NULL value on merge
    if (pKnownVal2.isZero()) {
      SMGKnownSymbolicValue tmp = pKnownVal1;
      pKnownVal1 = pKnownVal2;
      pKnownVal2 = tmp;
    }

    heap.replaceValue(pKnownVal1, pKnownVal2);
    SMGKnownExpValue expVal = explicitValues.remove(pKnownVal2);
    if (expVal != null) {
      explicitValues.put(pKnownVal1, expVal);
    }
  }

  public void identifyNonEqualValues(SMGKnownSymbolicValue pKnownVal1, SMGKnownSymbolicValue pKnownVal2) {
    heap.addNeqRelation(pKnownVal1, pKnownVal2);
  }

  @Override
  public boolean isTrackPredicatesEnabled() {
    return options.trackPredicates();
  }

  public void addPredicateRelation(SMGSymbolicValue pV1, int pCType1,
                                   SMGSymbolicValue pV2, int pCType2,
                                   BinaryOperator pOp, CFAEdge pEdge) {
  if (isTrackPredicatesEnabled() && pEdge instanceof CAssumeEdge) {
    BinaryOperator temp;
    if (((CAssumeEdge) pEdge).getTruthAssumption()) {
      temp = pOp;
    } else {
      temp = pOp.getOppositLogicalOperator();
    }
      logger.logf(
          Level.FINER, "SymValue1 %s %s SymValue2 %s AddPredicate: %s", pV1, temp, pV2, pEdge);
      getPathPredicateRelation().addRelation(pV1, pCType1, pV2, pCType2, temp);
  }
}

  public void addPredicateRelation(SMGSymbolicValue pV1, int pCType1,
                                   SMGExplicitValue pV2, int pCType2,
                                   BinaryOperator pOp, CFAEdge pEdge) {
    if (isTrackPredicatesEnabled() && pEdge instanceof CAssumeEdge) {
      BinaryOperator temp;
      if (((CAssumeEdge) pEdge).getTruthAssumption()) {
        temp = pOp;
      } else {
        temp = pOp.getOppositLogicalOperator();
      }
      logger.logf(
          Level.FINER, "SymValue %s %s; ExplValue %s; AddPredicate: %s", pV1, temp, pV2, pEdge);
      getPathPredicateRelation().addExplicitRelation(pV1, pCType1, pV2, pCType2, temp);
    }
  }

  @Override
  public PredRelation getPathPredicateRelation() {
    return heap.getPathPredicateRelation();
  }

  public void addErrorPredicate(SMGSymbolicValue pSymbolicValue, Integer pCType1,
                                SMGExplicitValue pExplicitValue, Integer pCType2,
                                CFAEdge pEdge) {
    if (isTrackPredicatesEnabled()) {
      logger.log(Level.FINER, "Add Error Predicate: SymValue  ",
          pSymbolicValue, " ; ExplValue", " ",
          pExplicitValue, "; on edge: ", pEdge);
      getErrorPredicateRelation()
          .addExplicitRelation(
              pSymbolicValue, pCType1, pExplicitValue, pCType2, BinaryOperator.GREATER_THAN);
    }
  }

  @Override
  public PredRelation getErrorPredicateRelation() {
    return heap.getErrorPredicateRelation();
  }

  public SMGState resetErrorRelation() {
    SMGState newState = copyOf();
    newState.heap.resetErrorRelation();
    return newState;
  }

  /**
   * @param pKey the key.
   * @param pValue the value.
   * @return explicit value merged with pKey, or Null if not merged
   */
  public SMGKnownSymbolicValue putExplicit(SMGKnownSymbolicValue pKey, SMGKnownExpValue pValue) {
    Preconditions.checkNotNull(pKey);
    Preconditions.checkNotNull(pValue);

    if (explicitValues.inverse().containsKey(pValue)) {
      SMGKnownSymbolicValue symValue = explicitValues.inverse().get(pValue);

      if (!pKey.equals(symValue)) {
        explicitValues.remove(symValue);
        if (symValue.isZero()) { // swap values, we prefer ZERO in the SMG.
          heap.replaceValue(symValue, pKey);
        } else {
          heap.replaceValue(pKey, symValue);
        }
        explicitValues.put(pKey, pValue);
        return symValue;
      }

      return null;
    }

    explicitValues.put(pKey, pValue);
    return null;
  }

  @Deprecated // unused
  public void clearExplicit(SMGKnownSymbolicValue pKey) {
    explicitValues.remove(pKey);
  }

  @Override
  public boolean isExplicit(SMGKnownSymbolicValue value) {
    return explicitValues.containsKey(value);
  }

  @Override
  @Nullable
  public SMGExplicitValue getExplicit(SMGKnownSymbolicValue pKey) {
    return explicitValues.get(pKey);
  }

  enum Property {
    INVALID_READ,
    INVALID_WRITE,
    INVALID_FREE,
    INVALID_HEAP
  }

  @Override
  public boolean hasMemoryErrors() {
    return errorInfo.hasMemoryErrors();
  }

  @Override
  public boolean hasMemoryLeaks() {
    return errorInfo.hasMemoryLeak();
  }

  @Override
  public boolean isInNeq(SMGSymbolicValue pValue1, SMGSymbolicValue pValue2) {

    if (pValue1.isUnknown() || pValue2.isUnknown()) {
      return false;
    } else {
      return heap.haveNeqRelation(pValue1, pValue2);
    }
  }

  @Override
  public SMGObject getObjectForFunction(CFunctionDeclaration pDeclaration) {

    /* Treat functions as global objects with unknown memory size.
     * Only write them into the smg when necessary*/
    String functionQualifiedSMGName = getUniqueFunctionName(pDeclaration);

    return heap.getObjectForVisibleVariable(functionQualifiedSMGName);
  }

  public SMGObject createObjectForFunction(CFunctionDeclaration pDeclaration)
      throws SMGInconsistentException {

    /* Treat functions as global variable with unknown memory size.
     * Only write them into the smg when necessary*/
    String functionQualifiedSMGName = getUniqueFunctionName(pDeclaration);

    assert heap.getObjectForVisibleVariable(functionQualifiedSMGName) == null;

    return addGlobalVariable(0, functionQualifiedSMGName);
  }

  private static String getUniqueFunctionName(CFunctionDeclaration pDeclaration) {

    StringBuilder functionName = new StringBuilder(pDeclaration.getQualifiedName());

    for (CParameterDeclaration parameterDcl : pDeclaration.getParameters()) {
      functionName.append("_");
      functionName.append(parameterDcl.toASTString().replace("*", "_").replace(" ", "_"));
    }

    return "__" + functionName;
  }

  /**
   * Try to abstract heap segments meaningfully.
   * @throws SMGInconsistentException Join lead to inconsistent smg.
   */
  public void executeHeapAbstraction() throws SMGInconsistentException {
    SMGAbstractionManager manager = new SMGAbstractionManager(logger, heap, this);
    manager.execute();
    performConsistencyCheck(SMGRuntimeCheck.HALF);
  }

  public boolean executeHeapAbstraction(Set<SMGAbstractionBlock> blocks,
      boolean usesHeapInterpoaltion)
      throws SMGInconsistentException {

    boolean change;

    if (usesHeapInterpoaltion) {
      SMGAbstractionManager manager =
          new SMGAbstractionManager(logger, heap, this, blocks, 2, 2, 2);
      change = manager.execute();
    } else {
      SMGAbstractionManager manager = new SMGAbstractionManager(logger, heap, this, blocks);
      change = manager.execute();
    }

    performConsistencyCheck(SMGRuntimeCheck.HALF);
    return change;
  }

  public Optional<SMGEdgeHasValue> forget(SMGMemoryPath location) {
    return heap.forget(location);
  }

  public void clearValues() {
    heap.clearValues();
  }

  public void writeUnknownValueInUnknownField(SMGObject target) {
    heap.getHVEdges(SMGEdgeHasValueFilter.objectFilter(target)).forEach(heap::removeHasValueEdge);
  }

  public void clearObjects() {
    heap.clearObjects();
  }

  public SMGAbstractionCandidate executeHeapAbstractionOneStep(Set<SMGAbstractionBlock> pResult)
      throws SMGInconsistentException {
    SMGAbstractionManager manager = new SMGAbstractionManager(logger, heap, this, pResult, 2, 2, 2);
    SMGAbstractionCandidate result = manager.executeOneStep();
    performConsistencyCheck(SMGRuntimeCheck.HALF);
    return result;
  }

  public boolean forgetNonTrackedHve(Set<SMGMemoryPath> pMempaths) {

    Set<SMGEdgeHasValue> trackkedHves = new HashSet<>(pMempaths.size());
    Set<SMGValue> trackedValues = new HashSet<>();
    trackedValues.add(SMGZeroValue.INSTANCE);

    for (SMGMemoryPath path : pMempaths) {
      Optional<SMGEdgeHasValue> hve = heap.getHVEdgeFromMemoryLocation(path);

      if (hve.isPresent()) {
        trackkedHves.add(hve.get());
        trackedValues.add(hve.get().getValue());
      }
    }

    boolean change = false;

    for (SMGEdgeHasValue edge : heap.getHVEdges()) {

      //TODO Robust heap abstraction?
      if (edge.getObject().isAbstract()) {
        trackedValues.add(edge.getValue());
        continue;
      }

      if (!trackkedHves.contains(edge)) {
        heap.removeHasValueEdge(edge);
        change = true;
      }
    }

    if (change) {
      for (SMGValue value : ImmutableSet.copyOf(heap.getValues())) {
        if (!trackedValues.contains(value)) {
          heap.removePointsToEdge(value);
          heap.removeValue(value);
          change = true;
        }
      }
    }

    return change;
  }

  public void forget(SMGEdgeHasValue pHveEdge) {
    heap.removeHasValueEdge(pHveEdge);
  }

  public void remember(SMGEdgeHasValue pHveEdge) {
    heap.addHasValueEdge(pHveEdge);
  }

  @Override
  public Map<MemoryLocation, SMGRegion> getStackVariables() {

    Map<MemoryLocation, SMGRegion> result = new HashMap<>();

    for (Entry<String, SMGRegion> variableEntry : heap.getGlobalObjects().entrySet()) {
      String variableName = variableEntry.getKey();
      SMGRegion reg = variableEntry.getValue();
      result.put(MemoryLocation.valueOf(variableName), reg);
    }

    for (CLangStackFrame frame : heap.getStackFrames()) {
      String functionName = frame.getFunctionDeclaration().getName();

      for (Entry<String, SMGRegion> variableEntry : frame.getVariables().entrySet()) {
        String variableName = variableEntry.getKey();
        SMGRegion reg = variableEntry.getValue();
        result.put(MemoryLocation.valueOf(functionName, variableName), reg);
      }
    }

    return result;
  }

  public boolean forgetNonTrackedStackVariables(Set<MemoryLocation> pTrackedStackVariables) {

    boolean change = false;

    for (String variable : heap.getGlobalObjects().keySet()) {
      MemoryLocation globalVar = MemoryLocation.valueOf(variable);
      if (!pTrackedStackVariables.contains(globalVar)) {
        heap.removeGlobalVariableAndEdges(variable);
        change = true;
      }
    }

    for (CLangStackFrame frame : heap.getStackFrames()) {
      String functionName = frame.getFunctionDeclaration().getName();
      for (String variable : frame.getVariables().keySet()) {
        MemoryLocation var = MemoryLocation.valueOf(functionName, variable);
        if (!pTrackedStackVariables.contains(var)) {
          heap.forgetFunctionStackVariable(var, false);
          change = true;
        }
      }
    }

    return change;
  }

  public SMGStateInformation forgetStackVariable(MemoryLocation pMemoryLocation) {
    return heap.forgetStackVariable(pMemoryLocation);
  }

  public void remember(MemoryLocation pMemoryLocation, SMGRegion pRegion,
      SMGStateInformation pInfo) {
    heap.remember(pMemoryLocation, pRegion, pInfo);
  }

  public void unknownWrite() {
    if (!isTrackPredicatesEnabled()) {
      heap.unknownWrite();
    }
  }

  @Override
  public String toDOTLabel() {
    return toString();
  }

  @Override
  public boolean shouldBeHighlighted() {
    return false;
  }

  @Override
  public UnmodifiableCLangSMG getHeap() {
    return heap;
  }

  @Override
  public Set<Entry<SMGKnownSymbolicValue, SMGKnownExpValue>> getExplicitValues() {
    return Collections.unmodifiableSet(explicitValues.entrySet());
  }
}
