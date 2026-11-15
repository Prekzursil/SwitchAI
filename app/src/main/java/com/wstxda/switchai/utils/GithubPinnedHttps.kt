package com.wstxda.switchai.utils

import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * HTTPS certificate pinning for the updater's calls to api.github.com.
 *
 * Pins the SHA-256 hashes of the Subject Public Key Info (SPKI) of the
 * server's certificate chain. The chain is validated normally first
 * (system trust anchors), and only then is at least one pinned SPKI
 * required, so an attacker needs both a CA-signed certificate for
 * github.com AND control of a pinned key to intercept the call.
 *
 * Pins (captured 2026-11-15):
 *   - leaf:        *.github.com (GitHub rotates this certificate routinely)
 *   - intermediates: the two Sectigo "Public Server Authentication" CA
 *     keys currently serving the chain; pinning the CA keys keeps the
 *   connection working across leaf rotations.
 *
 * If pinning ever fails after GitHub rotates its CAs, the updater shows
 * its generic error snackbar (no crash, no silent fallback) and the pins
 * must be refreshed here.
 */
object GithubPinnedHttps {

    private const val PINNED_HOST = "api.github.com"

    // base64(SHA-256(SubjectPublicKeyInfo DER))
    private val SPKI_PINS = setOf(
        "P5437A6f5e2OnunPOzNhRV2iWeOE1XGTN9xskQ53yTk=", // leaf *.github.com
        "ZSagvDzjltLkewXEBuDxIzpW/dpVw1Juvvmd0hhkzdY=", // Sectigo intermediate
        "sLVjNUaFYfW7n6EtgBeEpjOlcnBdNPMrZDRF36iwBdE=", // Sectigo intermediate
    )

    fun openConnection(url: java.net.URL): HttpsURLConnection {
        require(url.host.equals(PINNED_HOST, ignoreCase = true)) {
            "Certificate pins are only maintained for $PINNED_HOST, refusing ${url.host}"
        }

        // Standard system validation first…
        val systemTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        systemTmf.init(null as KeyStore?)
        val systemTm = systemTmf.trustManagers.filterIsInstance<X509TrustManager>().first()

        // …then refuse the chain unless it also contains a pinned SPKI.
        val pinningTm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                systemTm.checkClientTrusted(chain, authType)

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                systemTm.checkServerTrusted(chain, authType)
                val matched = chain.any { cert -> SPKI_PINS.contains(spkiSha256(cert)) }
                if (!matched) {
                    throw CertificateException(
                        "Certificate pinning failure: no pinned SPKI for $PINNED_HOST"
                    )
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = systemTm.acceptedIssuers
        }

        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(pinningTm), null)
        }

        val connection = url.openConnection() as HttpsURLConnection
        connection.sslSocketFactory = sslContext.socketFactory
        return connection
    }

    private fun spkiSha256(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(certificate.publicKey.encoded)
        return android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)
    }
}
