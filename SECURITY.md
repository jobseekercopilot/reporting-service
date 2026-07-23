# Security policy

This private repository is not yet beta-ready.

Report suspected vulnerabilities privately to the repository owner. Do not place
credentials, tokens, personal information, report contents, document contents, or
exploit details in a public channel or ordinary issue.

The current audit identifies a critical ownership gap: API calls trust
browser/service-supplied `X-User-Id` values and downstream Application Tracker data
is fetched by that identifier without authenticated ownership enforcement. Treat
the service as unsuitable for user data until identity propagation and cross-user
isolation are proven.

Rotate any accidentally disclosed secret and remove it from all affected systems;
do not rely on deleting a Git commit as the remediation.
