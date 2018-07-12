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

import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.FileLocation;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cfa.types.c.CBasicType;
import org.sosy_lab.cpachecker.cfa.types.c.CFunctionType;
import org.sosy_lab.cpachecker.cfa.types.c.CNumericTypes;
import org.sosy_lab.cpachecker.cfa.types.c.CPointerType;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.cpa.smg.evaluator.SMGAbstractObjectAndState.SMGAddressValueAndState;
import org.sosy_lab.cpachecker.cpa.smg.graphs.CLangSMG;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgeHasValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgeHasValueFilter;
import org.sosy_lab.cpachecker.cpa.smg.graphs.edge.SMGEdgePointsTo;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGRegion;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.dll.SMGDoublyLinkedList;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGAddressValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownSymValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGKnownSymbolicValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGUnknownValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGValue;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGZeroValue;

public class SMGStateTest {
  static private final  LogManager logger = LogManager.createTestLogManager();

  private SMGState consistent_state;
  private SMGState inconsistent_state;

  static private final CType mockType16b = TypeUtils.createTypeWithLength(16);
  static private final CType mockType8b = TypeUtils.createTypeWithLength(8);

  private final CFunctionType functionType = CFunctionType.functionTypeWithReturnType(CNumericTypes.UNSIGNED_LONG_INT);
  private final CFunctionDeclaration functionDeclaration3 =
      new CFunctionDeclaration(FileLocation.DUMMY, functionType, "main", ImmutableList.of());
  private CSimpleType unspecifiedType = new CSimpleType(false, false, CBasicType.UNSPECIFIED, false, false, true, false, false, false, false);
  private CType pointerType = new CPointerType(false, false, unspecifiedType);

  @Test
  public void abstractionTest() throws SMGInconsistentException, InvalidConfigurationException {

    CLangSMG smg1 = new CLangSMG(MachineModel.LINUX32);

    smg1.addStackFrame(functionDeclaration3);

    for (int i = 0; i < 20; i++) {
      SMGCPA.getNewValue();
    }

    SMGValue value5 = SMGKnownSymValue.valueOf(5);
    SMGValue value6 = SMGKnownSymValue.valueOf(6);
    SMGValue value7 = SMGKnownSymValue.valueOf(7);
    SMGValue value8 = SMGKnownSymValue.valueOf(8);
    SMGValue value9 = SMGKnownSymValue.valueOf(9);
    SMGValue value10 = SMGKnownSymValue.valueOf(10);

    SMGRegion l1 = new SMGRegion(96, "l1");
    SMGRegion l2 = new SMGRegion(96, "l2");
    SMGRegion l3 = new SMGRegion(96, "l3");
    SMGRegion l4 = new SMGRegion(96, "l4");
    SMGRegion l5 = new SMGRegion(96, "l5");

    SMGEdgeHasValue l1fn = new SMGEdgeHasValue(pointerType, 0, l1, value7);
    SMGEdgeHasValue l2fn = new SMGEdgeHasValue(pointerType, 0, l2, value8);
    SMGEdgeHasValue l3fn = new SMGEdgeHasValue(pointerType, 0, l3, value9);
    SMGEdgeHasValue l4fn = new SMGEdgeHasValue(pointerType, 0, l4, value10);
    SMGEdgeHasValue l5fn = new SMGEdgeHasValue(pointerType, 0, l5, value5);

    SMGEdgeHasValue l1fp = new SMGEdgeHasValue(pointerType, 4, l1, value5);
    SMGEdgeHasValue l2fp = new SMGEdgeHasValue(pointerType, 4, l2, value6);
    SMGEdgeHasValue l3fp = new SMGEdgeHasValue(pointerType, 4, l3, value7);
    SMGEdgeHasValue l4fp = new SMGEdgeHasValue(pointerType, 4, l4, value8);
    SMGEdgeHasValue l5fp = new SMGEdgeHasValue(pointerType, 4, l5, value9);

    SMGEdgePointsTo l1t = new SMGEdgePointsTo(value6, l1, 0);
    SMGEdgePointsTo l2t = new SMGEdgePointsTo(value7, l2, 0);
    SMGEdgePointsTo l3t = new SMGEdgePointsTo(value8, l3, 0);
    SMGEdgePointsTo l4t = new SMGEdgePointsTo(value9, l4, 0);
    SMGEdgePointsTo l5t = new SMGEdgePointsTo(value10, l5, 0);

    smg1.addHeapObject(l1);
    smg1.addHeapObject(l2);
    smg1.addHeapObject(l3);
    smg1.addHeapObject(l4);
    smg1.addHeapObject(l5);

    smg1.addValue(value5);
    smg1.addValue(value6);
    smg1.addValue(value7);
    smg1.addValue(value8);
    smg1.addValue(value9);
    smg1.addValue(value10);

    smg1.addHasValueEdge(l1fn);
    smg1.addHasValueEdge(l2fn);
    smg1.addHasValueEdge(l3fn);
    smg1.addHasValueEdge(l4fn);
    smg1.addHasValueEdge(l5fn);

    smg1.addHasValueEdge(l1fp);
    smg1.addHasValueEdge(l2fp);
    smg1.addHasValueEdge(l3fp);
    smg1.addHasValueEdge(l4fp);
    smg1.addHasValueEdge(l5fp);

    smg1.addPointsToEdge(l1t);
    smg1.addPointsToEdge(l2t);
    smg1.addPointsToEdge(l3t);
    smg1.addPointsToEdge(l4t);
    smg1.addPointsToEdge(l5t);

    smg1.setValidity(l1, true);
    smg1.setValidity(l2, true);
    smg1.setValidity(l3, true);
    smg1.setValidity(l4, true);
    smg1.setValidity(l5, true);

    SMGState smg1State = new SMGState(
        logger, new SMGOptions(Configuration.defaultConfiguration()), smg1, 0, HashBiMap.create());

    SMGObject head = smg1State.addGlobalVariable(64, "head");
    smg1State.addPointsToEdge(head, 0, value5);

    smg1State.writeValue(head, 0, pointerType, SMGKnownSymValue.valueOf(6));
    smg1State.writeValue(head, 4, pointerType, SMGKnownSymValue.valueOf(10));

    smg1State.performConsistencyCheck(SMGRuntimeCheck.NONE);

    smg1State.executeHeapAbstraction();

    smg1State.performConsistencyCheck(SMGRuntimeCheck.NONE);
  }

  @Test
  public void materialiseTest() throws SMGInconsistentException, InvalidConfigurationException {

    for (int i = 0; i < 20; i++) {
      SMGCPA.getNewValue();
    }

    CLangSMG heap = new CLangSMG(MachineModel.LINUX32);

    SMGValue value5 = SMGKnownSymValue.valueOf(5);
    SMGValue value6 = SMGKnownSymValue.valueOf(6);
    SMGValue value7 = SMGKnownSymValue.valueOf(7);
    SMGValue value8 = SMGKnownSymValue.valueOf(8);
    SMGValue value9 = SMGKnownSymValue.valueOf(9);
    SMGValue value10 = SMGKnownSymValue.valueOf(10);
    SMGValue value11 = SMGKnownSymValue.valueOf(11);
    SMGValue value12 = SMGKnownSymValue.valueOf(12);
    SMGValue value13 = SMGKnownSymValue.valueOf(13);

    SMGObject dll = new SMGDoublyLinkedList(96, 0, 0, 4, 0, 0);
    SMGEdgeHasValue dllN = new SMGEdgeHasValue(pointerType, 0, dll, value5);
    SMGEdgeHasValue dllP = new SMGEdgeHasValue(pointerType, 4, dll, value5);
    heap.addHeapObject(dll);
    heap.setValidity(dll, true);
    heap.addValue(value5);
    heap.addValue(value6);
    heap.addValue(value7);
    heap.addHasValueEdge(dllP);
    heap.addHasValueEdge(dllN);
    heap.addPointsToEdge(new SMGEdgePointsTo(value6, dll, 0, SMGTargetSpecifier.FIRST));
    heap.addPointsToEdge(new SMGEdgePointsTo(value7, dll, 0, SMGTargetSpecifier.LAST));

   SMGRegion l1 = new SMGRegion(96, "l1", 1);
   SMGRegion l2 = new SMGRegion(96, "l2", 1);
   SMGRegion l3 = new SMGRegion(96, "l3", 1);
   SMGRegion l4 = new SMGRegion(96, "l4", 1);
   SMGRegion l5 = new SMGRegion(96, "l5", 1);

    SMGEdgeHasValue l1fn = new SMGEdgeHasValue(pointerType, 0, l1, value13);
    SMGEdgeHasValue l2fn = new SMGEdgeHasValue(pointerType, 0, l2, value8);
    SMGEdgeHasValue l3fn = new SMGEdgeHasValue(pointerType, 0, l3, value9);
    SMGEdgeHasValue l4fn = new SMGEdgeHasValue(pointerType, 0, l4, value10);
    SMGEdgeHasValue l5fn = new SMGEdgeHasValue(pointerType, 0, l5, value11);
    SMGEdgeHasValue dllSub = new SMGEdgeHasValue(pointerType, 8, dll, value12);

    SMGEdgeHasValue l1fp = new SMGEdgeHasValue(pointerType, 4, l1, value11);
    SMGEdgeHasValue l2fp = new SMGEdgeHasValue(pointerType, 4, l2, value12);
    SMGEdgeHasValue l3fp = new SMGEdgeHasValue(pointerType, 4, l3, value13);
    SMGEdgeHasValue l4fp = new SMGEdgeHasValue(pointerType, 4, l4, value8);
    SMGEdgeHasValue l5fp = new SMGEdgeHasValue(pointerType, 4, l5, value9);

    SMGEdgePointsTo l1t = new SMGEdgePointsTo(value12, l1, 0);
    SMGEdgePointsTo l2t = new SMGEdgePointsTo(value13, l2, 0);
    SMGEdgePointsTo l3t = new SMGEdgePointsTo(value8, l3, 0);
    SMGEdgePointsTo l4t = new SMGEdgePointsTo(value9, l4, 0);
    SMGEdgePointsTo l5t = new SMGEdgePointsTo(value10, l5, 0);

   heap.addHeapObject(l1);
   heap.addHeapObject(l2);
   heap.addHeapObject(l3);
   heap.addHeapObject(l4);
   heap.addHeapObject(l5);

    heap.addValue(value11);
    heap.addValue(value12);
    heap.addValue(value13);
    heap.addValue(value8);
    heap.addValue(value9);
    heap.addValue(value10);

   heap.addHasValueEdge(l1fn);
   heap.addHasValueEdge(l2fn);
   heap.addHasValueEdge(l3fn);
   heap.addHasValueEdge(l4fn);
   heap.addHasValueEdge(l5fn);
   heap.addHasValueEdge(dllSub);

   heap.addHasValueEdge(l1fp);
   heap.addHasValueEdge(l2fp);
   heap.addHasValueEdge(l3fp);
   heap.addHasValueEdge(l4fp);
   heap.addHasValueEdge(l5fp);

   heap.addPointsToEdge(l1t);
   heap.addPointsToEdge(l2t);
   heap.addPointsToEdge(l3t);
   heap.addPointsToEdge(l4t);
   heap.addPointsToEdge(l5t);

   heap.setValidity(l1, true);
   heap.setValidity(l2, true);
   heap.setValidity(l3, true);
   heap.setValidity(l4, true);
   heap.setValidity(l5, true);

    SMGState smg1State = new SMGState(logger, new SMGOptions(
        Configuration.defaultConfiguration()), heap, 0, HashBiMap.create());

    smg1State.addStackFrame(functionDeclaration3);
    SMGObject head = smg1State.addGlobalVariable(64, "head");
    smg1State.addPointsToEdge(head, 0, value5);

    smg1State.writeValue(head, 0, pointerType, SMGKnownSymValue.valueOf(6));
    smg1State.writeValue(head, 4, pointerType, SMGKnownSymValue.valueOf(10));

    smg1State.performConsistencyCheck(SMGRuntimeCheck.NONE);

    List<SMGAddressValueAndState> add = smg1State.getPointerFromValue(value6);

    add.get(1).getSmgState().performConsistencyCheck(SMGRuntimeCheck.NONE);

    add.get(0).getSmgState().performConsistencyCheck(SMGRuntimeCheck.NONE);

    UnmodifiableSMGState newState = add.get(1).getSmgState();

    List<SMGAddressValueAndState> add2 = newState.getPointerFromValue(value7);

    add2.get(1).getSmgState().performConsistencyCheck(SMGRuntimeCheck.NONE);

    add2.get(0).getSmgState().performConsistencyCheck(SMGRuntimeCheck.NONE);
  }

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() throws SMGInconsistentException, InvalidConfigurationException {

    ConfigurationBuilder builder = Configuration.builder();
    builder.setOption("cpa.smg.runtimeCheck", "HALF");
    Configuration config = builder.build();

    consistent_state = new SMGState(logger, MachineModel.LINUX64, new SMGOptions(config));
    inconsistent_state = new SMGState(logger, MachineModel.LINUX64, new SMGOptions(config));
    SMGAddressValue pt = inconsistent_state.addNewHeapAllocation(8, "label");

    consistent_state.addGlobalObject((SMGRegion)pt.getObject());
    inconsistent_state.addGlobalObject((SMGRegion)pt.getObject());
  }

  /*
   * Test that consistency violation is reported on:
   *   - inconsistent state
   *   - requested check level is lower than threshold
   */
  @Test(expected=SMGInconsistentException.class)
  public void ConfigurableConsistencyInconsistentReported1Test() throws SMGInconsistentException {
    SMGState inconsistentState = this.inconsistent_state.copyOf();
    inconsistentState.performConsistencyCheck(SMGRuntimeCheck.NONE);
  }

  /*
   * Test that consistency violation is reported on:
   *   - inconsistent state
   *   - requested check level is equal to threshold
   */
  @Test(expected=SMGInconsistentException.class)
  public void ConfigurableConsistencyInconsistentReported2Test() throws SMGInconsistentException {
    SMGState inconsistentState = this.inconsistent_state.copyOf();
    inconsistentState.performConsistencyCheck(SMGRuntimeCheck.HALF);
  }

  /*
   * Test that no consistency violation is reported on:
   *   - inconsistent state
   *   - requested check level is higher than threshold
   */
  @Test
  public void ConfigurableConsistencyInconsistentNotReportedTest() throws SMGInconsistentException {
    SMGState inconsistentState = this.inconsistent_state.copyOf();
    inconsistentState.performConsistencyCheck(SMGRuntimeCheck.FULL);
  }

  /*
   * Test that no consistency violation is reported on:
   *   - consistent state
   *   - requested check level is lower than threshold
   */
  @Test
  public void ConfigurableConsistencyConsistent1Test() throws SMGInconsistentException {
    SMGState consistentState = this.consistent_state.copyOf();
    consistentState.performConsistencyCheck(SMGRuntimeCheck.HALF);
  }
  /*
   * Test that no consistency violation is reported on:
   *   - consistent state
   *   - requested check level is higher than threshold
   */
  @Test
  public void ConfigurableConsistencyConsistent2Test() throws SMGInconsistentException {
    SMGState consistentState = this.consistent_state.copyOf();
    consistentState.performConsistencyCheck(SMGRuntimeCheck.FULL);
  }

  @Test
  public void PredecessorsTest() throws InvalidConfigurationException {
    UnmodifiableSMGState original =
        new SMGState(
            logger, MachineModel.LINUX64, new SMGOptions(Configuration.defaultConfiguration()));
    UnmodifiableSMGState second = original.copyOf();
    Assert.assertNotEquals(original.getId(), second.getId());

    UnmodifiableSMGState copy = original.copyOf();
    Assert.assertNotEquals(copy.getId(), original.getId());
    Assert.assertNotEquals(copy.getId(), second.getId());

    Assert.assertEquals(second.getPredecessorId(), original.getId());
    Assert.assertEquals(copy.getPredecessorId(), original.getId());
  }

  @Test
  public void WriteReinterpretationTest() throws SMGInconsistentException, InvalidConfigurationException {
    // Empty state
    SMGState state = new SMGState(logger, MachineModel.LINUX64, new SMGOptions(Configuration.defaultConfiguration()));
    state.performConsistencyCheck(SMGRuntimeCheck.FORCED);

    // Add an 16b object and write a 16b value into it
    SMGAddressValue pt = state.addNewHeapAllocation(16, "OBJECT");
    SMGKnownSymbolicValue new_value = SMGKnownSymValue.of();
    SMGEdgeHasValue hv = state.writeValue(pt.getObject(), 0, mockType16b, new_value).getNewEdge();
    state.performConsistencyCheck(SMGRuntimeCheck.FORCED);

    // Check the object values and assert it has only the written 16b value
    SMGEdgeHasValueFilter filter = SMGEdgeHasValueFilter.objectFilter(pt.getObject());

    Set<SMGEdgeHasValue> values_for_obj = state.getHVEdges(filter);
    Assert.assertEquals(1, values_for_obj.size());
    Assert.assertTrue(values_for_obj.contains(hv));

    // Write a same 16b value into it and assert that the state did not change
    state.writeValue(pt.getObject(), 0, mockType16b, new_value);
    state.performConsistencyCheck(SMGRuntimeCheck.FORCED);
    values_for_obj = state.getHVEdges(filter);
    Assert.assertEquals(1, values_for_obj.size());
    Assert.assertTrue(values_for_obj.contains(hv));

    // Write a *different* 16b value into it and assert that the state *did* change
    SMGKnownSymbolicValue newer_value = SMGKnownSymValue.valueOf(SMGCPA.getNewValue());
    SMGEdgeHasValue new_hv = state.writeValue(pt.getObject(), 0, mockType16b, newer_value).getNewEdge();
    state.performConsistencyCheck(SMGRuntimeCheck.FORCED);
    values_for_obj = state.getHVEdges(filter);
    Assert.assertEquals(1, values_for_obj.size());
    Assert.assertTrue(values_for_obj.contains(new_hv));
    Assert.assertFalse(values_for_obj.contains(hv));

    // Write a 8b value at index 0 and see that the old value got overwritten
    SMGEdgeHasValue hv8at0 = state.writeValue(pt.getObject(), 0, mockType8b, new_value).getNewEdge();
    state.performConsistencyCheck(SMGRuntimeCheck.FORCED);
    values_for_obj = state.getHVEdges(filter);
    Assert.assertEquals(1, values_for_obj.size());
    Assert.assertTrue(values_for_obj.contains(hv8at0));

    // Write a 8b value at index 8 and see that the old value did *not* get overwritten
    SMGEdgeHasValue hv8at8 = state.writeValue(pt.getObject(), 8, mockType8b, new_value).getNewEdge();
    state.performConsistencyCheck(SMGRuntimeCheck.FORCED);
    values_for_obj = state.getHVEdges(filter);
    Assert.assertEquals(2, values_for_obj.size());
    Assert.assertTrue(values_for_obj.contains(hv8at0));
    Assert.assertTrue(values_for_obj.contains(hv8at8));

    // Write a 8b value at index 4 and see that the old value got overwritten
    SMGEdgeHasValue hv8at4 = state.writeValue(pt.getObject(), 4, mockType8b, new_value).getNewEdge();
    state.performConsistencyCheck(SMGRuntimeCheck.FORCED);
    values_for_obj = state.getHVEdges(filter);
    Assert.assertEquals(1, values_for_obj.size());
    Assert.assertTrue(values_for_obj.contains(hv8at4));
    Assert.assertFalse(values_for_obj.contains(hv8at0));
    Assert.assertFalse(values_for_obj.contains(hv8at8));
  }

  @Test
  public void WriteReinterpretationNullifiedTest() throws SMGInconsistentException, InvalidConfigurationException {
    // Empty state
    SMGState state = new SMGState(logger, MachineModel.LINUX64, new SMGOptions(Configuration.defaultConfiguration()));
    state.performConsistencyCheck(SMGRuntimeCheck.FORCED);

    // Add an 16b object and write a 16b zero value into it
    SMGAddressValue pt = state.addNewHeapAllocation(16, "OBJECT");
    SMGEdgeHasValue hv =
        state.writeValue(pt.getObject(), 0, mockType16b, SMGZeroValue.INSTANCE).getNewEdge();
    state.performConsistencyCheck(SMGRuntimeCheck.FORCED);

    // Check the object values and assert it has only the written 16b value
    Set<SMGEdgeHasValue> values_for_obj = state.getHVEdges(SMGEdgeHasValueFilter.objectFilter(pt.getObject()));
    Assert.assertEquals(1, values_for_obj.size());
    Assert.assertTrue(values_for_obj.contains(hv));

    // Write a 8b value at index 4
    // We should see three Has-Value edges: 4b zero, 8b just written, 4b zero
    SMGEdgeHasValue hv8at4 = state.writeValue(pt.getObject(), 4, mockType8b, SMGUnknownValue.INSTANCE).getNewEdge();
    state.performConsistencyCheck(SMGRuntimeCheck.FORCED);
    values_for_obj = state.getHVEdges(SMGEdgeHasValueFilter.objectFilter(pt.getObject()));
    Assert.assertEquals(3, values_for_obj.size());
    Assert.assertTrue(values_for_obj.contains(hv8at4));
    Assert.assertTrue(
        values_for_obj.contains(new SMGEdgeHasValue(4, 0, pt.getObject(), SMGZeroValue.INSTANCE)));
    Assert.assertTrue(
        values_for_obj.contains(new SMGEdgeHasValue(4, 12, pt.getObject(), SMGZeroValue.INSTANCE)));

    SMGEdgeHasValueFilter nullFilter =
        SMGEdgeHasValueFilter.objectFilter(pt.getObject()).filterHavingValue(SMGZeroValue.INSTANCE);
    Set<SMGEdgeHasValue> nulls_for_value = state.getHVEdges(nullFilter);
    Assert.assertEquals(2, nulls_for_value.size());

    Assert.assertEquals(
        1,
        state
            .getHVEdges(
                SMGEdgeHasValueFilter.objectFilter(pt.getObject())
                    .filterHavingValue(SMGZeroValue.INSTANCE)
                    .filterAtOffset(0))
            .size());
    Assert.assertEquals(
        1,
        state
            .getHVEdges(
                SMGEdgeHasValueFilter.objectFilter(pt.getObject())
                    .filterHavingValue(SMGZeroValue.INSTANCE)
                    .filterAtOffset(12))
            .size());
  }

  @Test
  public void getPointerFromValueTest() throws SMGInconsistentException, InvalidConfigurationException {
   // Empty state
    SMGState state = new SMGState(logger, MachineModel.LINUX64, new SMGOptions(Configuration.defaultConfiguration()));
    state.performConsistencyCheck(SMGRuntimeCheck.FORCED);

    SMGAddressValue pt = state.addNewHeapAllocation(16, "OBJECT");

    SMGAddressValue pt_obtained =
        Iterables.getOnlyElement(state.getPointerFromValue(pt)).getObject();
    Assert.assertEquals(pt_obtained.getObject(), pt.getObject());
  }

  @Test(expected=SMGInconsistentException.class)
  public void getPointerFromValueNonPointerTest() throws SMGInconsistentException, InvalidConfigurationException {
    SMGState state = new SMGState(logger, MachineModel.LINUX64, new SMGOptions(Configuration.defaultConfiguration()));
    state.performConsistencyCheck(SMGRuntimeCheck.FORCED);

    SMGAddressValue pt = state.addNewHeapAllocation(16, "OBJECT");
    SMGKnownSymbolicValue nonpointer = SMGKnownSymValue.of();
    state.writeValue(pt.getObject(), 0, mockType16b, nonpointer);

    state.getPointerFromValue(nonpointer);
  }

  @Test
  public void SMGStateMemoryLeaksTest() throws InvalidConfigurationException {
    SMGState state =
        new SMGState(
            logger, MachineModel.LINUX64, new SMGOptions(Configuration.defaultConfiguration()));

    Assert.assertFalse(state.hasMemoryLeaks());
    state.setMemLeak("", Collections.emptyList());
    Assert.assertTrue(state.hasMemoryLeaks());
  }
}
