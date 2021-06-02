abstract class Ordering {

    /**
     * returns p1 if the ordering prefers it; otherwise returns p2, including when they are equal
     * @return p1 iff Ordering would prefer p1 to p2
     */
    @JsName("preference")
    abstract fun preference(p1: Point, p2: Point): Point

}

/**
 * graded reverse lexicographic ordering, returns true iff sum of first point is larger than sum of second
 */
object GrevLex_Ordering : Ordering() {

    override fun preference(p1: Point, p2: Point): Point =
        if (p1.x + p1.y == p2.x + p2.y) {           // same weight; compare x
            if (p1.x > p2.x) p1                     // first x larger
            else p2                                 // first x equal or smaller
        }
        else if (p1.x + p1.y > p2.x + p2.y) p1      // first weight larger
        else p2                                     // second weight larger

}

/**
 * lexicographic ordering; returns true iff first x is larger than second x, or they are equal and
 * first y is larger than second y
 */
object Lex_Ordering : Ordering() {

    override fun preference(p1: Point, p2: Point): Point =
        if ( (p1.x > p2.x) || (p1.x == p2.x && p1.y > p2.y) ) p1    // first x larger
        else p2                                                     // both components equal

}

/**
 * weighted graded reverse lexicographic ordering: weights the x and y values, then applies the rule
 * for GrevLex_Ordering. default parameters provide the same results as GrevLex_Ordering
 * @see GrevLex_Ordering
 */
object Weighted_GrevLex : Ordering() {

    private var w_x: Int = 1
    private var w_y: Int = 1

    override fun preference(p1: Point, p2: Point): Point {
        val x1 = p1.x * w_x
        val y1 = p1.y * w_y
        val x2 = p2.x * w_x
        val y2 = p2.y * w_y
        return (
                if (x1 + y1 == x2 + y2) {         // same weight; compare x
                    if (x1 > x2) p1               // first x larger
                    else p2                       // first x equal or smaller
                }
                else if (x1 + y1 > x2 + y2) p1    // first weight larger
                else p2                           // first weight equal or smaller
                )
    }

    fun set_weight(x_weight: Int, y_weight: Int) {
        require(x_weight >= 0 && y_weight >= 0)
        w_x = x_weight
        w_y = y_weight
    }

}