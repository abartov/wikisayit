package wiki.asaf.wikisayit.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Top-level destinations of the recording flow described in DESIGN.md, plus the
 * hamburger-menu destinations (settings/stats/about). Feature epics fill in the real
 * screen content behind each route; this module only owns the graph shape.
 */
sealed interface WikiSayItRoute {
    /** [justLoggedOut] drives the post-logout confirmation note (s-nrb). */
    @Serializable
    data class SignIn(val justLoggedOut: Boolean = false) : WikiSayItRoute

    @Serializable
    data object NewProfile : WikiSayItRoute

    @Serializable
    data object Profile : WikiSayItRoute

    @Serializable
    data object Language : WikiSayItRoute

    @Serializable
    data object ListSource : WikiSayItRoute

    @Serializable
    data object Recording : WikiSayItRoute

    @Serializable
    data object Review : WikiSayItRoute

    @Serializable
    data object SessionSummary : WikiSayItRoute

    @Serializable
    data object Contribution : WikiSayItRoute

    @Serializable
    data object Done : WikiSayItRoute

    @Serializable
    data object Settings : WikiSayItRoute

    @Serializable
    data object Stats : WikiSayItRoute

    @Serializable
    data object About : WikiSayItRoute
}
