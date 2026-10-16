# Fork signing keystore

Run on a machine YOU control (Termux, laptop, or the box only if you accept it lives there).
Replace placeholders; never commit the .jks or passwords.

```bash
keytool -genkeypair -v \
  -keystore orbit-fork.jks -alias orbitfork \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass '<STORE_PASSWORD>' -keypass '<KEY_PASSWORD>' \
  -dname "CN=tmg270 Orbit Fork"
# (PKCS12 default: store and key password should be identical)

base64 -w0 orbit-fork.jks > orbit-fork.jks.b64     # macOS: base64 -i orbit-fork.jks -o orbit-fork.jks.b64

keytool -list -v -keystore orbit-fork.jks -alias orbitfork -storepass '<STORE_PASSWORD>' | grep SHA256
```

GitHub secrets (fork → Settings → Secrets and variables → Actions), or with gh:
```bash
gh secret set ORBIT_RELEASE_KEYSTORE_B64   -R tmg270/Orbit-Assistant < orbit-fork.jks.b64
gh secret set ORBIT_RELEASE_STORE_PASSWORD -R tmg270/Orbit-Assistant   # prompts
gh secret set ORBIT_RELEASE_KEY_PASSWORD   -R tmg270/Orbit-Assistant
gh secret set ORBIT_RELEASE_KEY_ALIAS      -R tmg270/Orbit-Assistant --body orbitfork
rm orbit-fork.jks.b64
```

**BACKUP WARNING:** keep orbit-fork.jks + passwords in a password manager / offline copy.
Lose it and every future build has a different signature: Android refuses to update over the
installed app, so you must uninstall (losing app data) to install a new build.
Record the SHA-256 fingerprint; compare it to the `apksigner` output in the workflow log.
