import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLInputElement
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
/** color of a stick that is highlighted for animation */
const val animation_color: String = "#dd00dd80"

/** randomizer for games, reassign to determine seed */
var game_randomizer : Random = Random

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
    val x1 = game_randomizer.nextInt(max_x)
    val y1 = game_randomizer.nextInt(max_y)
    var x2 = x1
    var y2 = y1
    // make sure the points aren't the same
    while (x1 == x2 && y1 == y2) {
        x2 = game_randomizer.nextInt(max_x)
        y2 = game_randomizer.nextInt(max_y)
    }
    return Stick(x1, y1, x2, y2)
}

/**
 * a button has been clicked and released, or a finger has touched down,
 * then come up, from the canvas; process it
 *
 * this removes the listeners, so that the user does not interfere with the animation.
 * it does not add the listeners; that is the task of [cleanup_select_stick]
 * @see cleanup_select_stick
 */
fun released(e: Event) {

    // stop the event from propagating
    e.preventDefault()

    // remove listeners
    canvas.removeEventListener("click", click_listener)
    canvas.removeEventListener("touchend", touchend_listener)

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

    // identify the stick chosen (if applicable) and proceed
    val game = game_context.game
    val grid = game_context.grid
    val was_selected = game.stick_is_selected()
    game.select_stick(grid.stick_at(location.first, location.second), stick_color, grid)
    if (was_selected && !game.stick_is_selected()) ++num_moves

}

/**
 * reverts to ordinary play after processing the selection of a stick
 *
 * adds a new stick to the game (if generated) by invoking [Groebner_Solitaire.add_stick],
 * adjusts the game window, draws the grid, and tests for end of game.
 * it also adds the event listeners removed by [released]
 */
fun cleanup_select_stick() {

    val game = game_context.game
    val grid = game_context.grid

    // if there's a new stick, add it
    game.add_stick()

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

    // activate listeners
    canvas.addEventListener("click", click_listener)
    canvas.addEventListener("touchend", touchend_listener)

}

/** listener for a click */
private val click_listener = EventListener { e -> released(e) }
/** listener for the end of a touch */
private val touchend_listener = EventListener { e -> released(e) }

/**
 * a fresh game is needed when we need to re-randomize;
 * otherwise, the game corresponding to the current seed is played
 */
var fresh_game_needed = true

/**
 * restores the random number generator to the state where it is seeded by [seed]
 */
@Suppress("Unused")
@JsName("restore_seed")
fun restore_seed(seed: Int) {
    game_randomizer = Random(seed)
    fresh_game_needed = false
}

/**
 * initializes [game_randomizer] to a new [seed] and restarts the game
 */
@Suppress("Unused")
@JsName("show_new_seed")
fun show_new_seed(seed: Int) {
    fresh_game_needed = true
    val seed_display = document.getElementById("seed_display") as HTMLInputElement
    seed_display.value = seed.toString()
}

/**
 *  tasks common for every new game, regardless of type
 *
 *  initializes [game], [game_context], and [num_moves],
 *  then generates a new seed for the game unless [fresh_game_needed] is true.
 *  in the latter case, it reads the seed from an input element named "seed_display"
 */
fun new_game() {

    game = Groebner_Solitaire()
    game_context = JS_Game(game, JS_Grid(game, canvas, window_x, window_y))
    num_moves = 0

    // generate a fresh game iff it is needed; otherwise, read from seed_display
    if (fresh_game_needed) {
        val seed = game_randomizer.nextInt()
        game_randomizer = Random(seed)
        show_new_seed(seed)
    } else {
        val seed_display = document.getElementById("seed_display") as HTMLInputElement
        val seed = seed_display.value.toInt()
        show_new_seed(seed)
    }

}

/** replays previous game */
@Suppress("Unused")
@JsName("replay")
fun replay() {

    game_context.game.reset_configuration(game_start)
    num_moves = 0
    game_context.grid.draw(GrevLex_Ordering)

}

/**
 * a level zero game consists of a horizontal stick and a vertical stick
 */
@Suppress("Unused")
@JsName("level_zero_game")
fun level_zero_game() {

    new_game()

    val a  = game_randomizer.nextInt(window_y / 2)
    val b = a + game_randomizer.nextInt(window_y / 2) + 1
    game.add_stick(Stick(Point(0,a), Point(0,b)), stick_color)

    val c = game_randomizer.nextInt(window_x / 2)
    val d  = c + game_randomizer.nextInt(window_y / 2) + 1
    val e = if (game_randomizer.nextBoolean()) 0 else game_randomizer.nextInt(window_y)
    game.add_stick(Stick(Point(c,e), Point(d,e)), stick_color)

    game_start = game.configuration

    val max_x = max(window_x, d)
    val max_y = max(window_y, b)
    game_context.grid.set_window(max_x, max_y)
    game_context.grid.draw(GrevLex_Ordering)

}

/**
 * a level one game consists of a vertical stick and a diagonal stick
 */
@Suppress("Unused")
@JsName("level_one_game")
fun level_one_game() {

    new_game()

    val a  = game_randomizer.nextInt(5)
    val b1 = game_randomizer.nextInt(window_y)
    val b2 = b1 + game_randomizer.nextInt(window_y) + 1
    game.add_stick(Stick(Point(a,b1), Point(a,b2)), stick_color)

    var c = game_randomizer.nextInt(window_x)
    while (c <= a) c = game_randomizer.nextInt(window_x)
    var d  = game_randomizer.nextInt(window_y)
    while (d == c) d = game_randomizer.nextInt(window_y)
    var e = c + game_randomizer.nextInt(window_x) + 1
    while (e == a) e = c + game_randomizer.nextInt(window_x) + 1
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

    console.log("random game")
    new_game()

    game.add_stick(random_stick(), stick_color)
    while (game.configuration.size == 1)
        game.add_stick(random_stick(), stick_color)

    game_start = game.configuration

    // draw the initial setup
    game_context.grid.draw(GrevLex_Ordering)

}

/** startup: initializes event listeners for [canvas] and starts a new [random_game] */
fun main() {

    // add event listeners
    canvas.addEventListener("click", click_listener, false)
    canvas.addEventListener("touchend", touchend_listener, false)

    random_game()

}