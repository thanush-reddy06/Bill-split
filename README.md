# Bill Split with Memory - fresh project

Read `START-HERE.txt` first. Extract into a new folder under `C:\Projects` and
run `START-APP.cmd`. This package contains the complete React and Spring Boot
source. It retains registration, login, private history and simple light styling.

## Changes for reliable startup

- Exactly one Java application entry point, explicitly selected in `pom.xml`.
- A Windows launcher checks JDKs and chooses an installed JDK 17-25 with a compiler.
- JAVA_HOME and PATH are set for the server process only.
- The launcher refreshes Windows PATH settings so stale terminal settings are less likely to interfere.
- Backend uses port 8085; frontend uses 5175 and proxies API calls to 8085.
- A busy port produces a clear message; the launcher does not terminate other programs.
- Startup does not run Maven clean or delete data.
- The ZIP contains no old target files, database, or node_modules.

Extract this as a NEW project. Do not combine it with a starter project or copy
old Java classes into it. A separate project avoids the duplicate startup classes
and locked build files from the previous folder. Keep the old folder as a backup.

## Features

- Register with name, email and password; log in with verified credentials.
- BCrypt password hashes, HttpOnly session cookies and CSRF protection.
- Each user can access only their own saved bills.
- Settle payments: enter total, people and what each already paid, including zero.
- Split equally: calculate each person's contribution before the bill is paid.
- Preview, save, search, edit, export, delete and mark settled.
- Exact paise/cents rounding. Names must be distinct within a bill.

Example: total INR 3,000, three people. Thanush paid 2,000, Arjun paid 1,000,
Sai paid 0. Each person's share is 1,000, so Sai pays Thanush 1,000.
If nobody has paid, select Split equally. For INR 100 and three people, the
shares are 33.34, 33.33 and 33.33, payable toward the bill.
In Settle payments mode, previous payments must add up to the total.
Partially paid bills are not supported. The app does not send money.

## Windows startup

Use START-APP.cmd for both servers. Alternatively, run START-BACKEND.cmd and
START-FRONTEND.cmd individually. These use the same environment checks.
On first launch, Maven and npm download dependencies. Keep both windows open.
Open http://127.0.0.1:5175 and register an account; no default credentials exist.

The script detects JDKs using JAVA_HOME, executable paths and common installation
folders. If you installed a JDK somewhere unusual, set JAVA_HOME to its root
folder (not bin) before starting. Supported Java range: 17 through 25.
Official requirements: https://docs.spring.io/spring-boot/3.5/system-requirements.html

Node 24 is recommended. Supported by this launcher: 20.19+, 22.12+, 24+.
If Windows blocks script execution through organization policy, use the manual
commands below with a correctly configured JDK instead.

## Manual startup in VS Code

Open the new BillSplit-Fresh folder.
From its backend folder, with JAVA_HOME pointing to a JDK:

```bat
.\mvnw.cmd -v
.\mvnw.cmd spring-boot:run
```

In a separate terminal in frontend:

```bat
npm.cmd ci
npm.cmd run dev
```

On macOS/Linux use `chmod +x mvnw`, `./mvnw` and `npm`.
Check the backend at http://127.0.0.1:8085/api/health.

## Data

A fresh database is created under backend/data on first launch. Stop the backend
before copying this folder for backup. The launchers always start from backend
so the database location stays consistent. Session cookies expire after 30 minutes
of inactivity. Accounts and bills survive restart, but you must log in again.

Your old project and its data are not changed. If you later want to restore an
old H2 database, stop both backends, back up both data folders, then copy the old
backend/data into this backend. Version 2 accounts and owned bills retain their
owners. Bills from before accounts existed can be assigned after registering an
account by temporarily setting `app.legacy-owner-email=your-email@example.com`
in application.properties and restarting, then removing that line.

## Development

Backend Java files: backend/src/main/java/com/thanush/billsplit
Frontend: frontend/src/App.jsx, api.js and styles.css
Only BillsplitApplication.java contains the application main method.
The project uses Spring Boot 3.5.16, Java target 17, React and Vite.

Tests:

```bat
cd backend
.\mvnw.cmd test
```

From frontend: `npm test` and `npm run build`.
From backend: `mvnw.cmd package` produces target/billsplit-2.1.0.jar.
This backend JAR serves the API; use the frontend server alongside it.

This is a local student project. Email verification, password reset, payment
processing, cross-trip debt consolidation and public deployment are not included.
An optional MySQL configuration is supplied; switching databases does not move
existing H2 data. See TEST_REPORT.md for what was actually verified.
