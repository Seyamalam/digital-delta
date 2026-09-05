package main

import (
	"slices"
	"testing"
)

func TestDefaultDashboardOriginsMatchNextDevelopmentServer(t *testing.T) {
	origins := splitOrigins(defaultDashboardOrigins)
	for _, expected := range []string{"http://127.0.0.1:3000", "http://localhost:3000"} {
		if !slices.Contains(origins, expected) {
			t.Fatalf("default dashboard origins %v do not contain %q", origins, expected)
		}
	}
	for _, retired := range []string{"http://127.0.0.1:5173", "http://localhost:5173"} {
		if slices.Contains(origins, retired) {
			t.Fatalf("default dashboard origins still contain retired Vite origin %q", retired)
		}
	}
}

func TestReducedHarnessRejectsNonLoopbackBindings(t *testing.T) {
	for _, address := range []string{"0.0.0.0:7070", ":7070", "192.168.1.1:7070", "localhost:7070"} {
		if requireLoopback(address) == nil {
			t.Fatalf("accepted %s", address)
		}
	}
	for _, address := range []string{"127.0.0.1:7070", "[::1]:7070"} {
		if err := requireLoopback(address); err != nil {
			t.Fatal(err)
		}
	}
}
