package cn.yidongchi.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cn.yidongchi.app.*
import cn.yidongchi.core.*
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

val Up=Color(0xFFD65345)
val Down=Color(0xFF16856B)
val Amber=Color(0xFFA8751A)
val Accent=Color(0xFF226E65)
fun BigDecimal.money()=setScale(if(scale()>2)3 else 2,RoundingMode.HALF_UP).toPlainString()
fun BigDecimal.pct()=movePointRight(2).setScale(2,RoundingMode.HALF_UP).toPlainString()+"%"
fun openLink(context:Context,url:String){context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}
fun copyText(context:Context,text:String){(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("异动尺",text))}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun YidongApp(state:AppState) {
    val dark=when(state.theme){"dark"->true;"light"->false;else->isSystemInDarkTheme()}
    val colors=if(dark)darkColorScheme(primary=Color(0xFFB8D989),primaryContainer=Color(0xFF294E48),secondary=Color(0xFF87BCB3),secondaryContainer=Color(0xFF29413D),background=Color(0xFF101D23),surface=Color(0xFF172830))
        else lightColorScheme(primary=Accent,primaryContainer=Color(0xFFDFF0D9),secondary=Color(0xFF557B71),secondaryContainer=Color(0xFFE0ECE6),background=Color(0xFFF4F6F2),surface=Color(0xFFFFFFFF))
    MaterialTheme(colorScheme=colors) {
        var page by remember {mutableIntStateOf(0)}
        var settings by remember {mutableStateOf(false)}
        var intro by remember {mutableStateOf(state.store.value("intro")!="done")}
        val snackbar=remember {SnackbarHostState()}
        val owner=LocalLifecycleOwner.current
        DisposableEffect(owner){val observer=LifecycleEventObserver {_,e->if(e==Lifecycle.Event.ON_START)state.active=true else if(e==Lifecycle.Event.ON_STOP)state.active=false};owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}}
        LaunchedEffect(state.active){while(state.active){val now=LocalDateTime.now(ZoneId.of("Asia/Shanghai"));if(TradingCalendar.isTradingDay(now.toLocalDate())&&now.hour in 9..15)state.refresh();delay(15_000)}}
        LaunchedEffect(state.message){if(state.message.isNotBlank()){snackbar.showSnackbar(state.message);state.message=""}}
        Scaffold(topBar={Column(Modifier.statusBarsPadding().padding(horizontal=20.dp,vertical=12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("异动尺",fontSize=25.sp,fontWeight=FontWeight.Bold);Text("把每一档价格，算清楚。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                IconButton(onClick={state.refreshNow()}){Icon(Icons.Outlined.Refresh,"刷新行情")};IconButton(onClick={settings=true}){Icon(Icons.Outlined.Settings,"设置")}}
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=8.dp))
        }},snackbarHost={SnackbarHost(snackbar)},bottomBar={NavigationBar {
            val labels=listOf("自选","计算","推演","预警","规则")
            val icons=listOf(Icons.Outlined.StarOutline,Icons.Outlined.Calculate,Icons.Outlined.Timeline,Icons.Outlined.NotificationsNone,Icons.Outlined.MenuBook)
            labels.forEachIndexed {i,s->NavigationBarItem(selected=page==i,onClick={page=i},icon={Icon(icons[i],s)},label={Text(s)})}
        }}) {padding->
            PullToRefreshBox(isRefreshing=state.busy,onRefresh={state.refreshNow()},modifier=Modifier.fillMaxSize().padding(padding)) {when(page){
                0->WatchScreen(state,onOpen={state.select(it);page=1})
                1->CalculationScreen(state)
                2->ForecastScreen(state)
                3->EventsScreen(state)
                4->RulesScreen()
            }}
        }
        if(settings)SettingsDialog(state){settings=false}
        if(intro)AlertDialog(onDismissRequest={intro=false;state.store.put("intro","done")},title={Text("欢迎使用异动尺")},text={Text("先搜索一只股票，点星号加入自选。计算页看今天和明天的临界价；推演页查看未来十个交易日。点价格可查看公式和窗口，向下拉动页面可刷新。")},confirmButton={TextButton(onClick={intro=false;state.store.put("intro","done")}){Text("开始使用")}},dismissButton={TextButton(onClick={intro=false;state.store.put("intro","done")}){Text("跳过")}})
    }
}

@Composable fun Section(title:String,subtitle:String?=null,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical=6.dp)) {Text(title,fontSize=18.sp,fontWeight=FontWeight.SemiBold);if(subtitle!=null)Text(subtitle,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(10.dp));content()}
}
@Composable fun Panel(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface,tonalElevation=1.dp) {Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)}
}
@Composable fun Note(text:String,color:Color=MaterialTheme.colorScheme.onSurfaceVariant) {Text(text,fontSize=12.sp,color=color,lineHeight=18.sp)}
@Composable fun Pill(text:String,color:Color=Accent) {Surface(shape=RoundedCornerShape(8.dp),color=color.copy(alpha=.10f)){Text(text,Modifier.padding(horizontal=8.dp,vertical=4.dp),fontSize=11.sp,color=color)}}
@Composable fun SearchBox(state:AppState,onOpen:(String)->Unit) {
    Column {OutlinedTextField(state.searchText,{state.search(it)},Modifier.fillMaxWidth(),label={Text("股票代码 / 名称 / 粘贴内容")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=RoundedCornerShape(16.dp))
        state.suggestions.forEach {s->Row(Modifier.fillMaxWidth().clickable {state.searchText="";state.search("");onOpen(s.symbol)}.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text(s.name,Modifier.weight(1f));Text(s.code);IconButton(onClick={state.add(s)}){Icon(Icons.Outlined.Add,"加入自选")}}}
        if(state.searchText.isBlank()&&state.history.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){state.history.forEach {s->SuggestionChip(onClick={onOpen(s)},label={Text(s.drop(2))})}}
    }
}
@Composable fun SnapshotHeader(state:AppState,s:MarketSnapshot) {
    Panel {Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(s.quote.stock.name,fontSize=21.sp,fontWeight=FontWeight.Bold);Note("${s.quote.stock.code} · ${s.quote.stock.board.label} · ${s.quote.stock.board.currency}")};IconButton(onClick={if(state.watches.any {it.symbol==s.quote.stock.symbol})state.remove(s.quote.stock.symbol) else state.add(s.quote.stock)}){Icon(if(state.watches.any {it.symbol==s.quote.stock.symbol})Icons.Outlined.Star else Icons.Outlined.StarOutline,"切换自选")}}
        val change=s.quote.price.ratio(s.quote.previousClose).subtract(BigDecimal.ONE)
        Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(s.quote.price.money(),fontSize=if(state.large)44.sp else 36.sp,fontWeight=FontWeight.Bold,color=if(change.signum()>=0)Up else Down);Text(change.pct(),color=if(change.signum()>=0)Up else Down,modifier=Modifier.padding(bottom=6.dp))}
        Note("行情 ${s.quote.time.format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))} · ${s.provider}")
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){Pill("条件估算",Amber);if(s.quote.stock.board==Board.STAR)Pill("科创累计口径待补证",Amber);if(state.errors.containsKey(s.quote.stock.symbol))Pill("缓存",Amber)}
        state.errors[s.quote.stock.symbol]?.let {Note(it,Amber)}
    }
}
