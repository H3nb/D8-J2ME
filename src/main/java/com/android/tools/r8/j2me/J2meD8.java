// Copyright (c) 2026, D8-J2ME contributors.
// All rights reserved. Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package com.android.tools.r8.j2me;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Version;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.threading.ThreadingModule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Minimal embedded D8 facade for converting instrumented Java ME class files to DEX. */
public final class J2meD8 {

  public static final int MIN_API = 23;
  private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;
  private static final Object COMPILATION_LOCK = new Object();

  private J2meD8() {}

  /**
   * Compiles class-file byte arrays to indexed DEX files.
   *
   * <p>The returned list is ordered as {@code classes.dex}, {@code classes2.dex}, and so on. At
   * least one class file is required. Callers must not mutate input byte arrays until this method
   * returns; the arrays are not retained afterwards.
   *
   * <p>Compilations are serialized deliberately. Embedded Java ME loaders are memory-sensitive, and
   * D8 also selects its process-wide threading provider during compilation.
   */
  public static List<byte[]> compile(Collection<byte[]> classFiles) throws J2meD8Exception {
    Objects.requireNonNull(classFiles, "classFiles");
    List<byte[]> validatedClassFiles = validateClassFiles(classFiles);

    synchronized (COMPILATION_LOCK) {
      return compileValidated(validatedClassFiles);
    }
  }

  private static List<byte[]> validateClassFiles(Collection<byte[]> classFiles) {
    if (classFiles.isEmpty()) {
      throw new IllegalArgumentException("classFiles must not be empty");
    }
    List<byte[]> validated = new ArrayList<>(classFiles.size());
    int index = 0;
    for (byte[] classFile : classFiles) {
      Objects.requireNonNull(classFile, "classFiles[" + index + "]");
      if (!hasClassFileMagic(classFile)) {
        throw new IllegalArgumentException("classFiles[" + index + "] is not a Java class file");
      }
      validated.add(classFile);
      index++;
    }
    return validated;
  }

  private static boolean hasClassFileMagic(byte[] classFile) {
    if (classFile.length < 8) {
      return false;
    }
    int magic =
        (classFile[0] & 0xff) << 24
            | (classFile[1] & 0xff) << 16
            | (classFile[2] & 0xff) << 8
            | (classFile[3] & 0xff);
    return magic == CLASS_FILE_MAGIC;
  }

  private static List<byte[]> compileValidated(List<byte[]> classFiles) throws J2meD8Exception {
    OutputConsumer output = new OutputConsumer();
    D8Command.Builder command =
        D8Command.builder()
            .setMode(CompilationMode.RELEASE)
            .setMinApiLevel(MIN_API)
            .setDisableDesugaring(true)
            .setProgramConsumer(output);

    for (int index = 0; index < classFiles.size(); index++) {
      command.addClassProgramData(classFiles.get(index), new InputOrigin(index));
    }

    try {
      ThreadingModule.Loader.enableJ2meSingleThreadedProvider();
      D8.run(command.build());
      return output.getDexFiles();
    } catch (CompilationFailedException | RuntimeException e) {
      throw new J2meD8Exception("D8 failed to compile Java ME class files", e);
    }
  }

  public static String getUpstreamVersion() {
    return Version.getVersionString();
  }

  /** Checked failure raised by the minimal embedded compiler facade. */
  public static final class J2meD8Exception extends Exception {
    private J2meD8Exception(String message, Throwable cause) {
      super(message, cause);
    }
  }

  private static final class OutputConsumer implements DexIndexedConsumer {
    private final Map<Integer, byte[]> dexFiles = new TreeMap<>();
    private boolean finished;

    @Override
    public synchronized void accept(
        int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
      if (finished) {
        throw new IllegalStateException("D8 emitted DEX data after finishing");
      }
      if (fileIndex < 0 || dexFiles.put(fileIndex, data.copyByteData()) != null) {
        throw new IllegalStateException(
            "D8 emitted an invalid or duplicate DEX index: " + fileIndex);
      }
    }

    @Override
    public synchronized void finished(DiagnosticsHandler handler) {
      finished = true;
    }

    synchronized List<byte[]> getDexFiles() {
      if (!finished) {
        throw new IllegalStateException("D8 did not finish its output consumer");
      }
      if (dexFiles.isEmpty()) {
        throw new IllegalStateException("D8 completed without producing DEX output");
      }
      int expectedIndex = 0;
      for (int fileIndex : dexFiles.keySet()) {
        if (fileIndex != expectedIndex++) {
          throw new IllegalStateException("D8 emitted non-contiguous DEX indexes");
        }
      }
      return Collections.unmodifiableList(new ArrayList<>(dexFiles.values()));
    }
  }

  private static final class InputOrigin extends Origin {
    private final int index;

    private InputOrigin(int index) {
      super(Origin.root());
      this.index = index;
    }

    @Override
    public String part() {
      return "classFiles[" + index + "]";
    }
  }
}
