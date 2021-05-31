/**
 * a position on the two-dimensional natural lattice at position ([x],[y])
 */
data class Point(val x: Int, val y: Int) {

    /**
     * returns true if and only if this point is southwest of the other
     */
    fun is_southwest_of(p: Point): Boolean = x <= p.x && y <= p.y

    /**
     * component-wise addition
     */
    operator fun plus(p: Point): Point {
        return Point( x + p.x, y + p.y )
    }

    /** ( [x] , [y] ) */
    override fun toString(): String = "( $x , $y )"

}