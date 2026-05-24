package com.rockhard.blocker.sync

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.content.Context
import android.os.Bundle

class StubAuthenticator(context: Context) : AbstractAccountAuthenticator(context) {
    override fun editProperties(r: AccountAuthenticatorResponse?, t: String?): Bundle? = null
    override fun addAccount(r: AccountAuthenticatorResponse?, t: String?, authType: String?, features: Array<out String>?, options: Bundle?): Bundle? = null
    override fun confirmCredentials(r: AccountAuthenticatorResponse?, account: Account?, options: Bundle?): Bundle? = null
    override fun getAuthToken(r: AccountAuthenticatorResponse?, account: Account?, authType: String?, options: Bundle?): Bundle? = null
    override fun getAuthTokenLabel(authType: String?): String? = null
    override fun updateCredentials(r: AccountAuthenticatorResponse?, account: Account?, authType: String?, options: Bundle?): Bundle? = null
    override fun hasFeatures(r: AccountAuthenticatorResponse?, account: Account?, features: Array<out String>?): Bundle? = null
}
