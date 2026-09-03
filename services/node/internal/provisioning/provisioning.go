package provisioning

import (
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/hex"
	"errors"
	"fmt"
	"time"

	deltav1 "github.com/Seyamalam/digital-delta/services/node/gen/digitaldelta/v1"
	"google.golang.org/protobuf/proto"
)

const signatureAlgorithm = "RSA-2048-PSS-SHA256"

type IssueOptions struct {
	IssuerIdentityID string
	IssuerKeyID      string
	IssuerPrivateKey *rsa.PrivateKey
	IssuedAt         time.Time
	ValidFor         time.Duration
}

func Issue(enrollment *deltav1.IdentityEnrollmentRequest, options IssueOptions) (*deltav1.IdentityProvisioningCredential, error) {
	if err := validateEnrollment(enrollment); err != nil {
		return nil, err
	}
	if options.IssuerIdentityID == "" || options.IssuerKeyID == "" || options.IssuerPrivateKey == nil {
		return nil, errors.New("issuer identity, key ID, and private key are required")
	}
	if options.IssuerPrivateKey.N.BitLen() < 2048 {
		return nil, errors.New("issuer RSA key must be at least 2048 bits")
	}
	if options.IssuedAt.IsZero() || options.ValidFor <= 0 {
		return nil, errors.New("positive credential validity is required")
	}
	credentialID, err := randomID()
	if err != nil {
		return nil, err
	}
	claims := &deltav1.IdentityProvisioningClaims{
		CredentialId:                   credentialID,
		IdentityId:                     enrollment.GetIdentityId(),
		NodeId:                         enrollment.GetNodeId(),
		DisplayName:                    enrollment.GetDisplayName(),
		Role:                           enrollment.GetRole(),
		EncryptionKeyId:                enrollment.GetEncryptionKeyId(),
		Rsa_2048EncryptionPublicKeyDer: append([]byte(nil), enrollment.GetRsa_2048EncryptionPublicKeyDer()...),
		SigningKeyId:                   enrollment.GetSigningKeyId(),
		Rsa_2048SigningPublicKeyDer:    append([]byte(nil), enrollment.GetRsa_2048SigningPublicKeyDer()...),
		IssuedAtUnixMs:                 options.IssuedAt.UnixMilli(),
		ExpiresAtUnixMs:                options.IssuedAt.Add(options.ValidFor).UnixMilli(),
		IssuerIdentityId:               options.IssuerIdentityID,
		Nonce:                          append([]byte(nil), enrollment.GetNonce()...),
	}
	canonical, err := proto.MarshalOptions{Deterministic: true}.Marshal(claims)
	if err != nil {
		return nil, fmt.Errorf("marshal provisioning claims: %w", err)
	}
	digest := sha256.Sum256(canonical)
	signature, err := rsa.SignPSS(rand.Reader, options.IssuerPrivateKey, crypto.SHA256, digest[:], &rsa.PSSOptions{
		SaltLength: rsa.PSSSaltLengthEqualsHash,
		Hash:       crypto.SHA256,
	})
	if err != nil {
		return nil, fmt.Errorf("sign provisioning claims: %w", err)
	}
	return &deltav1.IdentityProvisioningCredential{
		Claims: claims,
		IssuerSignature: &deltav1.Signature{
			KeyId:             options.IssuerKeyID,
			Rsa_2048PssSha256: signature,
			Algorithm:         signatureAlgorithm,
		},
	}, nil
}

func Verify(credential *deltav1.IdentityProvisioningCredential, trustedIssuer *rsa.PublicKey, now time.Time) error {
	if credential == nil || credential.GetClaims() == nil || credential.GetIssuerSignature() == nil {
		return errors.New("credential, claims, and signature are required")
	}
	if trustedIssuer == nil || trustedIssuer.N.BitLen() < 2048 {
		return errors.New("trusted issuer RSA key must be at least 2048 bits")
	}
	claims := credential.GetClaims()
	if now.UnixMilli() < claims.GetIssuedAtUnixMs() || now.UnixMilli() >= claims.GetExpiresAtUnixMs() {
		return errors.New("credential is outside its validity window")
	}
	if credential.GetIssuerSignature().GetAlgorithm() != signatureAlgorithm {
		return errors.New("unsupported credential signature algorithm")
	}
	canonical, err := proto.MarshalOptions{Deterministic: true}.Marshal(claims)
	if err != nil {
		return fmt.Errorf("marshal provisioning claims: %w", err)
	}
	digest := sha256.Sum256(canonical)
	if err := rsa.VerifyPSS(
		trustedIssuer,
		crypto.SHA256,
		digest[:],
		credential.GetIssuerSignature().GetRsa_2048PssSha256(),
		&rsa.PSSOptions{SaltLength: rsa.PSSSaltLengthEqualsHash, Hash: crypto.SHA256},
	); err != nil {
		return fmt.Errorf("verify provisioning signature: %w", err)
	}
	return nil
}

func validateEnrollment(enrollment *deltav1.IdentityEnrollmentRequest) error {
	if enrollment == nil || enrollment.GetIdentityId() == "" || enrollment.GetNodeId() == "" || enrollment.GetDisplayName() == "" {
		return errors.New("complete enrollment identity is required")
	}
	if enrollment.GetRole() == deltav1.IdentityRole_IDENTITY_ROLE_UNSPECIFIED {
		return errors.New("enrollment role is required")
	}
	if enrollment.GetEncryptionKeyId() == "" || enrollment.GetSigningKeyId() == "" || len(enrollment.GetNonce()) < 16 {
		return errors.New("enrollment keys and 128-bit nonce are required")
	}
	for _, encoded := range [][]byte{
		enrollment.GetRsa_2048EncryptionPublicKeyDer(),
		enrollment.GetRsa_2048SigningPublicKeyDer(),
	} {
		parsed, err := x509.ParsePKIXPublicKey(encoded)
		if err != nil {
			return fmt.Errorf("parse enrollment public key: %w", err)
		}
		key, ok := parsed.(*rsa.PublicKey)
		if !ok || key.N.BitLen() < 2048 {
			return errors.New("enrollment RSA keys must be at least 2048 bits")
		}
	}
	return nil
}

func randomID() (string, error) {
	value := make([]byte, 16)
	if _, err := rand.Read(value); err != nil {
		return "", fmt.Errorf("generate credential ID: %w", err)
	}
	return "credential-" + hex.EncodeToString(value), nil
}
