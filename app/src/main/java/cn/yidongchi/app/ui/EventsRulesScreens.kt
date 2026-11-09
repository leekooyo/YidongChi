package cn.yidongchi.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.yidongchi.app.*
import cn.yidongchi.core.*
import org.json.JSONArray
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable fun EventsScreen(state:AppState) {
    val workspace=state.workspaces.warnings
    val symbols=state.warningSymbols()
    val visibleAlerts=state.alerts.filter{it.symbol in symbols}
    var recordSymbol by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(symbols){state.ensureWarningData(symbols)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp).testTag("warning-list"),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item {
            Section("异动与预警") {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(!state.warningsAll,{state.setWarningScope(false)},label={Text("当前股票")})
                    FilterChip(state.warningsAll,{state.setWarningScope(true)},label={Text("全部自选")})
                }
                if(!state.warningsAll)SearchBox(state,{state.select(it,workspace)},workspace)
            }
        }
        item {Panel {
            Text("应用内提醒",fontWeight=FontWeight.Bold);Note("前台提醒 · 接近阈值 ${state.threshold}%")
            if(visibleAlerts.isEmpty())Text("暂无提醒")
            visibleAlerts.reversed().forEach{a->TextButton(onClick={state.readAlert(a)}){Text("${if(a.read)"已读"else "●"} ${a.text}",color=if(a.read)MaterialTheme.colorScheme.onSurfaceVariant else Amber)}}
        }}
        if(symbols.isEmpty())item{Panel{Text("暂无自选股票")}}
        items(symbols,key={it}) {symbol->EventStockSection(state,symbol){recordSymbol=symbol}}
    }
    recordSymbol?.let {target->key(target){
        var date by remember{mutableStateOf(state.snapshots[target]?.quote?.time?.toLocalDate()?.toString()?:"")}
        var kind by remember{mutableStateOf("severe10")};var direction by remember{mutableStateOf(Direction.UP)};var url by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
        AlertDialog(onDismissRequest={recordSymbol=null},title={Text("补充已核对事件 · ${target.drop(2)}")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Note("保存表示你已打开原公告核对；本 APP 不自动认证链接内容。")
            OutlinedTextField(date,{date=it},label={Text("交易所确认的事件日 YYYY-MM-DD")})
            listOf("normal" to "普通异动","severe10" to "10日严重异动","severe30" to "30日严重异动").forEach {(k,t)->FilterChip(kind==k,{kind=k},label={Text(t)})}
            Row{FilterChip(direction==Direction.UP,{direction=Direction.UP},label={Text("上行")});FilterChip(direction==Direction.DOWN,{direction=Direction.DOWN},label={Text("下行")})}
            OutlinedTextField(url,{url=it},label={Text("原公告 HTTPS 链接")});if(error.isNotBlank())Note(error,Up)
        }},confirmButton={TextButton(onClick={try{state.addEvent(date,kind,direction,url,symbol=target);recordSymbol=null}catch(e:Exception){error=e.message?:"日期或链接有误"}}){Text("保存记录")}},dismissButton={TextButton(onClick={recordSymbol=null}){Text("取消")}})
    }}
}

@Composable private fun EventStockSection(state:AppState,symbol:String,onRecord:()->Unit) {
    val s=state.snapshots[symbol]
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    if(s==null) {
        Panel(Modifier.testTag("warning-stock-$symbol")) {
            Text("${state.watches.firstOrNull{it.symbol==symbol}?.name?:symbol.drop(2)} · ${symbol.drop(2)}",fontWeight=FontWeight.Bold)
            Text(state.errors[symbol]?:"正在读取该股票行情")
            TextButton(onClick={state.refreshStock(symbol)}){Text("重试行情")}
        }
        return
    }
    val status=state.eventStatuses[symbol]?:"尚未加载记录"
    val recent=state.recent(s)
    val resets=state.resets(symbol)
    val replayAnalyses by produceState<List<Analysis>?>(null,s,resets) {
        value=null
        value=withContext(Dispatchers.Default){PriceEngine.replay(s.quote.stock,s.bars,s.indexBars,resets)}
    }
    Column(Modifier.fillMaxWidth().testTag("warning-stock-$symbol"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("${s.quote.stock.name} · ${s.quote.stock.code}",fontWeight=FontWeight.Bold)
        Note("最近10个交易日 · 截至 ${s.indexQuote.time.toLocalDate()}${if(symbol in state.errors)" · 缓存"else ""}")
        Pill(if(status.contains("不可用"))"事件源不可用"else if(status.contains("正在"))"加载中"else "事件记录待核实",Amber)
        Row{TextButton(onClick={scope.launch{state.loadEvents(symbol)}}){Text("刷新事件源")};TextButton(onClick=onRecord){Text("按原公告补充确认记录")}}
        val counts=EventLogic.directionCounts(recent,s.indexBars.map{it.date},s.quote.time.toLocalDate(),s.quote.stock.board,resets.severe)
        Panel {
            Text("10日内同向普通异动次数",fontWeight=FontWeight.Bold);Text("上行 ${counts.up}/${counts.threshold} · 下行 ${counts.down}/${counts.threshold}")
            if(counts.triggered)Pill("已确认记录满足同向多次严重异动条件",Up)
            Pill("按已确认记录计数",Amber)
        }
        if(recent.isEmpty())Panel{Text("暂无已收录事件");Pill("覆盖待核实",Amber)}
        recent.forEach{e->Panel {
            Text("${e.date} · ${when(e.kind){"severe10"->"10日严重异动";"severe30"->"30日严重异动";else->"普通异动"}}",fontWeight=FontWeight.SemiBold)
            Pill(if(e.confirmed)"用户依据原公告确认"else "行情源候选 · 待核对",if(e.confirmed)Accent else Amber)
            TextButton(onClick={openLink(context,e.sourceUrl)}){Text("查看记录来源")}
            if(e.confirmed&&e.kind!="normal")Note("事件后 ${s.bars.count{it.date>e.date}} 个交易记录 · 监控期待核实",Amber)
        }}
        Section("近期条件复算") {Panel {
            val analyses=replayAnalyses
            if(analyses==null)Text("正在复算")else {
                val hits=analyses.map{it.targetDate to it.levels.filter{l->l.ruleId!="normal"&&l.satisfied}}.filter{it.second.isNotEmpty()}
                if(hits.isEmpty())Text("未发现10/30日条件命中")
                hits.forEach{(date,levels)->Text("$date · ${levels.map{it.label}.distinct().joinToString("、")}")}
                val missing=s.bars.takeLast(10).size-analyses.size
                if(missing>0)Note("$missing 个目标日缺少对应指数，已跳过复算。",Amber)
            }
            Pill("条件估算",Amber)
        }}
    }
}

@Composable fun RulesScreen() {
    val context=LocalContext.current
    val rows=remember {val a=JSONArray(context.assets.open("rules.json").bufferedReader().use{it.readText()});(0 until a.length()).map {a.getJSONObject(it)}}
    var query by remember {mutableStateOf("")};var exchange by remember {mutableStateOf("全部")};var selectedRule by remember{mutableStateOf<org.json.JSONObject?>(null)}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item {Section("规则有出处 · ${rows.size}") {OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("搜索板块、条款或关键字")});Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("全部","上交所","深交所").forEach {e->FilterChip(exchange==e,{exchange=e},label={Text(e)})}}}}
        val filtered=rows.filter {(exchange=="全部"||it.optString("exchange")==exchange)&&(query.isBlank()||it.toString().contains(query,ignoreCase=true))}
        items(filtered.size) {i->val r=filtered[i];Panel {
            Pill(r.optString("category"));Text(r.getString("title"),fontWeight=FontWeight.Bold)
            Note(r.optString("exchange"))
            Text(r.getString("summary"),maxLines=2,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            
            
            TextButton(onClick={selectedRule=r}){Text("查看条款")}
        }}
    }
    selectedRule?.let{r->AlertDialog(onDismissRequest={selectedRule=null},title={Text(r.getString("title"))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(r.getString("summary"));Text(r.optString("article"));Text("生效 ${r.optString("effectiveDate")} · 核验 ${r.optString("verifiedDate")}");Text(r.getString("status"),color=Amber)}},confirmButton={TextButton(onClick={openLink(context,r.getString("sourceUrl"))}){Text("官方原文")}},dismissButton={TextButton(onClick={selectedRule=null}){Text("关闭")}})}

}
