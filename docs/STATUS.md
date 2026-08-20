# Project status

D8-J2ME is experimental and is not yet the production dexer for JL-Mod Plus.

## Initial validation

The initial fork is pinned to upstream commit `8dbc5793b507477adf078b5c60112f46a6bf6fd8`.

- `tools/gradle.py d8j2me` builds successfully on Windows.
- `d8-j2me.jar` is 8,032,918 bytes with 4,451 class entries. The unshrunk relocated R8 build is
  59,792,899 bytes with 26,677 class entries.
- Desktop smoke tests convert both a Java 8 class and the SimCity Societies Java ME JAR to a
  single DEX file.
- An isolated JL-Mod Plus release APK builds successfully with the custom artifact. The arm64 APK
  is 11,060,240 bytes, compared with 8,782,684 bytes for the DX baseline.
- The official combined `r8lib.jar` prototype was 14,566,725 bytes, so source-level D8 trimming
  reduces the measured APK overhead from 5,784,041 bytes to 2,277,556 bytes.

## Known limitations

- The Android build still reports interface-method desugaring warnings in optimized compiler
  internals. These warnings must be eliminated or proven unreachable before production use.
- The current artifact has not yet completed a fresh on-device runtime test after the custom
  threading-provider fix.
- Replacing DX with D8 does not fix the SimCity boolean/short verifier error. That requires a
  separate bytecode normalization step before dexing.
- The target remains substantially larger than the modified DX backend and needs further pruning.

## Next gates

1. Run the embedded compiler on Android API 23 and current Android, including SimCity conversion.
2. Remove the remaining default-interface desugaring warnings.
3. Re-run the representative Java ME corpus at `minApi 23`.
4. Establish an APK-size budget and retain DX as a fallback until runtime compatibility is proven.
