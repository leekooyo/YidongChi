package cn.yidongchi.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yidongchi.app.*
import cn.yidongchi.core.*

@Composable fun ForecastScreen(state:AppState) {
    val s=state.snapshots[state.selected]
    var stockChange by remember(state.selected){mutableStateOf(state.store.value("scenario-stock-${state.selected}","0"))}
    var indexChange by remember(state.selected){mutableStateOf(state.store.value("scenario-index-${state.selected}","0"))}
    var pathText by remember(state.selected){mutableStateOf("")}
    var indexPath by remember(state.selected){mutableStateOf("")}
    var mode by remember(state.selected){mutableStateOf("固定涨跌")}
    var reset by remember {mutableStateOf(false)}
    var compare by remember {mutableStateOf(false)}
    var advanced by remember {mutableStateOf(false)}
    var help by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Section("未来 10 个交易日"){}
        if(s==null){Panel {Text("先选择股票")};return@Column}
        SnapshotHeader(state,s)
        Panel {
            listOf(listOf("固定涨跌","连续涨停"),listOf("连续跌停","逐日路径")).forEach {row->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    row.forEach {m->FilterChip(mode==m,{mode=m},modifier=Modifier.weight(1f),label={Text(m,fontSize=15.sp)})}
                }
            }
            if(mode=="固定涨跌"||mode=="逐日路径")OutlinedTextField(stockChange,{stockChange=it;state.store.put("scenario-stock-${state.selected}",it)},Modifier.fillMaxWidth(),label={Text("个股每日涨跌幅（%）")},singleLine=true)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(indexChange.toBigDecimalOrNull()?.signum()==0,{indexChange="0";state.store.put("scenario-index-${state.selected}","0")},label={Text("指数不变")})
                TextButton(onClick={advanced=!advanced}){Text(if(advanced)"收起设置"else "更多条件")}
            }
            if(advanced||indexChange.toBigDecimalOrNull()?.signum()!=0) {
                OutlinedTextField(indexChange,{indexChange=it;state.store.put("scenario-index-${state.selected}",it)},Modifier.fillMaxWidth(),label={Text("指数每日涨跌幅（%）")},singleLine=true)
                Row {Checkbox(reset,{reset=it});Text("触发后模拟重置",Modifier.padding(top=12.dp))}
                Row {Checkbox(compare,{compare=it});Text("对比价格不变情景",Modifier.padding(top=12.dp))}
            }
            if(mode=="逐日路径") {
                OutlinedTextField(pathText,{pathText=it},Modifier.fillMaxWidth(),label={Text("个股路径：2,3,-1,0")})
                OutlinedTextField(indexPath,{indexPath=it},Modifier.fillMaxWidth(),label={Text("指数路径：0,0.2,-0.1")})
            }
        }
        val resets=state.resets(state.selected)
        val events=state.events.filter{it.symbol==state.selected}
        val result=remember(s,mode,stockChange,indexChange,pathText,indexPath,reset,resets,events) {
            runCatching {Projection.project(s.quote.stock,s.bars,s.indexBars,ForecastInputs.scenario(mode,stockChange,indexChange,pathText,indexPath,reset),resets=resets,events=events)}
        }
        val days=result.getOrNull()
        if(days==null)Panel {Text(if(result.exceptionOrNull() is NumberFormatException)"请输入有效涨跌幅"else result.exceptionOrNull()?.message?:"暂时无法推演",color=Amber)}
        else {
            val first=days.firstOrNull {it.analysis.levels.any {l->l.ruleId!="normal"&&l.satisfied}||it.directions.triggered}
            Panel {
                Text("$mode · ${if(indexChange.toBigDecimalOrNull()?.signum()==0)"指数不变"else "指数每日 $indexChange%"}",fontWeight=FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Column {Note("首日收盘");Text(days.first().close.money(),fontSize=28.sp,fontWeight=FontWeight.Bold,color=if(mode=="连续跌停")Down else Up)}
                    Column {Note("第10日收盘");Text(days.last().close.money(),fontSize=28.sp,fontWeight=FontWeight.Bold,color=if(mode=="连续跌停")Down else Up)}
                }
                Pill(first?.let{"严重异动条件：${it.date}"}?:"此情景未达严重异动条件",if(first!=null)Up else Accent)
                Canvas(Modifier.fillMaxWidth().height(70.dp)) {
                    val values=days.map{it.close.toFloat()};val low=values.min();val span=(values.max()-low).coerceAtLeast(.01f);val p=Path()
                    values.forEachIndexed{i,v->val x=i*size.width/(values.size-1);val y=size.height-(v-low)/span*(size.height-8);if(i==0)p.moveTo(x,y)else p.lineTo(x,y)}
                    drawPath(p,if(mode=="连续跌停")Down else Accent,style=androidx.compose.ui.graphics.drawscope.Stroke(4f))
                }
            }
            val b=if(compare)remember(s,resets){Projection.project(s.quote.stock,s.bars,s.indexBars,Scenario("情景B"),resets=resets)}else null
            days.forEachIndexed {i,d->
                var detail by remember(d.date){mutableStateOf(false)}
                Panel {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        Text("D${i+1} · ${d.date.toString().drop(5)}",fontWeight=FontWeight.Bold)
                        Text("情景收盘 ${d.close.money()}",fontWeight=FontWeight.Bold)
                    }
                    if(d.analysis.levels.isEmpty())Text("该日不适用异动指标",color=Amber)
                    d.analysis.levels.filter{it.ruleId!="normal"}.groupBy{it.ruleId}.forEach {(_,ls)->
                        val up=ls.firstOrNull{it.direction==Direction.UP};val down=ls.firstOrNull{it.direction==Direction.DOWN}
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(if(ls.first().ruleId=="severe10")"10日"else "30日")
                            Text("↑ ${up?.price?.money()?:"—"}",color=Up,fontWeight=FontWeight.SemiBold)
                            Text("↓ ${down?.price?.money()?:"—"}",color=Down,fontWeight=FontWeight.SemiBold)
                        }
                        if(ls.any{it.satisfied})Pill("达到${if(ls.first().ruleId=="severe10")"10日"else "30日"}价格条件",Up)
                    }
                    if(d.directions.triggered)Pill("达到同向多次条件",Up)
                    b?.getOrNull(i)?.let{other->Text("不变情景：${other.close.money()}")}
                    TextButton(onClick={detail=!detail}){Text(if(detail)"收起明细"else "明细")}
                    if(detail) {
                        Text("指数 ${d.indexClose.money()} · 昨收 ${d.analysis.previousClose.money()}")
                        d.analysis.levels.forEach {l->Text("${l.label}${if(l.direction==Direction.UP)"上行"else "下行"} ${l.price.money()} · ${if(l.reachable)"当日可达"else "当日不可达"} · 偏离 ${l.deviation.pct()}")}
                        Text("同向次数：上行 ${d.directions.up}/${d.directions.threshold}，下行 ${d.directions.down}/${d.directions.threshold}")
                        val previous=if(i==0)state.analysis(s)else days[i-1].analysis
                        d.analysis.levels.filter{it.direction==Direction.UP}.forEach{l->val old=previous.levels.firstOrNull{it.ruleId==l.ruleId&&it.direction==Direction.UP};if(old!=null&&old.start!=l.start)Text("${l.label}起点 ${old.start} → ${l.start}")}
                        if(d.afterHypotheticalTrigger)Text(if(reset)"包含假设重置"else "后续未计入未来公告重置",color=Amber)
                    }
                }
            }
        }
        TextButton(onClick={help=true}){Text("推演依据")}
        Spacer(Modifier.height(20.dp))
    }
    if(help)AlertDialog(onDismissRequest={help=false},title={Text("推演依据")},text={Text("未来结果取决于所选的股价、指数与重置情景。连续涨跌停按板块涨跌幅和报价单位逐日计算；未来停复牌、除权和正式公告仍会改变结果。")},confirmButton={TextButton(onClick={help=false}){Text("关闭")}})
}
