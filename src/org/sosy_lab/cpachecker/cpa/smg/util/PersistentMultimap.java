/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2017  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.smg.util;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Iterables;
import java.util.Set;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentMap;

/** A Multimap-implementation based on the {@link PathCopyingPersistentTreeMap}. */
public class PersistentMultimap<K, V> {

  private final PersistentMap<K, ImmutableSet<V>> delegate;

  private PersistentMultimap(PersistentMap<K, ImmutableSet<V>> pDelegate) {
    delegate = pDelegate;
  }

  public static <K extends Comparable<? super K>, V> PersistentMultimap<K, V> of() {
    return new PersistentMultimap<K, V>(PathCopyingPersistentTreeMap.of());
  }

  public PersistentMultimap<K, V> putAndCopy(K key, V value) {
    Builder<V> builder = ImmutableSet.builder();
    Set<V> old = delegate.get(key);
    if (old != null) {
      builder.addAll(old);
    }
    return new PersistentMultimap<>(delegate.putAndCopy(key, builder.add(value).build()));
  }

  public PersistentMultimap<K, V> removeAndCopy(K key) {
    return new PersistentMultimap<>(delegate.removeAndCopy(key));
  }

  public PersistentMultimap<K, V> removeAndCopy(K key, V value) {
    Set<V> old = delegate.get(key);
    if (old == null || !old.contains(value)) {
      return this;
    }
    Builder<V> builder = ImmutableSet.builder();
    builder.addAll(Iterables.filter(old, e -> !e.equals(value)));
    ImmutableSet<V> fresh = builder.build();
    if (fresh.isEmpty()) {
      return new PersistentMultimap<>(delegate.removeAndCopy(key));
    } else {
      return new PersistentMultimap<>(delegate.putAndCopy(key, fresh));
    }
  }

  public ImmutableSet<V> get(K key) {
    ImmutableSet<V> set = delegate.get(key);
    return set == null ? ImmutableSet.of() : set;
  }

  public ImmutableSet<V> values() {
    return ImmutableSet.copyOf(Iterables.concat(delegate.values()));
  }

  @Override
  public int hashCode() {
    return delegate.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof PersistentMultimap)) {
      return false;
    }
    @SuppressWarnings("unchecked")
    PersistentMultimap<K,V> other = (PersistentMultimap<K,V>)o;
    return delegate.equals(other.delegate);
  }

  @Override
  public String toString() {
    return delegate.toString();
  }
}