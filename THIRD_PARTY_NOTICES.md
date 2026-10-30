# Third-Party Code References

## Re-BiliTerminal

- Project: https://github.com/cyq114514/Re-BiliTerminal
- Authors: cyq114514 and Re-BiliTerminal contributors.
- Reference revision: `9d62f4bd6df213557f7033570eda6970ecc0eb3e`.
- License: GNU General Public License v3.0 (see the project LICENSE).
- Referenced files: `SearchBangumiFragment.java` and `SearchApi.java`.

The separate PGC search page, media-card mapping and stale-request guard
were adapted to this project's existing org.json APIs, ViewPager2 and card
adapter. The implementation adds a separate film/TV category, preserves
pagination metadata and handles verification/error responses explicitly.

The existing BiliClient authorship and licenses remain applicable to the
original code. No other Re-BiliTerminal modules or dependencies are bundled.
