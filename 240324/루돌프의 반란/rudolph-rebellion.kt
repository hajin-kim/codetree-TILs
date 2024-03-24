import kotlin.math.pow

class World(
    private val n: Int,
    private val powerC: Int,
    private val powerD: Int
) {
    lateinit var rudolph: Rudolph
    lateinit var santas: List<Santa>

    private var turn = 0

    private fun isInRange(r: Int, c: Int): Boolean {
        return (r in 1..n) && (c in 1..n)
    }

    private fun Int.pow(exponent: Int): Int {
        return toDouble().pow(exponent).toInt()
    }

    private fun distanceToRudolph(santa: Santa): Int {
        return distance(rudolph.r, rudolph.c, santa.r, santa.c)
    }

    private fun distanceToRudolph(r: Int, c: Int): Int {
        return distance(rudolph.r, rudolph.c, r, c)
    }

    private fun distance(r1: Int, c1: Int, r2: Int, c2: Int): Int {
        return (r1 - r2).pow(2) + (c1 - c2).pow(2)
    }

    private fun findSantaAt(r: Int, c: Int): Santa? {
        return santas.firstOrNull { it.r == r && it.c == c }
    }

    private fun findSantaAt(r: Int, c: Int, except: Santa): Santa? {
        return santas.firstOrNull { it != except && it.r == r && it.c == c }
    }

    fun startTurn(turn: Int) {
        this.turn = turn
        santas.forEach { it.startTurn(turn) }
    }

    fun rudolphShouldMoveTo(): Direction8 {
        val distanceBySanta = santas
            .filter { it.alive }
            .groupBy { distanceToRudolph(it) }
        val target = distanceBySanta
            .entries
            .minBy { it.key }!!
            .value
            .maxWith(compareBy<Santa> { it.r }.thenBy { it.c })!!

        val direction8 = Direction8
            .values()
            .minBy { direction ->
                val nr = rudolph.r + direction.dr
                val nc = rudolph.c + direction.dc
                distance(nr, nc, target.r, target.c)
            }!!

        return direction8
    }

    fun santaCanMoveTo(santa: Santa): Direction8? {
        val direction4s = mutableListOf<Direction8>()

        if (santa.r > rudolph.r)
            direction4s.add(Direction8.UP)
        if (santa.c < rudolph.c)
            direction4s.add(Direction8.RIGHT)
        if (santa.r < rudolph.r)
            direction4s.add(Direction8.DOWN)
        if (santa.c > rudolph.c)
            direction4s.add(Direction8.LEFT)

        return direction4s
            .filter { direction ->
                val nr = santa.r + direction.dr
                val nc = santa.c + direction.dc

                findSantaAt(nr, nc) == null
            }
            .minBy { direction ->
                val nr = santa.r + direction.dr
                val nc = santa.c + direction.dc

                distanceToRudolph(nr, nc)
            }
    }

    fun rudolphMoved(direction8: Direction8) {
        val santa = findSantaAt(rudolph.r, rudolph.c)

        santa ?: return

        val nr = santa.r + direction8.dr * powerC
        val nc = santa.c + direction8.dc * powerC

        if (!isInRange(nr, nc))
            santa.lose(powerC)
        else
            santa.crash(nr, nc, powerC, direction8, turn + 2)
    }

    fun santaMoved(santa: Santa, direction8: Direction8) {
        if (santa.r != rudolph.r || santa.c != rudolph.c)
            return

        val reversed = direction8.reverse()

        val nr = santa.r + reversed.dr * powerD
        val nc = santa.c + reversed.dc * powerD

        if (!isInRange(nr, nc))
            santa.lose(powerD)
        else
            santa.crash(nr, nc, powerD, reversed, turn + 2)
    }

    fun santaLanded(santa: Santa, direction8: Direction8) {
        val other = findSantaAt(santa.r, santa.c, santa)

        other ?: return

        val nr = other.r + direction8.dr
        val nc = other.c + direction8.dc

        if (!isInRange(nr, nc))
            other.lose()
        else
            other.landBySanta(nr, nc, direction8)
    }
}

enum class Direction8(
    val dr: Int,
    val dc: Int
) {
    RIGHT(0, 1), LEFT(0, -1), UP(-1, 0), DOWN(1, 0),
    RIGHT_UP(-1, 1), RIGHT_DOWN(1, 1), LEFT_UP(-1, -1), LEFT_DOWN(1, -1);

    fun reverse(): Direction8 {
        return when (this) {
            RIGHT -> LEFT
            LEFT -> RIGHT
            UP -> DOWN
            DOWN -> UP
            RIGHT_UP -> LEFT_DOWN
            RIGHT_DOWN -> LEFT_UP
            LEFT_UP -> RIGHT_DOWN
            LEFT_DOWN -> RIGHT_UP
        }
    }
}

abstract class Role(
    var r: Int,
    var c: Int,
    val world: World
)

class Rudolph(
    r: Int,
    c: Int,
    world: World
) : Role(r = r, c = c, world = world) {
    fun move() {
        val direction = world.rudolphShouldMoveTo()

        r += direction.dr
        c += direction.dc

        world.rudolphMoved(direction)
    }
}

class Santa(
    val id: Int,
    r: Int,
    c: Int,
    world: World
) : Role(r = r, c = c, world = world) {
    private var reviveAt: Int? = null
    var alive = true
        private set
    var score = 0
        private set


    fun lose(score: Int = 0) {
        r = -1
        c = -1
        alive = false
        this.score += score
    }

    fun startTurn(turn: Int) {
        if (reviveAt == turn)
            reviveAt = null
    }

    fun endTurn() {
        if (alive)
            score += 1
    }

    fun move() {
        if (!alive || reviveAt != null)
            return

        val direction = world.santaCanMoveTo(this)

        direction ?: return

        r += direction.dr
        c += direction.dc

        world.santaMoved(this, direction)
    }

    fun crash(nr: Int, nc: Int, dScore: Int, direction8: Direction8, reviveAt: Int) {
        r = nr
        c = nc
        score += dScore

        this.reviveAt = reviveAt

        world.santaLanded(this, direction8)
    }

    fun landBySanta(nr: Int, nc: Int, direction8: Direction8) {
        r = nr
        c = nc

        world.santaLanded(this, direction8)
    }
}

fun main() {
    val (n, m, p, powerC, powerD) = readLine()!!.split(" ").map { it.toInt() }
    val world = World(n = n, powerC = powerC, powerD = powerD)

    val (rr, rc) = readLine()!!.split(" ").map { it.toInt() }
    val rudolph = Rudolph(r = rr, c = rc, world = world)

    val santas = List(p) {
        val (pn, sr, sc) = readLine()!!.split(" ").map { it.toInt() }
        Santa(id = pn, r = sr, c = sc, world = world)
    }
        .sortedBy { it.id }

    world.rudolph = rudolph
    world.santas = santas

    for (turn in 1..m) {
        world.startTurn(turn)

        rudolph.move()
        santas.forEach { it.move() }

        if (santas.all { !it.alive })
            break

        santas.forEach { it.endTurn() }
    }

    println(santas.map { it.score }.joinToString(" "))
}