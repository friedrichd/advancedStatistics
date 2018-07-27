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

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.sosy_lab.cpachecker.util.statistics.AdvancedStatistics.StatEvent;
import org.sosy_lab.cpachecker.util.statistics.output.StatOutputStrategy;

public class AdvancedStatisticsTest {

  private AdvancedStatistics as;

  @Before
  public void init() {
    as = new AdvancedStatistics("foo");
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
    as.close(new StatEvent("Test", Duration.ofMillis(20), null), false);
  }

  @Test
  public void startstop() throws InterruptedException {
    StringBuilder sb = new StringBuilder();
    as.addOutputStrategy(
        new StatOutputStrategy(() -> "Total time for foo: $time.sum$\n")
            .addOutputWriter(s -> sb.append(s)));
    as.startTracking();
    Thread.sleep(10);
    as.stopTracking();
    as.printStatistics();

    String out = sb.toString();
    assertFalse("PrintStream is empty!", out.isEmpty());
    assertContains("Total time for foo", null, out);
  }

  @Test
  public void track() {
    StringBuilder sb = new StringBuilder();
    as.addOutputStrategy(
        new StatOutputStrategy(() -> "Total time for foo: $time.sum$\nTest: $test.value.hist$\n")
            .addOutputWriter(s -> sb.append(s)));
    as.startTracking();
    for (int i = 0; i < 9; i++) {
      as.track("Test", (i % 2 == 0 ? "A" : "B"));
    }
    as.stopTracking();
    as.printStatistics();

    String out = sb.toString();
    assertFalse("PrintStream is empty!", out.isEmpty());
    assertContains("Total time for foo", null, out);
    assertContains("Test", "[A x 5, B x 4]", out);
  }

  @Test
  public void openclose() throws InterruptedException {
    StringBuilder sb = new StringBuilder();
    as.addOutputStrategy(
        new StatOutputStrategy(
            () -> "Total time for foo: $time.sum$\n"
                + "Counter for Test: $test.count$\n"
                + "AVG for Test: $test.time.avg$\n"
                + "SUM for Test: $test.time.sum$\n").addOutputWriter(s -> sb.append(s)));
    as.startTracking();
    for (int i = 0; i < 9; i++) {
      StatEvent t = as.open("Test");
      Thread.sleep(2);
      as.close(t);
    }
    as.stopTracking();
    as.printStatistics();

    String out = sb.toString();
    assertFalse("PrintStream is empty!", out.isEmpty());
    assertContains("Total time for foo", null, out);
    assertContains("Counter for Test", "9", out);
    assertContains("AVG for Test", null, out);
    assertContains("SUM for Test", null, out);
  }

  @Test
  public void openclose_multiple() throws InterruptedException {
    StringBuilder sb = new StringBuilder();
    as.addOutputStrategy(
        new StatOutputStrategy(
            () -> "Total time for foo: $time.sum$\n"
                + "Counter for Test: $test.count$\n").addOutputWriter(s -> sb.append(s)));
    as.startTracking();
    ExecutorService es = Executors.newCachedThreadPool();
    es.execute(new TestWorker(18, 5));
    es.execute(new TestWorker(2, 100));
    es.execute(new TestWorker(8, 20));
    es.shutdown();

    StatEvent t = as.open("Test");
    Thread.sleep(2);
    as.close(t);

    boolean terminated = es.awaitTermination(1, TimeUnit.MINUTES);
    assertTrue("Time elapsed before all workers terminated.", terminated);

    as.stopTracking();
    as.printStatistics();

    String out = sb.toString();
    assertFalse("PrintStream is empty!", out.isEmpty());
    assertContains("Total time for foo", null, out);
    assertContains("Counter for Test", "29", out);
  }

  private void assertContains(String label, String value, String haystack) {
    assertTrue(
        "PrintStream does not contain \"" + label + "\"! " + haystack,
        haystack.toLowerCase().replaceAll("\\s+", " ").contains(label.toLowerCase() + ":"));

    if (value != null) {
      int k = haystack.indexOf(label + ":");
      k += label.length() + 2;
      int k2 = haystack.indexOf("\n", k);
      String v = haystack.substring(k, k2).trim();
      assertTrue(
          "\"" + label + "\" is " + v + ", but should be " + value + "! " + haystack,
          value.equals(v));
    }
  }

  class TestWorker implements Runnable {
    final int wait, times;

    public TestWorker(int times, int wait) {
      this.wait = wait;
      this.times = times;
    }

    @Override
    public void run() {
      for (int j = 0; j < times; j++) {
        StatEvent t = as.open("Test");
        try {
          Thread.sleep(wait);
        } catch (InterruptedException e) {
        }
        as.close(t);
      }
    }
  }

}
