import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.TouchEvent
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventListener
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.get
import kotlin.math.max
import kotlin.random.Random

/** default color of a stick in the game */
const val stick_color: String = "#000000"
/** color of a stick that forms part of the final solution */
const val solution_color: String = "#0000ff"
/** color of a stick that is inactive once the solution is discovered */
const val inactive_color: String = "#aaaaaa80"

/**
 *  how far the x-values should extend from 0
 *
 *  this determines the number of vertical grid lines, marking x-values
 */
const val window_x = 10
/**
 *  how far the y-values should extend from 0
 *
 *  this determines the number of horizontal grid lines, marking y-values
 */
const val window_y = 10

/** data for a game of Groebner solitaire */
@JsName("game")
private var game = Groebner_Solitaire()

/** canvas on which to draw */
@JsName("canvas")
var canvas = document.getElementById("game_canvas") as HTMLCanvasElement

/** context of a JavaScript game */
@JsName("game_context")
var game_context = JS_Game(game, JS_Grid(game, canvas, window_x, window_y))

/** number of moves the player performs */
var num_moves = 0

/** [Stick]s in play at the start of a game */
var game_start: List<Stick> = ArrayList()

/**
 *  Kotlin will optimize the game_context symbol away
 *  if we don't include this function and use it from main()
 */
@Suppress("Unused")
@JsName("my_game_context")
fun get_game_context(): JS_Game = game_context

/**
 * generates a random Stick with two distinct points
 *
 * @param max_x the largest possible x-value allowed for the Stick
 * @param max_y the largest possible y-value allowed for the Stick
 */
fun random_stick(max_x: Int = window_x, max_y: Int = window_y): Stick {
    val x1 = Random.nextInt(max_x)
    val y1 = Random.nextInt(max_y)
    var x2 = x1
    var y2 = y1
    // make sure the points aren't the same
    while (x1 == x2 && y1 == y2) {
        x2 = Random.nextInt(max_x)
        y2 = Random.nextInt(max_y)
    }
    return Stick(x1, y1, x2, y2)
}

/** a button has been clicked and released, or a finger has touched down, then come up, from the canvas; process it */
fun released(e: Event) {

    // stop the event from propagating
    e.preventDefault()

    // JavaScript doesn't offer a relative position of the click / touch on the canvas,
    // only an absolute position with respect to the window (or something to that effect),
    // so the following relativizes the location
    val rect = canvas.getBoundingClientRect()
    val dx = rect.left
    val dy = rect.top
    val location =
        when (e) {
            is MouseEvent -> Pair(e.x - dx, e.y - dy)
            !is TouchEvent -> Pair(0.0, 0.0)
            else -> {
                val touch = e.changedTouches[0]!!
                Pair(touch.clientX.toDouble() - dx, touch.clientY.toDouble() - dy)
            }
        }

    val game = game_context.game
    val grid = game_context.grid
    val was_selected = game.stick_is_selected()
    game.select_stick(grid.stick_at(location.first, location.second), stick_color)
    if (was_selected && !game.stick_is_selected()) ++num_moves

    // once two sticks are generated, we may generate a new stick, so adjust the window
    var (x,y) = Pair(window_x,window_y)
    for (s in game.configuration) {
        x = max(max(x, s.p.x), s.q.x)
        y = max(max(y, s.p.y), s.q.y)
    }
    grid.set_window(x, y)

    // draw the updated gameboard
    grid.draw(GrevLex_Ordering)

    // is the game over? if so, then highlight
    if (game.is_over(GrevLex_Ordering)) {
        for ( i in game.configuration.indices ) {
            if (game.configuration[i] !in game.solution)
                game.colors[i] = inactive_color
            else{
                game.colors[i] = solution_color
                game.show_region[i] = true
            }
        }
        grid.draw(GrevLex_Ordering)
        window.alert("The game is over! I solved in ${game.num_moves} moves; you solved in $num_moves")
    }

}

fun new_game() {

    game = Groebner_Solitaire()
    game_context = JS_Game(game, JS_Grid(game, canvas, window_x, window_y))
    num_moves = 0

}

@Suppress("Unused")
@JsName("replay")
fun replay() {

    game_context.game.reset_configuration(game_start)
    num_moves = 0
    game_context.grid.draw(GrevLex_Ordering)

}

@Suppress("Unused")
@JsName("level_zero_game")
fun level_zero_game() {

    new_game()

    val a  = Random.nextInt(window_y / 2)
    val b = a + Random.nextInt(window_y / 2) + 1
    game.add_stick(Stick(Point(0,a), Point(0,b)), stick_color)

    val c = Random.nextInt(window_x / 2)
    val d  = c + Random.nextInt(window_y / 2) + 1
    val e = if (Random.nextBoolean()) 0 else Random.nextInt(window_y)
    game.add_stick(Stick(Point(c,e), Point(d,e)), stick_color)

    game_start = game.configuration

    val max_x = max(window_x, d)
    val max_y = max(window_y, b)
    game_context.grid.set_window(max_x, max_y)
    game_context.grid.draw(GrevLex_Ordering)

}

@Suppress("Unused")
@JsName("level_one_game")
fun level_one_game() {

    new_game()

    val a  = Random.nextInt(5)
    val b1 = Random.nextInt(window_y)
    val b2 = b1 + Random.nextInt(window_y) + 1
    game.add_stick(Stick(Point(a,b1), Point(a,b2)), stick_color)

    var c = Random.nextInt(window_x)
    while (c <= a) c = Random.nextInt(window_x)
    var d  = Random.nextInt(window_y)
    while (d == c) d = Random.nextInt(window_y)
    var e = c + Random.nextInt(window_x) + 1
    while (e == a) e = c + Random.nextInt(window_x) + 1
    val f = b2 - b1 + d
    val max_x = max(window_x, e)
    val max_y = max(max(window_y, b2), f)
    game.add_stick(Stick(Point(c,d), Point(e,f)), stick_color)

    game_start = game.configuration

    game_context.grid.set_window(max_x, max_y)
    game_context.grid.draw(GrevLex_Ordering)

}

/**
 * a game with two random sticks
 */
fun random_game() {

    new_game()

    game.add_stick(random_stick(), stick_color)
    while (game.configuration.size == 1)
        game.add_stick(random_stick(), stick_color)

    game_start = game.configuration

    //console.log("basis is ${game.minimize(basis(game.configuration, GrevLex_Ordering), GrevLex_Ordering)}")

    // draw the initial setup
    game_context.grid.draw(GrevLex_Ordering)

}

fun main() {

    // add event listeners
    canvas.addEventListener("click", EventListener { e -> released(e) }, false)
    canvas.addEventListener("touchend", EventListener { e -> released(e) }, false)

    random_game()

}