package model.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CloudflareBody<D> {
    var errors: List<Error> = listOf()
    var messages: List<String> = listOf()
    var result: D? = null
    var success: Boolean = false

    @SerialName("result_info")
    var resultInfo: ResultInfo? = null
}

@Serializable
data class DeleteDns(
    var id: String
)

@Serializable
data class ResultInfo(
    var count: Int = 0,
    var page: Int = 0,
    @SerialName("per_page") var perPage: Int = 0,
    @SerialName("total_count") var totalCount: Int = 0,
    @SerialName("total_pages") var totalPages: Int = 0,
)


@Serializable
data class DnsRecord(
    var id: String = "",
    var type: String = "",
    var name: String = "",
    var content: String = "",
    var proxiable: Boolean = false,
    var proxied: Boolean = false,
    var ttl: Int = 0
)

@Serializable
data class DnsRecordRequest(
    var type: String = "",
    var name: String = "",
    var content: String = "",
    var ttl: Int = 0,
    var proxied: Boolean = false,
    var comment: String = "",
    var tags: List<String>
)

@Serializable
data class Error(
    val code: Int,
    var message: String = "",
    @SerialName("error_chain") var errorChain: List<Error>? = null
)
