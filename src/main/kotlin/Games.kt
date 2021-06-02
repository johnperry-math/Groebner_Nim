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
    fun add_stick(s: Stick, color: String = default_color) {
        if (s !in configuration) {
            _configuration.add(s)
            colors.add(color)
            old_colors.add(color)
            show_region.add(false)
            selected.add(false)
        }
    }

    /**
     * selects [Stick] [i] from [configuration]; if it is the second in a pair, performs a move
     * @param color assigned when a new [Stick] is generated
     * @see [perform_move]
     */
    fun select_stick(i: Int, color: String = default_color) {

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
                        perform_move(currently_selected, i, color, ord)
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
     * @param basis an [Iterable] collection of [Stick]s
     * @param ord how to determine a [Stick]'s head
     */
    @JsName("minimize")
    fun minimize(basis: Iterable<Stick>, ord: Ordering): HashSet<Stick> {
        val intermediate = basis.toHashSet()
        val result = HashSet<Stick>()
        for (f in intermediate)
            if (!intermediate.any { it !== f && it.head(ord).is_southwest_of(f.head(ord)) })
                result.add(f)
        return result
    }

    /**
     * combines two [Stick]s that have not been combined before (does not check for this!)
     * and adds if only if it is a legitimate stick
     * @param i first stick to combine
     * @param j second stick to combine
     * @param color color for a new stick, if generated
     * @param ord how to determine a [Stick]'s head
     */
    @JsName("move")
    fun move(i: Int, j: Int, color: String = default_color, ord: Ordering): Boolean {

        // some caution is in order
        require(i >= 0 && i < configuration.size)
        require(j >= 0 && j < configuration.size)

        // if we get here, it should be safe to proceed
        // record the move
        previous_moves.add(Pair(i,j))
        // generate a new stick
        var result = new_stick(configuration[i], configuration[j], ord)
        // reduce it modulo the others
        result = reduce(result, configuration, ord)
        // if it's still new, add it; either way, report accordingly
        return if (result.p != result.q) {
            add_stick(result, color)
            true
        } else {
            false
        }
    }

    /**
     * checks that the requested move has not been made, then performs it
     *
     * in reality, after checking that the move has not been made, it passes the data to [move]
     * @param i first stick to combine
     * @param j second stick to combine
     * @param color color for a new stick, if generated
     * @param ord how to determine a [Stick]'s head
     */
    @JsName("perform_move")
    fun perform_move(i: Int, j: Int, color: String, ord: Ordering): Boolean {
        return (
                if (Pair(i, j) in previous_moves || Pair(j, i) in previous_moves) false
                else return move(i, j, color, ord)
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

