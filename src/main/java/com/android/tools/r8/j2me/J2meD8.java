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

  private J2meD8() {}

  /**
   * Compiles class-file byte arrays to indexed DEX files.
   *
   * <p>The returned list is ordered as {@code classes.dex}, {@code classes2.dex}, and so on. Input
   * byte arrays are not retained after this method returns.
   */
  public static List<byte[]> compile(Collection<byte[]> classFiles) throws J2meD8Exception {
    Objects.requireNonNull(classFiles, "classFiles");

    OutputConsumer output = new OutputConsumer();
    D8Command.Builder command =
        D8Command.builder()
            .setMode(CompilationMode.RELEASE)
            .setMinApiLevel(MIN_API)
            .setDisableDesugaring(true)
            .setProgramConsumer(output);

    for (byte[] classFile : classFiles) {
      command.addClassProgramData(Objects.requireNonNull(classFile, "classFile"), Origin.unknown());
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

    @Override
    public synchronized void accept(
        int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
      dexFiles.put(fileIndex, data.copyByteData());
    }

    @Override
    public void finished(DiagnosticsHandler handler) {}

    synchronized List<byte[]> getDexFiles() {
      return Collections.unmodifiableList(new ArrayList<>(dexFiles.values()));
    }
  }
}
