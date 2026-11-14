# Security Policy

## Supported Versions

Security fixes and signed APK releases are provided for the latest minor release line:

| Version | Supported |
| ------- | --------- |
| `2.5.x` | Yes       |
| `<= 2.0.x` | No — please update to the latest release |

## Offline-First Security & Privacy Posture

Aayna is built as an **offline-first** Android camera application:

- **Zero Network Access**: `android.permission.INTERNET` is omitted from the merged APK manifest and verified automatically in CI.
- **No Cloud Storage or Telemetry**: All OpenGL ES 3.0 shader rendering, LUT grading, GIF/MP4 encoding, and Room v2 negative metadata stay strictly on-device (`android:allowBackup="false"`).
- **Scoped File Sharing**: Pre-Q and modern sharing uses `FileProvider` (`${applicationId}.fileprovider`) restricted to declared media paths (`app/src/main/res/xml/file_paths.xml`).
- **Signed Releases & Integrity**: Official release APKs are signed via GitHub Actions using encrypted repository secrets (`AAYNA_KEYSTORE_BASE64`, `AAYNA_KEYSTORE_PASSWORD`, `AAYNA_KEY_ALIAS`, `AAYNA_KEY_PASSWORD`) and published with `SHA256SUMS.txt`.

## Verifying Downloaded APKs

Before installing a downloaded release APK, verify its checksum against `SHA256SUMS.txt` from the [GitHub Release](https://github.com/8Masum/aayna/releases/latest):

```bash
sha256sum -c SHA256SUMS.txt
```

## Reporting a Vulnerability

**Please do not report security vulnerabilities or privacy regressions through public GitHub issues.**

Instead, report them privately using one of these channels:

1. **Preferred**: Open a private advisory at [GitHub Security Advisories](https://github.com/8Masum/aayna/security/advisories/new).
2. **Email**: Send details to `hello.masumiqbal@gmail.com` with the subject `[Aayna Security]`.

Please include:

- Affected Aayna version (`v2.5.0`, etc.) and Android API level
- Steps to reproduce or proof-of-concept
- Potential impact (for example: manifest permission leak, URI path traversal, unintended file exposure, or CI/supply-chain risk)

You can expect an initial acknowledgment within 72 hours and coordinated disclosure once a fix and release APK are ready.
