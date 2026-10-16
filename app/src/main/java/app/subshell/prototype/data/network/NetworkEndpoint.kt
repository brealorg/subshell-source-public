package app.subshell.prototype.data.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class NetworkEndpoint private constructor(
    val baseUrl: HttpUrl,
) {
    fun url(
        pathSegments: List<String>,
        queryParameters: List<Pair<String, String>> = emptyList(),
    ): HttpUrl {
        val builder = baseUrl.newBuilder()
        pathSegments.forEach { segment ->
            require(segment.isNotBlank()) { "Network path segment cannot be blank" }
            builder.addPathSegment(segment)
        }
        queryParameters.forEach { (name, value) ->
            require(name.isNotBlank()) { "Network query parameter name cannot be blank" }
            builder.addQueryParameter(name, value)
        }
        return builder.build()
    }

    companion object {
        fun https(baseUrl: String): NetworkEndpoint = https(baseUrl.toHttpUrl())

        fun https(baseUrl: HttpUrl): NetworkEndpoint {
            require(baseUrl.isHttps) { "Network endpoint must use HTTPS" }
            require(baseUrl.username.isEmpty()) { "Network endpoint cannot contain a username" }
            require(baseUrl.password.isEmpty()) { "Network endpoint cannot contain a password" }
            require(baseUrl.query == null) { "Network endpoint cannot contain query parameters" }
            require(baseUrl.fragment == null) { "Network endpoint cannot contain a fragment" }
            require(baseUrl.encodedPath.endsWith('/')) {
                "Network endpoint base path must end with a slash"
            }
            return NetworkEndpoint(baseUrl)
        }
    }
}
