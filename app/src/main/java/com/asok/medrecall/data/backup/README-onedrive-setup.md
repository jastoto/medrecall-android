# Finishing the OneDrive connector setup

MSAL (Microsoft's sign-in library) requires a "redirect URI" that's
unique to this exact app + this exact signing key, in the form:

    msauth://com.asok.medrecall/<base64 signature hash>

That hash isn't something I can generate from here -- it has to come
from the debug keystore on asok-25 itself. One-time steps:

1. Open a terminal on asok-25 (PowerShell, Command Prompt, or Android
   Studio's own Terminal tab both work) and run:

       keytool -exportcert -alias androiddebugkey -keystore "%USERPROFILE%\.android\debug.keystore" -storepass android -keypass android | openssl sha1 -binary | openssl base64

   (If `openssl` isn't found, it usually ships alongside Git for
   Windows -- try "Git Bash" instead of PowerShell, or install Git for
   Windows, which includes it.)

2. That prints something like `Nx3f...Qm4=` (with a trailing `=`,
   ~28 characters). Take that whole string.

3. Replace `REPLACE_WITH_SIGNATURE_HASH` in TWO places with it:
   - `app/src/main/AndroidManifest.xml` (the `android:path` on the
     `BrowserTabActivity` intent-filter)
   - `app/src/main/res/raw/msal_config.json` (the `redirect_uri` value)

4. In the Azure Portal, on this app registration
   (fb485f6f-b773-4c0a-8772-4ef2b1e1738e) -> Authentication -> Add a
   platform -> Android, enter package name `com.asok.medrecall` and
   that same signature hash, and save.

Until this is done, OneDrive sign-in will fail with a redirect-URI
mismatch error -- Google Drive backup/restore works independently of
this and doesn't need any of the above.

This all needs to be redone if the app is ever re-signed with a
different key (e.g. a real release keystore instead of the debug one).
