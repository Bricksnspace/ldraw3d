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
 * Part picking mode
 * 
 * NONE: selected part replace current selection. If no part selected, clear current selection
 * ADD: add selected part to current selection
 * TOGGLE: if part is in current selection remove it, else add
 * 
 * @author Mario Pascucci
 *
 */
public enum PickMode {
	NONE, ADD, TOGGLE, CENTER_TO
}
