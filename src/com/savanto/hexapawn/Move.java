package com.savanto.hexapawn;

/**
 * @author savanto
 *
 */
public class Move
{
	public int sourceRow, sourceCol, destRow, destCol;

	public Move(int sourceRow, int sourceCol, int destRow, int destCol)
	{
		this.sourceRow = sourceRow;
		this.sourceCol = sourceCol;
		this.destRow = destRow;
		this.destCol = destCol;
	}
}
