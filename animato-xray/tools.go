//go:build tools

// Keeps gomobile's bind package in go.mod, which `gomobile bind` needs to find in the module.
package tools

import _ "golang.org/x/mobile/bind"
