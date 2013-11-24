package com.savanto.hexapawn;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * @author savanto
 * Taken from
 * http://stackoverflow.com/questions/2948212/android-layout-with-sqare-buttons
 * http://blog.tomgibara.com/post/1696552527/implement-your-own-android-layouts
 * 
 * This layout arranges all child views in a square grid.
 * The whole layout always remains square (equal height and width).
 * The child views remain the same size and are square.
 * They are positioned in a dynamically-expanding grid.
 *
 */
public class SquareLayout extends ViewGroup
{

	/**
	 * The layout size
	 */
	private int layoutSize = 0;

	/**
	 * The size of each child. All children are equal in size,
	 * and are squares.
	 */
	private int childSize = 0;

	/**
	 * The dimension of each side of the grid.
	 */
	private int gridDimension = 0;

	// Constructors

	/**
	 * Constructor to create default layout 
	 * @param context
	 */
	public SquareLayout(Context context)
	{
		super(context);
	}

	/**
	 * Constructor to inflate layout from xml.
	 * @param context
	 * @param attrs
	 */
	public SquareLayout(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	/**
	 * Measures the layout and sets the measured dimensions
	 */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		// TODO: support for wrap_content or other size constrains.
		// Currently, only able to take up the whole width/height of the screen,
		// depending on which is smaller.
		this.layoutSize = Math.min(MeasureSpec.getSize(widthMeasureSpec), 
				MeasureSpec.getSize(heightMeasureSpec));

		// Count the number of children and determine how to arrange them
		// in a square grid. Arrangement is as follows:
		// Let the number of children be n. Let s be the smallest perfect
		// square where s >= n. Grid size is therfore sqrt(s) x sqrt(s),
		// with sqrt(s) children taking up the full size of the smaller
		// of the width or height, and remaining children are added below.
		// Ie.
		//  
		// o o		n = 3
		// o .
		// 
		// o o		n = 4
		// o o
		// 
		// o o o	n = 5
		// o o .
		// . . .
		//
		// o o o	n = 9
		// o o o
		// o o o
		//
		// o o o o	n = 10
		// o o o o
		// o o . .
		// . . . .
		//

		final int childCount = this.getChildCount();
		// Find the next perfect square that is >= the number of children
		for (int i = childCount; i <= childCount * 2; i++)
		{
			int root = (int) Math.round(Math.sqrt(i));
			if (root * root == i)
			{
				this.gridDimension = root;
				break;
			}
		}

		// Ask each child to measure themselves, based on the calculated
		// child size
		this.childSize = this.layoutSize / this.gridDimension;
		this.measureChildren(
				MeasureSpec.makeMeasureSpec(this.childSize, MeasureSpec.EXACTLY), 
				MeasureSpec.makeMeasureSpec(this.childSize, MeasureSpec.EXACTLY)
		);


		// Set the measured dimensions for this layout,
		// as required by measure.
		this.setMeasuredDimension(this.layoutSize, this.layoutSize);
	}


	/**
	 * Lays out each child onto the grid.
	 */
	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b)
	{
		// TODO: get the LayoutParams of the children in order to honor their
		// margins and padding settings.

		// Arrange children according to the grid
		int row = 0, col = 0;
		for (int i = 0; i < this.getChildCount(); i++)
		{
			View child = this.getChildAt(i);
			child.layout(
					l + col * this.childSize, 
					t + row * this.childSize, 
					col * this.childSize + this.childSize, 
					row * this.childSize + this.childSize
			);
			col++;
			// Check for edge
			if (col >= this.gridDimension)
			{
				col = 0;
				row++;
			}

		}
	}

	/**
	 * Gets the dimension of the child views.
	 * @return the size of the child views, in pixels (all children are the same size).
	 */
	public int getChildSize()
	{
		return this.layoutSize / this.gridDimension;
	}

	/**
	 * Gets the dimension of the whole layout.
	 * @return the size of the layout object, in pixels
	 */
	public int getLayoutSize()
	{
		return this.layoutSize;
	}
}
