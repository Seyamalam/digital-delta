# Offline provisioning runbook

Digital Delta provisions devices through signed Protocol Buffer codes. The flow needs no commercial internet: a field phone creates an enrollment request, a trusted laptop signs it, and phones validate the resulting credential with a pinned administrator public key.

The current UI renders enrollment QR codes and supports CameraX scanning or copy/paste for trust and credential codes in Bangla and English. The ML Kit barcode model is bundled in the APK; a physical airplane-mode camera rehearsal remains required evidence.

## One-time administrator setup

Run from `services/node` on the trusted laptop:

```bash
go run ./cmd/delta-provision init-admin \
  --private demo-secrets/admin-private.pem \
  --public demo-secrets/admin-public.der
```

The command refuses to overwrite either file. Keep `admin-private.pem` on the administrator laptop and out of screenshots, logs, chat, and version control. The generated `DIGITALDELTA:TRUST:...` line is public and can be transferred to field phones.

## Enroll a field device

1. On the phone, open the shield icon and select **Identity and offline keys** / **পরিচয় ও অফলাইন কী**.
2. Choose that phone's allow-listed role: N1 coordinator, N4 clinic, N6 hospital, or RLY-01 relay. Changing roles stops the existing relay and creates a distinct Keystore identity.
3. Copy the resulting `DIGITALDELTA:ENROLLMENT:...` code or scan its displayed QR.
4. On the laptop, issue a signed credential:

```bash
go run ./cmd/delta-provision issue \
  --admin-private demo-secrets/admin-private.pem \
  --issuer-id delta-admin-1 \
  --issuer-key-id admin-signing-1 \
  --valid-days 30 \
  --enrollment-code 'DIGITALDELTA:ENROLLMENT:...'
```

5. On the receiving phone, scan or paste the administrator `DIGITALDELTA:TRUST:...` code and choose **Pin administrator** / **প্রশাসককে বিশ্বাস করুন**.
6. Scan or paste that phone's issued `DIGITALDELTA:CREDENTIAL:...` code and choose **Verify and install credential** / **যাচাই করে পরিচয়পত্র ইনস্টল করুন**.

The phone verifies the administrator signature and validity window offline, then stores only public keys and the signed credential in Room. If the credential exactly matches this phone's selected profile and Keystore public keys, its role permissions become active; a mismatched credential may remain usable as a peer key but cannot authorize local actions. Private keys remain non-exportable in Android Keystore.

## Multi-phone demo order

Enroll all three profiles independently and install each phone's own credential on that phone. Each phone pins the same administrator public key. During Nearby pairing, the signed challenge-response carries and verifies the remote public credential, then stores it in the local directory; no private key ever leaves its originating phone.

## Failure proofs

- Change one character in a trust code: the phone rejects the key.
- Change one character in a credential: signature or Protocol Buffer validation fails.
- Use a credential outside its validity window: the phone rejects it.
- Restart after a valid import: the recipient remains in the durable public-key directory.
- Give an envelope to a relay: its unrelated private key cannot unwrap the AES content key.

Automated counterparts run through `scripts/verify-local.sh --connected`.
