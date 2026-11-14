package com.example.swift_codegen_test

import com.example.swift_codegen.GenerateSwiftEnum
@GenerateSwiftEnum
sealed interface HelloTestHello {
    object OneTab : HelloTestHello
    class OneFun(val s: String, val s2: String) : HelloTestHello
    class TwoFun(val s: String, val s2: String, val s3: String, val s4: String) : HelloTestHello
    class ThreeFun(val s: String, val s2: String, val s3: String) : HelloTestHello
    class UserFun(val user: User) : HelloTestHello
    class ThreeFun2(val s: String, val s2: Int, val s3: Boolean) : HelloTestHello
}

data class User(val name: String)
