package cn.yidongchi.core

import java.time.LocalDate

object EventLogic {
    fun merge(existing:List<OfficialEvent>,incoming:List<OfficialEvent>):List<OfficialEvent> =
        (existing+incoming).groupBy {Triple(it.symbol,it.date,it.kind)}.values.map {group->group.firstOrNull {it.confirmed}?:group.first()}

    fun directionCounts(events:List<OfficialEvent>,calendar:List<LocalDate>,asOf:LocalDate,board:Board,reset:LocalDate?=null):DirectionCounts {
        val days=calendar.filter {it<=asOf}.distinct().sorted().takeLast(10).toSet()
        val normal=merge(emptyList(),events).filter {it.confirmed&&it.kind=="normal"&&it.date in days&&(reset==null||it.date>=reset)}
        return DirectionCounts(normal.count {it.direction==Direction.UP},normal.count {it.direction==Direction.DOWN},if(board==Board.STAR||board==Board.CHINEXT)3 else 4)
    }
}
