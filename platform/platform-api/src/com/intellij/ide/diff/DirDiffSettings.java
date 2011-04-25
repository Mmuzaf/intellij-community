/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.diff;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PatternUtil;

import java.util.regex.Pattern;

/**
 * @author Konstantin Bulenkov
 */
public class DirDiffSettings {
  public boolean showSize = true;
  public boolean showDate = true;

  public boolean showEqual = false;
  public boolean showDifferent = true;
  public boolean showNewOnSource = true;
  public boolean showNewOnTarget = true;
  public boolean showCompareModes = true;
  public CompareMode compareMode = CompareMode.CONTENT;

  private String filter = "";
  private Pattern filterPattern = PatternUtil.fromMask("*");

  public String getFilter() {
    return filter;
  }

  public void setFilter(String filter) {
    this.filter = filter;
    filterPattern = PatternUtil.fromMask(StringUtil.isEmpty(filter) ? "*" : filter);
  }

  public Pattern getFilterPattern() {
    return filterPattern;
  }

  public static enum CompareMode {
    CONTENT, // the most honest, the slowest. Compares size, if equal compares contents. Ignores timestamps
    SIZE, // Compares size only
    TIMESTAMP // Compares size, if equal compares timestamps
  }
}
