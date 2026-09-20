package wiki.asaf.wikisayit.data.wikipedia

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import wiki.asaf.wikisayit.data.local.settings.DEFAULT_MAX_LIST_SIZE
import wiki.asaf.wikisayit.ui.session.CategoryDepth
import wiki.asaf.wikisayit.ui.session.EntryKind
import wiki.asaf.wikisayit.ui.session.ListBuildResult
import wiki.asaf.wikisayit.ui.session.QueueEntry
import javax.inject.Inject
import kotlin.math.min
import kotlin.random.Random

private const val MAIN_NAMESPACE = 0
private const val CATEGORY_NAMESPACE = 14

/** Max `cmlimit` a non-bot caller may request per `list=categorymembers` page. */
private const val CATEGORY_MEMBERS_LIMIT = "500"
private const val PAGEPROPS_BATCH_SIZE = 50

/** Safety valve against pathologically large or unbounded categories; not part of the product
 * spec, just a ceiling so a bad category name can't hang the list-build step. */
private const val MAX_PAGES = 2000
private const val MAX_CONTINUATIONS_PER_CATEGORY = 4

/** How many candidate pages to collect for every one the list will actually hold, before the
 * pool is shuffled and sampled (s-fi0.2). Collecting more than the list needs is what makes
 * repeat builds of the same category differ — and what leaves a caller-side [filter] spare
 * candidates to draw on when it drops entries. Members arrive 500 at a time, so a deeper pool
 * usually costs no extra `categorymembers` requests at all. */
private const val CANDIDATE_POOL_FACTOR = 5

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
 *
 * The pages found are shuffled before the list is cut down to `maxListSize`, so building a list
 * from the same category twice doesn't serve up the same words the speaker already chose to skip
 * (s-fi0.2).
 */
class WikipediaCategorySource
    @Inject
    constructor(
        private val httpClient: HttpClient,
    ) {
        /**
         * @param filter applied to each freshly resolved batch of candidates, letting the caller
         *   drop entries (already recorded, previously skipped) while there are still unresolved
         *   pages left to replace them with. Whatever it returns is appended to the list.
         * @param random seam for tests; production callers take the default source of randomness.
         */
        suspend fun build(
            categoryName: String,
            languageCode: String,
            depth: CategoryDepth,
            maxListSize: Int = DEFAULT_MAX_LIST_SIZE,
            random: Random = Random.Default,
            filter: suspend (List<QueueEntry>) -> List<QueueEntry> = { it },
        ): ListBuildResult {
            val apiBaseUrl = "https://${languageCode.ifBlank { "en" }}.wikipedia.org/w/api.php"
            val poolSize = min(maxListSize.toLong() * CANDIDATE_POOL_FACTOR, MAX_PAGES.toLong()).toInt()
            val (titles, traverseHadError) =
                traverse(normalizeCategoryTitle(categoryName), depth.maxSubcategoryLevels, apiBaseUrl, poolSize)
            val (entries, resolveHadError) =
                resolveToQueueEntries(titles.shuffled(random), apiBaseUrl, maxListSize, filter)
            return ListBuildResult(entries, hadFetchError = traverseHadError || resolveHadError)
        }

        /** @return (collected page titles, whether any `categorymembers` request failed). Stops
         * once [poolSize] pages are collected, itself derived from the configured list cap
         * (s-53x) and bounded by [MAX_PAGES] as an absolute ceiling. */
        private suspend fun traverse(
            rootTitle: String,
            maxLevels: Int,
            apiBaseUrl: String,
            poolSize: Int,
        ): Pair<List<String>, Boolean> {
            val effectiveCap = min(poolSize, MAX_PAGES)
            val visitedCategories = mutableSetOf<String>()
            val pageTitles = LinkedHashSet<String>()
            val queue = ArrayDeque<Pair<String, Int>>()
            queue.add(rootTitle to 0)
            var hadError = false
            while (queue.isNotEmpty() && pageTitles.size < effectiveCap) {
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

        /** Resolves [titles] to queue entries a batch at a time, running each batch past [filter]
         * and stopping as soon as [maxListSize] survivors are in hand — so a filter that drops a
         * lot of candidates reaches deeper into the pool, while one that drops none costs exactly
         * as many `pageprops` requests as before.
         *
         * @return (resolved entries, whether a `pageprops` request failed).
         */
        private suspend fun resolveToQueueEntries(
            titles: List<String>,
            apiBaseUrl: String,
            maxListSize: Int,
            filter: suspend (List<QueueEntry>) -> List<QueueEntry>,
        ): Pair<List<QueueEntry>, Boolean> {
            if (titles.isEmpty()) return emptyList<QueueEntry>() to false
            val entries = mutableListOf<QueueEntry>()
            var hadError = false
            for (batch in titles.chunked(PAGEPROPS_BATCH_SIZE)) {
                if (entries.size >= maxListSize) break
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
                val resolved =
                    response?.query?.pages?.values.orEmpty().mapNotNull { page ->
                        val qid = page.pageprops.wikibaseItem ?: return@mapNotNull null
                        QueueEntry(label = page.title, kind = EntryKind.ITEM, detail = "Wikidata item", qid = qid)
                    }
                if (resolved.isNotEmpty()) entries += filter(resolved)
            }
            return entries.take(maxListSize) to hadError
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
