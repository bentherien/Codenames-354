/*
 * Game.java
 * Created by: Steven Zanga
 * Created on: 23/01/19
 *
 * Contributors:
 * Steven Zanga
 * Benjamin Therien
 * Christophe Savard
 * Michael Wilgus
 */

package com.comp354pjb.codenames.model;

import com.comp354pjb.codenames.commander.Commander;
import com.comp354pjb.codenames.model.board.Board;
import com.comp354pjb.codenames.model.board.Card;
import com.comp354pjb.codenames.model.board.CardType;
import com.comp354pjb.codenames.model.player.*;
import com.comp354pjb.codenames.observer.events.ClueGivenEvent;
import com.comp354pjb.codenames.observer.events.PhaseEvent;
import com.comp354pjb.codenames.observer.events.RoundEvent;

import java.util.*;

public class Game
{
    //region Constants
    /**
     * Random number generator for the Game
     */
    public static final Random RANDOM = new Random();
    //endregion

    //region Events
    /**
     * Clue given event
     */
    public final ClueGivenEvent onClueGiven = new ClueGivenEvent();
    /**
     * Phase change event
     */
    public final PhaseEvent onPhaseChange = new PhaseEvent();
    /**
     * Round change event
     */
    public final RoundEvent onRoundChange = new RoundEvent();
    //endregion

    //region Fields
    private int playerIndex, round = 1;
    private ArrayList<Player> players = new ArrayList<>();
    //endregion

    //region Properties
    private final Board board;
    /**
     * Gets this game's Board
     */
    public Board getBoard()
    {
        return this.board;
    }

    private PlayerType startTeam;
    /**
     * Gets the starting team colour
     */
    public PlayerType getStartTeam()
    {
        return this.startTeam;
    }

    private Player currentPlayer;
    public void setCurrentPlayer(Player player) { this.currentPlayer = player; }

    //score keeping members
    private int guessesLeft;

    /**
     * Gets the number of guesses left given the current clue
     */
    public int getGuessesLeft() { return this.guessesLeft; }

    private int redCardsRevealed;

    private int blueCardsRevealed;

    private boolean assassinRevealed;
    /**
     * Sets if the assassin card has been revealed
     */
    public void setAssassinRevealed(boolean assassinRevealed)
    {
        this.assassinRevealed = assassinRevealed;
    }

    private PlayerType winner;
    /**
     * Gets the winning player
     */
    public PlayerType getWinner()
    {
        return this.winner;
    }
    /**
     * Sets the winning player
     */
    public void setWinner(PlayerType winner)
    {
        this.winner = winner;
        switch (this.winner)
        {
            case RED:
                this.loser = PlayerType.BLUE;
                break;
            case BLUE:
                this.loser = PlayerType.RED;
        }
    }

    private PlayerType loser;
    /**
     * Sets the game's loser
     */
    public void setLoser(PlayerType loser)
    {
        this.loser = loser;
        switch (this.loser)
        {
            case RED:
                this.winner = PlayerType.BLUE;
                break;
            case BLUE:
                this.winner = PlayerType.RED;
        }
    }

    private SuggestionGraph graph;
    /**
     *
     * @return
     */
    public SuggestionGraph getSuggestionMap()
    {
        return graph;
    }
    //endregion

    //region Constructors
    /**
     * Creates a new Game object and correctly sets up the board and cards, as well as players
     *
     * Update by Rezza-Zairan
     * ----------------------
     * @param passInt is passed by the controller to hold an array of PlayerIntelligence chosen by the user.
     */
    public Game(PlayerIntelligence passInt[])
    {
        String[] setup = DatabaseHelper.getBoardLayout();
        setPlayers(setup[0], passInt);
        this.board = new Board(DatabaseHelper.getRandomCodenames(25), setup[1]);
        this.graph = createSuggestionMap();
    }
    //endregion

    //region Methods
    /**
     * Sets the starting player for the game and initializes the AIs correctly
     * @param startingPlayer Starting team name
     *
     * Update by Rezza-Zairan
     * ----------------------
     * @param passInt is passed by the controller to hold an array of PlayerIntelligence chosen by the user.
     */
    private void setPlayers(String startingPlayer, PlayerIntelligence passInt[])
    {

        this.startTeam = PlayerType.parse(startingPlayer);
        PlayerType second = this.startTeam == PlayerType.RED ? PlayerType.BLUE : PlayerType.RED;

        //Rearranging AI according to who starts first
        PlayerIntelligence arrangedInt[] = new PlayerIntelligence[4];
        arrangedInt = passInt;

        if (this.startTeam == PlayerType.BLUE)
        {
            arrangedInt[0] = passInt[2];
            arrangedInt[1] = passInt[3];
            arrangedInt[2] = passInt[0];
            arrangedInt[3] = passInt[1];
        }

        Strategy startSpyMasterStrategy = StrategyFactory.makeStrategy("spymaster", this, arrangedInt[0]);
        Strategy startOperativeStrategy = StrategyFactory.makeStrategy("operative", this, arrangedInt[1]);
        Strategy secondSpyMasterStrategy = StrategyFactory.makeStrategy("spymaster", this, arrangedInt[2]);
        Strategy secondOperativeStrategy = StrategyFactory.makeStrategy("operative", this, arrangedInt[3]);

        Commander.log(this.startTeam.niceName() + " Team will start, which means they must guess 9 cards");
        Commander.log(second.niceName() + " Team will go second, which means they must guess 8 cards");
        this.players.add(new Player(this.startTeam, startSpyMasterStrategy));
        this.players.add(new Player(this.startTeam, startOperativeStrategy));
        this.players.add(new Player(second, secondSpyMasterStrategy));
        this.players.add(new Player(second, secondOperativeStrategy));
    }

    /**
     * Checks if the game must end
     * @return True if a winning game condition has been met
     */
    public boolean checkWinner()
    {
        //Game ends as soon as the Assassin is revealed
        if (this.assassinRevealed)
        {
            return true;
        }
        else
        {
             //Check the starting team for correct card numbers
            int redTarget = 8, blueTarget = 8;
            switch (this.startTeam)
            {
                case RED:
                    redTarget++;
                    break;
                case BLUE:
                    blueTarget++;
                    break;
            }

            //Check for a winner
            if (this.redCardsRevealed == redTarget)
            {
                setWinner(PlayerType.RED);
                return true;
            }
            if (this.blueCardsRevealed == blueTarget)
            {
                setWinner(PlayerType.BLUE);
                return true;
            }
            return false;
        }
    }

    /**
     * This method plays the next turn of the game
     */
    public void enterNextGameTurn()
    {
        //Play the current player's turn
        this.currentPlayer = this.players.get(this.playerIndex);

        this.currentPlayer.play();

        if (currentPlayer.isFinished())
        {
            currentPlayer.setFinished(false);
            this.playerIndex = (this.playerIndex + 1) % this.players.size();

            if (this.playerIndex == 0)
            {
                this.onRoundChange.invoke(++this.round);
            }
        }

    }

    private Clue currentClue;

    public Clue getCurrentClue()
    {
        return this.currentClue;
    }

    /**
     * Sets the current clue
     * @param clue New clue
     */
    public void setCurrentClue(Clue clue)
    {
        this.currentClue = clue;
        this.guessesLeft = clue.value;
        this.onClueGiven.invoke(clue);
    }

    /**
     * Reveal a card on this games board
     * @param card The card to reveal
     */
    public void revealCard(Card card)
    {
        this.board.revealCard(card);
        this.graph.pickCard(card.getWord());

        switch (card.getType()) {
            //Actions for revealing an assassin card
            case ASSASSIN:
                this.setLoser(this.currentPlayer.getTeam());
                this.setAssassinRevealed(true);
                //Actions for revealing a civilian card
            case CIVILIAN:
                this.guessesLeft = 0;
                return;

            //Actions for revealing a red card
            case RED:
                this.redCardsRevealed++;
                break;

            //Actions for revealing a red card
            case BLUE:
                this.blueCardsRevealed++;
                break;

        }
        //Take according actions
        if(this.currentPlayer.getTeam().getCardType().equals(card.getType())) {
            this.guessesLeft--;
        } else {
            this.guessesLeft = 0;
        }
    }

    /**
     * Sets the game's phase
     * @param phase New game phase
     */
    public void setPhase(String phase)
    {
        this.onPhaseChange.invoke(phase);
    }
    //endregion

    //region Helpers
    //
    private SuggestionGraph createSuggestionMap()
    {
        ArrayList<Card> codenames = board.getCards();
        for(Card c : codenames)
        {
            String[] clues = DatabaseHelper.getCluesForCodename(c.getWord().toLowerCase());
            for(int i = 0; i < clues.length; i++)
            {
                String clue = DatabaseHelper.toCamelCase(clues[i]);
                c.addClue(clue);
            }
        }

        HashMap<String, Clue> clues = new HashMap<>();
        HashMap<String, Card> cards = new HashMap<>();
        Card assassin = null;
        for(Card c : codenames)
        {
            if(c.getType() == CardType.ASSASSIN)
            {
                assassin = c;
            }

            cards.put(c.getWord(), c);
            HashSet<String> suggestions = c.getClues();
            for(String s : suggestions)
            {
                Clue clue = clues.getOrDefault(s, new Clue(s, 0));
                clue.addCard(c);
                clues.put(s, clue);
            }
        }

        ArrayList<String> badClues = new ArrayList<>();
        for(Clue clue : clues.values())
        {
            if(clue.onlySuggestsAssassinOrCivilian())
            {
                badClues.add(clue.word);

            }
            if(cards.containsKey(clue.word))
            {
                clue.isActiveCodename = true;
            }
        }

        for(String key : badClues)
        {
            clues.remove(key);
            assassin.removeClue(key);
        }

        return new SuggestionGraph(clues, cards);
    }
    //endregion
}
