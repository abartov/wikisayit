package wiki.asaf.wikisayit.data.wikipedia

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import wiki.asaf.wikisayit.ui.session.CategoryDepth
import wiki.asaf.wikisayit.ui.session.EntryKind
import wiki.asaf.wikisayit.ui.session.ListBuildResult
import wiki.asaf.wikisayit.ui.session.QueueEntry
import javax.inject.Inject

private const val MAIN_NAMESPACE = 0
private const val CATEGORY_NAMESPACE = 14

/** Max `cmlimit` a non-bot caller may request per `list=categorymembers` page. */
private const val CATEGORY_MEMBERS_LIMIT = "500"
private const val PAGEPROPS_BATCH_SIZE = 50

/** Safety valve against pathologically large or unbounded categories; not part of the product
 * spec, just a ceiling so a bad category name can't hang the list-build step. */
private const val MAX_PAGES = 2000
private const val MAX_CONTINUATIONS_PER_CATEGORY = 4

private val CategoryDepth.maxSubcategoryLevels: Int
    get() =
        when (this) {
            CategoryDepth.NONE -> 0
            CategoryDepth.TWO -> 2
            CategoryDepth.FIVE -> 5
        }

/**
 * Builds a list-sourcing queue from a Wikipedia category, per the s-dbm.4 spec: fetches the
 * named category's direct member pages on the given language's Wikipedia and, if [CategoryDepth]
 * asks for it, recurses into subcategories up to that many levels deep with cycle detection
 * (a category already visited, however it was reached, is never re-queued). Each member page is
 * then resolved to its connected Wikidata item via `pageprops`; pages with no linked item are
 * dropped, since there is no id left to record a pronunciation against.
 */
class WikipediaCategorySource
    @Inject
    constructor(
        private val httpClient: HttpClient,
    ) {
        suspend fun build(
            categoryName: String,
            languageCode: String,
            depth: CategoryDepth,
        ): ListBuildResult {
            val apiBaseUrl = "https://${languageCode.ifBlank { "en" }}.wikipedia.org/w/api.php"
            val (titles, traverseHadError) =
                traverse(normalizeCategoryTitle(categoryName), depth.maxSubcategoryLevels, apiBaseUrl)
            val (entries, resolveHadError) = resolveToQueueEntries(titles, apiBaseUrl)
            return ListBuildResult(entries, hadFetchError = traverseHadError || resolveHadError)
        }

        /** @return (collected page titles, whether any `categorymembers` request failed). */
        private suspend fun traverse(
            rootTitle: String,
            maxLevels: Int,
            apiBaseUrl: String,
        ): Pair<List<String>, Boolean> {
            val visitedCategories = mutableSetOf<String>()
            val pageTitles = LinkedHashSet<String>()
            val queue = ArrayDeque<Pair<String, Int>>()
            queue.add(rootTitle to 0)
            var hadError = false
            while (queue.isNotEmpty() && pageTitles.size < MAX_PAGES) {
                val (title, level) = queue.removeFirst()
                if (!visitedCategories.add(title)) continue
                val (pages, subcats, fetchHadError) = fetchMembers(title, apiBaseUrl)
                if (fetchHadError) hadError = true
                pageTitles += pages
                if (level < maxLevels) {
                    subcats.forEach { sub -> if (sub !in visitedCategories) queue.add(sub to level + 1) }
                }
            }
            return pageTitles.toList() to hadError
        }

        /** @return (article page titles, subcategory titles, whether a request failed) among
         * [categoryTitle]'s direct members. */
        private suspend fun fetchMembers(
            categoryTitle: String,
            apiBaseUrl: String,
        ): Triple<List<String>, List<String>, Boolean> {
            val pages = mutableListOf<String>()
            val subcats = mutableListOf<String>()
            var cmContinue: String? = null
            var continuations = 0
            var hadError = false
            do {
                val response =
                    runCatching {
                        httpClient
                            .get(apiBaseUrl) {
                                parameter("action", "query")
                                parameter("list", "categorymembers")
                                parameter("cmtitle", categoryTitle)
                                parameter("cmlimit", CATEGORY_MEMBERS_LIMIT)
                                parameter("format", "json")
                                cmContinue?.let { parameter("cmcontinue", it) }
                            }.let { if (it.status.isSuccess()) it.body<WikiCategoryMembersResponse>() else null }
                    }.getOrNull()
                if (response == null) {
                    hadError = true
                    break
                }
                response.query.categorymembers.forEach { member ->
                    when (member.ns) {
                        CATEGORY_NAMESPACE -> subcats += member.title
                        MAIN_NAMESPACE -> pages += member.title
                    }
                }
                cmContinue = response.continueToken?.cmcontinue
                continuations++
            } while (cmContinue != null && continuations < MAX_CONTINUATIONS_PER_CATEGORY)
            return Triple(pages, subcats, hadError)
        }

        /** @return (resolved entries, whether a `pageprops` request failed). */
        private suspend fun resolveToQueueEntries(
            titles: List<String>,
            apiBaseUrl: String,
        ): Pair<List<QueueEntry>, Boolean> {
            if (titles.isEmpty()) return emptyList<QueueEntry>() to false
            val entries = mutableListOf<QueueEntry>()
            var hadError = false
            for (batch in titles.chunked(PAGEPROPS_BATCH_SIZE)) {
                val response =
                    runCatching {
                        httpClient
                            .get(apiBaseUrl) {
                                parameter("action", "query")
                                parameter("prop", "pageprops")
                                parameter("ppprop", "wikibase_item")
                                parameter("titles", batch.joinToString("|"))
                                parameter("format", "json")
                            }.body<WikiPagePropsResponse>()
                    }.getOrNull()
                if (response == null) hadError = true
                response?.query?.pages?.values?.forEach { page ->
                    val qid = page.pageprops.wikibaseItem
                    if (qid != null) {
                        entries +=
                            QueueEntry(label = page.title, kind = EntryKind.ITEM, detail = "Wikidata item", qid = qid)
                    }
                }
            }
            return entries to hadError
        }
    }

private fun normalizeCategoryTitle(input: String): String {
    val trimmed = input.trim()
    return if (trimmed.contains(':')) trimmed else "Category:$trimmed"
}

@Serializable
private data class WikiCategoryMembersResponse(
    val query: WikiCategoryMembersQuery = WikiCategoryMembersQuery(),
    @SerialName("continue") val continueToken: WikiContinueToken? = null,
)

@Serializable
private data class WikiCategoryMembersQuery(
    val categorymembers: List<WikiCategoryMember> = emptyList(),
)

@Serializable
private data class WikiCategoryMember(
    val title: String,
    val ns: Int,
)

@Serializable
private data class WikiContinueToken(
    val cmcontinue: String? = null,
)

@Serializable
private data class WikiPagePropsResponse(
    val query: WikiPagePropsQuery = WikiPagePropsQuery(),
)

@Serializable
private data class WikiPagePropsQuery(
    val pages: Map<String, WikiPage> = emptyMap(),
)

@Serializable
private data class WikiPage(
    val title: String,
    val pageprops: WikiPageProps = WikiPageProps(),
)

@Serializable
private data class WikiPageProps(
    @SerialName("wikibase_item") val wikibaseItem: String? = null,
)
