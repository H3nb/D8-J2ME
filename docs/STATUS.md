# Project status

D8-J2ME is experimental and is not yet the production dexer for JL-Mod Plus.

## Initial validation

The initial fork is pinned to upstream commit `8dbc5793b507477adf078b5c60112f46a6bf6fd8`.

- `tools/gradle.py d8j2me` builds successfully on Windows.
- `tools/gradle.py d8j2meTest` passes against the final shrunken artifact, including deterministic,
  repeated, and concurrent compilation plus invalid-input checks.
- `d8-j2me.jar` is 8,044,740 bytes with 4,452 class entries. The unshrunk relocated R8 build is
  59,792,899 bytes with 26,677 class entries.
- The shrunken artifact retains its checked exception and generic public signature, so consumers see
  `List<byte[]> compile(Collection<byte[]>)` without unchecked API erosion.
- Desktop smoke tests convert both a Java 8 class and the SimCity Societies Java ME JAR to a
  single DEX file.
- An isolated JL-Mod Plus release APK builds successfully with the custom artifact. The arm64 APK
  is 11,060,240 bytes, compared with 8,782,684 bytes for the DX baseline.
- The official combined `r8lib.jar` prototype was 14,566,725 bytes, so specialized artifact trimming
  reduces the measured APK overhead from 5,784,041 bytes to 2,277,556 bytes.
- On a POCO `25053PC47G` running Android 16/API 36, the embedded release compiler converts and runs
  J2ME ImageViewer successfully. The earlier threading-provider lookup failure is no longer present.
- On the same device, SimCity Societies converts successfully but ART rejects its pre-existing
  short-to-boolean bytecode mismatch. This is a Java ME bytecode compatibility issue outside D8.

## Known limitations

- A downstream Android release build still reports interface-method desugaring warnings for
  fastutil `Map` and `Comparator` default-method bridges in optimized compiler internals. R8 replaces
  those specific super calls with `NoSuchMethodError` below API 24, so API 23 support must not be
  claimed until the paths are removed, rewritten, or covered by core-library desugaring.
- Replacing DX with D8 does not fix the SimCity boolean/short verifier error. That requires a
  separate bytecode normalization step before dexing.
- The target remains substantially larger than the modified DX backend and needs further pruning.
- Android API 23 is the configured compiler target, but runtime execution has only been verified on
  API 36 so far; an API 23 device or emulator remains a compatibility gate.
- Representative-corpus, memory-pressure, cancellation, and lifecycle stress testing remain open.

## Next gates

1. Run the embedded compiler on Android API 23.
2. Remove or prove unreachable the remaining default-interface desugaring warnings.
3. Re-run the representative Java ME corpus at `minApi 23`, tracking conversion time and peak RAM.
4. Add any required Java ME bytecode normalization outside the D8 facade.
5. Establish an APK-size budget and retain a known-good dexer fallback until consumer-specific
   runtime compatibility is proven.
