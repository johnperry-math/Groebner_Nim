import kotlinx.coroutines.*
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.*

/** number of milliseconds a frame should remain onscreen during an animation */
const val frame_length = 50
/** number of frames that an action should take (meeting or collapse of sticks) */
const val action_frames = 20
/** number of frames to pause between actions */
const val pause_frames = 5

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
    /** multiple for grid / integer values to canvas / double values */
    open var scale_x: Int = 0
    open var scale_y: Int = 0
    /** offset from the canvas' leftmost and bottommost edges,
     * giving room to breathe so that points are clearly visible even there
     */
    open var offset_x: Double = 0.0
    open var offset_y: Double = 0.0

    /** largest x value on the grid */
    open var max_x: Int = x_max
        set(value) {
            field = value
            scale_x = canvas.width / max_x
        }

    /** largest y value on the grid */
    open var max_y: Int = y_max
        set(value) {
            field = value
            scale_y = canvas.height / max_y
        }

    /**
     * draws the gameboard
     * @param ordering the ordering used to identify regions covered by heads
     */
    @JsName("draw_grid")
    abstract fun draw_grid(ordering: Ordering = GrevLex_Ordering)

    /**
     * draws an intermediate stick of an animated move
     *
     * this can also draw a non-intermediate stick of a configuration
     * @param s the [Stick] to draw
     * @param ?? how far [s] has traveled from its initial position
     * @param color the color to assign [s]
     * @param ordering how to distinguish [s]' head
     * @see [draw_intermediate_stick]
     */
    @JsName("draw_intermediate_stick")
    open fun draw_intermediate_stick(
        s: Stick,
        ??: Pair<Double, Double>,
        color: String,
        ordering: Ordering = GrevLex_Ordering
    ) {
        draw_intermediate_stick(
            s.p.x + ??.first, s.p.y + ??.second, // first point
            s.q.x + ??.first, s.q.y + ??.second, // second point
            color, ordering.preference(s.p, s.q) == s.p
        )
    }

    /**
     * draws an intermediate [Stick] of an animated move
     *
     * this can also draw a non-intermediate stick of a configuration
     * @param x1 first point's x-value
     * @param y1 first point's y-value
     * @param x2 second point's x-value
     * @param y2 second point's y-value
     * @param color the color to assign the point
     * @param highlight_first set to true iff (x1,y1) is the head
     */
    abstract fun draw_intermediate_stick(
        x1: Double, y1: Double, x2: Double, y2: Double,
        color: String,
        highlight_first: Boolean
    )

    /**
     * draws a [Stick] according to a given color, distinguishing the head from the tail
     *
     * calls [draw_intermediate_stick] with parameter ?? = 0
     * @param s [Stick] to draw
     * @param color color to draw the [Stick]; use a 6-letter hex code like #000000
     * @param ordering used to identify regions covered by heads
     * @see draw_intermediate_stick
     */
    @JsName("draw_stick")
    open fun draw_stick(s: Stick, color: String, ordering: Ordering = GrevLex_Ordering) {
        draw_intermediate_stick(s, Pair(0.0, 0.0), color, ordering)
    }

    /**
     * draws all the sticks relevant to the game
     *
     * the default implementation calls draw_stick on each [Stick], passing the corresponding
     * color known in [game].[Groebner_Solitaire.colors], and the ordering
     * @param ordering the ordering used to identify regions covered by heads
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
            val ??_p = acos( (d_rp * d_rp + d_pq * d_pq - d_rq * d_rq) / (2 * d_rp * d_pq) )
            val ??_q = acos( (d_rq * d_rq + d_pq * d_pq - d_rp * d_rp) / (2 * d_rq * d_pq) )
            if ( ( ??_p < PI / 2.0 ) && ( ??_q < PI / 2.0 ) ) {
                val d = d_rp * sin(??_p)
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

    /**
     * draws the grid with two new, intermediate sticks; see details
     *
     * draws the grid asynchronously after [delay] * [frame_length] milliseconds,
     * along with all old sticks,
     * adding intermediate sticks offset from [first] and [second].
     * the offset is found by scaling [first_??] and [second_??], respectively,
     * by the ratio [how_far] / [action_frames].
     *
     * @see [draw_intermediate_stick]
     * @param first one of the sticks involved
     * @param second the other stick involved
     * @param first_?? how far [first] has to travel to arrive at the meeting point
     * @param second_?? how far [second] has to travel to arrive at the meeting point
     * @param how_far how far along the route [first] and [second] have gotten so far
     * @param ord the method of distinguishing a [Stick]'s head
     * @param delay how long to delay the start of the animation
     * @param color color of the [Stick]s during the animation
     */
    @JsName("draw_with_intermediate_sticks")
    open fun draw_with_intermediate_sticks(
        first: Stick, second: Stick,
        first_??: Pair<Double, Double>, second_??: Pair<Double, Double>,
        color: String,
        how_far: Int,
        delay: Int,
        ord: Ordering
    ) {
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            delay(delay.toLong())
            draw(ord)
            draw_intermediate_stick(
                first,
                Pair(first_??.first * how_far / action_frames, first_??.second * how_far / action_frames),
                color, ord
            )
            draw_intermediate_stick(
                second,
                Pair(second_??.first * how_far / action_frames, second_??.second * how_far / action_frames),
                color, ord
            )
        }
    }

    /**
     * draws the grid with two new, intermediate, collapsing sticks; see details
     *
     * draws the grid asynchronously after [delay] * [frame_length] milliseconds,
     * along with all old sticks,
     * adding two intermediate sticks formed from [first_fixed] and a third point,
     * as well as [second_fixed] and a third point.
     * the third point is determined by the formula (x or y) + [??].(x or y) * [how_far] / [action_frames].
     * the general intent is that as [how_far] proceeds from 1 to [action_frames],
     * these two sticks seem to collapse into one stick,
     * with their meeting place moving from [from] to [from] offset by [??].
     *
     * @param first_fixed a fixed point, definitely drawn
     * @param second_fixed a fixed point, definitely drawn
     * @param from a point in motion, not drawn, but used to compute a third point that is drawn
     * @param ?? how far [from] is supposed to travel, though it likely hasn't traveled that far yet
     * @param how_far a number between 1 and [action_frames] inclusive, giving
     */
    open fun draw_with_collapsing_sticks(
        first_fixed: Point, second_fixed: Point,
        from: Point, ??: Pair<Double, Double>, how_far: Int,
        color: String, delay: Int, ord: Ordering
    ) {
        val scope = CoroutineScope(Dispatchers.Default)
        scope.launch {
            delay(delay.toLong())
            draw(ord)
            draw_intermediate_stick(
                first_fixed.x.toDouble(), first_fixed.y.toDouble(),
                from.x + ??.first * how_far / action_frames, from.y + ??.second * how_far / action_frames,
                color, ord.preference(first_fixed, from) == first_fixed
            )
            draw_intermediate_stick(
                second_fixed.x.toDouble(), second_fixed.y.toDouble(),

                from.x + ??.first * how_far / action_frames, from.y + ??.second * how_far / action_frames,
                color, ord.preference(second_fixed, from) == second_fixed
            )
        }
    }

    /**
     *  animate the meeting of the given [Stick]s; see details
     *
     *  this first animates the motion of [first] and [second]
     *  such that [first_source] and [second_source] come together at [where].
     *  it also calls [set_window] to adjust the viewing area so that the entire animation is in view;
     *  no [Stick]s should go offscreen.
     *
     *  subsequently, it animates the creation of a new [Stick], or the evaporation of same.
     *
     *  each frame remains on screen for [frame_length] milliseconds.
     *  hence, use [delay] to put off the beginning of the animation
     *  by [delay] * [frame_length] milliseconds.
     *  you can also think of [delay] as the number of frames that have to take place
     *  before this animation starts.
     *
     *  @param first one of the sticks involved
     *  @param second the other stick involved
     *  @param first_source the source [Point] of [first] that gives us [where]
     *  @param second_source the source [Point] of [second] that gives us [where]
     *  @param where the point where [first] and [second] are to meet
     *  @param ord the method of distinguishing a [Stick]'s head
     *  @param delay how long to delay the start of the animation
     *  @param color color of the [Stick]s during the animation
     *  @see set_window
     *  @see draw_with_intermediate_sticks
     *  @see draw_with_collapsing_sticks
     */
    @JsName("animate_meeting")
    open fun animate_meeting(
        first: Stick, second: Stick,
        first_source: Point, second_source: Point,
        where: Point,
        ord: Ordering,
        delay: Int = 0,
        color: String = animation_color
    ) {
        val first_?? = Pair((where.x - first_source.x).toDouble(), (where.y - first_source.y).toDouble())
        val second_?? = Pair((where.x - second_source.x).toDouble(), (where.y - second_source.y).toDouble())
        val check_x = max(max(max(max_x, where.x), first.tail(ord).x + first_??.first.toInt()), second.tail(ord).x + second_??.first.toInt())
        val check_y = max(max(max(max_y, where.y), first.tail(ord).y + first_??.second.toInt()), second.tail(ord).y + second_??.second.toInt())
        set_window(check_x, check_y)
        for (i in 1..action_frames) {
            draw_with_intermediate_sticks(
                first, second, first_??, second_??,
                color, i, delay + i * frame_length, ord
            )
        }
        // now determine where first and second have moved
        val first_moved = Stick(
            first.p.x + first_??.first.toInt() , first.p.y + first_??.second.toInt() ,
            first.q.x + first_??.first.toInt() , first.q.y + first_??.second.toInt()
        )
        val second_moved = Stick(
            second.p.x + second_??.first.toInt() , second.p.y + second_??.second.toInt() ,
            second.q.x + second_??.first.toInt() , second.q.y + second_??.second.toInt()
        )
        // determine points of new stick
        val first_tail = if (first_moved.p == where) first_moved.q else first_moved.p
        val second_tail = if (second_moved.p == where) second_moved.q else second_moved.p
        val midpoint = Pair(
            ( first_tail.x + second_tail.x ).toDouble() / 2 ,
            ( first_tail.y + second_tail.y ).toDouble() / 2
        )
        val third_?? = Pair( ( midpoint.first - where.x ) , ( midpoint.second - where.y ) )
        for (i in 1 .. action_frames) {
            draw_with_collapsing_sticks(
                first_tail, second_tail, where,
                third_??, i, color, delay + (i + action_frames + pause_frames) * frame_length, ord)
        }
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

    override fun draw_intermediate_stick(
        x1: Double, y1: Double, x2: Double, y2: Double,
        color: String, highlight_first: Boolean
    ) {

        // get sticks and their coordinates
        val p_x = offset_x + x1 * scale_x
        val p_y = canvas.height - offset_y - y1 * scale_y
        val q_x = offset_x + x2 * scale_x
        val q_y = canvas.height - offset_y - y2 * scale_y

        // determine the distances for drawing heads and tails
        val r_tail = min(0.125 * scale_x, 0.125 * scale_y)
        val r_head = min(0.25 * scale_x, 0.25 * scale_y)

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
        var r: Double = if (highlight_first) r_head else r_tail
        context.arc(p_x, p_y, r, 0.0, 2 * PI)
        context.stroke()
        context.fill()

        // draw q's dot
        context.beginPath()
        r = if (highlight_first) r_tail else r_head
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
                // its head, adjusted for the canvas
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