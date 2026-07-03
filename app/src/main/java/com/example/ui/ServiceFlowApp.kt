package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.layout.layout
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

enum class AppTab(val title: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Default.Dashboard),
    Cases("Active Cases", Icons.Default.Assignment),
    Roster("Agent Roster", Icons.Default.SupportAgent),
    AuditTrail("Audit Trail", Icons.Default.History),
    Settings("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceFlowApp(viewModel: ServiceFlowViewModel = viewModel()) {
    val cases by viewModel.allCases.collectAsState()
    val agents by viewModel.allAgents.collectAsState()
    val logs by viewModel.allAuditLogs.collectAsState()
    val notifications by viewModel.notifications.collectAsState()
    val isAnalyzing by viewModel.isAnalyzingCase.collectAsState()

    var currentTab by remember { mutableStateOf(AppTab.Dashboard) }
    var showIngestDialog by remember { mutableStateOf(false) }
    var expandedNotificationCenter by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "ServiceFlow AI",
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Notification center bell
                    Box {
                        IconButton(onClick = { expandedNotificationCenter = !expandedNotificationCenter }) {
                            Icon(
                                imageVector = if (notifications.isNotEmpty()) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = "System Notifications",
                                tint = if (notifications.isNotEmpty()) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (notifications.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 8.dp, end = 8.dp)
                                    .size(8.dp)
                                    .background(Color(0xFFE53935), CircleShape)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                )
            )
        },
        floatingActionButton = {
            if (currentTab == AppTab.Cases || currentTab == AppTab.Dashboard) {
                FloatingActionButton(
                    onClick = { showIngestDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("ingest_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Ingest Support Case")
                }
            }
        },
        bottomBar = {
            if (!isTablet) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                ) {
                    AppTab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title, fontSize = 10.sp) },
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isTablet) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    modifier = Modifier.width(96.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    AppTab.values().forEach { tab ->
                        NavigationRailItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            modifier = Modifier.testTag("nav_rail_${tab.name.lowercase()}")
                        )
                    }
                }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Main screens
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "TabContent"
                ) { targetTab ->
                    when (targetTab) {
                        AppTab.Dashboard -> DashboardScreen(cases, agents, viewModel)
                        AppTab.Cases -> CasesScreen(cases, agents, viewModel)
                        AppTab.Roster -> RosterScreen(agents, viewModel)
                        AppTab.AuditTrail -> AuditTrailScreen(logs)
                        AppTab.Settings -> SettingsScreen(viewModel)
                    }
                }

                // Inline Notification alert popup
                if (notifications.isNotEmpty() && !expandedNotificationCenter) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                            .fillMaxWidth()
                    ) {
                        val latestAlert = notifications.first()
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = when (latestAlert.type) {
                                    "BREACH" -> Color(0xFFFEEBEE)
                                    "WARNING" -> Color(0xFFFFF3E0)
                                    "INGESTION" -> Color(0xFFE1F5FE)
                                    else -> Color(0xFFE8F5E9)
                                }
                            ),
                            elevation = CardDefaults.cardElevation(6.dp),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = when (latestAlert.type) {
                                        "BREACH" -> Icons.Default.ReportProblem
                                        "WARNING" -> Icons.Default.Warning
                                        "INGESTION" -> Icons.Default.CloudUpload
                                        else -> Icons.Default.CheckCircle
                                    },
                                    contentDescription = null,
                                    tint = when (latestAlert.type) {
                                        "BREACH" -> Color(0xFFD32F2F)
                                        "WARNING" -> Color(0xFFF57C00)
                                        "INGESTION" -> Color(0xFF0288D1)
                                        else -> Color(0xFF388E3C)
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = latestAlert.message,
                                    color = Color.Black,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Full Notification Center slide down overlay
                if (expandedNotificationCenter) {
                    NotificationCenterDialog(
                        notifications = notifications,
                        onDismiss = { expandedNotificationCenter = false }
                    )
                }
            }
        }
    }

    // New Ticket Ingestion Dialog
    if (showIngestDialog) {
        IngestCaseDialog(
            isAnalyzing = isAnalyzing,
            onIngest = { title, desc, channel, topic, urgency, tier ->
                viewModel.ingestSupportCase(title, desc, channel, topic, urgency, tier)
                showIngestDialog = false
            },
            onDismiss = { showIngestDialog = false }
        )
    }
}

// ==========================================
// SCREEN 1: DASHBOARD
// ==========================================
@Composable
fun DashboardScreen(
    cases: List<CaseEntity>,
    agents: List<AgentEntity>,
    viewModel: ServiceFlowViewModel
) {
    val totalCases = cases.size
    val activeCases = cases.filter { it.status != "Resolved" }
    val resolvedCases = cases.filter { it.status == "Resolved" }
    val escalatedCases = cases.filter { it.status == "Escalated" }

    val slaBreachedCount = cases.count { it.slaStatus == "Breached" }
    val slaCompliantPercent = if (totalCases > 0) {
        ((totalCases - slaBreachedCount) * 100 / totalCases)
    } else 100

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "ServiceNow Case Operations Desk",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Real-time SLA tracking and workload balancing metrics",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Operational Metrics Cards Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    title = "Active Queue",
                    value = activeCases.size.toString(),
                    subtext = "${escalatedCases.size} Escalated",
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "SLA Compliance",
                    value = "$slaCompliantPercent%",
                    subtext = "$slaBreachedCount Breaches",
                    color = if (slaCompliantPercent >= 80) Color(0xFFE8F5E9) else Color(0xFFFEEBEE),
                    textColor = if (slaCompliantPercent >= 80) Color(0xFF2E7D32) else Color(0xFFC62828),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Subtitle: Agent Workloads
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Agent Workload & Capacity Balancing",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (agents.isEmpty()) {
                        Text("No active agents configured.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        agents.forEach { agent ->
                            val workloadFraction = agent.currentWorkload.toFloat() / agent.maxWorkload.toFloat()
                            val progressColor = when {
                                !agent.isAvailable -> Color.Gray
                                workloadFraction >= 1.0f -> Color(0xFFD32F2F) // Full capacity
                                workloadFraction >= 0.75f -> Color(0xFFF57C00) // Warning load
                                else -> MaterialTheme.colorScheme.primary
                            }

                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(if (agent.isAvailable) Color(0xFF4CAF50) else Color.Gray)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(agent.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("(${agent.skills.split(",").firstOrNull() ?: ""})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(
                                        text = if (agent.isAvailable) "${agent.currentWorkload}/${agent.maxWorkload} Cases" else "OFFLINE",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = progressColor
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { if (agent.isAvailable) workloadFraction.coerceIn(0f, 1f) else 0f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = progressColor,
                                    trackColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottleneck Analysis
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Incoming Channel Routing Overview",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val channels = listOf("Web", "Email", "Chat", "Phone")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        channels.forEach { channel ->
                            val count = cases.count { it.channel == channel }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = when (channel) {
                                        "Web" -> Icons.Default.Language
                                        "Email" -> Icons.Default.Email
                                        "Chat" -> Icons.Default.Chat
                                        else -> Icons.Default.Phone
                                    },
                                    contentDescription = channel,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(channel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(count.toString(), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    subtext: String,
    color: Color,
    textColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = modifier.height(110.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (textColor != Color.Unspecified) textColor.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = if (textColor != Color.Unspecified) textColor else MaterialTheme.colorScheme.onSurface)
            Text(subtext, fontSize = 11.sp, color = if (textColor != Color.Unspecified) textColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ==========================================
// SCREEN 2: ACTIVE CASES FEED
// ==========================================
@Composable
fun CasesScreen(
    cases: List<CaseEntity>,
    agents: List<AgentEntity>,
    viewModel: ServiceFlowViewModel
) {
    var selectedFilterStatus by remember { mutableStateOf("All") }
    var selectedFilterTopic by remember { mutableStateOf("All") }
    var expandedCaseId by remember { mutableStateOf<Int?>(null) }
    var showResolutionDialogCase by remember { mutableStateOf<CaseEntity?>(null) }

    val filteredCases = cases.filter { case ->
        val matchesStatus = when (selectedFilterStatus) {
            "All" -> true
            "Active" -> case.status != "Resolved"
            else -> case.status.equals(selectedFilterStatus, ignoreCase = true)
        }
        val matchesTopic = if (selectedFilterTopic == "All") true else case.topic.equals(selectedFilterTopic, ignoreCase = true)
        matchesStatus && matchesTopic
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Service Request Queue",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal filter bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status filter chip
            var statusExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                Button(
                    onClick = { statusExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Icon(imageVector = Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Status: $selectedFilterStatus", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                    listOf("All", "Active", "New", "Assigned", "Escalated", "Resolved").forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status) },
                            onClick = {
                                selectedFilterStatus = status
                                statusExpanded = false
                            }
                        )
                    }
                }
            }

            // Topic filter chip
            var topicExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                Button(
                    onClick = { topicExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                ) {
                    Icon(imageVector = Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Topic: $selectedFilterTopic", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = topicExpanded, onDismissRequest = { topicExpanded = false }) {
                    listOf("All", "Hardware", "Software", "Network", "Account").forEach { topic ->
                        DropdownMenuItem(
                            text = { Text(topic) },
                            onClick = {
                                selectedFilterTopic = topic
                                topicExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredCases.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No cases matched current filters.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredCases, key = { it.id }) { case ->
                    CaseItemRow(
                        case = case,
                        isExpanded = expandedCaseId == case.id,
                        onClick = {
                            expandedCaseId = if (expandedCaseId == case.id) null else case.id
                        },
                        onResolve = { showResolutionDialogCase = case },
                        onEscalate = { viewModel.escalateCase(case) },
                        onReRoute = { viewModel.triggerReRoute(case.id) }
                    )
                }
            }
        }
    }

    if (showResolutionDialogCase != null) {
        ResolutionNotesDialog(
            case = showResolutionDialogCase!!,
            onConfirm = { notes ->
                viewModel.resolveCase(showResolutionDialogCase!!, notes)
                showResolutionDialogCase = null
            },
            onDismiss = { showResolutionDialogCase = null }
        )
    }
}

@Composable
fun CaseItemRow(
    case: CaseEntity,
    isExpanded: Boolean,
    onClick: () -> Unit,
    onResolve: () -> Unit,
    onEscalate: () -> Unit,
    onReRoute: () -> Unit
) {
    val urgencyColor = when (case.urgency) {
        "Critical" -> Color(0xFFC62828)
        "High" -> Color(0xFFE65100)
        "Medium" -> Color(0xFFF9A825)
        else -> Color(0xFF2E7D32)
    }

    val statusContainerColor = when (case.status) {
        "Resolved" -> Color(0xFFE8F5E9)
        "Escalated" -> Color(0xFFFEEBEE)
        "Assigned" -> Color(0xFFE3F2FD)
        else -> Color(0xFFECEFF1)
    }

    val statusTextColor = when (case.status) {
        "Resolved" -> Color(0xFF2E7D32)
        "Escalated" -> Color(0xFFC62828)
        "Assigned" -> Color(0xFF1565C0)
        else -> Color(0xFF37474F)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("case_card_${case.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Urgency & Customer Tier Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(urgencyColor, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            case.urgency,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFFFECE0), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            case.customerTier,
                            color = Color(0xFFD84315),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = case.topic,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp
                    )
                }

                // Ingestion channel icon
                Icon(
                    imageVector = when (case.channel) {
                        "Web" -> Icons.Default.Language
                        "Email" -> Icons.Default.Email
                        "Chat" -> Icons.Default.Chat
                        else -> Icons.Default.Phone
                    },
                    contentDescription = case.channel,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Case Title
            Text(
                text = "#${case.id} - ${case.title}",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Assignee & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (case.agentName != null) "Assignee: ${case.agentName}" else "Assignee: Unassigned Queue",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                Box(
                    modifier = Modifier
                        .background(statusContainerColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        case.status,
                        color = statusTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Dynamic live SLA Countdown Row
            if (case.status != "Resolved") {
                LiveSlaCountdown(deadline = case.slaDeadline, initialSlaStatus = case.slaStatus)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF388E3C), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SLA Met (Resolved)", fontSize = 11.sp, color = Color(0xFF388E3C), fontWeight = FontWeight.Bold)
                }
            }

            // Expanded detail panel
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Description",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = case.description,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (case.notes != null) {
                    Text(
                        "Resolution Notes",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF2E7D32)
                    )
                    Text(
                        text = case.notes,
                        fontSize = 13.sp,
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                }

                // Operator Action buttons
                if (case.status != "Resolved") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onResolve,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("resolve_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resolve", fontSize = 11.sp)
                        }

                        if (case.status != "Escalated") {
                            Button(
                                onClick = onEscalate,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("escalate_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                            ) {
                                Icon(imageVector = Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Escalate", fontSize = 11.sp)
                            }
                        }

                        if (case.agentId == null) {
                            Button(
                                onClick = onReRoute,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reroute_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(imageVector = Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Auto-Route", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LiveSlaCountdown(deadline: Long, initialSlaStatus: String) {
    var timeLeftText by remember { mutableStateOf("") }
    var slaStatus by remember { mutableStateOf(initialSlaStatus) }

    LaunchedEffect(deadline) {
        while (true) {
            val now = System.currentTimeMillis()
            val diff = deadline - now
            if (diff <= 0) {
                timeLeftText = "SLA BREACHED!"
                slaStatus = "Breached"
            } else {
                val seconds = (diff / 1000) % 60
                val minutes = (diff / (1000 * 60)) % 60
                timeLeftText = String.format("%02d:%02d remaining", minutes, seconds)
                if (diff < 45000) {
                    slaStatus = "Warning"
                }
            }
            delay(1000)
        }
    }

    val badgeColor = when (slaStatus) {
        "Breached" -> Color(0xFFD32F2F)
        "Warning" -> Color(0xFFEF6C00)
        else -> Color(0xFF2E7D32)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(badgeColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "SLA Countdown: $timeLeftText",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = badgeColor
        )
    }
}

// ==========================================
// SCREEN 3: AGENT ROSTER
// ==========================================
@Composable
fun RosterScreen(
    agents: List<AgentEntity>,
    viewModel: ServiceFlowViewModel
) {
    var showOnboardDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Service Agent Directory",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    "Engineers skills & active workloads registry",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { showOnboardDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(imageVector = Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Onboard Agent", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (agents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No agents registered.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(agents, key = { it.id }) { agent ->
                    AgentRowCard(
                        agent = agent,
                        onToggleAvailability = { viewModel.toggleAgentAvailability(agent) }
                    )
                }
            }
        }
    }

    if (showOnboardDialog) {
        OnboardAgentDialog(
            onOnboard = { name, skills, certs, maxLoad ->
                viewModel.addAgent(name, "avatar", skills, certs, maxLoad)
                showOnboardDialog = false
            },
            onDismiss = { showOnboardDialog = false }
        )
    }
}

@Composable
fun AgentRowCard(
    agent: AgentEntity,
    onToggleAvailability: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("agent_card_${agent.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle Avatar showing initials
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = agent.name.split(" ").map { it.firstOrNull() ?: "" }.joinToString("").take(2).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Profile info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = agent.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = Color(0xFFF9A825), modifier = Modifier.size(12.dp))
                    Text(agent.tierRating.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Text(
                    text = "Certifications: ${agent.certifications}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Skills tags row
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    agent.skills.split(",").forEach { skill ->
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(skill.trim(), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Workload Capacity + Online toggle
            Column(horizontalAlignment = Alignment.End) {
                Switch(
                    checked = agent.isAvailable,
                    onCheckedChange = { onToggleAvailability() },
                    modifier = Modifier.scale(0.8f).testTag("agent_toggle_${agent.id}")
                )
                Text(
                    text = if (agent.isAvailable) "Active: ${agent.currentWorkload}/${agent.maxWorkload}" else "Offline",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (agent.isAvailable) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }
        }
    }
}

// Extension to scale Composable switches
fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout((placeable.width * scale).toInt(), (placeable.height * scale).toInt()) {
            placeable.placeWithLayer(0, 0) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
)

// ==========================================
// SCREEN 4: AUDIT TRAIL LOGS
// ==========================================
@Composable
fun AuditTrailScreen(logs: List<AuditLogEntity>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "ServiceNow Workflow Ledger",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            "Immutable audit trail for compliance and continuous improvement",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (logs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Ledger is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs) { log ->
                    AuditLogItemCard(log)
                }
            }
        }
    }
}

@Composable
fun AuditLogItemCard(log: AuditLogEntity) {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val formattedTime = sdf.format(Date(log.timestamp))

    val colorAccent = when (log.action) {
        "Case Ingested" -> Color(0xFF0288D1)
        "Agent Assigned" -> Color(0xFF388E3C)
        "Case Resolved" -> Color(0xFF2E7D32)
        "SLA Escalation", "SLA Warning" -> Color(0xFFD32F2F)
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colorAccent)
                    .padding(top = 6.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(log.action, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = colorAccent)
                    Text(formattedTime, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (log.caseId > 0) {
                    Text("Ticket #${log.caseId}: ${log.caseTitle}", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
                }
                Text(log.details, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

// ==========================================
// SCREEN 5: SETTINGS & SYSTEM CONFIG
// ==========================================
@Composable
fun SettingsScreen(viewModel: ServiceFlowViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "System Configuration Panel",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("ServiceNow Integration Status", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Live Web/Chat Connectors", fontSize = 13.sp)
                    Box(modifier = Modifier.background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("Active & Ready", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SLA Escalations Rules Daemon", fontSize = 13.sp)
                    Box(modifier = Modifier.background(Color(0xFFE8F5E9), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                        Text("Online", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Gemini AI Categorizer Status", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                val apiKey = com.example.BuildConfig.GEMINI_API_KEY
                val isGeminiConnected = apiKey.isNotEmpty() && apiKey != "MY_GEMINI_API_KEY"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Connection Mode", fontSize = 13.sp)
                    Box(
                        modifier = Modifier
                            .background(
                                if (isGeminiConnected) Color(0xFFE8F5E9) else Color(0xFFFFECE0),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isGeminiConnected) "Gemini REST Active" else "Offline Local Heuristics",
                            color = if (isGeminiConnected) Color(0xFF2E7D32) else Color(0xFFD84315),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isGeminiConnected) {
                        "Successfully authenticated with your AI Studio Secrets. Gemini-3.5-flash will automatically process raw inbound emails, chats, and calls into precise urgency levels, customer tiers, and topics."
                    } else {
                        "No custom Gemini API key was detected in the AI Studio Secrets panel. The app has activated a high-performance local heuristics compiler to categorize cases instantly offline."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.resetDatabase() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Clear & Hard Reset Database")
        }
    }
}

// ==========================================
// DIALOGS & OVERLAYS
// ==========================================

@Composable
fun NotificationCenterDialog(
    notifications: List<NotificationAlert>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notification Center", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (notifications.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No notifications.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(notifications) { alert ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (alert.type) {
                                                "BREACH" -> Color(0xFFD32F2F)
                                                "WARNING" -> Color(0xFFF57C00)
                                                "INGESTION" -> Color(0xFF0288D1)
                                                else -> Color(0xFF388E3C)
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(alert.message, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardAgentDialog(
    onOnboard: (name: String, skills: String, certs: String, maxLoad: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var skills by remember { mutableStateOf("Software") }
    var certs by remember { mutableStateOf("CompTIA A+, ITIL") }
    var maxLoad by remember { mutableStateOf("5") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Onboard New Service Agent", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Agent Name") },
                    modifier = Modifier.fillMaxWidth().testTag("agent_name_input")
                )

                OutlinedTextField(
                    value = skills,
                    onValueChange = { skills = it },
                    label = { Text("Skills (comma-separated: e.g. Network,Hardware)") },
                    modifier = Modifier.fillMaxWidth().testTag("agent_skills_input")
                )

                OutlinedTextField(
                    value = certs,
                    onValueChange = { certs = it },
                    label = { Text("Certifications (e.g. ITIL, CCNA)") },
                    modifier = Modifier.fillMaxWidth().testTag("agent_certs_input")
                )

                OutlinedTextField(
                    value = maxLoad,
                    onValueChange = { maxLoad = it },
                    label = { Text("Maximum Workload Capacity") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("agent_load_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onOnboard(name, skills, certs, maxLoad.toIntOrNull() ?: 5)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Onboard Profile")
                    }
                }
            }
        }
    }
}

@Composable
fun ResolutionNotesDialog(
    case: CaseEntity,
    onConfirm: (notes: String) -> Unit,
    onDismiss: () -> Unit
) {
    var notes by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Complete Service Request #${case.id}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Provide detailed troubleshooting or root cause notes below to close this incident.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Resolution & Root Cause Notes") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("resolution_notes_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (notes.isNotBlank()) {
                                onConfirm(notes)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                    ) {
                        Text("Resolve Incident")
                    }
                }
            }
        }
    }
}

@Composable
fun IngestCaseDialog(
    isAnalyzing: Boolean,
    onIngest: (title: String, desc: String, channel: String, topic: String, urgency: String, tier: String) -> Unit,
    onDismiss: () -> Unit
) {
    var rawTitle by remember { mutableStateOf("") }
    var rawDesc by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf("Web") }
    var urgency by remember { mutableStateOf("Medium") }
    var topic by remember { mutableStateOf("Software") }
    var tier by remember { mutableStateOf("Bronze") }

    var smartAiIngestMode by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Text(
                    text = "ServiceNow Integration Intake",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Ingest new support request into the auto-routing queue.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Mode Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (smartAiIngestMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { smartAiIngestMode = true }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = if (smartAiIngestMode) Color.White else MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("AI Auto-Classify", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (smartAiIngestMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!smartAiIngestMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { smartAiIngestMode = false }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = if (!smartAiIngestMode) Color.White else MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Manual Input", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (!smartAiIngestMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        Text("Ingestion Channel", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Web", "Email", "Chat", "Phone").forEach { mode ->
                                Button(
                                    onClick = { channel = mode },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (channel == mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (channel == mode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(mode, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    if (!smartAiIngestMode) {
                        item {
                            OutlinedTextField(
                                value = rawTitle,
                                onValueChange = { rawTitle = it },
                                label = { Text("Case Title") },
                                modifier = Modifier.fillMaxWidth().testTag("case_title_input")
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = rawDesc,
                            onValueChange = { rawDesc = it },
                            label = { Text("Inbound Body / Problem Description") },
                            placeholder = { Text(if (smartAiIngestMode) "e.g. CEO's workspace network disconnected. Urgent battery replacements needed." else "Full request details...") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth().testTag("case_description_input")
                        )
                    }

                    if (!smartAiIngestMode) {
                        item {
                            Text("Topic Category", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("Hardware", "Software", "Network", "Account").forEach { cat ->
                                    Button(
                                        onClick = { topic = cat },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (topic == cat) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (topic == cat) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(cat, fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        item {
                            Text("Ticket Urgency", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("Low", "Medium", "High", "Critical").forEach { urg ->
                                    Button(
                                        onClick = { urgency = urg },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (urgency == urg) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (urgency == urg) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(urg, fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        item {
                            Text("Customer Tier", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("Bronze", "Silver", "Gold", "Enterprise").forEach { t ->
                                    Button(
                                        onClick = { tier = t },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (tier == t) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = if (tier == t) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(t, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isAnalyzing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("AI Engine classifying request metadata...", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isAnalyzing) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (rawDesc.isNotBlank()) {
                                onIngest(rawTitle, rawDesc, channel, topic, urgency, tier)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = rawDesc.isNotBlank() && !isAnalyzing,
                        modifier = Modifier.testTag("submit_ingest_button")
                    ) {
                        Icon(imageVector = if (smartAiIngestMode) Icons.Default.AutoAwesome else Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (smartAiIngestMode) "Analyze & Route" else "Ingest Case")
                    }
                }
            }
        }
    }
}
