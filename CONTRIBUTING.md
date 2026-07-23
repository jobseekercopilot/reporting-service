# Contributing

This is a private, proprietary repository.

1. Start from `develop` and use a focused feature branch for an approved issue.
2. Keep one issue and one concern per pull request.
3. Do not commit generated JARs, credentials, personal data, report contents,
   document contents, or real user fixtures.
4. Define source data, formula, time zone, boundaries, duplicate handling, and
   expected answers for every changed metric.
5. Run `mvn -B clean verify` and relevant security checks before review.
6. Open a pull request into `develop`; do not push implementation work directly to
   `develop`.

Security concerns must follow `SECURITY.md`, not a public issue.
