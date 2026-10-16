package app.subshell.prototype.data.network

import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLProtocolException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * NET03 (#62): only handshake/certificate failures are TLS ("never sent");
 * an SSLException while reading the answer or writing the body is IO, so
 * writers read back instead of sending again.
 */
class Net03TlsClassificationTest {
    @Test
    fun handshakeAndCertificateFailuresAreTls() {
        assertEquals(NetworkTransportFailureKind.TLS, SSLHandshakeException("handshake failed").toNetworkTransportFailureKind())
        assertEquals(NetworkTransportFailureKind.TLS, SSLPeerUnverifiedException("hostname not verified").toNetworkTransportFailureKind())
    }

    @Test
    fun aResetWhileReadingOrWritingIsIo() {
        assertEquals(
            NetworkTransportFailureKind.IO,
            SSLException("Read error: ssl=0x7b: I/O error during system call, Connection reset by peer").toNetworkTransportFailureKind(),
        )
        assertEquals(
            NetworkTransportFailureKind.IO,
            SSLException("Write error: ssl=0x7b: I/O error during system call, Broken pipe").toNetworkTransportFailureKind(),
        )
        assertEquals(NetworkTransportFailureKind.IO, SSLProtocolException("bad record").toNetworkTransportFailureKind())
    }

    @Test
    fun otherKindsAreUnchanged() {
        assertEquals(NetworkTransportFailureKind.TIMEOUT, SocketTimeoutException().toNetworkTransportFailureKind())
        assertEquals(NetworkTransportFailureKind.IO, IOException("closed").toNetworkTransportFailureKind())
    }
}
