import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.*

/**
 * this abstract class encapsulates functionality common for various backends
 * @property game the [Groebner_Solitaire] game this grid depicts
 * @param x_max the largest number of x values to display (initially); see [max_x]
 * @param y_max the largest number of y values to display (initially); see [max_y]
 */
abstract class Grid(val game: Groebner_Solitaire, x_max: Int, y_max: Int) {

    /** width of the canvas / component / etc. onto which we draw the grid */
    open val width: Double = 0.0
    /** height of the canvas / component / etc. onto which we draw the grid */
    open val height: Double = 0.0

    // data related to drawing onto the grid; names not quite so self-explanatory
    // multiple for grid / integer values to canvas / double values
    open var scale_x: Int = 0
    open var scale_y: Int = 0
    // offset from the canvas' leftmost and bottommost edges,
    // giving room to breathe so that points are clearly visible even there
    open var offset_x: Double = 0.0
    open var offset_y: Double = 0.0

    // largest x value on the grid
    open var max_x: Int = x_max
        set(value) {
            field = value
            scale_x = canvas.width / max_x
        }

    // largest y value on the grid
    open var max_y: Int = y_max
        set(value) {
            field = value
            scale_y = canvas.height / max_y
        }

    /**
     * draws the gameboard
     * @param ordering the ordering used to identify regions covered by distinguished points
     */
    @JsName("draw_grid")
    abstract fun draw_grid(ordering: Ordering = GrevLex_Ordering)

    /**
     * draws a [Stick] according to a given color, identifying the distinguished [Point]
     * @param s [Stick] to draw
     * @param color color to draw the [Stick]; use a 6-letter hex code like #000000
     * @param ordering used to identify regions covered by distinguished [Point]s
     */
    @JsName("draw_stick")
    abstract fun draw_stick(s: Stick, color: String, ordering: Ordering = GrevLex_Ordering)

    /**
     * draws all the sticks relevant to the game
     *
     * the default implementation calls draw_stick on each [Stick], passing the corresponding
     * color known in [game].[Groebner_Solitaire.colors], and the ordering
     * @param ordering the ordering used to identify regions covered by distinguished points
     */
    @JsName("draw_sticks")
    open fun draw_sticks(ordering: Ordering = GrevLex_Ordering) {
        val game = game
        val configuration = game.configuration
        configuration.indices.forEach { draw_stick(configuration[it], game.colors[it], ordering) }
    }

    /**
     * draws the gameboard and all the [Stick]s, coloring and shading regions as appropriate
     */
    @JsName("draw")
    fun draw(ordering: Ordering = GrevLex_Ordering) {
        draw_grid(ordering)
        draw_sticks(ordering)
    }

    /**
     * returns the index of the [Stick] at the point (x_unscaled, y_unscaled)
     * @param x_unscaled the x position clicked / touched on the canvas
     * @param y_unscaled the y position clicked / touched on the canvas
     *
     * this should work for every backend, but if not, override it.
     * it makes use of [offset_x], [offset_y], [scale_x], [scale_y], [height],
     * and [game].[Groebner_Solitaire.configuration]
     */
    @JsName("stick_at")
    open fun stick_at(x_unscaled: Double, y_unscaled: Double): Int {

        val x = ( x_unscaled - offset_x ) / scale_x
        val y = ( - y_unscaled + height - offset_y ) / scale_y

        for ( i in game.configuration.indices.reversed() ) {
            // use law of cosines to figure angle between (x,y) and stick's points
            // if both are acute, figure distance to stick
            // if less than 0.25, select
            val s = game.configuration[i]
            val p = s.p
            val q = s.q
            val p_x = p.x.toDouble()
            val p_y = p.y.toDouble()
            val q_x = q.x.toDouble()
            val q_y = q.y.toDouble()
            val d_rp = sqrt( (x - p_x) * (x - p_x) + (y - p_y) * (y - p_y) )
            val d_rq = sqrt( (x - q_x) * (x - q_x) + (y - q_y) * (y - q_y) )
            val d_pq = sqrt( (p_x - q_x) * (p_x - q_x) + (p_y - q_y) * (p_y - q_y) )
            val α_p = acos( (d_rp * d_rp + d_pq * d_pq - d_rq * d_rq) / (2 * d_rp * d_pq) )
            val α_q = acos( (d_rq * d_rq + d_pq * d_pq - d_rp * d_rp) / (2 * d_rq * d_pq) )
            if ( ( α_p < PI / 2.0 ) && ( α_q < PI / 2.0 ) ) {
                val d = d_rp * sin(α_p)
                if (d < 0.25 && (!game.stick_is_selected() || game.stick_is_potential_pair(i))) {
                    return i
                }
            }
        }
        return -1
    }


    /**
     * sets the window to display the indicated number of x- and y-values
     *
     * this will not shrink the window; it can only get larger, which happens when [Stick]s are added
     */
    @JsName("set_window")
    open fun set_window(x: Int, y: Int) {
        max_x = x
        max_y = y
        scale_x = canvas.width / (max_x + 1)
        scale_y = canvas.height / (max_y + 1)
        offset_x = scale_x.toDouble() / 2
        offset_y = scale_y.toDouble() / 2
    }

}

/**
 * implementation of [Grid] for a JavaScript backend
 * @param _game the game this grid depicts
 * @param canvas the canvas this grid depicts
 * @param _max_x the largest x-value to show in the grid
 * @param _max_y the largest y-value to show in the grid
 * @param background_color for the field behind the grid
 * @param grid_color for the grid lines
 */
@JsName("JS_Grid")
data class JS_Grid(
    private val _game: Groebner_Solitaire,
    private val canvas: HTMLCanvasElement,
    private var _max_x: Int = 10,
    private var _max_y: Int = 10,
    var background_color: String = "#ffffff",
    var grid_color: String = "#000000"
) : Grid(_game, _max_x, _max_y) {

    // data related to drawing onto the grid; names should be self-explanatory
    private val context = canvas.getContext("2d") as CanvasRenderingContext2D
    override val width: Double = canvas.width.toDouble()
    override val height: Double = canvas.height.toDouble()

    /**
     * initializes the grid drawing mechanisms
     *
     *  the JavaScript version only calls set_window
     */
    init {
        set_window(max_x, max_y)
    }

    override fun draw_stick(s: Stick, color: String, ordering: Ordering) {

        // get sticks and their coordinates
        val p = s.p
        val q = s.q
        val p_x = offset_x + (p.x * scale_x).toDouble()
        val p_y = canvas.height - offset_y - p.y * scale_y
        val q_x = offset_x + (q.x * scale_x).toDouble()
        val q_y = canvas.height - offset_y - q.y * scale_y

        // determine the distances for drawing distinguished and non-distinguished points
        val r_small = min(0.125 * scale_x, 0.125 * scale_y)
        val r_large = min(0.25 * scale_x, 0.25 * scale_y)

        // colors we'll use to fill
        context.strokeStyle = color
        context.fillStyle = color

        // connect the dots, though we don't have them yet
        val old_width = context.lineWidth
        context.beginPath()
        context.moveTo(p_x, p_y)
        context.lineTo(q_x, q_y)
        context.lineWidth = 0.0625 * min(scale_x, scale_y)
        context.stroke()
        context.lineWidth = old_width

        // draw p's dot
        context.beginPath()
        var r: Double = if (ordering.preference(p, q) == p) r_large else r_small
        context.arc(p_x, p_y, r, 0.0, 2 * PI)
        context.stroke()
        context.fill()

        // draw q's dot
        context.beginPath()
        r = if (ordering.preference(p, q) == q) r_large else r_small
        context.arc(q_x, q_y, r, 0.0, 2 * PI)
        context.stroke()
        context.fill()

    }

    override fun draw_grid(ordering: Ordering) {

        // clear background
        context.clearRect(0.0, 0.0, width, height)

        // we'll want local doubles for this
        val scale_x = scale_x.toDouble()
        val scale_y = scale_y.toDouble()

        // if any stick's region is to be highlighted, do that first;
        // we can then draw the lines &c. over it
        for (i in game.configuration.indices) {
            if (game.show_region[i]) {

                // current stick
                val s = game.configuration[i]
                val p = s.p
                val q = s.q
                // its distinguished point, adjusted for the canvas
                val lm = if (ordering.preference(p, q) == p) p else q
                val x = lm.x * scale_x + offset_x
                val y = height - lm.y * scale_y - offset_y
                // its color
                val c = game.colors[i]

                context.beginPath()
                val gradient = context.createLinearGradient(x, y, width, 0.0)
                gradient.addColorStop(0.0, c + "88")
                gradient.addColorStop(1.0, c + "00")
                context.fillStyle = gradient
                context.fillRect(x, 0.0, width - x, y)
                context.stroke()

            }
        }

        // now draw the lines
        context.beginPath()
        context.strokeStyle = grid_color
        // vertical lines
        for (i in 0..max_x) {
            val x = offset_x + scale_x * i
            val y1 = height - offset_y
            val y2 = offset_y
            context.moveTo(x, y1)
            context.lineTo(x, y2)
            context.stroke()
            // for a bit of eye candy, have the lines fade away as they near the end of the canvas
            context.beginPath()
            val gradient = context.createLinearGradient(x, y2, x, 0.0)
            gradient.addColorStop(0.0, grid_color)
            gradient.addColorStop(1.0, grid_color + "00")
            context.strokeStyle = gradient
            context.moveTo(x, y2)
            context.lineTo(x, 0.0)
            context.stroke()
            // reset for ordinary lines
            context.beginPath()
            context.strokeStyle = grid_color
        }
        // horizontal lines
        for (i in 0..max_y) {
            val y = height - offset_y - scale_y * i
            val x1 = offset_x
            val x2 = width - offset_x
            context.moveTo(x1, y)
            context.lineTo(x2, y)
            context.stroke()
            // for a bit of eye candy, have the lines fade away as they near the end of the canvas
            context.beginPath()
            val gradient = context.createLinearGradient(x2, y, width, y)
            gradient.addColorStop(0.0, grid_color)
            gradient.addColorStop(1.0, grid_color + "00")
            context.strokeStyle = gradient
            context.moveTo(x2, y)
            context.lineTo(width, y)
            context.stroke()
            // reset for ordinary lines
            context.beginPath()
            context.strokeStyle = grid_color
        }
        context.stroke()

    }

}