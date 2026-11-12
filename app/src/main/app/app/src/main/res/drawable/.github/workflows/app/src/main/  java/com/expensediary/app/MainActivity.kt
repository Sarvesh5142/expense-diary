package com.expensediary.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private val Deep = Color(0xFF0B5D3B)
private val Green = Color(0xFF1E8E5A)
private val Canvas = Color(0xFFF4FAF6)
private val Ink = Color(0xFF12301F)
private val Muted = Color(0xFF4F6B5C)
private val Danger = Color(0xFFB3261E)

data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val section: String,
    val name: String,
    val amount: Double,
    val date: String,
    val note: String
) {
    fun json() = JSONObject().put("id", id).put("section", section)
        .put("name", name).put("amount", amount).put("date", date).put("note", note)

    companion object {
        fun from(o: JSONObject) = Entry(
            o.optString("id", UUID.randomUUID().toString()),
            o.optString("section"), o.optString("name"),
            o.optDouble("amount"), o.optString("date"), o.optString("note")
        )
    }
}

class SectionInfo(
    val key: String,
    val title: String,
    val hint: String,
    val icon: ImageVector,
    val bg: Color,
    val tint: Color,
    val nameLabel: String
)

private val allSections = listOf(
    SectionInfo("Medicine", "Medicine", "Medicine name and price", Icons.Default.Medication, Color(0xFFDDF1E5), Color(0xFF0B6B45), "Medicine name"),
    SectionInfo("People", "People", "Whom I gave money to", Icons.Default.People, Color(0xFFE2F4DF), Color(0xFF2E7D32), "Person's name"),
    SectionInfo("Other Expenses", "Other", "Anything else I spent on", Icons.Default.EditNote, Color(0xFFD8F1EA), Color(0xFF00796B), "What was it for?")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ExpenseDiaryApp() }
    }
}

@Composable
fun Modifier.bounceClick(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

@Composable
fun animatedMoney(total: Double): String {
    val v by animateFloatAsState(total.toFloat(), tween(800, easing = FastOutSlowInEasing), label = "amount")
    return "₹" + money(v.toDouble())
}

@Composable
fun Footer() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Favorite, contentDescription = null, tint = Green, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("App created by Sarvesh", fontSize = 15.sp, color = Muted, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ExpenseDiaryApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("expense_diary", 0) }
    var entries by remember {
        mutableStateOf(runCatching {
            val arr = JSONArray(prefs.getString("entries", "[]"))
            List(arr.length()) { Entry.from(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList()))
    }
    fun persist(list: List<Entry>) {
        entries = list
        val arr = JSONArray()
        list.forEach { arr.put(it.json()) }
        prefs.edit().putString("entries", arr.toString()).apply()
    }

    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = openKey != null) { openKey = null }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green, onPrimary = Color.White,
            background = Canvas, surface = Color.White, onSurface = Ink
        )
    ) {
        Surface(color = Canvas, modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = openKey,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(380))) togetherWith
                            (slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it / 3 } + fadeOut(tween(250)))
                    } else {
                        (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(380))) togetherWith
                            (slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(250)))
                    }
                },
                label = "screens"
            ) { key ->
                val current = allSections.firstOrNull { it.key == key }
                if (current == null) {
                    HomeScreen(entries, onOpen = { openKey = it.key })
                } else {
                    SectionScreen(
                        info = current,
                        entries = entries.filter { it.section == current.key },
                        onBack = { openKey = null },
                        onSave = { e ->
                            persist(
                                if (entries.any { it.id == e.id }) entries.map { if (it.id == e.id) e else it }
                                else listOf(e) + entries
                            )
                        },
                        onDelete = { e -> persist(entries.filterNot { it.id == e.id }) }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(entries: List<Entry>, onOpen: (SectionInfo) -> Unit) {
    val visible = entries.filter { e -> allSections.any { it.key == e.section } }
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val totalText = animatedMoney(visible.sumOf { it.amount })

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                AnimatedVisibility(
                    visible = appeared,
                    enter = fadeIn(tween(500)) + slideInVertically(tween(500, easing = FastOutSlowInEasing)) { -it / 3 }
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(Brush.linearGradient(listOf(Deep, Green)))
                            .padding(22.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).background(Color.White.copy(alpha = 0.2f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Text("Expense Diary", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("Total written so far", color = Color(0xFFCDEBDB), fontSize = 18.sp)
                        Text(totalText, color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            item {
                Text("Touch a box to write or see your records", fontSize = 19.sp, color = Muted)
            }
            itemsIndexed(allSections, key = { _, s -> s.key }) { index, s ->
                val list = entries.filter { it.section == s.key }
                val delay = 150 + index * 130
                AnimatedVisibility(
                    visible = appeared,
                    enter = fadeIn(tween(450, delayMillis = delay)) +
                        slideInVertically(tween(450, delayMillis = delay, easing = FastOutSlowInEasing)) { it / 2 }
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().bounceClick { onOpen(s) },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(68.dp).background(s.bg, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(s.icon, contentDescription = null, tint = s.tint, modifier = Modifier.size(36.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Ink)
                                Text(s.hint, fontSize = 18.sp, color = Muted)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (list.isEmpty()) "Nothing written yet" else "${list.size} written  •  ₹${money(list.sumOf { it.amount })}",
                                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = s.tint
                                )
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = Green, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
        Footer()
    }
}

@Composable
fun SectionScreen(
    info: SectionInfo,
    entries: List<Entry>,
    onBack: () -> Unit,
    onSave: (Entry) -> Unit,
    onDelete: (Entry) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Entry?>(null) }
    var deleting by remember { mutableStateOf<Entry?>(null) }
    val totalText = animatedMoney(entries.sumOf { it.amount })

    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.035f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(56.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Deep, modifier = Modifier.size(32.dp))
            }
            Text(info.title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(Deep, Green)))
                        .padding(20.dp)
                ) {
                    Text("Total in ${info.title}", fontSize = 18.sp, color = Color(0xFFCDEBDB))
                    Text(totalText, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
            if (entries.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(info.icon, contentDescription = null, tint = Green, modifier = Modifier.size(56.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Nothing written yet", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text("Press the big button below to add", fontSize = 18.sp, color = Muted)
                    }
                }
            } else {
                items(entries, key = { it.id }) { e ->
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth().animateItem().bounceClick { editing = e; showDialog = true }
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(e.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Text("₹${money(e.amount)}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = info.tint)
                            }
                            Text(e.date, fontSize = 17.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
                            if (e.note.isNotBlank()) {
                                Text(e.note, fontSize = 18.sp, color = Ink, modifier = Modifier.padding(top = 8.dp))
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(
                                    onClick = { editing = e; showDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Deep),
                                    modifier = Modifier.weight(1f).height(50.dp)
                                ) {
                                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Edit", fontSize = 18.sp)
                                }
                                OutlinedButton(
                                    onClick = { deleting = e },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                                    modifier = Modifier.weight(1f).height(50.dp)
                                ) {
                                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Delete", fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = { editing = null; showDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .height(66.dp)
                .graphicsLayer { scaleX = pulseScale; scaleY = pulseScale }
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(8.dp))
            Text("Add new", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Footer()
    }

    if (showDialog) {
        EntryDialog(
            info = info, initial = editing,
            onDismiss = { showDialog = false; editing = null },
            onSave = { onSave(it); showDialog = false; editing = null }
        )
    }

    val toDelete = deleting
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Color.White,
            title = { Text("Delete this?", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink) },
            text = { Text("${toDelete.name}  •  ₹${money(toDelete.amount)}", fontSize = 20.sp, color = Ink) },
            confirmButton = {
                Button(
                    onClick = { onDelete(toDelete); deleting = null },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger)
                ) { Text("Yes, delete", fontSize = 18.sp) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("No, keep it", fontSize = 18.sp, color = Deep) } },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun EntryDialog(info: SectionInfo, initial: Entr
