package main

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/base64"
	"encoding/pem"
	"errors"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"time"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
	"github.com/Seyamalam/digital-delta/services/node/internal/provisioning"
	"google.golang.org/protobuf/proto"
)

func main() {
	if err := run(os.Args[1:]); err != nil {
		fmt.Fprintln(os.Stderr, "delta-provision:", err)
		os.Exit(1)
	}
}

func run(args []string) error {
	if len(args) == 0 {
		return errors.New("use init-admin, issue, or revoke")
	}
	switch args[0] {
	case "init-admin":
		return initAdmin(args[1:])
	case "issue":
		return issue(args[1:])
	case "revoke":
		return revoke(args[1:])
	default:
		return fmt.Errorf("unknown command %q; use init-admin, issue, or revoke", args[0])
	}
}

func revoke(args []string) error {
	flags := flag.NewFlagSet("revoke", flag.ContinueOnError)
	credentialPath := flags.String("credential", "", "binary credential previously written by issue --out")
	privatePath := flags.String("admin-private", "demo-secrets/admin-private.pem", "administrator private key path")
	issuerID := flags.String("issuer-id", "delta-admin-1", "administrator identity ID")
	issuerKeyID := flags.String("issuer-key-id", "admin-signing-1", "administrator signing key ID")
	reasonCode := flags.String("reason", "DEVICE_LOST", "machine-readable revocation reason")
	outPath := flags.String("out", "", "optional binary revocation output path")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if *credentialPath == "" {
		return errors.New("--credential is required")
	}
	credentialBytes, err := os.ReadFile(*credentialPath)
	if err != nil {
		return fmt.Errorf("read credential: %w", err)
	}
	credential := new(deltav1.IdentityProvisioningCredential)
	if err := proto.Unmarshal(credentialBytes, credential); err != nil {
		return fmt.Errorf("parse credential: %w", err)
	}
	claims := credential.GetClaims()
	if claims == nil {
		return errors.New("credential claims are missing")
	}
	adminKey, err := readPrivateKey(*privatePath)
	if err != nil {
		return err
	}
	revocation, err := provisioning.IssueRevocation(provisioning.RevocationOptions{
		CredentialID:     claims.GetCredentialId(),
		IdentityID:       claims.GetIdentityId(),
		NodeID:           claims.GetNodeId(),
		ReasonCode:       strings.TrimSpace(*reasonCode),
		IssuerIdentityID: *issuerID,
		IssuerKeyID:      *issuerKeyID,
		IssuerPrivateKey: adminKey,
		RevokedAt:        time.Now(),
	})
	if err != nil {
		return err
	}
	revocationBytes, err := proto.MarshalOptions{Deterministic: true}.Marshal(revocation)
	if err != nil {
		return fmt.Errorf("encode revocation: %w", err)
	}
	if *outPath != "" {
		if err := writeExclusive(*outPath, revocationBytes, 0o644); err != nil {
			return err
		}
	}
	fmt.Printf("Credential revoked for %s (%s).\n", claims.GetIdentityId(), claims.GetNodeId())
	fmt.Printf("DIGITALDELTA:REVOCATION:%s\n", base64.RawURLEncoding.EncodeToString(revocationBytes))
	return nil
}

func initAdmin(args []string) error {
	flags := flag.NewFlagSet("init-admin", flag.ContinueOnError)
	privatePath := flags.String("private", "demo-secrets/admin-private.pem", "new private key path")
	publicPath := flags.String("public", "demo-secrets/admin-public.der", "new public key path")
	if err := flags.Parse(args); err != nil {
		return err
	}
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return fmt.Errorf("generate RSA-2048 admin key: %w", err)
	}
	privateDER, err := x509.MarshalPKCS8PrivateKey(key)
	if err != nil {
		return fmt.Errorf("encode private key: %w", err)
	}
	publicDER, err := x509.MarshalPKIXPublicKey(&key.PublicKey)
	if err != nil {
		return fmt.Errorf("encode public key: %w", err)
	}
	if err := writeExclusive(*privatePath, pem.EncodeToMemory(&pem.Block{Type: "PRIVATE KEY", Bytes: privateDER}), 0o600); err != nil {
		return err
	}
	if err := writeExclusive(*publicPath, publicDER, 0o644); err != nil {
		return err
	}
	fmt.Printf("Administrator key created. Keep %s private.\n", *privatePath)
	fmt.Printf("DIGITALDELTA:TRUST:%s\n", base64.RawURLEncoding.EncodeToString(publicDER))
	return nil
}

func issue(args []string) error {
	flags := flag.NewFlagSet("issue", flag.ContinueOnError)
	enrollmentCode := flags.String("enrollment-code", "", "DIGITALDELTA:ENROLLMENT code from a field device")
	privatePath := flags.String("admin-private", "demo-secrets/admin-private.pem", "administrator private key path")
	issuerID := flags.String("issuer-id", "delta-admin-1", "administrator identity ID")
	issuerKeyID := flags.String("issuer-key-id", "admin-signing-1", "administrator signing key ID")
	validDays := flags.Int("valid-days", 30, "credential validity in days")
	outPath := flags.String("out", "", "optional binary credential output path")
	if err := flags.Parse(args); err != nil {
		return err
	}
	if *enrollmentCode == "" {
		return errors.New("--enrollment-code is required")
	}
	encoded := strings.TrimPrefix(*enrollmentCode, "DIGITALDELTA:ENROLLMENT:")
	enrollmentBytes, err := base64.RawURLEncoding.DecodeString(encoded)
	if err != nil {
		return fmt.Errorf("decode enrollment code: %w", err)
	}
	enrollment := new(deltav1.IdentityEnrollmentRequest)
	if err := proto.Unmarshal(enrollmentBytes, enrollment); err != nil {
		return fmt.Errorf("parse enrollment request: %w", err)
	}
	adminKey, err := readPrivateKey(*privatePath)
	if err != nil {
		return err
	}
	credential, err := provisioning.Issue(enrollment, provisioning.IssueOptions{
		IssuerIdentityID: *issuerID,
		IssuerKeyID:      *issuerKeyID,
		IssuerPrivateKey: adminKey,
		IssuedAt:         time.Now(),
		ValidFor:         time.Duration(*validDays) * 24 * time.Hour,
	})
	if err != nil {
		return err
	}
	credentialBytes, err := proto.MarshalOptions{Deterministic: true}.Marshal(credential)
	if err != nil {
		return fmt.Errorf("encode credential: %w", err)
	}
	if *outPath != "" {
		if err := writeExclusive(*outPath, credentialBytes, 0o644); err != nil {
			return err
		}
	}
	fmt.Printf("Credential issued for %s (%s).\n", enrollment.GetDisplayName(), enrollment.GetNodeId())
	fmt.Printf("DIGITALDELTA:CREDENTIAL:%s\n", base64.RawURLEncoding.EncodeToString(credentialBytes))
	return nil
}

func readPrivateKey(path string) (*rsa.PrivateKey, error) {
	contents, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read admin private key: %w", err)
	}
	block, _ := pem.Decode(contents)
	if block == nil || block.Type != "PRIVATE KEY" {
		return nil, errors.New("admin private key must be PKCS#8 PEM")
	}
	parsed, err := x509.ParsePKCS8PrivateKey(block.Bytes)
	if err != nil {
		return nil, fmt.Errorf("parse admin private key: %w", err)
	}
	key, ok := parsed.(*rsa.PrivateKey)
	if !ok || key.N.BitLen() < 2048 {
		return nil, errors.New("admin private key must be RSA-2048 or stronger")
	}
	return key, nil
}

func writeExclusive(path string, contents []byte, mode os.FileMode) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o750); err != nil {
		return fmt.Errorf("create output directory: %w", err)
	}
	file, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_EXCL, mode)
	if err != nil {
		return fmt.Errorf("create %s without overwriting: %w", path, err)
	}
	defer file.Close()
	if _, err := file.Write(contents); err != nil {
		return fmt.Errorf("write %s: %w", path, err)
	}
	return nil
}
