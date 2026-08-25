# Bundled libraries

`sky-auth.aar` is the minified Android release library and
`sky-auth-localization.aar` is its local AAR transitive dependency. Both are based on
[`fengwu726/sky-auth`](https://github.com/fengwu726/sky-auth) commit
`7e4e781d206731e7e584ec69318cb7ffb51153b5`. The bundled authentication AAR removes
its private certificate anchors and uses Android's system CA trust for HTTPS instead;
cleartext transport remains disabled.

SHA-256:

```text
f3a821c8e4d410e5b552081b43da840e96f27949b2b5244f397ac8bea0f1c0eb  sky-auth.aar
d32841c0afb636c2afa9b3d7be6a401d9a908e66a5f8e0d5c7a313816357d851  sky-auth-localization.aar
```

To update them, build `:skyauth:assembleRelease :localization:assembleRelease`
in that repository and replace both AARs, then update the commit and checksums
above in the same change.
