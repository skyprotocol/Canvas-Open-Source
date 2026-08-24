# Bundled libraries

`sky-auth.aar` is the minified Android release library and
`sky-auth-localization.aar` is its local AAR transitive dependency. Both are built from
[`fengwu726/sky-auth`](https://github.com/fengwu726/sky-auth) commit
`218aef3d29e5b51b53d1d88bbc9fe61eaf2ce2d6`.

SHA-256:

```text
83cf8f8048ccea6fd7ce3660bd0ab147e5b4534e648d84f4ade9cdfb46745223  sky-auth.aar
d32841c0afb636c2afa9b3d7be6a401d9a908e66a5f8e0d5c7a313816357d851  sky-auth-localization.aar
```

To update them, build `:skyauth:assembleRelease :localization:assembleRelease`
in that repository and replace both AARs, then update the commit and checksums
above in the same change.
