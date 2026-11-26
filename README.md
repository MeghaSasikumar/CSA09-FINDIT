# FindIt - Campus Lost and Found (Spring Boot)

Run locally (Windows PowerShell), from the folder that contains pom.xml:
    $env:JAVA_HOME = "<your jdk folder>"; $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    .\mvnw.cmd spring-boot:run
Open http://localhost:8080

Local logins: ADMIN / admin123, DEMO1 / DEMO1, DEMO2 / DEMO2
Only emails ending with @saveetha.com can be added (env var ALLOWED_EMAIL_DOMAIN).

IMPORTANT after updating from an older version: delete the `data` folder once (the old test
database does not know the new statuses), then start the app again.

To see technical error details while testing, start with:  $env:ERROR_DETAIL="always"

Cloud settings (environment variables):
  DB_URL        jdbc:postgresql://HOST/DBNAME?sslmode=require
  DB_USER, DB_PASSWORD
  ADMIN_PASSWORD   REQUIRED on the cloud, at least 8 characters (the app refuses to start with admin123)
  SEED_DEMO        true = create DEMO1/DEMO2 (off on the cloud unless you set it)
  FOLLOWUP_DAYS    days before an unmatched lost item gets a "not found yet" message (default 30)

Claim workflow:
  SUGGESTED -> VERIFICATION_REQUESTED -> APPROVED (admin) -> COLLECTED (owner confirms)
                                                          -> MISMATCH_REPORTED (owner: "not mine") -> admin decides
Security: 5 wrong passwords lock an account for 15 minutes.
