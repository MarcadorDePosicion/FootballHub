package pl.stasgroch.footballhub.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
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
    val focusManager = LocalFocusManager.current
    val state by vm.state.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val userDataReady by vm.userDataReady.collectAsStateWithLifecycle()
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val tabs = listOf(Triple("home","Start",HubIcons.Home), Triple("matches","Mecze",HubIcons.Matches), Triple("leagues","Ligi",HubIcons.Leagues), Triple("favorites","Ulubione",HubIcons.Star), Triple("journal","Moje",HubIcons.Notes))
    val searchScroll = androidx.compose.foundation.rememberScrollState()
    val searchState = rememberSaveable(saver = HeaderSearchState.Saver) { HeaderSearchState() }
    val matchFilters = rememberSaveable(saver = MatchFilters.Saver) { MatchFilters() }
    var journalFilter by rememberSaveable { mutableStateOf("ALL") }
    var activeTab by rememberSaveable { mutableStateOf("home") }
    LaunchedEffect(route) { if (tabs.any { it.first == route }) activeTab = route }
    val homeList = rememberLazyListState()
    var matchesList by remember { mutableStateOf(androidx.compose.foundation.lazy.LazyListState()) }
    val leaguesList = rememberLazyListState()
    val favoritesList = rememberLazyListState()
    val journalList = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var returnToTop by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val mainLists = mapOf("home" to homeList, "matches" to matchesList, "leagues" to leaguesList,
        "favorites" to favoritesList, "journal" to journalList)
    val scroll = rememberNavigationScrollState()
    val showNavigation = tabs.any { it.first == route }
    val currentList = mainLists[route]
    LaunchedEffect(route, currentList) {
        scroll.expand()
        if (currentList != null) snapshotFlow { currentList.canScrollBackward }.collect { if (!it) scroll.expand() }
    }
    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = { if (route !in listOf("league/{id}", "team/{id}?player={player}")) {
            if (tabs.none { it.first == route }) {
                Row(Modifier.fillMaxWidth().hubHeaderGradient().statusBarsPadding().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ nav.popBackStack() }) { Icon(HubIcons.Back, "Wstecz") }
                    if (route == "player/{id}") Text("Profil zawodnika", style = MaterialTheme.typography.titleLarge)
                    if (route == "match/{id}") Text("Szczegóły meczu", style = MaterialTheme.typography.titleLarge)
                }
            } else {
            TopAppBar(title = {
                HeaderSearch(state.data.teams, state.data.players, searchState, openPlayer = { player -> nav.navigate("player/${player.id}") }, resultScroll = searchScroll) { teamId ->
                    nav.navigate("team/$teamId")
                }            }, navigationIcon = {
                if (route !in tabs.map { it.first }) IconButton({ nav.popBackStack() }) { Icon(HubIcons.Back, "Wstecz") }
            }, actions = {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.padding(end = HubSpace.md)) {
                    Text("DEMO", Modifier.padding(horizontal = HubSpace.sm, vertical = HubSpace.xs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        } } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().pointerInput(focusManager, route) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press && tabs.any { it.first == route }) focusManager.clearFocus()
                }
            }
        }) {
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
                composable("matches") { MatchesScreen(data, matchFilters, { matchesList = it }) { nav.navigate("match/$it") } }
                composable("leagues") { LeaguesScreen(data, leaguesList) { nav.navigate("league/$it") } }
                composable("league/{id}") { StandingsScreen(it.arguments?.getString("id")?.toIntOrNull() ?: -1, data, favorites, openMatch = { id -> nav.navigate("match/$id") }, back = { nav.popBackStack() }) { id -> nav.navigate("team/$id") } }
                composable("team/{id}?player={player}") {
                    TeamScreen(it.arguments?.getString("id")?.toIntOrNull() ?: -1, data, { id -> nav.navigate("match/$id") }, openTeam = { id -> nav.navigate("team/$id") { launchSingleTop = true } }, back = { nav.popBackStack() }, highlightedPlayerId = it.arguments?.getString("player")?.toIntOrNull(), openPlayer = { id -> nav.navigate("player/$id") }) { team ->
                        IconToggleButton(checked = team.id in favorites, onCheckedChange = { vm.favorite(team.id, it) }, enabled = !saving) {
                            Icon(if (team.id in favorites) HubIcons.StarFilled else HubIcons.Star,
                                if (team.id in favorites) "Usuń z ulubionych" else "Dodaj do ulubionych",
                                tint = if (team.id in favorites) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                composable("match/{id}") {
                    MatchDetailsScreen(it.arguments?.getString("id")?.toIntOrNull() ?: -1, data, { id -> nav.navigate("team/$id") }, { id -> nav.navigate("league/$id") }, openPlayer = { id -> nav.navigate("player/$id") }, actions = { match ->
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
                    }) { match ->
                        val own = entries.filter { e -> e.matchId == match.id }
                        if (own.isEmpty()) EmptyState("Nie masz jeszcze wpisów do tego meczu.")
                        own.forEach { e -> EntryCard(e, { nav.navigate("form/${e.matchId}/${e.kind}/${e.id}") }, { vm.deleteEntry(e.id) }) }
                    }
                }
                composable("player/{id}") {
                    PlayerScreen(it.arguments?.getString("id")?.toIntOrNull() ?: -1, data,
                        openMatch = { id -> nav.navigate("match/$id") }, openTeam = { id -> nav.navigate("team/$id") })
                }
                composable("favorites") { FavoritesScreen(data, favorites, { nav.navigate("team/$it") }, { vm.favorite(it, false) }, { nav.navigate("leagues") }, favoritesList) }
                composable("journal") { JournalScreen(data, entries, journalFilter, { journalFilter = it }, { nav.navigate("match/$it") }, { nav.navigate("form/${it.matchId}/${it.kind}/${it.id}") }, vm::deleteEntry, { nav.navigate("matches") }, journalList) }
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
            if (route == path) {
                mainLists[path]?.let { list ->
                    if (list.canScrollBackward && returnToTop?.isActive != true) returnToTop = scope.launch {
                        list.animateScrollToItem(0)
                        scroll.expand()
                    }
                }
            } else nav.navigate(path) { popUpTo("home"); launchSingleTop = true }
        }
    }
}








