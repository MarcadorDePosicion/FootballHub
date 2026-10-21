package pl.stasgroch.footballhub.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import pl.stasgroch.footballhub.ui.FootballViewModel
import pl.stasgroch.footballhub.ui.screens.*
import pl.stasgroch.footballhub.ui.theme.HubSpace
import pl.stasgroch.footballhub.ui.theme.hubHeaderGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FootballApp(vm: FootballViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val userDataReady by vm.userDataReady.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val tabs = listOf(Triple("home","Start",HubIcons.Home), Triple("matches","Mecze",HubIcons.Matches), Triple("leagues","Ligi",HubIcons.Leagues), Triple("favorites","Ulubione",HubIcons.Star), Triple("journal","Moje",HubIcons.Notes))
    val matchFilters = rememberSaveable(saver = MatchFilters.Saver) { MatchFilters() }
    var journalFilter by rememberSaveable { mutableStateOf("ALL") }
    var activeTab by rememberSaveable { mutableStateOf("home") }
    LaunchedEffect(route) { if (tabs.any { it.first == route }) activeTab = route }
    val homeList = rememberLazyListState()
    val matchesList = rememberLazyListState()
    val scroll = rememberNavigationScrollState()
    val showNavigation = !route.startsWith("form/")
    val currentList = if (route == "matches") matchesList else homeList
    LaunchedEffect(route, currentList) {
        scroll.expand()
        snapshotFlow { currentList.canScrollBackward }.collect { if (!it) scroll.expand() }
    }
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = { if (route !in listOf("league/{id}", "team/{id}")) {
            if (tabs.none { it.first == route }) {
                Row(Modifier.fillMaxWidth().hubHeaderGradient().statusBarsPadding().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ nav.popBackStack() }) { Icon(HubIcons.Back, "Wstecz") }
                }
            } else {
            TopAppBar(title = {
                HeaderSearch(matchFilters.query, { matchFilters.query = it }) {
                    nav.navigate("matches") { popUpTo("home"); launchSingleTop = true }
                }            }, navigationIcon = {
                if (route !in tabs.map { it.first }) IconButton({ nav.popBackStack() }) { Icon(HubIcons.Back, "Wstecz") }
            }, actions = {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(end = HubSpace.md)) {
                    Text("DEMO", Modifier.padding(horizontal = HubSpace.sm, vertical = HubSpace.xs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        } } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { message ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(HubSpace.md)) {
                    Column(Modifier.padding(HubSpace.md)) { Text(message, style = MaterialTheme.typography.bodyMedium); TextButton(vm::refresh) { Text("Spróbuj ponownie") } }
                }
            }
            if (!userDataReady || (state.loading && state.data.matches.isEmpty())) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }
            val data = state.data
            NavHost(navController = nav, startDestination = "home", modifier = Modifier.weight(1f).nestedScroll(scroll)) {
                composable("home") { HomeScreen(data, homeList) { nav.navigate("match/$it") } }
                composable("matches") { MatchesScreen(data, matchFilters, matchesList) { nav.navigate("match/$it") } }
                composable("leagues") { LeaguesScreen(data) { nav.navigate("league/$it") } }
                composable("league/{id}") { StandingsScreen(it.arguments?.getString("id")?.toIntOrNull() ?: -1, data, favorites, openMatch = { id -> nav.navigate("match/$id") }, back = { nav.popBackStack() }) { id -> nav.navigate("team/$id") } }
                composable("team/{id}") {
                    TeamScreen(it.arguments?.getString("id")?.toIntOrNull() ?: -1, data, { id -> nav.navigate("match/$id") }, openTeam = { id -> nav.navigate("team/$id") { launchSingleTop = true } }, back = { nav.popBackStack() }) { team ->
                        IconToggleButton(checked = team.id in favorites, onCheckedChange = { vm.favorite(team.id, it) }, enabled = !saving) {
                            Icon(if (team.id in favorites) HubIcons.StarFilled else HubIcons.Star,
                                if (team.id in favorites) "Usuń z ulubionych" else "Dodaj do ulubionych",
                                tint = if (team.id in favorites) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                composable("match/{id}") {
                    MatchDetailsScreen(it.arguments?.getString("id")?.toIntOrNull() ?: -1, data, { id -> nav.navigate("team/$id") }, { id -> nav.navigate("league/$id") }) { match ->
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ nav.navigate("form/${match.id}/NOTE/new") }, Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp), shape = MaterialTheme.shapes.medium) {
                                Text("Dodaj notatkę", style = MaterialTheme.typography.labelMedium)
                            }
                            FilledTonalButton({ nav.navigate("form/${match.id}/PREDICTION/new") }, Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp), shape = MaterialTheme.shapes.medium) {
                                Text("Dodaj typ wyniku", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        val own = entries.filter { e -> e.matchId == match.id }
                        if (own.isEmpty()) EmptyState("Nie masz jeszcze wpisów do tego meczu.")
                        own.forEach { e -> EntryCard(e, { nav.navigate("form/${e.matchId}/${e.kind}/${e.id}") }, { vm.deleteEntry(e.id) }) }
                    }
                }
                composable("favorites") { FavoritesScreen(data, favorites, { nav.navigate("team/$it") }, { vm.favorite(it, false) }, { nav.navigate("leagues") }) }
                composable("journal") { JournalScreen(data, entries, journalFilter, { journalFilter = it }, { nav.navigate("match/$it") }, { nav.navigate("form/${it.matchId}/${it.kind}/${it.id}") }, vm::deleteEntry, { nav.navigate("matches") }) }
                composable("form/{matchId}/{kind}/{entryId}") { backStack ->
                    val matchId = backStack.arguments?.getString("matchId")?.toIntOrNull() ?: -1
                    val kind = backStack.arguments?.getString("kind") ?: "NOTE"
                    val entryId = backStack.arguments?.getString("entryId")?.takeUnless { it == "new" }
                    val existing = entries.find { it.id == entryId }
                    EntryFormScreen(data.matches.find { it.id == matchId }, data, kind, existing, entryId != null && existing == null, saving,
                        { text, home, away -> vm.saveEntry(entryId, matchId, kind, text, home, away) { nav.popBackStack() } }, { nav.popBackStack() })
                }
            }
        }
    }
        if (showNavigation) FloatingNavigation(tabs, activeTab, scroll.expanded,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) { path ->
            nav.navigate(path) { popUpTo("home"); launchSingleTop = true }
        }
    }
}






