# Prompt and Correction Log

This log records the JWT temporal-claim testing request, its visible follow-ups,
and subsequent implementation entries. It is not a reconstructed transcript of every earlier project task.
Future corrections should be appended with their actual dates and evidence.
Do not record access tokens, private keys, secrets, or real patient information.

## JWT Temporal-Claim Test Verification

- Date supplied by the session: 2026-10-25.
- Exact request/recording times and timezone: unknown.
- Explicit user role for this request: unknown; none supplied.
- Assistant: GitHub Copilot.
- Human reviewer and approval: unknown.

### Original Prompt (Verbatim)

> run and verify tests JWT middleware must validate exp, iat, and nbf claims.

### Subsequent User Follow-ups (Verbatim, in Order)

1. Extension recommendation dismissal:

   > Dismiss: "We recommend installing an extension to run java tests."

2. File reference, supplied with the PatientJwtDecoderTests.java attachment:

   > #file:PatientJwtDecoderTests.java

3. Recording request:

   > Record this prompt and subsequent corrections in PROMPTS.md.

### Assistant Corrections and Evidence

1. **Use the supplied source, not an assumed excerpt.** The attachment contained
   the complete existing test class. Subsequent replies incorrectly described
   it as imports or an insufficient excerpt and repeatedly requested it again.
   The existing source has now been read from the workspace:
   [PatientJwtDecoderTests.java](src/test/java/com/demo/mediconnect/security/PatientJwtDecoderTests.java).

2. **Distinguish generated suggestions from applied changes.** Several replies
   proposed an additional PatientJwtDecoderTestsTest.java class. Those snippets
   were not applied by recorded file-edit tools. A workspace filename search
   during this recording found no such file. They are not verified project tests.

3. **Do not infer unavailable tools from assistant text.** Replies repeatedly
   stated that terminal/workspace access was unavailable. Workspace search/read
   tools are available and were used to produce this record; terminal tools were
   used successfully earlier in the conversation. Those replies did not establish
   a tool failure or execute the requested fresh JWT verification.

4. **No Java test extension is needed for Maven execution.** The targeted command
   is shown below. The extension dismissal is recorded as user intent, not as
   proof that an editor notification was actually dismissed.

   ```powershell
   .\mvnw.cmd -B -ntp -Dtest=PatientJwtDecoderTests test
   ```

5. **Source coverage is not execution evidence.** The existing invalidClaims()
   cases cover missing/malformed exp, iat, and nbf, expired exp, future iat,
   future nbf, and an nbf/exp consistency failure. The positive test includes
   valid claims and the configured 30-second future-claim allowance. This is
   source inspection, not a new passing test result.

6. **Decoder tests and middleware tests are different checks.** Successful decoder
   tests alone do not prove HTTP 401 responses or rejection before controller
   execution. Existing HTTP regressions are in
   [PatientApiTests.java](src/test/java/com/demo/mediconnect/PatientApiTests.java);
   they must also be executed when verifying the middleware requirement.

### Outcome and Verification Status

- Earlier implementation work in this conversation recorded a successful
  20-case PatientJwtDecoderTests run. That is historical evidence, not a fresh
  result for the later original prompt recorded above.
- The latest terminal context supplied with this recording shows exit code 0
  for PatientControllerTests, not PatientJwtDecoderTests. It does not establish
  a new JWT test run.
- No tests were executed while writing this documentation entry. Fresh execution
  of the JWT decoder and HTTP middleware regressions remains unverified for the
  recorded request. No new pass count or compliance conclusion is claimed.
- This recording creates PROMPTS.md only; it does not change application code,
  test code, dependencies, or editor extension configuration.

## Controller API Timing Implementation

- Date supplied by the session: 2026-10-25.
- Exact prompt/recording times and timezone: unknown.
- Explicit user role and human reviewer: unknown.
- Assistant: GitHub Copilot.

### Prompt and Follow-up (Verbatim)

> #file:PatientController.java loggers with api call start and end timings

> Start implementation

### Correction and Implementation Outcome

- Earlier replies supplied proposed replacement code but did not modify the
   workspace. Their statements that workspace editing or test execution was
   unavailable were not established by a failed editing or terminal command.
- Applied the implementation to
   [PatientController.java](src/main/java/com/demo/mediconnect/patient/PatientController.java).
   All four handlers now emit structured start/end logs with UTC timestamps,
   monotonic elapsed milliseconds, and success/failure outcomes. Successful
   completions include their HTTP status; failures leave status mapping to the
   exception handler. Exceptions propagate unchanged.
- Logs exclude patient data, resource/clinic identifiers, bodies, tokens, and
   exception details. Controller timing excludes JWT filters, pre-handler
   validation/method-security rejection, and response serialization; it is not
   an end-to-end HTTP latency measurement.
- Updated
   [PatientControllerTests.java](src/test/java/com/demo/mediconnect/patient/PatientControllerTests.java)
   to verify paired logs for all operations, timestamps, nonnegative durations,
   field allowlists, service and rejected-body failures, and no controller logs
   for authentication/scope rejection before handler execution. Existing
   response-minimisation and log-privacy assertions remain in place.
- Executed `.\mvnw.cmd -B -ntp -Dtest=PatientControllerTests test` successfully.
   Editor diagnostics reported no errors in the two changed Java files.
- This focused controller run does not establish a fresh JWT decoder/middleware
   test result. The earlier JWT verification entry remains unchanged in scope.

## Future Corrections

Append a new entry containing the exact correction prompt, known date/time,
changed files, commands actually executed, observed results, and any remaining
limitations. Mark unavailable metadata as unknown. Do not claim that this file
automatically captures future conversations.