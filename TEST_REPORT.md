# Fresh project verification

Verified on 12 October 2026 in a Linux environment with Java 17.

- Backend: Maven package succeeded; all 15 tests passed.
- Frontend: all 5 tests passed; production build succeeded.
- Exactly one Java application entry point is included. The Maven plugin explicitly selects it, and the packaged JAR manifest names it correctly.
- Real HTTP checks passed through frontend port 5175 and backend port 8085: health, registration, login, incorrect password rejection, session and CSRF handling, and logout.
- Unauthorized API responses do not include a WWW-Authenticate header, so this backend does not request a browser Basic Authentication popup.
- Settlement example passed: total INR 3000, Thanush paid 2000, Arjun paid 1000, Sai paid 0; Sai owes Thanush INR 1000.
- Unpaid equal split passed: INR 100 across three people gives INR 33.34, INR 33.33 and INR 33.33.
- Separate accounts cannot access each other's bills.
- Saved bills remain available after a backend restart using the same database.

## Limits

Windows CMD and PowerShell launchers were reviewed but could not be executed on Windows in this environment. Java 25 and MySQL were not runtime-tested. The UI was covered by component tests and a production build; no visual browser review was performed for this fresh package.

The ZIP contains source code, Maven wrapper, locked frontend dependencies, tests and instructions. Generated build output, downloaded dependencies and user databases are excluded. Internet access is required on first startup.
