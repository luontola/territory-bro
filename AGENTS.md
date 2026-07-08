# Repository Guidelines

## Commit Messages

Use an appropriate emoji from https://gitmoji.dev/ at the start of the commit subject.

The commit subject must be a one-line summary that explains what changed.

Follow the subject with a blank line and a `Why:` section.
The `Why:` section must be a list of `- ` bullet points.
Wrap the bullet text at 72 columns and indent continuation lines by two spaces.

The `Why:` bullets must explain the motivation for the change.
It may also list alternative approaches that were considered and rejected.

Example:

```text
🐛 Stabilize Auth0 browser login test

Why:
- Auth0's hosted login page can run delayed autofocus scripts after the
  fields are visible, which made WebDriver append the password to the
  username field in flaky runs.
- Fill, verify, and retry the credential fields before submitting, so
  failures point at credential entry instead of timing out later.
- Rejected fixed sleeps, because they would still depend on timing and
  slow the e2e suite.
```
