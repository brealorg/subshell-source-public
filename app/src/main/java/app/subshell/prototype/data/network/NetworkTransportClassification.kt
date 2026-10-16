package app.subshell.prototype.data.network

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLKeyException
import javax.net.ssl.SSLPeerUnverifiedException

// NET03_TLS_ONLY_FOR_HANDSHAKE_FAILURES_V1 (#62): TLS means "the request was
// never sent" to every writer (no read-back, safe to send again). That holds
// only for handshake and certificate failures. On Android (Conscrypt) a reset
// or abort while the response is read, or a failed body write, also surfaces as
// SSLException ("Read error ... Connection reset by peer", "Write error ...
// Broken pipe") after the request may have reached Reddit; those are IO, so a
// writer treats the outcome as unknown and reads back instead of re-sending.
internal fun IOException.toNetworkTransportFailureKind(): NetworkTransportFailureKind = when (this) {
    is UnknownHostException -> NetworkTransportFailureKind.DNS
    is ConnectException,
    is NoRouteToHostException,
    -> NetworkTransportFailureKind.CONNECT

    is SocketTimeoutException -> NetworkTransportFailureKind.TIMEOUT
    is SSLHandshakeException,
    is SSLPeerUnverifiedException,
    is SSLKeyException,
    -> NetworkTransportFailureKind.TLS
    else -> NetworkTransportFailureKind.IO
}
