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
package org.sosy_lab.cpachecker.cpa.smg.graphs.object.test;

import java.util.Collection;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.types.MachineModel;
import org.sosy_lab.cpachecker.cpa.smg.SMGInconsistentException;
import org.sosy_lab.cpachecker.cpa.smg.graphs.CLangSMG;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObject;
import org.sosy_lab.cpachecker.cpa.smg.graphs.object.SMGObjectKind;
import org.sosy_lab.cpachecker.cpa.smg.graphs.value.SMGValue;

@RunWith(Parameterized.class)
public class SMGAttachRegionToListTest {

  private static final String GLOBAL_LIST_POINTER_LABEL = "pointer";
  private static final MachineModel MACHINE_MODEL_FOR_TESTING = MachineModel.LINUX64;
  private static final int LEVEL_ZERO = 0;

  @Parameters
  public static Collection<Object[]> data() {
    return SMGListAbstractionTestInputs.getAttachRegionToListTestInputs();
  }

  @Parameter(value = 0)
  public SMGValue[] values;

  @Parameter(value = 1)
  public SMGValue valueToAttach;

  @Parameter(value = 2)
  public SMGListCircularity circularity;

  @Parameter(value = 3)
  public SMGListLinkage linkage;

  private CLangSMG smg;
  private SMGValue addressOfList;
  private SMGValue addressOfRegion;
  private SMGObjectKind listKind;
  private int nodeSize;
  private int hfo;
  private int nfo;
  private int pfo;

  @Before
  public void setUp() {

    final int intSize = MACHINE_MODEL_FOR_TESTING.getSizeofInt();
    final int ptrSize = MACHINE_MODEL_FOR_TESTING.getSizeofPtr();

    hfo = 0;
    nfo = 0;
    pfo = (linkage == SMGListLinkage.DOUBLY_LINKED) ? ptrSize : -1;
    final int dfo = (linkage == SMGListLinkage.DOUBLY_LINKED) ? 2 * ptrSize : ptrSize;
    final int dataSize = intSize;
    nodeSize = dfo + dataSize;
    listKind = (linkage == SMGListLinkage.DOUBLY_LINKED) ? SMGObjectKind.DLL : SMGObjectKind.SLL;

    smg = new CLangSMG(MACHINE_MODEL_FOR_TESTING);

    // create one region
    addressOfRegion =
        SMGListAbstractionTestHelpers.addLinkedRegionsWithValuesToHeap(
            smg,
            new SMGValue[] {valueToAttach},
            nodeSize,
            hfo,
            nfo,
            pfo,
            dfo,
            dataSize,
            circularity,
            linkage)[0];

    // create one list
    addressOfList =
        SMGListAbstractionTestHelpers.addLinkedListsWithValuesToHeap(
            smg,
            new SMGValue[][] {values},
            nodeSize,
            hfo,
            nfo,
            pfo,
            dfo,
            dataSize,
            circularity,
            linkage)[0];
  }

  @Test
  public void testAbstractionOfListWithPrependedRegion()
      throws InvalidConfigurationException, SMGInconsistentException {

    SMGValue firstAddress = addressOfRegion;
    SMGValue secondAddress = addressOfList;
    firstAddress =
        SMGListAbstractionTestHelpers.linkObjectsOnHeap(
            smg, new SMGValue[] {firstAddress, secondAddress}, hfo, nfo, pfo, circularity, linkage)[
            0];

    SMGListAbstractionTestHelpers.addGlobalListPointerToSMG(
        smg, firstAddress, GLOBAL_LIST_POINTER_LABEL);

    SMGListAbstractionTestHelpers.executeHeapAbstraction(smg);

    Assert.assertTrue(smg.isPointer(firstAddress));
    SMGObject segment = smg.getObjectPointedBy(firstAddress);

    SMGListAbstractionTestHelpers.assertAbstractListSegmentAsExpected(
        segment, nodeSize, LEVEL_ZERO, listKind, values.length + 1);
  }

  @Test
  public void testAbstractonOfListWithAppendedRegion()
      throws InvalidConfigurationException, SMGInconsistentException {

    SMGValue firstAddress = addressOfList;
    SMGValue secondAddress = addressOfRegion;
    firstAddress =
        SMGListAbstractionTestHelpers.linkObjectsOnHeap(
            smg, new SMGValue[] {firstAddress, secondAddress}, hfo, nfo, pfo, circularity, linkage)[
            0];

    SMGListAbstractionTestHelpers.addGlobalListPointerToSMG(
        smg, firstAddress, GLOBAL_LIST_POINTER_LABEL);

    SMGListAbstractionTestHelpers.executeHeapAbstraction(smg);

    Assert.assertTrue(smg.isPointer(firstAddress));
    SMGObject segment = smg.getObjectPointedBy(firstAddress);

    SMGListAbstractionTestHelpers.assertAbstractListSegmentAsExpected(
        segment, nodeSize, LEVEL_ZERO, listKind, values.length + 1);
  }
}