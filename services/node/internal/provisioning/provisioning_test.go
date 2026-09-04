package provisioning

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"testing"
	"time"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
	"google.golang.org/protobuf/proto"
)

func TestIssueAndVerifyOfflineCredential(t *testing.T) {
	admin, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	encryptionKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	signingKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	issuedAt := time.UnixMilli(1_800_000_000_000)
	encryptionPublicDER, err := x509.MarshalPKIXPublicKey(&encryptionKey.PublicKey)
	if err != nil {
		t.Fatal(err)
	}
	signingPublicDER, err := x509.MarshalPKIXPublicKey(&signingKey.PublicKey)
	if err != nil {
		t.Fatal(err)
	}
	enrollment := &deltav1.IdentityEnrollmentRequest{
		IdentityId:                     "hospital-operator-1",
		NodeId:                         "N6",
		DisplayName:                    "Habiganj Medical",
		Role:                           deltav1.IdentityRole_IDENTITY_ROLE_HOSPITAL,
		EncryptionKeyId:                "n6-encryption-1",
		Rsa_2048EncryptionPublicKeyDer: encryptionPublicDER,
		SigningKeyId:                   "n6-signing-1",
		Rsa_2048SigningPublicKeyDer:    signingPublicDER,
		CreatedAtUnixMs:                issuedAt.Add(-time.Minute).UnixMilli(),
		Nonce:                          make([]byte, 16),
	}

	credential, err := Issue(enrollment, IssueOptions{
		IssuerIdentityID: "delta-admin-1",
		IssuerKeyID:      "admin-signing-1",
		IssuerPrivateKey: admin,
		IssuedAt:         issuedAt,
		ValidFor:         30 * 24 * time.Hour,
	})
	if err != nil {
		t.Fatal(err)
	}
	if err := Verify(credential, &admin.PublicKey, issuedAt.Add(time.Hour)); err != nil {
		t.Fatalf("verify credential: %v", err)
	}
	if got := credential.GetClaims().GetNodeId(); got != "N6" {
		t.Fatalf("node ID = %q, want N6", got)
	}

	tampered := proto.Clone(credential).(*deltav1.IdentityProvisioningCredential)
	tampered.Claims.NodeId = "relay-attacker"
	if err := Verify(tampered, &admin.PublicKey, issuedAt.Add(time.Hour)); err == nil {
		t.Fatal("tampered credential verified")
	}
	if err := Verify(credential, &admin.PublicKey, issuedAt.Add(31*24*time.Hour)); err == nil {
		t.Fatal("expired credential verified")
	}
}

func TestIssueAndVerifyCredentialRevocation(t *testing.T) {
	admin, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	revokedAt := time.UnixMilli(1_788_380_000_000)
	revocation, err := IssueRevocation(RevocationOptions{
		CredentialID:     "credential-n4",
		IdentityID:       "clinic-sylhet-01",
		NodeID:           "N4",
		ReasonCode:       "DEVICE_LOST",
		IssuerIdentityID: "delta-admin-1",
		IssuerKeyID:      "admin-signing-1",
		IssuerPrivateKey: admin,
		RevokedAt:        revokedAt,
	})
	if err != nil {
		t.Fatalf("issue revocation: %v", err)
	}
	if err := VerifyRevocation(revocation, &admin.PublicKey, revokedAt.Add(time.Minute)); err != nil {
		t.Fatalf("verify revocation: %v", err)
	}
	if got := revocation.GetClaims().GetCredentialId(); got != "credential-n4" {
		t.Fatalf("credential ID = %q", got)
	}

	tampered := proto.Clone(revocation).(*deltav1.SignedCredentialRevocation)
	tampered.Claims.NodeId = "N6"
	if err := VerifyRevocation(tampered, &admin.PublicKey, revokedAt.Add(time.Minute)); err == nil {
		t.Fatal("tampered revocation verified")
	}
	if err := VerifyRevocation(revocation, &admin.PublicKey, revokedAt.Add(-10*time.Minute)); err == nil {
		t.Fatal("future revocation verified")
	}
}
