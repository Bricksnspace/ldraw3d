/*
	Copyright 2014 Mario Pascucci <mpascucci@gmail.com>
	This file is part of LDraw3D

	LDraw3D is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	LDraw3D is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with LDraw3D.  If not, see <http://www.gnu.org/licenses/>.

*/


package bricksnspace.ldraw3d;

/**
 * Set of methods that long task execution can call to show its progress
 * 
 * @author Mario Pascucci
 *
 */
public interface ProgressUpdater {


	/**
	 * Update for work done
	 * 
	 *	Typ. a progress bar that increase during work
	 * 
	 * @param done count of performed actions
	 * @param total whole actions to do
	 */
	public void updateDone(int done, int total);
	
	
	/**
	 * Update for remaining work
	 * 
	 * Typ. a decreasing progress bar
	 * @param todo count of remaining actions 
	 * @param total whole actions to do
	 */
	public void updateRemaining(int todo, int total);
	
	
	/**
	 * Update a spinning bar or a counter, or an animation
	 * 
	 * Typ. when how many actions required to complete task are unknown
	 */
	public void updateDoing();
	
	
	/**
	 * called at task start
	 */
	public void updateStart();
	
	
	/**
	 * Called at task end
	 */
	public void updateComplete();
	
	
	/**
	 * Called if interrupted with error or exception
	 */
	public void updateIncomplete();
	
}
