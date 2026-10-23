package com.foxy.keygen

import android.app.Application
import com.foxy.keygen.data.FxaAuthRepository
import com.foxy.keygen.data.GistRegistry
import com.foxy.keygen.data.GuardianClient
import com.foxy.keygen.data.TokenStore

class KeygenApp : Application() {

    lateinit var tokenStore: TokenStore
    lateinit var authRepository: FxaAuthRepository
    lateinit var guardianClient: GuardianClient
    lateinit var gistRegistry: GistRegistry

    override fun onCreate() {
        super.onCreate()
        tokenStore = TokenStore(this)
        authRepository = FxaAuthRepository(tokenStore)
        guardianClient = GuardianClient()
        gistRegistry = GistRegistry(this)
    }

    companion object {
        const val GUARDIAN_ENDPOINT = "https://vpn.mozilla.org"
    }
}
