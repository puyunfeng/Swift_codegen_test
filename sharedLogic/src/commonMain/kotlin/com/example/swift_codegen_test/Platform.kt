package com.example.swift_codegen_test

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform