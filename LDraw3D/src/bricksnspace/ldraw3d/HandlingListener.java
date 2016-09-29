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

import bricksnspace.j3dgeom.Point3D;

/**
 * Interface to allow part selection handling from user application 
 * 
 * @author Mario Pascucci
 *
 */
public interface HandlingListener {

	
	/**
	 * callback for "click" on GL window
	 * @param partId
	 * @param eyeNear ray vector point near to user
	 * @param eyeFar ray vector point far from user, near model 
	 * @param mode
	 */
	public void picked(int partId, Point3D eyeNear,Point3D eyeFar, PickMode mode);
	
	
	/**
	 * Callback for user moving mouse cursor on GL window
	 * @param partId part under cursor, 0 if none 
	 * @param eyeNear ray vector point near to user
	 * @param eyeFar ray vector point far from user, near model 
	 */
	public void moved(int partId, Point3D eyeNear,Point3D eyeFar);
	
	
	
	/**
	 * Callback when user release mouse button after dragging a selection window
	 */
	public void endDragSelectionWindow(); 
	
}
