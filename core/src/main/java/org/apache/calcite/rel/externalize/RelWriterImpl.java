/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.calcite.rel.externalize;

import org.apache.calcite.avatica.util.Spacer;
import org.apache.calcite.linq4j.Ord;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelWriter;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.sql.SqlExplainLevel;
import org.apache.calcite.util.Pair;

import com.google.common.collect.ImmutableList;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link org.apache.calcite.rel.RelWriter}.
 */
public class RelWriterImpl implements RelWriter {
  //~ Instance fields --------------------------------------------------------

  protected final PrintWriter pw;
  protected final SqlExplainLevel detailLevel;
  protected final boolean withIdPrefix;
  protected final boolean expand;
  protected final Spacer spacer = new Spacer();
  private final List<Pair<String, @Nullable Object>> values = new ArrayList<>();

  //~ Constructors -----------------------------------------------------------

  public RelWriterImpl(PrintWriter pw) {
    this(pw, SqlExplainLevel.EXPPLAN_ATTRIBUTES, true);
  }

  public RelWriterImpl(
      PrintWriter pw, SqlExplainLevel detailLevel,
      boolean withIdPrefix) {
    this(pw, detailLevel, withIdPrefix, false);
  }
  public RelWriterImpl(
      PrintWriter pw, SqlExplainLevel detailLevel,
      boolean withIdPrefix, boolean expand) {
    this.pw = pw;
    this.detailLevel = detailLevel;
    this.withIdPrefix = withIdPrefix;
    this.expand = expand;
  }

  //~ Methods ----------------------------------------------------------------

  protected void explain_(RelNode rel,
      List<Pair<String, @Nullable Object>> values) {
    List<RelNode> inputs = rel.getInputs();
    final RelMetadataQuery mq = rel.getCluster().getMetadataQuery();
    if (!mq.isVisibleInExplain(rel, detailLevel)) {
      // render children in place of this, at same level
      explainInputs(inputs);
      return;
    }

    StringBuilder s = new StringBuilder();
    spacer.spaces(s);
    if (withIdPrefix) {
      s.append(rel.getId()).append(":");
    }
    s.append(rel.getRelTypeName());
    if (detailLevel != SqlExplainLevel.NO_ATTRIBUTES) {
      int j = 0;
      for (Pair<String, @Nullable Object> value : values) {
        if (value.right instanceof RelNode) {
          continue;
        }
        if (j++ == 0) {
          s.append("(");
        } else {
          s.append(", ");
        }
        s.append(value.left)
            .append("=[")
            .append(value.right)
            .append("]");
      }
      if (j > 0) {
        s.append(")");
      }
    }
    switch (detailLevel) {
    case ALL_ATTRIBUTES:
      s.append(": rowcount = ")
          .append(mq.getRowCount(rel))
          .append(", cumulative cost = ")
          .append(mq.getCumulativeCost(rel));
      break;
    default:
      break;
    }
    switch (detailLevel) {
    case NON_COST_ATTRIBUTES:
    case ALL_ATTRIBUTES:
      if (!withIdPrefix) {
        // If we didn't print the rel id at the start of the line, print
        // it at the end.
        s.append(", id = ").append(rel.getId());
      }
      break;
    default:
      break;
    }
    pw.println(s);
    spacer.add(2);
    explainInputs(inputs);
    spacer.subtract(2);
  }

  private void explainInputs(List<RelNode> inputs) {
    for (RelNode input : inputs) {
      input.explain(this);
    }
  }

  @Override public final void explain(RelNode rel, List<Pair<String, @Nullable Object>> valueList) {
    explain_(rel, valueList);
  }

  @Override public SqlExplainLevel getDetailLevel() {
    return detailLevel;
  }

  @Override public RelWriter item(String term, @Nullable Object value) {
    values.add(Pair.of(term, value));
    return this;
  }

  @Override public RelWriter done(RelNode node) {
    assert checkInputsPresentInExplain(node);
    final List<Pair<String, @Nullable Object>> valuesCopy =
        ImmutableList.copyOf(values);
    values.clear();
    explain_(node, valuesCopy);
    pw.flush();
    return this;
  }

  private boolean checkInputsPresentInExplain(RelNode node) {
    int i = 0;
    if (!values.isEmpty() && values.get(0).left.equals("subset")) {
      ++i;
    }
    for (RelNode input : node.getInputs()) {
      assert values.get(i).right == input;
      ++i;
    }
    return true;
  }

  /**
   * Converts the collected terms and values to a string. Does not write to
   * the parent writer.
   */
  public String simple() {
    final StringBuilder buf = new StringBuilder("(");
    for (Ord<Pair<String, @Nullable Object>> ord : Ord.zip(values)) {
      if (ord.i > 0) {
        buf.append(", ");
      }
      buf.append(ord.e.left).append("=[").append(ord.e.right).append("]");
    }
    buf.append(")");
    return buf.toString();
  }

  @Override public boolean expand() {
    return this.expand;
  }
}
