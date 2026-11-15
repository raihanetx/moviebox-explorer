package com.example.movieboxexplorer.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Minimal reader for Nuxt's `__NUXT_DATA__` devalue payload.
 *
 * The MovieBox SSR pages embed their catalog in a flat array where objects reference
 * each other by index. The public `/tab-operating` API hides `webHighRisk` titles, but
 * the server-rendered payload still contains them, so the app can show the same rows as
 * the website by reading this payload.
 */
object NuxtPayload {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val WRAPPERS = setOf("ShallowReactive", "Reactive", "Ref", "ShallowRef")

    private val SCRIPT = Regex(
        "<script[^>]*id=\"__NUXT_DATA__\"[^>]*>(.*?)</script>",
        RegexOption.DOT_MATCHES_ALL,
    )

    fun parse(html: String): JsonElement? {
        val body = SCRIPT.find(html)?.groupValues?.getOrNull(1) ?: return null
        return runCatching { json.parseToJsonElement(body) }.getOrNull()
    }

    /** Resolve the whole payload into a normal JSON tree. */
    fun resolve(root: JsonElement): JsonElement {
        val nodes = root.jsonArray
        return resolveNode(nodes, 0, HashSet())
    }

    /** Find the first `operatingList` array anywhere in the resolved tree. */
    fun findOperatingList(root: JsonElement): JsonArray? {
        fun search(element: JsonElement): JsonArray? {
            return when (element) {
                is JsonObject -> {
                    (element["operatingList"] as? JsonArray)?.let { return it }
                    element.values.firstNotNullOfOrNull { search(it) }
                }
                is JsonArray -> element.firstNotNullOfOrNull { search(it) }
                else -> null
            }
        }
        return search(root)
    }

    /** Collect every `operatingList` array in the resolved tree. */
    fun findOperatingLists(root: JsonElement): List<JsonArray> {
        val result = mutableListOf<JsonArray>()
        fun search(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    (element["operatingList"] as? JsonArray)?.let { result.add(it) }
                    element.values.forEach { search(it) }
                }
                is JsonArray -> element.forEach { search(it) }
                else -> {}
            }
        }
        search(root)
        return result
    }

    /** Find `subjectList.items` for the SSR pages that use a flat grid. */
    fun findSubjectListItems(root: JsonElement): JsonArray? {
        fun search(element: JsonElement): JsonArray? {
            return when (element) {
                is JsonObject -> {
                    (element["subjectList"] as? JsonObject)?.let { list ->
                        (list["items"] as? JsonArray)?.let { return it }
                    }
                    element.values.firstNotNullOfOrNull { search(it) }
                }
                is JsonArray -> element.firstNotNullOfOrNull { search(it) }
                else -> null
            }
        }
        return search(root)
    }

    /** Find the first value for a key anywhere in the resolved tree. */
    fun findFirst(root: JsonElement, key: String): JsonElement? {
        fun search(element: JsonElement): JsonElement? {
            return when (element) {
                is JsonObject -> {
                    element[key]?.let { return it }
                    element.values.firstNotNullOfOrNull { search(it) }
                }
                is JsonArray -> element.firstNotNullOfOrNull { search(it) }
                else -> null
            }
        }
        return search(root)
    }

    private fun resolveNode(
        nodes: List<JsonElement>,
        index: Int,
        seen: MutableSet<Int>,
    ): JsonElement {
        if (index !in nodes.indices) return JsonNull
        if (!seen.add(index)) return JsonNull
        try {
            return when (val node = nodes[index]) {
                is JsonPrimitive -> node
                is JsonObject -> buildJsonObject {
                    node.forEach { (key, value) ->
                        put(key, resolveValue(nodes, value, seen))
                    }
                }
                is JsonArray -> {
                    val first = node.firstOrNull()
                    val tag = (first as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                    when {
                        tag in WRAPPERS && node.size >= 2 ->
                            resolveValue(nodes, node[1], seen)
                        tag == "Date" && node.size >= 2 -> node[1]
                        tag == "Set" && node.size >= 2 ->
                            JsonArray(node.drop(1).map { resolveValue(nodes, it, seen) })
                        else ->
                            JsonArray(node.map { resolveValue(nodes, it, seen) })
                    }
                }
            }
        } finally {
            seen.remove(index)
        }
    }

    private fun resolveValue(
        nodes: List<JsonElement>,
        element: JsonElement,
        seen: MutableSet<Int>,
    ): JsonElement {
        if (element is JsonPrimitive) {
            if (element.isString || element.booleanOrNull != null || element is JsonNull) {
                return element
            }
            return resolveNode(nodes, element.intOrNull ?: return JsonNull, seen)
        }
        return resolveNode(nodes, element.jsonPrimitive.intOrNull ?: return JsonNull, seen)
    }
}