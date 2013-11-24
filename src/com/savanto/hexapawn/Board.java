package com.savanto.hexapawn;

import java.util.ArrayList;
import java.util.Random;

/**
 * @author savanto
 *
 */
public class Board
{
	/**
	 * The default Board dimension
	 */
	public static final int SIZE = 3;

	/**
	 * The possible turns.
	 * BLACK: black to move.
	 * WHITE: white to move.
	 */
	public static enum Color { BLACK, WHITE };

	/**
	 * The white and black pawns are each represented by a different
	 * array of bits, marking their positions on the Board.
	 * Eg:
	 * 
	 * 3 b b b
	 * 2 . . .
	 * 1 w w w
	 *   a b c
	 * 
	 * black: 111 000 000
	 * white: 000 000 111
	 */
	private int white;
	private int black;

	/**
	 * The color to play on this Board.
	 */
	private Color turn;

	/**
	 * The different rank and file masks for testing pawn locations.
	 */
	// RANK 1		RANK 2		RANK 3
	// 0 0 0		0 0 0		1 1 1
	// 0 0 0		1 1 1		0 0 0
	// 1 1 1		0 0 0		0 0 0
	public static final int RANK_1 = 7;
	public static final int RANK_2 = Board.nextRank(RANK_1);
	public static final int RANK_3 = Board.nextRank(RANK_2);

	// FILE A		FILE B		FILE C
	// 1 0 0		0 1 0		0 0 1
	// 1 0 0		0 1 0		0 0 1
	// 1 0 0		0 1 0		0 0 1
	public static final int FILE_C = 73;
	public static final int FILE_B = Board.prevFile(FILE_C);
	public static final int FILE_A = Board.prevFile(FILE_B);

	/**
	 * An array of all children Boards that are possible
	 * after legal moves on this Board.
	 */
	private ArrayList<Board> children;

	/**
	 * Constructor. Produces a Board from the given pawn configurations.
	 * @param black - bit array of black pawns.
	 * @param white - bit array of white pawns.
	 * @param turn - which color to move on this Board.
	 */
	public Board(int black, int white, Color turn)
	{
		this.black = black;
		this.white = white;
		this.turn = turn;
		this.children = new ArrayList<Board>();
	}

	/**
	 * Constructor. Produces a Board from the given array of pawns.
	 * @param array - array of pawns
	 */
	public Board(Color[][] array)
	{
		for (int row = 0, rank = RANK_3; row < Board.SIZE; row++, rank = Board.prevRank(rank))
		{
			for (int col = 0, file = FILE_A; col < Board.SIZE; col++, file = Board.nextFile(file))
			{
				if (array[row][col] != null)
				{
					switch (array[row][col])
					{
						case BLACK:
							this.black = this.black | (rank & file);
							break;
						case WHITE:
							this.white = this.white | (rank & file);
							break;
					}
				}
			}
		}
	}

	// Accessors

	/**
	 * Compare two Boards.
	 * @param rhs - the Board to compare to this Board.
	 * @return true if pawn configurations are the same, ie. Boards are identical.
	 */
	public boolean equals(Board rhs)
	{
		return this.black == rhs.black && this.white == rhs.white;
	}

	/**
	 * Checks whether this Board is a victory Board based on whether
	 * there are possible child Boards.
	 * @return true if there are no child Boards, ie. this is a victory Board.
	 */
	public boolean isVictory()
	{
		return this.children.size() == 0 ? true : false;
	}

	/**
	 * Calculates the mask of the next file from the given one
	 * @param file - current file on the Board
	 * @return the next file on the Board
	 */
	public static int nextFile(int file)
	{
		return file >> 1;
	}

	/**
	 * Calculates the mask of the previous file from the given one
	 * @param file - current file on the Board
	 * @return the previous file on the Board
	 */
	public static int prevFile(int file)
	{
		return file << 1;
	}

	/**
	 * Calculates the mask of the next rank from the given one
	 * @param rank - current rank on the Board
	 * @return the next rank on the Board
	 */
	public static int nextRank(int rank)
	{
		return rank << 3;
	}

	/**
	 * Calculates the mask of the previous rank from the given one
	 * @param rank - current rank on the Board
	 * @return the previous rank on the Board
	 */
	public static int prevRank(int rank)
	{
		return rank >> 3;
	}

	public Move getMove(Board child)
	{
		// Determine the move, and hence which color will be checked for moves.
		int sourceRank, destRank, sourceRow = 0, sourceCol = 0, destRow = 0, destCol = 0, diff;	// seekers
		switch (this.turn)
		{
			// BLACK has moved to produce the child Board
			case BLACK:
				// Isolate the source and destinations
				diff = this.black ^ child.black;
				// For black, higher ranks will be the source, lower the destination.
				// Seek the first hit => source
				sourceRank = RANK_3;
				sourceRow = 0;
				while ((diff & sourceRank) == 0)
				{
					sourceRank = Board.prevRank(sourceRank);
					sourceRow++;
				}
				// Destination row is source row + 1
				destRank = Board.prevRank(sourceRank);
				destRow = sourceRow + 1;
				// Seek files/columns of both source and destination
				for (int col = 0, file = FILE_A; col < Board.SIZE; col++, file = Board.nextFile(file))
				{
					if ((diff & sourceRank & file) != 0)
						sourceCol = col;
					if ((diff & destRank & file) != 0)
						destCol = col;
				}

				break;

				// WHITE has moved to produce the child Board
				case WHITE:
					// Isolate the source and destinations
					diff = this.white ^ child.white;
					// For white, lower ranks will be the source, higher the destination.
					// Seek the first hit => source
					sourceRank = RANK_1;
					sourceRow = Board.SIZE - 1;
					while ((diff & sourceRank) == 0)
					{
						sourceRank = Board.nextRank(sourceRank);
						sourceRow--;
					}
					// Destination row is source row - 1
					destRank = Board.nextRank(sourceRank);
					destRow = sourceRow - 1;
					// Seek files/columns of both source and destination
					for (int col = 0, file = FILE_A; col < Board.SIZE; col++, file = Board.nextFile(file))
					{
						if ((diff & sourceRank & file) != 0)
							sourceCol = col;
						else if ((diff & destRank & file) != 0)
							destCol = col;
					}

					break;
		}
		return new Move(sourceRow, sourceCol, destRow, destCol);
	}

	/**
	 * Choose a child Board at random from among this Board's children Boards.
	 * @param rng - a seeded pseudorandom number generator.
	 * @return returns a randomly chosen child Board, or null if no children Boards available.
	 */
	public Board pickBoard(Random rng)
	{
		int n = this.children.size();
		if (n == 0)
			return null;
		return this.children.get(rng.nextInt(n));
	}

	public ArrayList<Board> getChildren()
	{
		return this.children;
	}

	/**
	 * Look up a Board in this Board's list of child Boards, to see if a given
	 * Board arose as a result of a legal move on this Board.
	 * @param child - the child Board resulting from the move
	 * @return the child Board if it is found, indicating a legal move; null otherwise
	 */
	public Board getLegal(Board test)
	{
		// Traverse child boards array
		for (int i = 0; i < this.children.size(); i++)
		{
			Board child = this.children.get(i);
			if (child.equals(test))
				return child;
		}
		// Matching child Board not found
		return null;
	}

	public int getBlack()
	{
		return this.black;
	}

	public int getWhite()
	{
		return this.white;
	}

	public Color getTurn()
	{
		return this.turn;
	}

	/**
	 * Returns a 2D array representing the game Board.
	 * Note that array has coordinates different from rank and file system:
	 *   0 1 2
	 * 0 . . .
	 * 1 . . .
	 * 2 . . .
	 * 
	 * This is for easier management with the graphical board, since the
	 * origin of the screen is in the top left corner.
	 * 
	 * @return 2D array with BLACK, WHITE, and null fields representing the pawns
	 * and empty places of the Board.
	 */
	public Color[][] toArray()
	{
		Color[][] array = new Color[Board.SIZE][Board.SIZE];
		for (int rank = RANK_3, row = 0; row < Board.SIZE; rank = Board.prevRank(rank), row++)
		{
			for (int file = FILE_A, col = 0; col < Board.SIZE; file = Board.nextFile(file), col++)
			{
				if ((rank & file & this.white) != 0)
					array[row][col] = Color.WHITE;
				else if ((rank & file & this.black) != 0)
					array[row][col] = Color.BLACK;
				else
					array[row][col] = null;
			}
		}
		return array;
	}

	// Modifiers

	/**
	 * Recursively generate all children moves of the given board,
	 * and their children. Used to create all possible moves for the first time.
	 */
	public void generate()
	{
		// Check for the first two victory conditions: one side eliminated,
		// or one side has reached the home rank of the other.
		// If so, stop generation of children, as this is a victory Board.
		if (this.isVictoryCondition())
			return;

		// Otherwise, check for available moves.
		// Note that if no available moves are found, this is victory condition #3,
		// and this Board will have no children and be a victory Board as well.

		// Check moves for appropriate side, based on turn
		int rank, file, pawn, move;
		switch (this.turn)
		{
			case BLACK:
				// Isolate pawns to check for moves.
				// If move is possible, generate a new Board with the result,
				// put it on the children list, and generate subsequent moves.

				// Black pawns are only found on ranks 2 and 2 (1 is victory)
				for (rank = RANK_2; rank <= RANK_3; rank = Board.nextRank(rank))
				{
					for (file = FILE_A; file >= FILE_C; file = Board.nextFile(file))
					{
						pawn = file & rank & this.black;
						if (pawn != 0)
						{
							// Forward move is possible for all pawns (equivalent to shift right by 3).
							// Check move forward: possible if no other pawns (black or white) on destination
							move = pawn >> 3;
							if ((move & this.white) == 0 && (move & this.black) == 0)	// no obstructions
							{
								// Generate new Board:
								// The white pawns remain the same.
								// The black pawns: remove the current pawn under consideration, and add it in its new place.
								// The turn will now be WHITE, since BLACK just moved.
								Board child = new Board(this.black ^ pawn | move, this.white, Color.WHITE);
								this.children.add(child);
								// Generate the child's children moves
								child.generate();
							}

							// Capture moves.
							// File A pawns may only capture to the right diagonal, ie. aN -> bN-1 (shift right by 4)
							// File C pawns may only capture to the left diagonal, ie. cN -> bN-1 (shift right by 2)
							// File B pawns may capture right or left diagonal, ie. bN -> cN-1 OR aN-1 (shift left by 4 OR 2)
							// Capture move possible if a white pawn is on the destination
							if (file != FILE_A)
							{
								move = pawn >> 2;
								if ((move & this.white) != 0)
								{
									// Generate new Board:
									// The white pawn at destination is removed.
									// The black pawns: remove the current pawn under consideration, and add it in its new place.
									// The turn will now be WHITE, since BLACK just moved.
									Board child = new Board(this.black ^ pawn | move, this.white ^ move, Color.WHITE);
									this.children.add(child);
									// Generate the child's children moves
									child.generate();									
								}
							}
							if (file != FILE_C)
							{
								move = pawn >> 4;
								if ((move & this.white) != 0)
								{
									// Generate new Board:
									// The white pawn at destination is removed.
									// The black pawns: remove the current pawn under consideration, and add it in its new place.
									// The turn will now be WHITE, since BLACK just moved.
									Board child = new Board(this.black ^ pawn | move, this.white ^ move, Color.WHITE);
									this.children.add(child);
									// Generate the child's children moves
									child.generate();									
								}
							}
						}
					}
				}
				break;

			case WHITE:
				// Isolate pawns to check for moves.
				// If move is possible, generate a new Board with the result,
				// put it on the children list, and generate subsequent moves.

				// White pawns are only found on ranks 1 and 2 (3 is victory)
				for (rank = RANK_1; rank <= RANK_2; rank = Board.nextRank(rank))
				{
					for (file = FILE_A; file >= FILE_C; file = Board.nextFile(file))
					{
						pawn = file & rank & this.white;
						if (pawn != 0)
						{
							// Forward move is possible for all pawns (equivalent to shift left by 3).
							// Check move forward: possible if no other pawns (black or white) on destination
							move = pawn << 3;
							if ((move & this.black) == 0 && (move & this.white) == 0)	// no obstructions
							{
								// Generate new Board:
								// The black pawns remain the same.
								// The white pawns: remove the current pawn under consideration, and add it in its new place.
								// The turn will now be BLACK, since WHITE just moved.
								Board child = new Board(this.black, this.white ^ pawn | move, Color.BLACK);
								this.children.add(child);
								// Generate the child's children moves
								child.generate();
							}

							// Capture moves.
							// File A pawns may only capture to the right diagonal, ie. aN -> bN+1 (shift left by 2)
							// File C pawns may only capture to the left diagonal, ie. cN -> bN+1 (shift left by 4)
							// File B pawns may capture right or left diagonal, ie. bN -> cN+1 OR aN+1 (shift left by 2 OR 4)
							// Capture move possible if a black pawn is on the destination
							if (file != FILE_A)
							{
								move = pawn << 4;
								if ((move & this.black) != 0)
								{
									// Generate new Board:
									// The black pawn at destination is removed.
									// The white pawns: remove the current pawn under consideration, and add it in its new place.
									// The turn will now be BLACK, since WHITE just moved.
									Board child = new Board(this.black ^ move, this.white ^ pawn | move, Color.BLACK);
									this.children.add(child);
									// Generate the child's children moves
									child.generate();									
								}
							}
							if (file != FILE_C)
							{
								move = pawn << 2;
								if ((move & this.black) != 0)
								{
									// Generate new Board:
									// The black pawn at destination is removed.
									// The white pawns: remove the current pawn under consideration, and add it in its new place.
									// The turn will now be BLACK, since WHITE just moved.
									Board child = new Board(this.black ^ move, this.white ^ pawn | move, Color.BLACK);
									this.children.add(child);
									// Generate the child's children moves
									child.generate();									
								}
							}
						}
					}
				}
				break;
		}
	}

	/**
	 * Adds the given child Board to this Board's children array.
	 * @param child - the child Board to add.
	 */
	public void addChild(Board child)
	{
		this.children.add(child);
	}

	/**
	 * Removes a given child Board from the moves tree.
	 * @param child - child Board to remove.
	 */
	public void prune(Board child)
	{
		for (int i = 0; i < this.children.size(); i++)
		{
			if (this.children.get(i).equals(child))
				this.children.remove(i);
		}
	}

	// Internal helper functions

	/**
	 * Checks for victory conditions on this Board.
	 * @return true if this is a victory Board for one of the sides.
	 */
	private boolean isVictoryCondition()
	{
		// 1. Victory condition: one of the teams has been eliminated
		if (this.white == 0 || this.black == 0)
			return true;

		// 2. Victory condition: a pawn has reached the opponent's home row,
		// ie. white has reached rank 3, or black has reached rank 1
		if ((this.white & RANK_3) > 0 || (this.black & RANK_1) > 0)
			return true;

		// 3. Victory condition: the side whose move it is, has none available.
		// This condition is met from the way boards are created, and does not
		// need to be checked.

		// Otherwise, no victory conditions have been met.
		return false;
	}

	/**
	 * Produces a string representation for the board, for ease of storage in database.
	 */
	@Override
	public String toString()
	{
		return Integer.toString(this.black) + " " + Integer.toString(this.white);
	}
}
