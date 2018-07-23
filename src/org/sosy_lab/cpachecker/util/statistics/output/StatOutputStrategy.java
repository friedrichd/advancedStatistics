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
package org.sosy_lab.cpachecker.util.statistics.output;

import com.google.common.base.Charsets;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sosy_lab.common.time.TimeSpan;
import org.sosy_lab.cpachecker.util.statistics.StatisticsUtils;

/**
 * Retrieves the template for an statistic output, replaces the variables and writes it to multiple
 * output files/consumers.
 */
public class StatOutputStrategy {

  private String template;
  private final Supplier<String> loadTemplate;
  private final List<Consumer<String>> outWriters = new ArrayList<>();

  /** Creates a StatOutputStrategy that uses a string supplier as template. */
  public StatOutputStrategy(Supplier<String> loadTemplate) {
    this.loadTemplate = loadTemplate;
  }

  /** Creates a StatOutputStrategy that loads the content of a file as template. */
  public StatOutputStrategy(File templateFile) {
    loadTemplate = getSupplierFromFile(templateFile);
  }

  /** Returns the cached template, if available, otherwise calls <code>loadTemplate()</code>. */
  public synchronized String getTemplate() {
    if (template == null) {
      template = loadTemplate.get();
    }
    return template;
  }

  /** Returns all variables which are used in the template. */
  public Collection<String> getRequiredVariables() {
    Collection<String> variables = new HashSet<>();
    Matcher m = Pattern.compile("\\$([A-Za-z0-9_\\.]+)\\$").matcher(getTemplate());
    while (m.find()) {
      variables.add(StatisticsUtils.escape(m.group(1)));
    }
    return variables;
  }

  /**
   * Adds a new output to this template.
   *
   * @param outWriter The consumer for the output string
   */
  public StatOutputStrategy addOutputWriter(Consumer<String> outWriter) {
    outWriters.add(outWriter);
    return this;
  }

  /**
   * Adds a new output file to this template.
   *
   * @param outFile The output file (existing files with the same name will be replaced)
   */
  public StatOutputStrategy addOutputWriter(File outFile) {
    outWriters.add(getConsumerForFile(outFile, false));
    return this;
  }

  /**
   * Adds a new output file to this template.
   *
   * @param outFile The output file
   * @param append If <code>true</code>, the output will be appended to the existing file, otherwise
   *        the file will be replaced
   */
  public StatOutputStrategy addOutputWriter(File outFile, boolean append) {
    outWriters.add(getConsumerForFile(outFile, append));
    return this;
  }

  /**
   * Replaces all variables and writes the result to all consumers.</br>
   * <strong>Notice:</strong> If no output was linked (via
   * {@link StatOutputStrategy#addOutputWriter}), there will be no output.
   *
   * @param mapping A mapping of variables and replacement objects
   */
  public void write(Map<String, Object> mapping) {
    String result = replaceVariables(mapping);
    for (Consumer<String> out : outWriters) {
      out.accept(result);
    }
  }

  /**
   * Replaces all variables in the template with the given mappings.
   *
   * @param mapping A mapping of variables and replacement objects
   */
  public String replaceVariables(Map<String, Object> mapping) {
    Map<String, Object> map = new HashMap<>();
    for (Entry<String, Object> entry : mapping.entrySet()) {
      Object obj = entry.getValue();
      if(obj instanceof Duration){
        obj = TimeSpan.ofMillis(((Duration) obj).toMillis());
      } else if (obj instanceof Number) {
        String tmp = String.format("%.2f", ((Number) obj).doubleValue());
        obj = tmp.endsWith(".00") || tmp.endsWith(",00") ? tmp.substring(0, tmp.length() - 3) : tmp;
      }
      String escapedKey = StatisticsUtils.escape(entry.getKey());
      if (!mapping.keySet().contains(escapedKey)) {
        map.putIfAbsent(escapedKey, obj);
      } else {
        map.put(entry.getKey(), obj);
      }
    }
    Matcher m = Pattern.compile("\\$([A-Za-z0-9_\\.]+)\\$").matcher(getTemplate());
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      Object obj = map.get(StatisticsUtils.escape(m.group(1)));
      if (obj != null) {
        m.appendReplacement(sb, obj.toString().trim());
      }
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private Supplier<String> getSupplierFromFile(File f) {
    return () -> {
      try {
        return Files.asCharSource(f, Charsets.UTF_8).read();
      } catch (Exception e) {
        e.printStackTrace();
        return null;
      }
    };
  }

  private Consumer<String> getConsumerForFile(File f, boolean append) {
    if (append) {
      return s -> {
        try {
          Files.asCharSink(f, Charsets.UTF_8, FileWriteMode.APPEND).write(s);
        } catch (IOException e) {
          e.printStackTrace();
        }
      };
    } else {
      return s -> {
        try {
          Files.asCharSink(f, Charsets.UTF_8).write(s);
        } catch (IOException e) {
          e.printStackTrace();
        }
      };
    }
  }

}
