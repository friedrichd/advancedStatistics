/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.algorithm.tiger.fql.ecp;

import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFANode;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class SingletonECPEdgeSet implements ECPEdgeSet {

  private class SingletonIterator implements Iterator<CFAEdge> {

    boolean mStarted = false;

    @Override
    public boolean hasNext() {
      return !mStarted;
    }

    @Override
    public CFAEdge next() {
      if (mStarted) {
        throw new NoSuchElementException();
      }

      mStarted = true;

      return mCFAEdge;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

  }

  private final CFAEdge mCFAEdge;

  public SingletonECPEdgeSet(CFAEdge pCFAEdge) {
    mCFAEdge = pCFAEdge;
  }

  public CFAEdge getCFAEdge() {
    return mCFAEdge;
  }

  @Override
  public boolean contains(CFAEdge pCFAEdge) {
    return (mCFAEdge.equals(pCFAEdge));
  }

  @Override
  public ECPEdgeSet startIn(ECPNodeSet pNodeSet) {
    for (CFANode pCFANode : pNodeSet) {
      if (mCFAEdge.getPredecessor().equals(pCFANode)) {
        return this;
      }
    }

    return EmptyECPEdgeSet.INSTANCE;
  }

  @Override
  public ECPEdgeSet endIn(ECPNodeSet pNodeSet) {
    for (CFANode pCFANode : pNodeSet) {
      if (mCFAEdge.getSuccessor().equals(pCFANode)) {
        return this;
      }
    }

    return EmptyECPEdgeSet.INSTANCE;
  }

  @Override
  public ECPEdgeSet intersect(ECPEdgeSet pOther) {
    if (pOther.contains(mCFAEdge)) {
      return this;
    }

    return EmptyECPEdgeSet.INSTANCE;
  }

  @Override
  public ECPEdgeSet union(ECPEdgeSet pOther) {
    if (pOther.contains(mCFAEdge)) {
      return pOther;
    }

    return null;
  }

  @Override
  public int size() {
    return 1;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public Iterator<CFAEdge> iterator() {
    return new SingletonIterator();
  }

  @Override
  public <T> T accept(ECPVisitor<T> pVisitor) {
    return pVisitor.visit(this);
  }

  @Override
  public boolean equals(Object pOther) {
    if (pOther == this) {
      return true;
    }

    if (pOther == null) {
      return false;
    }

    if (pOther.getClass().equals(getClass())) {
      SingletonECPEdgeSet lOther = (SingletonECPEdgeSet)pOther;

      return (lOther.mCFAEdge.equals(mCFAEdge));
    }

    return false;
  }

  @Override
  public String toString() {
    return mCFAEdge.toString();
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }

}