import kotlin.math.max

/**
 * a [Stick] is a line segment connecting two [Point]s, or a binomial polynomial if you prefer
 *
 * internally we order the [Point]s so that the first one is lexicographically larger
 * @property _p first [Point]
 * @property _q second [Point]
 */
data class Stick(private val _p: Point, private val _q: Point) {

    /** first [Point] */
    val p = if (_p.x < _q.x || ( _p.x == _q.x && _p.y < _q.y)) _q else _p
    /** second [Point] */
    val q = if (p == _p) _q else _p

    /** constructs [Point]s for (x1,y1) , (x2,y2) */
    constructor(x1: Int, y1: Int, x2: Int, y2: Int): this(Point(x1, y1), Point(x2, y2))

    /**
     * determines the head according to the given ordering
     * @see [ord].[Ordering.preference]
     * @param ord how to identify the head
     */
    fun head(ord: Ordering = GrevLex_Ordering) = ord.preference(p, q)

    /**
     * overrides the dollar-sign operator to display "[ P ; Q ]"
     */
    override fun toString(): String {
        return "[ ( ${p.x} , ${p.y} ) ; ( ${q.x} , ${q.y} ) ]"
    }

    /**
     * two sticks will be considered equal evey when the [Point]s are reversed
     */
    override operator fun equals(other: Any?): Boolean =
        when {
            this === other -> true
            other !is Stick -> false
            else -> (this.p == other.p && this.q == other.q) || (this.q == other.p && this.p == other.q)
        }

    override fun hashCode(): Int {
        var result = p.hashCode()
        result = 31 * result + q.hashCode()
        return result
    }

}

/**
 * creates a new [Stick] by moving the given sticks rightwards or upwards
 * until their heads meet, then returns the tails
 * @param s1 first [Stick] to combine
 * @param s2 second [Stick] to combine
 * @param ord how to determine a head
 */
fun new_stick(s1: Stick, s2: Stick, ord: Ordering): Stick {

    // determine head (h) and tail (t) points
    val (h1, t1) = if (s1.head(ord) == s1.p) Pair(s1.p, s1.q) else Pair(s1.q, s1.p)
    val (h2, t2) = if (s2.head(ord) == s2.p) Pair(s2.p, s2.q) else Pair(s2.q, s2.p)
    // find where they meet when you move the sticks upwards or rightwards
    val lcm = Point(max(h1.x,h2.x), max(h1.y,h2.y))
    // how far does each move to get there?
    val u1 = Point(lcm.x - h1.x, lcm.y - h1.y)
    val u2 = Point(lcm.x - h2.x, lcm.y - h2.y)
    // join the tails
    return Stick( Point(t1.x + u1.x, t1.y + u1.y), Point(t2.x + u2.x, t2.y + u2.y) )

}

/**
 * reduce a given [Stick] by an [Iterable] of [Stick]s, according to an [Ordering]
 *
 * whenever a head of [by] is southwest of either of [stick]'s points,
 * we move that reducer to meet [stick]'s said point, then combine the remaining points.
 * @param stick the stick to reduce
 * @param by [Stick]s to reduce by
 * @param ord how to determine a [Stick]'s head
 */
fun reduce(stick: Stick, by: Iterable<Stick>, ord: Ordering): Stick {

    // start with input
    var result = stick

    do {

        // if both points are the same, we've reduced to nonexistence: quit!
        if (result.p == result.q) break

        // indicates whether a reducer we've discovered
        // reduces result's head, or the other one
        var reduce_head = true
        // can we find a reducer for result.p?
        var reducer = by.find { it.head(ord).is_southwest_of(result.p) }
        // if not, can we find one for result.q? make a note of that if so
        if (reducer == null) {
            reduce_head = false
            reducer = by.find { it.head(ord).is_southwest_of(result.q) }
        }
        if (reducer == null) break // no reducer found
        else { // we have a reducer!
            val t = reducer.head(ord)
            val u = if (reduce_head) Point(result.p.x - t.x, result.p.y - t.y)
                    else Point(result.q.x - t.x, result.q.y - t.y)
            val v = if (t === reducer.p) reducer.q else reducer.p
            result = if (reduce_head) Stick( result.q , u + v ) else Stick( result.p , u + v )
        }

    } while (true)

    return result
    
}

/**
 * remove the pairs that correspond to elements whose heads
 * lie on opposite axes; in the context of Groebner bases, this is sometimes
 * called Buchberger's first criterion, or, Buchberger's gcd criterion
 * @param pairs a list of paired elements of [basis]
 * @param basis a list of [Stick]s that defines a basis of the game
 * @param ord how we determine a [Stick]'s head
 */
fun prune_gcd(pairs: ArrayList<Pair<Int, Int>>, basis: List<Stick>, ord: Ordering) {
    val b1_pairs = HashSet<Pair<Int, Int>>()
    for (p in pairs) {
        val t1 = basis[p.first].head(ord)
        val t2 = basis[p.second].head(ord)
        if ( (t1.x == 0 && t2.y == 0) || (t1.y == 0 && t2.x == 0) ) {
            b1_pairs.add(p)
            console.log("b1 pruned ${p.first}, ${p.second}")
        }
    }
    pairs.removeAll(b1_pairs)
}

fun prune_lcm(
        pairs: ArrayList<Pair<Int, Int>>,
        basis: List<Stick>,
        considered_pairs: Set<Pair<Int, Int>>,
        ord: Ordering
) {
    val b2_pairs = HashSet<Pair<Int, Int>>()
    for (p in pairs) {
        val i = p.first
        val j = p.second
        val t1 = basis[i].head(ord)
        val t2 = basis[j].head(ord)
        val t12 = Point( max(t1.x, t2.x) , max(t1.y, t2.y) )
        for (k in basis.indices) {
            val u = basis[k].head(ord)
            if (
                u.is_southwest_of(t12)
                && (Pair(i,k) in considered_pairs || Pair(k,i) in considered_pairs)
                && (Pair(j,k) in considered_pairs || Pair(k,j) in considered_pairs)
            ) {
                b2_pairs.add(p)
                console.log("b2 pruned ${p.first}, ${p.second}")
            }
        }
    }
    pairs.removeAll(b2_pairs)
}

/**
 * a comparison function that indicates which [Pair] is larger
 * when we apply to [ord] to
 *    * the [lcm] of the heads of the [source] elements indexed by [a], and
 *    * the [lcm] of the heads of the [source] elements indexed by [b]
 * @param first_pair indexes a pair of [Stick]s in [source]
 * @param second_pair indexes a pair of [Stick]s in [source]
 * @param source a list of [Stick]s
 * @param ord how to determine the head of a [Stick]
 */
fun pair_comparison(
    first_pair: Pair<Int, Int>,
    second_pair: Pair<Int, Int>,
    source: MutableList<Stick>,
    ord: Ordering): Int
{

    // get the first pair's heads' lcm
    val f1 = source[first_pair.first]
    val f2 = source[first_pair.second]
    val t1 = ord.preference(f1.p, f1.q)
    val t2 = ord.preference(f2.p, f2.q)
    val t12 = lcm(t1, t2)

    // repeat for the second pair
    val g1 = source[second_pair.first]
    val g2 = source[second_pair.second]
    val u1 = ord.preference(g1.p, g1.q)
    val u2 = ord.preference(g2.p, g2.q)
    val u12 = lcm(u1, u2)

    // compare lcm's according to ord
    return when (t12) {
        u12 -> 0
        ord.preference(t12, u12) -> 1
        else -> -1
    }

}

/**
 * compute a solution to a game of Groebner_Nim
 *
 * technically, this is computing a Groebner basis of a polynomial ideal
 *
 * @param input the initial configuration
 * @param ord how to determine a [Stick]'s head
 * @return [Stick]s generated by the end of the game,
 *      along with the number of moves required to generate the final stick
 */
fun buchberger_basis(input: Iterable<Stick>, ord: Ordering): Pair<Set<Stick>, Int> {

    val intermediate = input.toMutableList()

    // set up pairs we need to consider
    val unconsidered_pairs = ArrayList< Pair< Int, Int > >()
    val considered_pairs = HashSet< Pair< Int, Int > >()
    for (i in intermediate.indices) {
        for (j in intermediate.subList(0, i).indices) {
            unconsidered_pairs.add( Pair(j,i) )
        }
    }

    // count pairs used to obtain basis
    var number_computed = 0
    var number_verified = 0

    // loop until we have considered all pairs
    prune_gcd(unconsidered_pairs, intermediate, ord)
    while (!unconsidered_pairs.isEmpty()) {
        ++number_computed
        val p = unconsidered_pairs.first()
        console.log("$p")
        unconsidered_pairs.remove(p)
        considered_pairs.add(p)
        var s = new_stick(intermediate[p.first], intermediate[p.second], ord)
        s = reduce(s, intermediate, ord)
        if (s.p == s.q) // don't add a vanished stick to the basis!
            ++number_verified
        else {
            for (i in intermediate.indices)
                unconsidered_pairs.add( Pair(i, intermediate.size) )
            intermediate.add(s)
            number_verified = 0
        }
        unconsidered_pairs.sortWith { a, b -> pair_comparison(a, b, intermediate, ord) }
        prune_gcd(unconsidered_pairs, intermediate, ord)
        prune_lcm(unconsidered_pairs, intermediate, considered_pairs, ord)
    }
    
    return Pair(intermediate.toHashSet(), number_computed - number_verified)

}