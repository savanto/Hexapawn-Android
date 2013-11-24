package com.savanto.hexapawn;

import java.util.ArrayList;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;

/**
 * @author savanto
 *
 * Helper class to simplify interaction with the database from the main program.
 */
public class DatabaseHelper extends SQLiteOpenHelper
{
	/**
	 * Version of the database. If the schema is changed, the version number must be incremented.
	 */
	private static final int DATABASE_VERSION = 1;

	/**
	 * Filename of the database.
	 */
	public static final String DATABASE_NAME = "Hexapawn.db";

	/**
	 * The Board, holding all game Boards, either passed in
	 * after move generation, or retrieved from the database.
	 */
	private Board board;

	/* SELECT criteria */
	/**
	 * SELECT clauses
	 */
	private static final String[] LOAD_SELECT =
		{
			DatabaseSchema.BoardsTable._ID,
			DatabaseSchema.BoardsTable.FIELD_NAME_BLACK,
			DatabaseSchema.BoardsTable.FIELD_NAME_WHITE,
			DatabaseSchema.BoardsTable.FIELD_NAME_TURN
		};
	private static final String[] SELECT_ID = { DatabaseSchema.BoardsTable._ID };

	/**
	 * WHERE clauses
	 */
	private static final String LOAD_WHERE =
			DatabaseSchema.BoardsTable.FIELD_NAME_ACTIVE + " = 1"
			+ " AND "
			+ DatabaseSchema.BoardsTable.FIELD_NAME_PARENT + " = ?";
	private static final String PRUNE_WHERE =
			DatabaseSchema.BoardsTable.FIELD_NAME_BLACK + " = ? AND "
			+ DatabaseSchema.BoardsTable.FIELD_NAME_WHITE + " = ? AND "
			+ DatabaseSchema.BoardsTable.FIELD_NAME_TURN  + " = ?";
	private static final String WHERE_WHITE_WINS =
			DatabaseSchema.BoardsTable.FIELD_NAME_VICTORY + " = 1 AND "
			+ DatabaseSchema.BoardsTable.FIELD_NAME_TURN + " = 0";
	private static final String WHERE_WHITE_WINS_ACTIVE =
			WHERE_WHITE_WINS + " AND " + DatabaseSchema.BoardsTable.FIELD_NAME_ACTIVE + " = 1";
	private static final String WHERE_ID = 
			DatabaseSchema.BoardsTable._ID + " = ?";
	private static final String WHERE_PARENT =
			DatabaseSchema.BoardsTable.FIELD_NAME_PARENT + " = ?";

	// Constructors

	/**
	 * Standard constructor.
	 * @param context
	 */
	public DatabaseHelper(Context context)
	{
		super(context, DatabaseHelper.DATABASE_NAME, null, DatabaseHelper.DATABASE_VERSION);
	}

	/* (non-Javadoc)
	 * @see android.database.sqlite.SQLiteOpenHelper#onCreate(android.database.sqlite.SQLiteDatabase)
	 */
	@Override
	public void onCreate(SQLiteDatabase db)
	{
		// Create database for the first time.
		// Create the tables.
		db.execSQL(DatabaseSchema.BoardsTable.SQL_CREATE_TABLE_BOARDS);

		// Populate boards table from Board.
		// Traverse the Board tree recursively, inserting Boards into the database.
		this.insertBoards(this.board, 0, db);
	}

	/* (non-Javadoc)
	 * @see android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database.sqlite.SQLiteDatabase, int, int)
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
	{
		// Upgrade policy:
		// Create moves tree from stored data,
		// drop old tables, and call onCreate to recreate db from moves tree.

		// Load all moves
		this.board = this.loadBoards();
		db.execSQL(DatabaseSchema.BoardsTable.SQL_DROP_TABLE_BOARDS);
		this.onCreate(db);
	}

	/**
	 * Called if moves tree has been generated and needs to be
	 * stored in the database.
	 * @param board
	 */
	public void storeBoards(Board board)
	{
		this.board = board;
		this.getWritableDatabase();
	}

	/**
	 * Function to load the Boards from the database into a move tree.
	 * @return the root of a complete moves tree.
	 */
	public Board loadBoards()
	{
		// Load root Board with id = 1
		String[] whereArgs = { Integer.toString(0) };

		Cursor c = this.getReadableDatabase().query(
				DatabaseSchema.BoardsTable.TABLE_NAME,
				LOAD_SELECT,
				LOAD_WHERE,
				whereArgs,
				null,	// don't group rows
				null,	// don't filter by row groups
				null	// don't order
		);

		// Error, no root Board found in db.
		if (c.getCount() == 0)
			return null;

		// Otherwise, create new root Board from row
		c.moveToFirst();
		Board root = new Board(
				c.getInt(c.getColumnIndexOrThrow(DatabaseSchema.BoardsTable.FIELD_NAME_BLACK)),
				c.getInt(c.getColumnIndexOrThrow(DatabaseSchema.BoardsTable.FIELD_NAME_WHITE)),
				c.getInt(c.getColumnIndexOrThrow(DatabaseSchema.BoardsTable.FIELD_NAME_TURN)) == 0 ? Board.Color.BLACK : Board.Color.WHITE
		);

		// Recursively look up the root Board's children and add them
		this.loadBoards(root, c.getInt(c.getColumnIndexOrThrow(DatabaseSchema.BoardsTable._ID)));

		return root;
	}

	/**
	 * Recursive helper function to query database for all children of a given
	 * parent Board and add them to the moves tree.
	 * @param parent - the Board that is the parent of the children being queried.
	 * @param rowid - the id in the database of the parent Board.
	 */
	private void loadBoards(Board parent, int rowid)
	{
		String[] whereArgs = { Integer.toString(rowid) };

		Cursor c = this.getReadableDatabase().query(
				DatabaseSchema.BoardsTable.TABLE_NAME,
				LOAD_SELECT,
				LOAD_WHERE,
				whereArgs,
				null,	// don't group rows
				null,	// don't filter by row groups
				null	// don't order
		);

		// Base case: no rows returned with given parent, so this is an
		// ending Board. No children to add.
		if (c.getCount() == 0)
			return;

		// Otherwise, load all child Boards recursively,
		// adding them to the children array.
		c.moveToFirst();
		while (! c.isAfterLast())
		{
			Board child = new Board(
				c.getInt(c.getColumnIndexOrThrow(DatabaseSchema.BoardsTable.FIELD_NAME_BLACK)),
				c.getInt(c.getColumnIndexOrThrow(DatabaseSchema.BoardsTable.FIELD_NAME_WHITE)),
				c.getInt(c.getColumnIndexOrThrow(DatabaseSchema.BoardsTable.FIELD_NAME_TURN)) == 0 ? Board.Color.BLACK : Board.Color.WHITE
			);
			parent.addChild(child);
			this.loadBoards(child, c.getInt(c.getColumnIndexOrThrow(DatabaseSchema.BoardsTable._ID)));
			c.moveToNext();
		}
	}

	/**
	 * Recursively inserts given Board and its children into the database. 
	 * @param board - the parent Board being inserted.
	 * @param rowid - the rowid of the parent Board.
	 * @param db - the database
	 */
	private void insertBoards(Board board, long rowid, SQLiteDatabase db)
	{
		// Insert given Board.
		ContentValues values = new ContentValues();
		values.put(DatabaseSchema.BoardsTable.FIELD_NAME_BLACK, board.getBlack());
		values.put(DatabaseSchema.BoardsTable.FIELD_NAME_WHITE, board.getWhite());
		values.put(DatabaseSchema.BoardsTable.FIELD_NAME_TURN, board.getTurn() == Board.Color.BLACK ? 0 : 1);
		values.put(DatabaseSchema.BoardsTable.FIELD_NAME_PARENT, rowid);
		values.put(DatabaseSchema.BoardsTable.FIELD_NAME_VICTORY, board.isVictory() ? 1 : 0);
		values.put(DatabaseSchema.BoardsTable.FIELD_NAME_ACTIVE, 1);
		rowid = db.insert(DatabaseSchema.BoardsTable.TABLE_NAME, null, values);

		// Pass over children array, insert them into db, recursively
		// traversing down each child's tree.
		ArrayList<Board> children = board.getChildren();
		int n = children.size();

		// Base case: no children to insert
		if (n == 0)
			return;

		for (int i = 0; i < n; i++)
			this.insertBoards(children.get(i), rowid, db);
	}

	/**
	 * Update the database, setting the given Board and all child
	 * Boards as inactive, and not eligible for loading in the future.
	 * @param board - the parent Board to prune, along with all children.
	 */
	public void pruneBoards(Board board)
	{
		// Query database for given Board
		String[] whereArgs =
			{
				Integer.toString(board.getBlack()),
				Integer.toString(board.getWhite()),
				Integer.toString(board.getTurn() == Board.Color.BLACK ? 0 : 1)
			};

		Cursor c = this.getReadableDatabase().query(
				DatabaseSchema.BoardsTable.TABLE_NAME,
				SELECT_ID,
				PRUNE_WHERE,
				whereArgs,
				null,	// don't group rows
				null,	// don't filter by row groups
				null	// don't order
		);

		// No Boards found, cancel pruning.
		if (c.getCount() == 0)
			return;
		// Otherwise, recursively prune all children.
		c.moveToFirst();
		int rowid = c.getInt(c.getColumnIndexOrThrow(DatabaseSchema.BoardsTable._ID));

		ContentValues values = new ContentValues();
		values.put(DatabaseSchema.BoardsTable.FIELD_NAME_ACTIVE, 0);

		this.pruneBoards(rowid, values);

		// Finally, update this Board.
		whereArgs = new String[]{ Integer.toString(rowid) };

		this.getWritableDatabase().update(
				DatabaseSchema.BoardsTable.TABLE_NAME,
				values,
				WHERE_ID,
				whereArgs
		);
	}

	/**
	 * Update the database recursively, updating all child records to be inactive
	 * and ineligible for loading in the future.
	 * @param rowid - the parent id to use in the WHERE clause.
	 * @param values - update values.
	 */
	private void pruneBoards(int rowid, ContentValues values)
	{
		// Update all children records to be inactive.
		String[] whereArgs = { Integer.toString(rowid) };

		this.getWritableDatabase().update(
				DatabaseSchema.BoardsTable.TABLE_NAME,
				values,
				WHERE_PARENT,
				whereArgs
		);

		// Query database for all children of the given Board.
		Cursor c = this.getReadableDatabase().query(
				DatabaseSchema.BoardsTable.TABLE_NAME,
				SELECT_ID,
				WHERE_PARENT,
				whereArgs,
				null,	// don't group rows
				null,	// don't filter by row groups
				null	// don't order
		);

		// Recursively update all of the childrens' children.
		c.moveToFirst();
		while (! c.isAfterLast())
		{
			this.pruneBoards(c.getInt(c.getColumnIndexOrThrow(DatabaseSchema.BoardsTable._ID)), values);
			c.moveToNext();
		}
	}

	/**
	 * Calculates the AI skill based on how many losing moves remain
	 * @return skill of AI, as a percentage of 100
	 */
	public int getSkill()
	{
		// Calculate the AI skill:
		// skill = (total_white_wins - active_white_wins) / total_white_wins * 100%
		int total_white_wins = this.getReadableDatabase().query(
				DatabaseSchema.BoardsTable.TABLE_NAME,
				SELECT_ID,
				WHERE_WHITE_WINS,
				null,
				null,
				null,
				null
		).getCount();

		int active_white_wins = this.getReadableDatabase().query(
					DatabaseSchema.BoardsTable.TABLE_NAME,
					SELECT_ID,
					WHERE_WHITE_WINS_ACTIVE,
					null,
					null,
					null,
					null
			).getCount();

		return (int) (((float) total_white_wins - active_white_wins) / total_white_wins * 100.0);
	}

	/**
	 * "Reset" the AI skill level by marking all Boards as active
	 * in the database. Then reload the game moves tree.
	 */
	public Board resetAI()
	{
		// Update all Boards to be active.
		ContentValues values = new ContentValues();
		values.put(DatabaseSchema.BoardsTable.FIELD_NAME_ACTIVE, 1);
		this.getWritableDatabase().update(
				DatabaseSchema.BoardsTable.TABLE_NAME,
				values,
				null,
				null
		);
		return this.loadBoards();
	}

	/**
	 * Defines the schema of the database storage system which stores
	 * all of the possible game Boards.
	 */
	private final class DatabaseSchema
	{
		private static final String COMMA = ",";

		/* Inner classes defining individual tables in the database. */

		/**
		 * TABLE Boards: stores all the possible generated moves Boards,
		 * their parent boards, and whether they are 'active' or not,
		 * that is, if they have been eliminated as winning moves.
		 */
		private abstract class BoardsTable implements BaseColumns
		{
			private static final String TABLE_NAME				= "Boards";

			private static final String FIELD_ID_TYPE			= " INTEGER PRIMARY KEY";

			private static final String FIELD_NAME_BLACK		= "black";
			private static final String FIELD_TYPE_BLACK		= " INTEGER"; 

			private static final String FIELD_NAME_WHITE		= "white";
			private static final String FIELD_TYPE_WHITE		= " INTEGER";

			private static final String FIELD_NAME_TURN			= "turn";
			private static final String FIELD_TYPE_TURN			= " INTEGER";

			private static final String FIELD_NAME_PARENT		= "parent";
			private static final String FIELD_TYPE_PARENT		= " INTEGER";

			private static final String FIELD_NAME_VICTORY		= "victory";
			private static final String FIELD_TYPE_VICTORY		= " INTEGER";

			private static final String FIELD_NAME_ACTIVE		= "active";
			private static final String FIELD_TYPE_ACTIVE		= " INTEGER";

			// SQL create table string
			private static final String SQL_CREATE_TABLE_BOARDS
					= "CREATE TABLE " + TABLE_NAME
					+ "( "
					+ _ID 				+ FIELD_ID_TYPE 	+ COMMA
					+ FIELD_NAME_BLACK	+ FIELD_TYPE_BLACK	+ COMMA
					+ FIELD_NAME_WHITE	+ FIELD_TYPE_WHITE	+ COMMA
					+ FIELD_NAME_TURN	+ FIELD_TYPE_TURN	+ COMMA
					+ FIELD_NAME_PARENT	+ FIELD_TYPE_PARENT	+ COMMA
					+ FIELD_NAME_VICTORY+ FIELD_TYPE_VICTORY+ COMMA
					+ FIELD_NAME_ACTIVE	+ FIELD_TYPE_ACTIVE
					+ " )";

			// SQL drop table string
			private static final String SQL_DROP_TABLE_BOARDS
					= "DROP TABLE IF EXISTS " + TABLE_NAME;
		}
	}
}
