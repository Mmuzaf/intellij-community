// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.openapi.components.PathMacroMap;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.hash.LinkedHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 *
 * @see PathMacrosImpl#addMacroReplacements(ReplacePathToMacroMap)
 * @see com.intellij.openapi.components.PathMacroManager
 */
public class ReplacePathToMacroMap extends PathMacroMap {
  private List<String> myPathsIndex = null;
  private final Map<String, String> myMacroMap = new LinkedHashMap<>();

  @NonNls public static final String[] PROTOCOLS;
  static {
    List<String> protocols = new ArrayList<>();
    protocols.add("file");
    protocols.add("jar");
    if (Extensions.getRootArea().hasExtensionPoint(PathMacroExpandableProtocolBean.EP_NAME.getName())) {
      for (PathMacroExpandableProtocolBean bean : PathMacroExpandableProtocolBean.EP_NAME.getExtensions()) {
        protocols.add(bean.protocol);
      }
    }
    PROTOCOLS = ArrayUtil.toStringArray(protocols);
  }

  public ReplacePathToMacroMap() {
  }

  @SuppressWarnings("CopyConstructorMissesField")
  public ReplacePathToMacroMap(@NotNull ReplacePathToMacroMap map) {
    for (Map.Entry<String, String> entry : map.myMacroMap.entrySet()) {
      myMacroMap.put(entry.getKey(), entry.getValue());
    }
  }

  public void addMacroReplacement(String path, String macroName) {
    addReplacement(FileUtil.toSystemIndependentName(path), "$" + macroName + "$", true);
  }

  public void addReplacement(String path, String macroExpr, boolean overwrite) {
    path = StringUtil.trimEnd(path, "/");
    putIfAbsent(path, macroExpr, overwrite);
    for (String protocol : PROTOCOLS) {
      putIfAbsent(protocol + ":" + path, protocol + ":" + macroExpr, overwrite);
      putIfAbsent(protocol + ":/" + path, protocol + ":/" + macroExpr, overwrite);
      putIfAbsent(protocol + "://" + path, protocol + "://" + macroExpr, overwrite);
    }
  }

  private void putIfAbsent(final String path, final String substitution, final boolean overwrite) {
    if (overwrite || !myMacroMap.containsKey(path)) {
      myMacroMap.put(path, substitution);
    }
  }

  @Override
  public String substitute(@Nullable String text, boolean caseSensitive) {
    if (text == null) {
      return null;
    }

    for (final String path : getPathIndex()) {
      text = replacePathMacro(text, path, caseSensitive);
    }
    return text;
  }

  private String replacePathMacro(@NotNull String text, @NotNull final String path, boolean caseSensitive) {
    if (text.length() < path.length() || path.isEmpty()) {
      return text;
    }

    if (!(caseSensitive ? text.startsWith(path) : StringUtilRt.startsWithIgnoreCase(text, path))) {
      return text;
    }

    //check that this is complete path (ends with "/" or "!/")
    // do not collapse partial paths, i.e. do not substitute "/a/b/cd" in paths like "/a/b/cdeFgh"
    int endOfOccurrence = path.length();
    final boolean isWindowsRoot = path.endsWith(":/");
    if (!isWindowsRoot &&
        endOfOccurrence < text.length() &&
        text.charAt(endOfOccurrence) != '/' &&
        !(text.charAt(endOfOccurrence) == '!' && text.substring(endOfOccurrence).startsWith("!/"))) {
      return text;
    }

    String s = myMacroMap.get(path);
    if (text.length() > endOfOccurrence) {
      return s + text.substring(endOfOccurrence);
    }
    else {
      return s;
    }
  }

  @NotNull
  @Override
  public String substituteRecursively(@NotNull String text, final boolean caseSensitive) {
    for (final String path : getPathIndex()) {
      text = replacePathMacroRecursively(text, path, caseSensitive);
    }
    return text;
  }

  private String replacePathMacroRecursively(@NotNull final String text, @NotNull final String path, boolean caseSensitive) {
    if (text.length() < path.length()) {
      return text;
    }

    if (path.isEmpty()) return text;

    final StringBuilder newText = new StringBuilder();
    final boolean isWindowsRoot = path.endsWith(":/");
    int i = 0;
    while (i < text.length()) {
      int occurrenceOfPath = caseSensitive ? text.indexOf(path, i) : StringUtil.indexOfIgnoreCase(text, path, i);
      if (occurrenceOfPath >= 0) {
        int endOfOccurrence = occurrenceOfPath + path.length();
        if (!isWindowsRoot &&
            endOfOccurrence < text.length() &&
            text.charAt(endOfOccurrence) != '/' &&
            text.charAt(endOfOccurrence) != '\"' &&
            text.charAt(endOfOccurrence) != ' ' &&
            !text.substring(endOfOccurrence).startsWith("!/")) {
          newText.append(text, i, endOfOccurrence);
          i = endOfOccurrence;
          continue;
        }
        if (occurrenceOfPath > 0) {
          char prev = text.charAt(occurrenceOfPath - 1);
          if (Character.isLetterOrDigit(prev) || prev == '_') {
            newText.append(text, i, endOfOccurrence);
            i = endOfOccurrence;
            continue;
          }
        }
      }
      if (occurrenceOfPath < 0) {
        if (newText.length() == 0) {
          return text;
        }
        newText.append(text.substring(i));
        break;
      }
      else {
        newText.append(text, i, occurrenceOfPath);
        newText.append(myMacroMap.get(path));
        i = occurrenceOfPath + path.length();
      }
    }
    return newText.toString();
  }

  private static int getIndex(@NotNull String replacement) {
    if (replacement.contains("..")) return 1;
    if (replacement.contains("$" + PathMacroUtil.USER_HOME_NAME + "$")) return 1;
    if (replacement.contains("$" + PathMacroUtil.APPLICATION_HOME_DIR + "$")) return 1;
    if (replacement.contains(PathMacroUtil.DEPRECATED_MODULE_DIR) ||
        replacement.contains("$" + PathMacroUtil.PROJECT_DIR_MACRO_NAME + "$") ||
        replacement.contains("$" + PathMacrosImpl.MAVEN_REPOSITORY + "$")) {
      return 3;
    }
    return 2;
  }

  private static int stripPrefix(@NotNull String key) {
    key = StringUtil.trimStart(key, "jar:");
    key = StringUtil.trimStart(key, "file:");
    while (key.startsWith("/")) {
      key = key.substring(1);
    }
    return key.length();
  }

  @NotNull
  public List<String> getPathIndex() {
    if (myPathsIndex == null || myPathsIndex.size() != myMacroMap.size()) {
      List<Map.Entry<String, String>> entries = new ArrayList<>(myMacroMap.entrySet());

      final TObjectIntHashMap<String> weights = new TObjectIntHashMap<>(entries.size());
      for (Map.Entry<String, String> entry : entries) {
        weights.put(entry.getKey(), getIndex(entry.getValue()) * 512 + stripPrefix(entry.getKey()));
      }

      entries.sort((o1, o2) -> weights.get(o2.getKey()) - weights.get(o1.getKey()));
      myPathsIndex = ContainerUtilRt.map2List(entries, entry -> entry.getKey());
    }
    return myPathsIndex;
  }

  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (!(obj instanceof ReplacePathToMacroMap)) return false;

    return myMacroMap.equals(((ReplacePathToMacroMap)obj).myMacroMap);
  }

  public int hashCode() {
    return myMacroMap.hashCode();
  }

  public void put(String path, String replacement) {
    myMacroMap.put(path, replacement);
  }

}
