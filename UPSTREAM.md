# Upstream tracking

D8-J2ME is derived from Google's R8 repository.

- Upstream repository: <https://r8.googlesource.com/r8>
- Initial upstream commit: `8dbc5793b507477adf078b5c60112f46a6bf6fd8`
- Initial snapshot date: 2026-08-20
- Upstream license: BSD-style license in [`LICENSE`](LICENSE)

The `upstream` Git remote points to the official R8 repository. Fork changes must remain focused on
the embedded D8 path used by Java ME loaders. Sync upstream deliberately and re-run the Java ME
conversion and Android runtime corpus before accepting an update.
