// Copyright (c) 2026, D8-J2ME contributors.
// All rights reserved. Use of this source code is governed by a BSD-style
// license that can be found in the LICENSE file.

package com.android.tools.r8.j2me;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Artifact-level smoke tests for the public embedded compiler API. */
public final class J2meD8SmokeTest {

  private static final byte[] DEX_035_MAGIC = {'d', 'e', 'x', '\n', '0', '3', '5', 0};

  public static void main(String[] args) throws Exception {
    byte[] fixture = readFixture();
    List<byte[]> reference = J2meD8.compile(Collections.singletonList(fixture));
    verifyDexOutput(reference);
    verifyDeterministicOutput(fixture, reference);
    verifyConcurrentCalls(fixture, reference);
    verifyInputValidation();
    verifyOutputIsUnmodifiable(reference);
    require(!J2meD8.getUpstreamVersion().trim().isEmpty(), "upstream version is empty");
    System.out.println("D8-J2ME smoke tests passed");
  }

  private static void verifyDeterministicOutput(byte[] fixture, List<byte[]> reference)
      throws Exception {
    List<byte[]> repeated = J2meD8.compile(Collections.singletonList(fixture));
    require(equalDexLists(reference, repeated), "repeated compilation is not deterministic");
  }

  private static void verifyConcurrentCalls(byte[] fixture, List<byte[]> reference)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(4);
    try {
      List<Future<List<byte[]>>> futures = new ArrayList<>();
      Callable<List<byte[]>> compilation = () -> J2meD8.compile(Collections.singletonList(fixture));
      for (int i = 0; i < 4; i++) {
        futures.add(executor.submit(compilation));
      }
      for (Future<List<byte[]>> future : futures) {
        require(equalDexLists(reference, future.get()), "concurrent compilation changed output");
      }
    } finally {
      executor.shutdownNow();
    }
  }

  private static void verifyInputValidation() throws Exception {
    expectFailure(
        IllegalArgumentException.class,
        () -> J2meD8.compile(Collections.emptyList()),
        "empty input was accepted");
    expectFailure(
        NullPointerException.class, () -> J2meD8.compile(null), "null collection was accepted");
    expectFailure(
        NullPointerException.class,
        () -> J2meD8.compile(Collections.singletonList(null)),
        "null class file was accepted");
    expectFailure(
        IllegalArgumentException.class,
        () -> J2meD8.compile(Collections.singletonList(new byte[] {1, 2, 3, 4})),
        "invalid class-file magic was accepted");
  }

  private static void verifyOutputIsUnmodifiable(List<byte[]> output) throws Exception {
    expectFailure(
        UnsupportedOperationException.class,
        () -> output.add(new byte[0]),
        "output list is mutable");
  }

  private static void verifyDexOutput(List<byte[]> output) {
    require(output.size() == 1, "expected one DEX file, got " + output.size());
    byte[] dex = output.get(0);
    require(dex.length > DEX_035_MAGIC.length, "DEX output is truncated");
    for (int i = 0; i < DEX_035_MAGIC.length; i++) {
      require(dex[i] == DEX_035_MAGIC[i], "unexpected DEX magic");
    }
  }

  private static boolean equalDexLists(List<byte[]> first, List<byte[]> second) {
    if (first.size() != second.size()) {
      return false;
    }
    for (int dexIndex = 0; dexIndex < first.size(); dexIndex++) {
      byte[] firstDex = first.get(dexIndex);
      byte[] secondDex = second.get(dexIndex);
      if (firstDex.length != secondDex.length) {
        return false;
      }
      for (int byteIndex = 0; byteIndex < firstDex.length; byteIndex++) {
        if (firstDex[byteIndex] != secondDex[byteIndex]) {
          return false;
        }
      }
    }
    return true;
  }

  private static byte[] readFixture() throws IOException {
    String resource = J2meD8SmokeTest.class.getName().replace('.', '/') + "$Fixture.class";
    try (InputStream input = J2meD8SmokeTest.class.getClassLoader().getResourceAsStream(resource)) {
      if (input == null) {
        throw new IOException("Missing compiled fixture: " + resource);
      }
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int read;
      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }

  private static void expectFailure(
      Class<? extends Throwable> expected, ThrowingRunnable action, String message)
      throws Exception {
    try {
      action.run();
    } catch (Throwable failure) {
      if (expected.isInstance(failure)) {
        return;
      }
      throw new AssertionError(
          message + "; expected " + expected.getName() + " but got " + failure, failure);
    }
    throw new AssertionError(message);
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
    }
  }

  private interface ThrowingRunnable {
    void run() throws Exception;
  }

  private static final class Fixture {
    private final int value;

    private Fixture(int value) {
      this.value = value;
    }

    int twice() {
      return value * 2;
    }
  }
}
