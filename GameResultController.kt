package at.aau.serg.controllers

import at.aau.serg.models.GameResult
import at.aau.serg.services.GameResultService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/game-results")
class GameResultController(
    private val gameResultService: GameResultService
) {

    @GetMapping("/{gameResultId}")
    fun getGameResult(@PathVariable gameResultId: Long): GameResult? {
        return gameResultService.getGameResult(gameResultId);
    }

    @GetMapping
    fun getAllGameResults(): List<GameResult> {
        return gameResultService.getGameResults();
    }

    @PostMapping
    fun addGameResult(@RequestBody gameResult: GameResult) {
        gameResultService.addGameResult(gameResult)
    }

    @DeleteMapping("/{gameResultId}")
    fun deleteGameResult(@PathVariable gameResultId: Long) {
        gameResultService.deleteGameResult(gameResultId)
    }
    
}