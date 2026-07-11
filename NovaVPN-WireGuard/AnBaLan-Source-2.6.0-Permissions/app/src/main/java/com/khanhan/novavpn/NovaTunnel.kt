package com.khanhan.novavpn

import com.wireguard.android.backend.Tunnel

class NovaTunnel(
    private val tunnelName: String,
    private val stateCallback: (Tunnel.State) -> Unit,
) : Tunnel {
    override fun getName(): String = tunnelName

    override fun onStateChange(newState: Tunnel.State) {
        stateCallback(newState)
    }
}
