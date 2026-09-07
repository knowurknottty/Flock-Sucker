#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[2]
app=(root/'app/src/main/java/com/inversionlabs/flocksucker/security/AppLockManager.kt').read_text()
duress=(root/'app/src/main/java/com/inversionlabs/flocksucker/security/DuressAuthenticator.kt').read_text()
ui=(root/'app/src/main/java/com/inversionlabs/flocksucker/ui/screens/SecuritySettingsScreen.kt').read_text()
checks={
 'normal verification PBKDF2 offloads': 'withContext(Dispatchers.Default) { deriveKey(pin, salt) }' in app,
 'async PIN setter exists': 'suspend fun setPinAsync(pin: String): Boolean' in app,
 'set PIN UI uses async setter': 'appLockManager.setPinAsync(pin)' in ui,
 'change PIN UI uses async setter': 'appLockManager.setPinAsync(newPin)' in ui,
 'duress entered PIN PBKDF2 offloads': 'withContext(Dispatchers.Default) { deriveKey(enteredPin, duressSalt) }' in duress,
 'duress normal PIN PBKDF2 offloads': 'withContext(Dispatchers.Default) { deriveKey(enteredPin, normalSalt) }' in duress,
 'duress setup PBKDF2 offloads': 'withContext(Dispatchers.Default) { deriveKey(pin, salt) }' in duress,
}
failed=[name for name,ok in checks.items() if not ok]
for name,ok in checks.items(): print(('PASS' if ok else 'FAIL')+': '+name)
if failed: raise SystemExit(1)
