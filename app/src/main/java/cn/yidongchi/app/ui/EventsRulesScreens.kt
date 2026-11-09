package cn.yidongchi.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.yidongchi.app.*
import cn.yidongchi.core.*
import org.json.JSONArray
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable fun EventsScreen(state:AppState) {
    val s=state.snapshots[state.selected]
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var record by remember {mutableStateOf(false)}
    LaunchedEffect(state.selected){state.loadEvents()}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Section("异动与预警","记录、监控期间、情景条件分别查看。"){}
        Panel {Text("应用内提醒",fontWeight=FontWeight.Bold);Note("当前前台刷新期间监测自选；退出或锁屏后不持续盯盘。接近阈值 ${state.threshold}% ，可在设置调整。")
            if(state.alerts.isEmpty())Note("暂无价格提醒；信息不齐或未接近条件时不会生成提醒。")
            state.alerts.reversed().forEach {a->TextButton(onClick={state.readAlert(a)}){Text("${if(a.read)"已读" else "●"} ${a.text}",color=if(a.read)MaterialTheme.colorScheme.onSurfaceVariant else Amber)}}
        }
        if(s==null){Note("先选择股票获取行情");return@Column}
        Text("${s.quote.stock.name} · 最近 10 个交易日",fontWeight=FontWeight.Bold)
        Note("截至基准行情日 ${s.indexQuote.time.toLocalDate()}；${if(state.errors.containsKey(state.selected))"当前为缓存，先刷新核对。" else "按市场交易日筛选。"}")
        Note(state.eventStatus,Amber)
        Row {TextButton(onClick={scope.launch{state.loadEvents()}}){Text("刷新事件源")};TextButton(onClick={record=true}){Text("按原公告补充确认记录")}}
        val recent=state.recent(s)
        val directionCounts=EventLogic.directionCounts(recent,s.indexBars.map{it.date},s.quote.time.toLocalDate(),s.quote.stock.board,state.resets(state.selected).severe)
        Panel {Text("10日内同向普通异动次数",fontWeight=FontWeight.Bold);Text("上行 ${directionCounts.up}/${directionCounts.threshold} · 下行 ${directionCounts.down}/${directionCounts.threshold}")
            if(directionCounts.triggered)Pill("已确认记录满足同向多次严重异动条件",Up)
            Note("仅统计去重且方向已确认的普通事件；记录源覆盖不全时，未达到不能认定未触发。",Amber)
        }
        if(recent.isEmpty())Panel {Text("本地未收录近期事件");Note("这不等于近十日没有异动；免费记录源覆盖可能不足，请核对交易所公告。",Amber)}
        recent.forEach {e->Panel {Text("${e.date} · ${when(e.kind){"severe10"->"10日严重异动";"severe30"->"30日严重异动";else->"普通异动"}}",fontWeight=FontWeight.SemiBold);Pill(if(e.confirmed)"用户依据原公告确认" else "行情源候选 · 待核对",if(e.confirmed)Accent else Amber);TextButton(onClick={openLink(context,e.sourceUrl)}){Text("查看记录来源")}
            if(e.confirmed&&e.kind!="normal") {val elapsed=s.bars.count {it.date>e.date};Note("事件后已记录 $elapsed 个交易日；10日申报速率监管的起算、停牌和重复事件仍需按公告核对。",Amber)}
        }}
        val sessions=s.bars.map {it.date}.takeLast(10)
        val replayAnalyses=remember(s,state.message) {PriceEngine.replay(s.quote.stock,s.bars,s.indexBars,state.resets(state.selected))}
        val replays=replayAnalyses.map{it.targetDate to it.levels.filter{l->l.ruleId!="normal"&&l.satisfied}}.filter{it.second.isNotEmpty()}
        Section("近期价格条件复算","按现有参考数据与重置条件回放；不是官方事件计数。") {
            Panel {if(replays.isEmpty())Note("现有条件下未复算出10/30日严重异动价格命中。")
                replays.forEach {(date,levels)->Text("$date · ${levels.map {it.label}.distinct().joinToString("、")}")}
                if(replayAnalyses.size<sessions.size)Note("${sessions.size-replayAnalyses.size} 个目标日缺少对应指数，已跳过复算。",Amber)
                Note("重叠窗口不作为多次官方异动。历史证券状态、停牌和除权资料不足时仅供条件核对。",Amber)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if(record){var date by remember{mutableStateOf(s?.quote?.time?.toLocalDate()?.toString()?:"")};var kind by remember{mutableStateOf("severe10")};var direction by remember{mutableStateOf(Direction.UP)};var url by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
        AlertDialog(onDismissRequest={record=false},title={Text("补充已核对事件")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Note("保存表示你已打开原公告核对；本 APP 不自动认证链接内容。")
            OutlinedTextField(date,{date=it},label={Text("交易所确认的事件日 YYYY-MM-DD")})
            listOf("normal" to "普通异动","severe10" to "10日严重异动","severe30" to "30日严重异动").forEach {(k,t)->FilterChip(kind==k,{kind=k},label={Text(t)})}
            Row{FilterChip(direction==Direction.UP,{direction=Direction.UP},label={Text("上行")});FilterChip(direction==Direction.DOWN,{direction=Direction.DOWN},label={Text("下行")})}
            OutlinedTextField(url,{url=it},label={Text("原公告 HTTPS 链接")});if(error.isNotBlank())Note(error,Up)
        }},confirmButton={TextButton(onClick={try{state.addEvent(date,kind,direction,url);record=false}catch(e:Exception){error=e.message?:"日期或链接有误"}}){Text("保存记录")}},dismissButton={TextButton(onClick={record=false}){Text("取消")}})
    }
}

@Composable fun RulesScreen() {
    val context=LocalContext.current
    val rows=remember {val a=JSONArray(context.assets.open("rules.json").bufferedReader().use{it.readText()});(0 until a.length()).map {a.getJSONObject(it)}}
    var query by remember {mutableStateOf("")};var exchange by remember {mutableStateOf("全部")}
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item {Section("规则有出处","截至2026-11-09 · ${rows.size}条规则与文件索引") {OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("搜索板块、条款或关键字")});Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("全部","上交所","深交所").forEach {e->FilterChip(exchange==e,{exchange=e},label={Text(e)})}};Note("目录包含已核验条款与仍待逐条核验的文件；不包含未公开监管口径。",Amber)}}
        val filtered=rows.filter {(exchange=="全部"||it.optString("exchange")==exchange)&&(query.isBlank()||it.toString().contains(query,ignoreCase=true))}
        items(filtered.size) {i->val r=filtered[i];Panel {
            Pill(r.optString("category"));Text(r.getString("title"),fontWeight=FontWeight.Bold)
            Note("${r.optString("exchange")} · ${r.optString("article")}")
            Text(r.getString("summary"),style=MaterialTheme.typography.bodyMedium)
            Note("生效 ${r.optString("effectiveDate")} · 核验 ${r.optString("verifiedDate")}")
            Note(r.getString("status"),if(r.getString("status").contains("待"))Amber else Accent)
            TextButton(onClick={openLink(context,r.getString("sourceUrl"))}){Text("打开官方原文 ↗")}
        }}
    }
}
