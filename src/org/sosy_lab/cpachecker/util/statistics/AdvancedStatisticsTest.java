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
package org.sosy_lab.cpachecker.util.statistics;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.Before;
import org.junit.Test;
import org.sosy_lab.cpachecker.util.statistics.AdvancedStatistics.StatEvent;

public class AdvancedStatisticsTest {

  private AdvancedStatistics as;
  private ByteArrayOutputStream outContent;

  @Before
  public void init() {
    as = new AdvancedStatistics("foo");
    outContent = new ByteArrayOutputStream();
  }

  @Test(expected = AssertionError.class)
  public void trackWithoutStart() {
    as.track("Test");
  }

  @Test(expected = AssertionError.class)
  public void openWithoutStart() {
    as.open("Test");
  }

  @Test(expected = AssertionError.class)
  public void closeWithoutStart() {
    as.close("Test", false);
  }

  @Test
  public void startstop() throws InterruptedException {
    as.startTracking();
    Thread.sleep(10);
    as.stopTracking();
    as.printStatistics(new PrintStream(outContent), null, null);

    assertFalse("PrintStream is empty!", outContent.toString().isEmpty());
    assertContains("Total time for foo", null);
    assertNotContains("Defect StatEvents");
  }

  @Test
  public void track() {
    as.startTracking();
    for (int i = 0; i < 9; i++) {
      as.track("Test", (i % 2 == 0 ? "A" : "B"));
    }
    as.stopTracking();
    as.printStatistics(new PrintStream(outContent), null, null);

    assertFalse("PrintStream is empty!", outContent.toString().isEmpty());
    assertContains("Total time for foo", null);
    assertContains("Test", "[A x 5, B x 4]");
    assertNotContains("Defect StatEvents");
  }

  @Test
  public void openclose() throws InterruptedException {
    as.startTracking();
    for (int i = 0; i < 9; i++) {
      StatEvent t = as.open("Test");
      t.storage.setPrintFormat(StatKind.COUNT, "Counter for Test");
      t.storage.setPrintFormat(StatKind.AVG, "AVG for Test");
      t.storage.setPrintFormat(StatKind.SUM, "SUM for Test");
      Thread.sleep(2);
      as.close(t);
    }
    as.stopTracking();
    as.printStatistics(new PrintStream(outContent), null, null);

    assertFalse("PrintStream is empty!", outContent.toString().isEmpty());
    assertContains("Total time for foo", null);
    assertContains("Counter for Test", "9");
    assertContains("AVG for Test", null);
    assertContains("SUM for Test", null);
    assertNotContains("Defect StatEvents");
  }

  @Test
  public void openclose_multiple() throws InterruptedException {
    as.startTracking();
    Thread t1 = new TestWorker(10, 5);
    t1.start();
    Thread t2 = new TestWorker(2, 100);
    t2.start();
    Thread t3 = new TestWorker(4, 20);
    t3.start();
    t1.join();
    t2.join();
    t3.join();
    as.stopTracking();
    as.printStatistics(new PrintStream(outContent), null, null);

    assertFalse("PrintStream is empty!", outContent.toString().isEmpty());
    assertContains("Total time for foo", null);
    assertContains("Counter for Test", "16");
    assertNotContains("Defect StatEvents");
  }

  private void assertContains(String label, String value) {
    assertTrue(
        "PrintStream does not contain \"" + label + "\"! " + outContent.toString(),
        outContent.toString()
            .toLowerCase()
            .replaceAll("\\s+", " ")
            .contains(label.toLowerCase() + ":"));

    if (value != null) {
      int k = outContent.toString().indexOf(label + ":");
      k += label.length() + 2;
      int k2 = outContent.toString().indexOf("\n", k);
      String v = outContent.toString().substring(k, k2).trim();
      assertTrue(
          "\"" + label + "\" is " + v + ", but should be " + value + "! " + outContent.toString(),
          value.equals(v));
    }
  }

  private void assertNotContains(String label) {
    assertFalse(
        "PrintStream contains \"" + label + "\"! " + outContent.toString(),
        outContent.toString().toLowerCase().replaceAll("\\s+", " ").contains(label.toLowerCase()));
  }

  class TestWorker extends Thread {
    final int wait, times;

    public TestWorker(int times, int wait) {
      super();
      this.wait = wait;
      this.times = times;
    }

    @Override
    public void run() {
      for (int j = 0; j < times; j++) {
        StatEvent t = as.open("Test");
        t.storage.setPrintFormat(StatKind.COUNT, "Counter for Test");
        try {
          Thread.sleep(wait);
        } catch (InterruptedException e) {
        }
        as.close(t);
      }
    }
  }

}
