package cn.yidongchi.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yidongchi.app.*
import cn.yidongchi.core.*
import java.math.BigDecimal
import java.time.LocalDate

@Composable fun CalculationScreen(state:AppState) {
    val context=LocalContext.current
    val s=state.snapshots[state.selected]
    var nextDay by state.workspaces.calculation.flag("next-day")
    var indexChange by state.workspaces.calculation.text("index","0","index-${state.selected}")
    var expanded by remember {mutableStateOf<PriceLevel?>(null)}
    var resetEditor by remember {mutableStateOf(false)}
    var indexEditor by remember {mutableStateOf(false)}
    var ordinary by remember {mutableStateOf(false)}
    var allWindows by remember {mutableStateOf(false)}
    var history by remember {mutableStateOf(false)}
    var replayDate by remember(state.selected){mutableStateOf("")}
    val snapshot=if(replayDate.isBlank())s else state.historySnapshot(replayDate)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        SearchBox(state,{state.select(it);replayDate=""})
        if(snapshot==null)Panel {Text("正在获取行情");state.errors[state.selected]?.let{Text(it,color=Amber)};Button(onClick={state.refreshNow()}){Text("重试")}}
        else {
            SnapshotHeader(state,snapshot)
            val resets=if(replayDate.isNotBlank())snapshot.recordedResets?:ResetDates()else state.resets(state.selected)
            val outcome=runCatching {
                val change=indexChange.ifBlank{"0"}.bd()
                if(nextDay)Projection.project(snapshot.quote.stock,snapshot.bars,snapshot.indexBars,Scenario(dailyIndexPercent=change),1,resets).first().analysis else state.analysis(snapshot,change,resets)
            }
            val a=outcome.getOrNull()
            Panel {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(!nextDay,{nextDay=false},modifier=Modifier.weight(1f),label={Text("当日 ${snapshot.quote.time.toLocalDate().toString().drop(5)}")})
                    FilterChip(nextDay,{nextDay=true},modifier=Modifier.weight(1f),label={Text("次日 ${if(nextDay&&a!=null)a.targetDate.toString().drop(5)else ""}")})
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(indexChange.toBigDecimalOrNull()?.signum()==0,{indexChange="0";state.store.put("index-${state.selected}","0")},label={Text("指数不变")})
                    TextButton(onClick={indexEditor=true}){Text(if(indexChange.toBigDecimalOrNull()?.signum()==0)"调整指数"else "指数 $indexChange%")}
                }
                if(replayDate.isNotBlank())Text("快照 $replayDate · ${snapshot.ruleVersion}",color=Amber)
                outcome.exceptionOrNull()?.let{Text(if(it is NumberFormatException)"请输入有效指数幅度"else it.message?:"暂时无法计算",color=Amber)}
            }
            if(a!=null) {
                Section("严重异动临界价") {
                    val shown=a.levels.filter{ordinary||it.ruleId!="normal"}.groupBy{it.ruleId}.toSortedMap(compareBy{when(it){"severe10"->0;"severe30"->1;else->2}})
                    if(shown.isEmpty())Panel{Text("该阶段不适用价格指标",color=Amber)}
                    shown.forEach{(_,levels)->
                        Panel {
                            Text(levels.first().label,fontWeight=FontWeight.Bold,fontSize=18.sp)
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                                levels.forEach{l->Column(Modifier.weight(1f).clickable{expanded=l}) {
                                    Text(if(l.direction==Direction.UP)"上行 ≥"else "下行 ≤",color=MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(l.price.money(),fontSize=if(state.large)34.sp else 28.sp,fontWeight=FontWeight.Bold,color=if(l.direction==Direction.UP)Up else Down)
                                    val trigger=l.price.ratio(a.previousClose).subtract(BigDecimal.ONE)
                                    Text("触发涨幅 ${trigger.pct()}",fontSize=14.sp)
                                    Text(when{l.satisfied->"已达价格条件";!l.reachable->"当日不可达";else->"空间 ${l.price.subtract(a.current).ratio(a.previousClose).movePointRight(2).setScale(2,java.math.RoundingMode.HALF_UP)} 个百分点"},fontSize=14.sp,color=if(l.satisfied)Up else MaterialTheme.colorScheme.onSurfaceVariant)
                                }}
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    Row {TextButton(onClick={ordinary=!ordinary}){Text(if(ordinary)"收起3日普通异动"else "3日普通异动")};TextButton(onClick={allWindows=!allWindows}){Text(if(allWindows)"收起窗口"else "全部窗口")}}
                    if(allWindows)a.allLevels.filter{ordinary||it.ruleId!="normal"}.forEach{l->TextButton(onClick={expanded=l}){Text("${l.label} · ${l.start} 起 ${if(l.direction==Direction.UP)"↑"else "↓"} ${l.price.money()}")}}
                    Row {
                        TextButton(onClick={copyText(context,"${snapshot.quote.stock.name} ${snapshot.quote.stock.code}\n目标 ${a.targetDate}；指数假设 ${a.indexPrice}\n"+a.levels.filter{ordinary||it.ruleId!="normal"}.joinToString("\n"){"${it.label}${if(it.direction==Direction.UP)" ≥ "else " ≤ "}${it.price}"}+"\n行情 ${snapshot.quote.time}；条件估算");state.message="已复制"}){Text("复制结果")}
                        TextButton(onClick={resetEditor=true}){Text("数据与重置")}
                        TextButton(onClick={history=!history}){Text("历史回放")}
                    }
                }
            }
            if(history) {
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    FilterChip(replayDate.isBlank(),{replayDate=""},label={Text("最新")})
                    state.store.savedDates(state.selected).forEach{d->FilterChip(replayDate==d,{replayDate=d},label={Text(d)})}
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
    if(indexEditor)AlertDialog(onDismissRequest={indexEditor=false},title={Text("指数假设")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("基准 ${snapshot?.quote?.stock?.board?.indexSymbol} · 最新 ${snapshot?.indexQuote?.price?.money()}")
        OutlinedTextField(indexChange,{indexChange=it;state.store.put("index-${state.selected}",it)},label={Text("较最新点位变化（%）")},singleLine=true)
    }},confirmButton={TextButton(onClick={indexEditor=false}){Text("确定")}})
    expanded?.let{l->AlertDialog(onDismissRequest={expanded=null},title={Text(l.label)},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("${l.start} 至 ${l.end} · ${l.sessions} 个记录")
        Text("门槛 ${l.threshold.pct()} · 条件偏离 ${l.deviation.pct()}")
        Text("基期价 ${l.basePrice.stripTrailingZeros().toPlainString()}")
        Text("指数 ${l.indexStart} → ${l.indexEnd}")
        Text("理论临界价 ${l.theoretical.stripTrailingZeros().toPlainString()}")
        Text("触发报价 ${l.price} · 最小单位 ${snapshot?.quote?.stock?.tick}")
        Text("上行向上、下行向下取到首个满足精确条件的报价。")
        if(snapshot?.quote?.stock?.board==Board.STAR)Text("科创累计口径仍有待补证解释。",color=Amber)
    }},confirmButton={TextButton(onClick={expanded=null}){Text("关闭")}})}
    if(resetEditor) {
        val reset=state.resets(state.selected)
        var normal by remember{mutableStateOf(reset.normal?.toString()?:"")}
        var severe by remember{mutableStateOf(reset.severe?.toString()?:"")}
        var error by remember{mutableStateOf("")}
        AlertDialog(onDismissRequest={resetEditor=false},title={Text("数据与重置")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(snapshot?.provider?:"");Text(snapshot?.notes?:"")
            Text("填入公告核实后的重新起算交易日；留空为未知。")
            OutlinedTextField(normal,{normal=it},label={Text("普通异动 YYYY-MM-DD")})
            OutlinedTextField(severe,{severe=it},label={Text("严重异动 YYYY-MM-DD")})
            if(error.isNotBlank())Text(error,color=Up)
        }},confirmButton={TextButton(onClick={try{state.setResets(normal,severe);resetEditor=false}catch(e:Exception){error="日期格式应为 YYYY-MM-DD"}}){Text("保存")}},dismissButton={TextButton(onClick={resetEditor=false}){Text("关闭")}})
    }
}
