package com.savanto.hexapawn;

import android.content.Context;
import android.view.View;
import android.widget.RelativeLayout;

/**
 * @author savanto
 *
 */
public class PawnView extends View
{
	/**
	 * The color of the pawn represented by this PawnView.
	 */
	private Board.Color color;

	/**
	 * Constant size of pawn. getWidth and getHeight cannot be
	 * relied upon because dragging the pawn on the screen may
	 * distort its sizes.
	 */
	private int size;

	// Constructors

	public PawnView(Context context)
	{
		super(context);
	}

	/**
	 * @param color - the Color the pawn will be.
	 * @param context - the Context passed from the activity.
	 * @param drawableRes - the resource ID of the drawable to set this pawn's background to
	 * @param params - the layout parameters defining this pawn's size and position
	 */
	public PawnView(Board.Color color, Context context, int drawableRes, int size, int row, int col, OnTouchListener listener)
	{
		super(context);
		// Set color
		this.color = color;
		// Set background drawable
		this.setBackgroundResource(drawableRes);
		// Set size and position
		this.size = size;
		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(this.size, this.size);
		params.leftMargin = col * this.size;
		params.topMargin = row * this.size;
		this.setLayoutParams(params);
		// Set listener
		this.setOnTouchListener(listener);
	}

	// Accessors

	/**
	 * Gets this pawn's Color.
	 * @return this pawn's Color.
	 */
	public Board.Color getColor()
	{
		return this.color;
	}

	/**
	 * Gets this PawnView size.
	 * @return size of this PawnView, in pixels
	 */
	public int getSize()
	{
		return this.size;
	}

	/**
	 * Easy retrieval of PawnView center location, in pixels.
	 * @return x component of PawnView location on the screen.
	 */
	public int getCenterX()
	{
		return this.getLeft() + this.size / 2;
	}

	/**
	 * Easy retrieval of PawnView center location, in pixels.
	 * @return y component of PawnView location on the screen.
	 */
	public int getCenterY()
	{
		return this.getTop() + this.size / 2;
	}

	// Modifiers

	/**
	 * Make this pawn 'captured', or GONE
	 */
	public void capture()
	{
		this.setVisibility(View.GONE);
	}

	/**
	 * Set the position based on screen pixel values.
	 * @param x
	 * @param y
	 */
	public void setRawPosition(int x, int y)
	{
		RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) this.getLayoutParams();
		params.leftMargin = x;
		params.topMargin = y;
		this.setLayoutParams(params);		
	}

	/**
	 * Set the position based on row and column of a grid.
	 * @param row
	 * @param col
	 */
	public void setPosition(int row, int col)
	{
		this.setRawPosition(col * this.size, row * this.size);
	}

	/**
	 * Set the position based on screen pixel values.
	 * @param x
	 * @param y
	 */
	public void setRawPosition(float x, float y)
	{
		this.setRawPosition((int) x - this.size / 2, (int) y - this.size);
	}
}
