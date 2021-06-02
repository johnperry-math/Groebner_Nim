# Gröbner Nim
An implementation of Gröbner Nim in Kotlin/JS.

## Description

Gröbner Nim is a game that involves the production of sticks.
It is named for [Gröbner bases](https://en.wikipedia.org/wiki/Gr%C3%B6bner_basis),
and by sheer coincidence simulates the computation of a Gröbner basis over the modulo-2 field.
A full description appears [in a paper I wrote with Haley Dozier](http://dx.doi.org/10.4169/math.mag.89.4.235).
You can find the game in action [here](http://www.math.usm.edu/perry/groebner-nim/),
along with a summary of how to play, along with a couple of variants.

## Purpose
The game is pedagogical in nature, and I wrote it for a summer program with high school students.
However, I have had some fun trying to beat the computer.

## Status
This is a work-in-progress and is provided free-of-charge, so while it is in current use,
it isn't guaranteed to be suitable for any needs except my own, and even then I encounter bugs on occasion.

## Usage
  * First build it in Idea, obtaining the files `Groebner_Nim.js`, `Groebner_Nim.js.map` in the `build/distributions` folder.

  * Copy these files to the folder where your HTML source code resides.

  * In the HTML file, create a canvas element. The example linked above uses dimensions of 600&times;600.
       * For now, it is essential to give the ID `game_canvas` to the canvas element; that is, `<canvas id="game_canvas" ...>`.

  * At the beginning of the HTML file, preferably in the header, add the following line:

       <script type="text/javascript" src="path/to/kotlin.js"></script>
       <script type="text/javascript" src="path/to/Groebner_Nim.js"></script>

    `path/to/` is the path to wherever you've placed `Groebner_Nim.js`.
    If `Groebner_Nim.js` is in the same folder as the HTML file, you can simply write `src=Groebner_Nim.js` and ignore the path.
    You can probably place this line anywhere in the file, just so long as it appears before the line stated below.

    After that, you should be able to access the following functions from JavaScript:
    `Groebner_Nim.random_game()`, `Groebner_Nim.level_zero_game()`, `Groebner_Nim.level_one_game()`, `Groebner_Nim.replay()`.
    The example page above invokes these when the user clicks a button; for instance,
    ```javascript
        <input type="button" value="Random" onclick="Groebner_Nim.random_game()">
    ```
    ...but there are other ways to do it.

## Structure
The source code is in Kotlin and compiles to JavaScript. Once Kotlin compiles to WebAssembly, I intend to bypass JavaScript entirely.

   * `simple.kt` handles the game control. The unfortunate name is due to Idea's defaults, and I plan to change it once I get around to figuring out how.
   * `Games.kt` defines a game of `Groebner_Solitaire`, along with a `JS_Game` data class, a front-end for JavaScript.
   * `Grids.kt` defines the abstract `Grid` class and the `JS_Grid` data class, a front-end for JavaScript.
   * `Orderings.kt` defines several ways to evaluate the head of a stick.
      * An abstract `Ordering` class.
      * Three objects that implement and instantiate it: `GrevLex_Ordering`, `Lex_Ordering`, and `Weighted_GrevLex`.
   * `Points.kt` defines a `Point` data class.
   * `Sticks.kt` defines both a `Stick` data class and several functions related to determining when the game finishes.

There is also an example HTML page, `index.html`.

## Examples of this package in "real-world" use
Already linked, but [here it is again](http://www.math.usm.edu/perry/groebner-nim/).

## License
<span xmlns:dct="http://purl.org/dc/terms/" property="dct:title">Gröbner Nim</span> by
<a xmlns:cc="http://creativecommons.org/ns#" href="https://github.com/johnperry-math" property="cc:attributionName" rel="cc:attributionURL">
  John Perry</a>
is licensed under a
<a rel="license" href="http://creativecommons.org/licenses/by-sa/4.0/">
  Creative Commons Attribution-ShareAlike 4.0 International License</a>.

![CC-BY-SA](https://i.creativecommons.org/l/by-sa/4.0/88x31.png)
