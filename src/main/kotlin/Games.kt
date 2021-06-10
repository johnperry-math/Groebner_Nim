import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * a game of Groebner Solitaire
 * @property ord the term ordering to use during the game; this can change, making for a dynamic game
 */
@JsName("Groebner_Solitaire")
class Groebner_Solitaire(
    @Suppress("Private")
    var ord: Ordering = GrevLex_Ordering
) {

    // backing field
    private var _configuration: ArrayList<Stick> = ArrayList()
    /** the [Stick]s in play */
    val configuration: List<Stick>
        get() = _configuration.toList()
    /** the colors for each [Stick]; these correspond to [configuration] */
    var colors: ArrayList<String> = ArrayList()
    /**
     *  the colors for each [Stick], held temporarily while they're highlighted differently;
     *  these correspond to [configuration]
     */
    private var old_colors: ArrayList<String> = ArrayList()
    /** whether the show the region each [Stick] dominates; these correspond to [configuration] */
    val show_region: ArrayList<Boolean> = ArrayList()
    /** whether the corresponding [Stick] in [configuration] has been selected */
    private val selected: ArrayList<Boolean> = ArrayList()

    // colors used during the game to identify sticks
    private var default_color: String = "#deadbe"   // originally I had deadbeef but it's a problem for alpha
    private var pair_color: String = default_color  // colors of Sticks that can pair with a selected Stick
    private var highlight_color: String = "#ff0000" // color of a selected stick

    // which Stick in configuration is currently selected
    private var currently_selected: Int = -1

    // default & reset value for new_stick
    private val zero_stick = Stick(Point(0,0), Point(0,0))
    // when a new stick is created, we temporarily store it here
    private var new_stick = zero_stick
    // the new stick's color
    private var new_stick_color = stick_color

    /** returns true if and only if a stick is currently selected for pairing */
    fun stick_is_selected(): Boolean = currently_selected != -1
    /** returns true if and only if the indicated stick is a highlighted, possible pairing */
    fun stick_is_potential_pair(i: Int): Boolean = colors[i] != old_colors[i]

    // pairs considered previously
    private val previous_moves: MutableSet< Pair<Int, Int> > = HashSet()
    // backing field
    private var _solution = setOf<Stick>()
    /** the eventual solution (a Groebner basis of the original [configuration] */
    val solution: Set<Stick>
        get() = _solution.toSet()
    /** the number of moves we take to find [solution] */
    private var _num_moves = 0
    val num_moves: Int
        get() = _num_moves

    /** sets the configuration to the indicated value */
    fun reset_configuration(to: List<Stick>) {
        _configuration.clear()
        new_stick = zero_stick
        for (s in to) _configuration.add(s)
        previous_moves.clear()
        currently_selected = -1
        colors.clear()
        for (i in _configuration.indices) colors.add(old_colors[i])
        old_colors.clear()
        show_region.clear()
        selected.clear()
        for (i in _configuration.indices) {
            old_colors.add(colors[i])
            show_region.add(false)
            selected.add(false)
        }

    }

    /** adds [s] to the game, with the given [color] */
    fun add_stick(s: Stick = new_stick, color: String = new_stick_color) {
        if (s !in configuration && (s.p != s.q)) {
            _configuration.add(s)
            colors.add(color)
            old_colors.add(color)
            show_region.add(false)
            selected.add(false)
        }
    }

    /**
     * selects [Stick] [i] from [configuration]; if it is the second in a pair, performs a move
     *
     * when applicable, animates the creation of a new stick
     * @param color assigned when a new [Stick] is generated
     * @param grid where to draw the animation
     * @see [perform_move]
     */
    fun select_stick(i: Int, color: String = default_color, grid: Grid) {

        // number of frames in a turn's animation
        var frames_generated = 0

        // no point doing anything if i isn't a valid choice
        if (i in selected.indices) {

            // if not selected, select it, and vice versa
            selected[i] = !selected[i]

            if (selected[i]) { // stick i was selected

                if (currently_selected == -1) { // it's the first stick of a pair

                    // highlight this stick
                    old_colors[i] = colors[i]
                    colors[i] = highlight_color
                    currently_selected = i

                    // indicate pairs allowed to combine with it
                    for (j in configuration.indices) {
                        if (
                            i != j
                            && Pair(i, j) !in previous_moves
                            && Pair(j, i) !in previous_moves
                        ) {
                            old_colors[j] = colors[j]
                            colors[j] = pair_color
                        }
                    }

                } else { // it's the second stick of a pair

                    if (colors[i] == pair_color) { // only allow unconsidered pairs
                        // add new stick
                        frames_generated = perform_move(currently_selected, i, color, ord, grid)
                        // update considered pairs
                        previous_moves.add(Pair(currently_selected, i))
                        // unselect everything
                        colors.indices.forEach { colors[it] = old_colors[it] }
                        selected[i] = false
                        selected[currently_selected] = false
                        currently_selected = -1
                    }

                }

            } else { // stick i was unselected

                // return its color to the old color
                colors[i] = old_colors[i]
                // return the colors of all potential pairs to the old color
                for (j in configuration.indices) {
                    if (j != i && colors[j] == pair_color) colors[j] = old_colors[j]
                }
                // reset
                currently_selected = -1

            }

        }

        // postpone cleanup in order to allow launched animations to complete
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            console.log("waiting ${frames_generated * frame_length} to cleanup")
            delay(frames_generated.toLong() * frame_length)
            cleanup_select_stick()
        }

    }

    /**
     * returns true iff the game is over
     *
     * compares [configuration] to [solution]. if [solution] is unknown at this time (default value)
     * then it computes it
     * @param ord how to determine a [Stick]'s head
     */
    @JsName("is_over")
    fun is_over(ord: Ordering): Boolean {
        // generate solution if not already known
        if (solution.isEmpty()) {
            val compute_data = buchberger_basis(configuration, ord)
            _solution = minimize(compute_data.first, ord)
            _num_moves = compute_data.second
            console.log("solution:")
            for (s in solution) {
                console.log(("\t$s"))
            }
        }
        // compare
        return minimize(configuration, ord) == solution
    }

    /**
     * minimizes [basis] by pruning [Stick]s whose head is redundant with another's
     * and replacing [Stick]s whose tail is redundant by another's with a reduced version of itself
     * @param basis an [Iterable] collection of [Stick]s
     * @param ord how to determine a [Stick]'s head
     */
    @JsName("minimize")
    fun minimize(basis: Iterable<Stick>, ord: Ordering): HashSet<Stick> {
        val intermediate = basis.toHashSet()
        val result = HashSet<Stick>()
        for (f in intermediate) {
            val t = f.head(ord)
            if (!intermediate.any { it !== f && it.head(ord).is_southwest_of(t) }) {
                val u = if (f.p == t) f.q else f.p
                if (!intermediate.any { it !== f && it.head(ord).is_southwest_of(u) })
                    result.add(f)
                else {
                    val reducer = intermediate.find { it !== f && it.head(ord).is_southwest_of(u) }!!
                    result.add(Stick(t, reducer.tail(ord)))
                }
            }
        }
        return result
    }

    /**
     * combines two [Stick]s that have not been combined before (does not check for this!)
     * and adds if only if it is a legitimate stick; also animates the move
     * @param i first stick to combine
     * @param j second stick to combine
     * @param color color for a new stick, if generated
     * @param ord how to determine a [Stick]'s head
     * @param grid where to draw a performed move
     * @return number of keyframes in the move's animation
     */
    @JsName("move")
    fun move(i: Int, j: Int, color: String = default_color, ord: Ordering, grid: Grid): Int {

        // some caution is in order
        require(i >= 0 && i < configuration.size)
        require(j >= 0 && j < configuration.size)

        // if we get here, it should be safe to proceed
        // record the move
        previous_moves.add(Pair(i,j))
        // generate a new stick
        val (lcm, result) = new_stick(configuration[i], configuration[j], ord)
        grid.animate_meeting(
            configuration[i], configuration[j],
            configuration[i].head(ord), configuration[j].head(ord), lcm,
            ord
        )
        // reduce it modulo the others
        val (time, poly) = reduce(result, configuration, ord, grid)
        // if it's still new, add it; either way, report accordingly
        if (poly.p != poly.q) {
            //add_stick(poly, color)
            new_stick = poly
            new_stick_color = color
        }
        return time
    }

    /**
     * checks that the requested move has not been made, then performs and animates it
     *
     * in reality, after checking that the move has not been made, it passes the data to [move]
     * @param i first stick to combine
     * @param j second stick to combine
     * @param color color for a new stick, if generated
     * @param ord how to determine a [Stick]'s head
     * @param grid where to draw a performed move
     * @return number of keyframes in move's animation
     */
    @JsName("perform_move")
    fun perform_move(i: Int, j: Int, color: String, ord: Ordering, grid: Grid): Int {
        return (
                if (Pair(i, j) in previous_moves || Pair(j, i) in previous_moves) 0
                else return move(i, j, color, ord, grid)
        )
    }

}

/**
 * JavaScript version of the game
 *
 * eventually I will optimize this away, since it doesn't seem necessary (?)
 */
@JsName("JS_Game")
data class JS_Game(val game: Groebner_Solitaire, val grid: Grid)

