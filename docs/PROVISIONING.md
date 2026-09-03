# Offline provisioning runbook

Digital Delta provisions devices through signed Protocol Buffer codes. The flow needs no commercial internet: a field phone creates an enrollment request, a trusted laptop signs it, and phones validate the resulting credential with a pinned administrator public key.

The current UI renders enrollment QR codes and supports copy/paste for trust and credential codes in Bangla and English. Camera scanning is still pending and must not be described as complete.

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
2. Copy the `DIGITALDELTA:ENROLLMENT:...` code or scan the displayed QR with an external test scanner.
3. On the laptop, issue a signed credential:

```bash
go run ./cmd/delta-provision issue \
  --admin-private demo-secrets/admin-private.pem \
  --issuer-id delta-admin-1 \
  --issuer-key-id admin-signing-1 \
  --valid-days 30 \
  --enrollment-code 'DIGITALDELTA:ENROLLMENT:...'
```

4. On the receiving phone, paste the administrator `DIGITALDELTA:TRUST:...` code and choose **Pin administrator** / **প্রশাসককে বিশ্বাস করুন**.
5. Paste the issued `DIGITALDELTA:CREDENTIAL:...` code and choose **Verify and add recipient** / **যাচাই করে প্রাপক যোগ করুন**.

The phone verifies the administrator signature and validity window offline, then stores only the recipient public keys and signed credential in Room. Its own RSA private keys remain non-exportable in Android Keystore.

## Multi-phone demo order

For a request from N4 to N6, first enroll the N6 phone and import its signed credential on the N4 phone. Repeat in the opposite direction only if N6 must encrypt a response for N4. Each phone pins the same administrator public key independently.

## Failure proofs

- Change one character in a trust code: the phone rejects the key.
- Change one character in a credential: signature or Protocol Buffer validation fails.
- Use a credential outside its validity window: the phone rejects it.
- Restart after a valid import: the recipient remains in the durable public-key directory.
- Give an envelope to a relay: its unrelated private key cannot unwrap the AES content key.

Automated counterparts run through `scripts/verify-local.sh --connected`.
