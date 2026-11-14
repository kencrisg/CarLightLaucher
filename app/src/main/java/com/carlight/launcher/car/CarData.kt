package com.carlight.launcher.car

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * "Mi auto": odómetro propio (km del tablero + km medidos por GPS), mantenimiento y gasolina.
 * El auto no envía estos datos a la radio: todo se calcula con el GPS y lo que registra el usuario.
 */
class CarData(ctx: Context) {

    private val sp = ctx.applicationContext.getSharedPreferences("car", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- odómetro

    /** Km del tablero que ingresó el usuario (null = aún no configurado). */
    val odometerBase: Int? get() = sp.getInt(KEY_ODO_BASE, -1).takeIf { it >= 0 }

    /** Metros medidos por GPS desde que se ingresó [odometerBase]. */
    val gpsMeters: Double get() = java.lang.Double.longBitsToDouble(sp.getLong(KEY_ODO_GPS, 0L))

    /** Kilometraje estimado actual. */
    val odometerKm: Int get() = (odometerBase ?: 0) + (gpsMeters / 1000.0).toInt()

    val hasOdometer get() = odometerBase != null

    fun setOdometer(km: Int) {
        sp.edit().putInt(KEY_ODO_BASE, km).putLong(KEY_ODO_GPS, 0L).apply()
    }

    fun addGpsMeters(m: Double) {
        sp.edit().putLong(KEY_ODO_GPS, java.lang.Double.doubleToRawLongBits(gpsMeters + m)).apply()
    }

    // ---------------------------------------------------------------- mantenimiento

    data class Item(
        val id: String,
        val name: String,
        /** Cada cuántos km (0 = solo por fecha). */
        val everyKm: Int,
        /** Cada cuántos meses (0 = solo por km). */
        val everyMonths: Int,
        val lastKm: Int,
        /** 0 = nunca registrado. */
        val lastDate: Long,
        /** Mes fijo del año (1..12) en que toca, p. ej. la matriculación. 0 = no aplica. */
        val month: Int = 0,
    ) {
        val isMonthly get() = id == PLATE

        fun kmLeft(odo: Int): Int? = if (everyKm > 0) lastKm + everyKm - odo else null

        fun dueDate(): Long? {
            if (isMonthly) {
                if (month !in 1..12) return null
                // Fin del mes elegido: este año si aún no se hizo en este ciclo, si no el próximo.
                val now = Calendar.getInstance()
                val ref = Calendar.getInstance().apply { timeInMillis = if (lastDate > 0) lastDate else now.timeInMillis }
                var year = ref.get(Calendar.YEAR)
                val doneThisCycle = lastDate > 0 && ref.get(Calendar.MONTH) >= month - 1 - 1   // hecho desde el mes anterior
                if (doneThisCycle || (lastDate == 0L && now.get(Calendar.MONTH) > month - 1)) year++
                return Calendar.getInstance().apply {
                    clear(); set(year, month - 1, 1)
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23)
                }.timeInMillis
            }
            if (everyMonths <= 0 || lastDate <= 0) return null
            return Calendar.getInstance().apply {
                timeInMillis = lastDate
                add(Calendar.MONTH, everyMonths)
            }.timeInMillis
        }

        fun daysLeft(now: Long = System.currentTimeMillis()): Int? = dueDate()?.let { ((it - now) / 86_400_000L).toInt() }

        /** 0 = al día, 1 = se acerca, 2 = vencido. */
        fun status(odo: Int): Int {
            val km = kmLeft(odo)
            val days = daysLeft()
            if ((km != null && km <= 0) || (days != null && days < 0)) return 2
            val nearKm = km != null && km <= maxOf(500, everyKm / 10)
            val nearDays = days != null && days <= (if (isMonthly) 45 else 15)
            return if (nearKm || nearDays) 1 else 0
        }
    }

    fun items(): List<Item> {
        val raw = sp.getString(KEY_ITEMS, null) ?: return defaults()
        val stored = runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Item(o.getString("id"), o.getString("name"), o.getInt("km"), o.getInt("months"),
                    o.getInt("lastKm"), o.getLong("lastDate"), o.optInt("month", 0))
            }
        }.getOrElse { return defaults() }
        // Solo los recordatorios actuales (y los que falten), en orden fijo.
        val byId = stored.associateBy { it.id }
        return defaults().map { d ->
            byId[d.id]?.let { if (it.id == PLATE) it.copy(everyKm = 0, everyMonths = 0, name = d.name) else it.copy(name = d.name) } ?: d
        }
    }

    fun saveItems(items: List<Item>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("id", it.id).put("name", it.name).put("km", it.everyKm)
                .put("months", it.everyMonths).put("lastKm", it.lastKm).put("lastDate", it.lastDate)
                .put("month", it.month))
        }
        sp.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    fun markDone(id: String) = saveItems(items().map {
        if (it.id == id) it.copy(lastKm = odometerKm, lastDate = System.currentTimeMillis()) else it
    })

    fun setMonth(id: String, month: Int) = saveItems(items().map {
        if (it.id == id) it.copy(month = month, lastDate = 0L) else it
    })

    fun updateInterval(id: String, km: Int, months: Int) = saveItems(items().map {
        if (it.id == id) it.copy(everyKm = km, everyMonths = months) else it
    })

    /** El ítem más urgente que se acerca o venció (para el aviso del panel). */
    fun mostUrgent(): Item? {
        if (!hasOdometer) return null
        val odo = odometerKm
        return items().filter { it.status(odo) > 0 }.maxByOrNull { it.status(odo) }
    }

    private fun defaults(): List<Item> {
        val now = System.currentTimeMillis()
        val odo = odometerKm
        return listOf(
            Item(OIL, "Cambio de aceite", 5000, 6, odo, now),
            Item(GEARBOX, "Aceite de caja", 40000, 0, odo, now),
            Item(PLATE, "Matriculación", 0, 0, odo, 0L, month = 0),
        )
    }

    // ---------------------------------------------------------------- gasolina

    data class Fill(val date: Long, val km: Int, val gallons: Double, val price: Double, val full: Boolean)

    var tankGallons: Double
        get() = sp.getFloat(KEY_TANK, 15f).toDouble()
        set(v) = sp.edit().putFloat(KEY_TANK, v.toFloat()).apply()

    fun fills(): List<Fill> = runCatching {
        val arr = JSONArray(sp.getString(KEY_FILLS, "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Fill(o.getLong("date"), o.getInt("km"), o.getDouble("gal"), o.getDouble("price"), o.getBoolean("full"))
        }
    }.getOrDefault(emptyList())

    fun addFill(f: Fill) {
        val list = (fills() + f).sortedBy { it.km }.takeLast(100)
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("date", it.date).put("km", it.km).put("gal", it.gallons)
                .put("price", it.price).put("full", it.full))
        }
        sp.edit().putString(KEY_FILLS, arr.toString()).apply()
    }

    fun removeLastFill() {
        val list = fills().dropLast(1)
        sp.edit().remove(KEY_FILLS).apply()
        list.forEach { addFill(it) }
    }

    /**
     * Consumo real (km por galón) con el método de "tanque lleno a tanque lleno":
     * km entre dos cargas completas / galones cargados después de la primera.
     */
    fun kmPerGallon(): Double? {
        val f = fills()
        val fulls = f.indices.filter { f[it].full }
        if (fulls.size < 2) return null
        val a = fulls.first()
        val b = fulls.last()
        val km = f[b].km - f[a].km
        val gal = (a + 1..b).sumOf { f[it].gallons }
        return if (km > 0 && gal > 0) km / gal else null
    }

    /** Costo por km con todas las cargas entre la primera y la última. */
    fun costPerKm(): Double? {
        val f = fills()
        if (f.size < 2) return null
        val km = f.last().km - f.first().km
        val cost = f.drop(1).sumOf { it.price }
        return if (km > 0) cost / km else null
    }

    fun monthSpend(): Double {
        val c = Calendar.getInstance()
        val y = c.get(Calendar.YEAR); val m = c.get(Calendar.MONTH)
        return fills().filter {
            c.timeInMillis = it.date
            c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m
        }.sumOf { it.price }
    }

    /** Autonomía estimada en km desde la última carga completa (null si falta información). */
    fun rangeKm(): Int? {
        val kpg = kmPerGallon() ?: return null
        val lastFull = fills().lastOrNull { it.full } ?: return null
        val after = fills().filter { it.km > lastFull.km }.sumOf { it.gallons }
        val fuel = (tankGallons + after).coerceAtMost(tankGallons)
        return (fuel * kpg - (odometerKm - lastFull.km)).toInt().coerceAtLeast(0)
    }

    companion object {
        const val OIL = "oil"
        const val GEARBOX = "gearbox"
        const val PLATE = "plate"
        private const val KEY_ODO_BASE = "odo_base"
        private const val KEY_ODO_GPS = "odo_gps"
        private const val KEY_ITEMS = "maint_items"
        private const val KEY_FILLS = "fills"
        private const val KEY_TANK = "tank_gal"
    }
}
