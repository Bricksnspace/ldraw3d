/*
	Copyright 2014-2015 Mario Pascucci <mpascucci@gmail.com>
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

import java.awt.Color;
import java.util.List;

import bricksnspace.j3dgeom.JSimpleGeom;
import bricksnspace.j3dgeom.Matrix3D;
import bricksnspace.j3dgeom.Point3D;
import bricksnspace.ldrawlib.ConnectionPoint;
import bricksnspace.ldrawlib.LDPrimitive;
import bricksnspace.ldrawlib.LDrawColor;

/**
 * Helper function for drawing (like axis and grids)
 * 
 * @author Mario Pascucci
 *
 */
public class DrawHelpers {

	public enum PointerMode { DEFAULT,CROSS,STUD,CLIP };
	
	////// axis settings
	public static final int AXIS = -2;
	private Gadget3D axis = null;
	
	////// grid settings
	// grid axis vectors
	private Point3D p1 = new Point3D(0,0,0); 
	private Point3D p2 = new Point3D(1,0,0); 
	private Point3D p3 = new Point3D(0,0,-1);
	// grid origin
	Point3D o = new Point3D(0, 0, 0);
	// grid "gadget"
	private static final int GRID = -3;
	private Gadget3D grid = null;
	// grid step in LDU
	private float gridSize = 20;
	// grid/pointer snap in LDU
	private float snap = 2;
	// grid transformation matrix
	private Matrix3D gridMatrix = new Matrix3D();
	
	public static int SELWIN = -4; 
	private static int BBOX = -5;
	private static int ROTWHEEL = -6;
	private static int ROTHANDLE = -7;
	private Matrix3D rotWheelMatrix;
	private Matrix3D rotMatrix;
	private float rotAngle;
	private Point3D wheelp1;
	private Point3D wheelp2;
	private Point3D wheelp3;
	private Point3D wheelp4;

	////// pointer settings
	public static final int POINTER = -1;
	private Gadget3D pointer = null;
	private int pointerColor = LDrawColor.RED;
	private PointerMode pointerMode = PointerMode.DEFAULT;
	// pointer matrix
	private Matrix3D pointerMatrix = new Matrix3D();
	
	// flexible parts
	public static final int FLEXPOINT = -8;
	public static final int BEZIER = -9;
	private static int bezierResolution = 10;
	
	
	////// coupled GL display
	private LDrawGLDisplay gldisplay = null;

	
	// constructor
	public DrawHelpers(LDrawGLDisplay display) {

		if (display == null)
			throw new IllegalArgumentException("[DrawHelpers constructor] No display defined");
		gldisplay = display;
	}
	
	
	/**
	 * Defines what display is coupled with this helper
	 * @param gldisplay 
	 */
	public void setGldisplay(LDrawGLDisplay display) {
		if (display == null)
			throw new IllegalArgumentException("[DrawHelpers.setGlDisplay] No display defined");
		gldisplay = display;
	}

	
	///////////////////////////////////////
	//
	// axis handling
	
	public float getSnap() {
		return snap;
	}


	public void setSnap(float snap) {
		this.snap = snap;
	}


	public Gadget3D getAxis() {
		
		if (axis == null) {
			axis = Gadget3D.newStaticGadget(AXIS,gridMatrix);
			// x line
			axis.addElement(LDPrimitive.newLine(LDrawColor.BLACK, -200, 0, 0, 200, 0, 0));
			//axis.addElement(LDPrimitive.newLine(LDrawColor.BLACK, -200, -0.01f, -0.01f, 200, -0.01f, -0.01f));
			axis.addElement(LDPrimitive.newLine(LDrawColor.BLACK, 200, 0, 0, 180, 10, 0)); 
			axis.addElement(LDPrimitive.newLine(LDrawColor.BLACK, 200, 0, 0, 180, -10, 0));
			// y line
			axis.addElement(LDPrimitive.newLine(LDrawColor.BLUE, 0, -200, 0, 0, 200, 0));
			//axis.addElement(LDPrimitive.newLine(LDrawColor.BLUE, -0.01f, -200, -0.01f, -0.01f, 200, -0.01f));
			axis.addElement(LDPrimitive.newLine(LDrawColor.BLUE, 0, 200, 0, 10, 180, 0)); 
			axis.addElement(LDPrimitive.newLine(LDrawColor.BLUE, 0, 200, 0, -10, 180, 0));
			// z line
			axis.addElement(LDPrimitive.newLine(LDrawColor.GREEN, 0, 0, -200, 0, 0, 200));
			//axis.addElement(LDPrimitive.newLine(LDrawColor.GREEN, -0.01f, -0.01f, -200, -0.01f, -0.01f, 200));
			axis.addElement(LDPrimitive.newLine(LDrawColor.GREEN, 0, 0, 200, 0, 10, 180)); 
			axis.addElement(LDPrimitive.newLine(LDrawColor.GREEN, 0, 0, 200, 0, -10, 180));
		}
		axis.render();
		return axis;
	}
	
	
	public boolean isAxisEnabled() {
		return !axis.isHidden();
	}
	
	
	public void enableAxis(boolean enable) {
		
		if (enable) {
			axis.show();
			gldisplay.showGadget(axis);
		}
		else {
			axis.hide();
			gldisplay.hideGadget(axis);
		}
	}
	
	
	public void addAxis() {
		if (gldisplay == null)
			throw new IllegalArgumentException("[DrawHelpers.addAxis] No display defined");
		gldisplay.addGadget(getAxis());
	}

	
	
	///////////////////////////////////////
	// 
	// grid handling
	
	
	
	public void enableGrid(boolean enable) {
		
		if (enable) {
			grid.show();
			gldisplay.showGadget(grid);
		}
		else { 
			grid.hide();
			gldisplay.hideGadget(grid);
		}
	}
	
	
	public boolean isGridEnabled() {
		return !grid.isHidden();
	}

	
	
//	public Gadget3D getGrid() {
//		return grid;
//	}
//
//	
	
	public float getGridSize() {
		return gridSize;
	}


	public void setGridSize(float size) {
		gridSize = size;
	}

	
	
	public void addGrid() {
		if (gldisplay == null)
			throw new IllegalArgumentException("[DrawHelpers.addGrid] No display defined");
		if (grid == null) {
			grid = getGrid();
		}
		gldisplay.addGadget(getGrid());
	}


	
	private Gadget3D getGrid() {
		grid = Gadget3D.newStaticGadget(GRID,gridMatrix);
		int stepnum = Math.round(400/gridSize);
		for (int i=-stepnum;i<=stepnum;i++) {
			if (i != 0) {
				grid.addElement(LDPrimitive.newLine(LDrawColor.TR_BROWN, i*gridSize, 0, -400, i*gridSize, 0, 400));
				grid.addElement(LDPrimitive.newLine(LDrawColor.TR_BROWN, -400, 0, i*gridSize, 400, 0, i*gridSize));
			}
			else {
				grid.addElement(LDPrimitive.newLine(LDrawColor.GREEN, i*gridSize, 0, -400, i*gridSize, 0, 400));
				grid.addElement(LDPrimitive.newLine(LDrawColor.BLACK, -400, 0, i*gridSize, 400, 0, i*gridSize));
			}
			grid.addElement(LDPrimitive.newLine(LDrawColor.BLUE, 0, 0, 0, 0, 200, 0));
			grid.addElement(LDPrimitive.newLine(LDrawColor.BLUE, 0, 200, 0, 0, 180, 10)); 
			grid.addElement(LDPrimitive.newLine(LDrawColor.BLUE, 0, 200, 0, 0, 180, -10));
		}
		grid.render();
		return grid;
	}

	

	
	//  è il caso di allineare anche l'angolo orizzontale?
	// no, meglio studiare qualche altra cosa
	// tipo allineare il pezzo alla matrice del pezzo a cui si connette
	public void setGridMatrix(Matrix3D m) {
		p1 = new Point3D(0,0,0).transform(m);
		p2 = new Point3D(1,0,0).transform(m);
		p3 = new Point3D(0,0,-1).transform(m);
		gridMatrix = JSimpleGeom.alignMatrix(p1, p2, p3);
		addGrid();
	}
	
	
	/** 
	 * resets grid and pointer to origin
	 */
	public void resetGrid() {
		
		p1 = new Point3D(0,0,0); 
		p2 = new Point3D(1,0,0); 
		p3 = new Point3D(0,0,-1);
		gridMatrix = new Matrix3D();
		addGrid();
	}

	
	/**
	 * Move grid along Y axis 
	 */
	public void downY() {
		p1.y -= snap;
		p2.y -= snap;
		p3.y -= snap;
		gridMatrix = JSimpleGeom.alignMatrix(p1, p2, p3);
		addGrid();
	}
	
	
	public void upY() {
		p1.y += snap;
		p2.y += snap;
		p3.y += snap;
		gridMatrix = JSimpleGeom.alignMatrix(p1, p2, p3);
		addGrid();
	}
	
	
	
	/**
	 * Move grid along X axis 
	 */
	public void downX() {
		p1.x -= snap;
		p2.x -= snap;
		p3.x -= snap;
		gridMatrix = JSimpleGeom.alignMatrix(p1, p2, p3);
		addGrid();
	}
	
	
	public void upX() {
		p1.x += snap;
		p2.x += snap;
		p3.x += snap;
		gridMatrix = JSimpleGeom.alignMatrix(p1, p2, p3);
		addGrid();
	}
	
	
	
	/**
	 * Move grid along Z axis 
	 */
	public void downZ() {
		p1.z -= snap;
		p2.z -= snap;
		p3.z -= snap;
		gridMatrix = JSimpleGeom.alignMatrix(p1, p2, p3);
		addGrid();
	}
	
	
	public void upZ() {
		p1.z += snap;
		p2.z += snap;
		p3.z += snap;
		gridMatrix = JSimpleGeom.alignMatrix(p1, p2, p3);
		addGrid();
	}
	
	
	
	public void alignXY() {
		p1 = new Point3D(0,0,0).transform(gridMatrix);
		p2 = new Point3D(p1.x+1,p1.y,p1.z);
		p3 = new Point3D(p1.x,p1.y+1,p1.z);
		gridMatrix = JSimpleGeom.alignMatrix(p1, p2, p3);
		addGrid();
	}
	
	
	public void alignYZ() {
		p1 = new Point3D(0,0,0).transform(gridMatrix);
		p2 = new Point3D(p1.x,p1.y+1,p1.z);
		p3 = new Point3D(p1.x,p1.y,p1.z-1);
		gridMatrix = JSimpleGeom.alignMatrix(p1, p2, p3);
		addGrid();
	}
	
	
	
	public void alignXZ() {
		p1 = new Point3D(0,0,0).transform(gridMatrix);
		p2 = new Point3D(p1.x+1,p1.y,p1.z);
		p3 = new Point3D(p1.x,p1.y,p1.z-1);
		gridMatrix = JSimpleGeom.alignMatrix(p1, p2, p3);
		addGrid();
	}
	
	
	///////////////////////////////
	//
	// pointer handling
	

	public void movePointer(Point3D p) {
		
		gldisplay.addGadget(pointer.fastMove(p));
	}
	
	
	public void enablePointer() {
		
		pointer.show();
		gldisplay.showGadget(pointer);
	}
	
	
	public void disablePointer() {
		
		pointer.hide();
		gldisplay.hideGadget(pointer);
	}
	

	
	/**
	 * set pointer mode and color using LDraw color index
	 */
	public void setPointerMode(PointerMode m, int color) {
		
		pointerMode = m;
		pointerColor = color;
		getPointer();
	}
	
	
	/**
	 * set pointer mode and color using Java AWT Color class
	 */
	public void setPointerMode(PointerMode m, Color color) {
		
		pointerMode = m;
		pointerColor = (color.getRGB() & 0xffffff) + 0x2000000;
		getPointer();
	}
	

	
	public void setPointerMode(PointerMode m) {
		
		pointerMode = m;
		getPointer();
	}
	
	

	/**
	 * set pointer mode and color using LDraw color index
	 */
	public void setPointerColor(int color) {
		
		pointerColor = color;
		getPointer();
	}
	
	
	/**
	 * set pointer mode and color using Java AWT Color class
	 */
	public void setPointerColor(Color color) {
		
		pointerColor = (color.getRGB() & 0xffffff) + 0x2000000;
		getPointer();
	}
	


	private Gadget3D getPointer() {
		
		pointer = Gadget3D.newStaticGadget(POINTER, getCurrentMatrix());
		switch (pointerMode) {
		case CLIP:
			pointer.addElement(LDPrimitive.newLine(pointerColor, -4, 0, 0, 4, 0, 0));
			pointer.addElement(LDPrimitive.newLine(pointerColor, 0, 0, -4, 0, 0, 4));					
			pointer.addElement(LDPrimitive.newLine(pointerColor, -4, 0, -4, 4, 0, 4));
			pointer.addElement(LDPrimitive.newLine(pointerColor, -4, 0, 4, 4, 0, -4));
			break;
		case CROSS:
			pointer.addElement(LDPrimitive.newLine(pointerColor, -6, 0, -6, -6, 0, 6));
			pointer.addElement(LDPrimitive.newLine(pointerColor, -6, 0, 6, 6, 0, 6));
			pointer.addElement(LDPrimitive.newLine(pointerColor, 6, 0, 6, 6, 0, -6));
			pointer.addElement(LDPrimitive.newLine(pointerColor, 6, 0, -6, -6, 0, -6));
			pointer.addElement(LDPrimitive.newLine(pointerColor, -6, 0, -6, 6, 0, 6));
			pointer.addElement(LDPrimitive.newLine(pointerColor, -6, 0, 6, 6, 0, -6));					
			break;
		case STUD:
			pointer.addElement(LDPrimitive.newLine(pointerColor, -6, 0, -6, -6, 0, 6));
			pointer.addElement(LDPrimitive.newLine(pointerColor, -6, 0, 6, 6, 0, 6));
			pointer.addElement(LDPrimitive.newLine(pointerColor, 6, 0, 6, 6, 0, -6));
			pointer.addElement(LDPrimitive.newLine(pointerColor, 6, 0, -6, -6, 0, -6));
			pointer.addElement(LDPrimitive.newLine(pointerColor, -6, 0, 0, 6, 0, 0));
			pointer.addElement(LDPrimitive.newLine(pointerColor, 0, 0, -6, 0, 0, 6));					
			break;
		default:
			// 'X' pointer
			pointer.addElement(LDPrimitive.newLine(pointerColor, -12, 0, 12, 12, 0, -12));
			pointer.addElement(LDPrimitive.newLine(pointerColor, 12, 0, 12, -12, 0, -12));		
		}
		pointer.render();
		return pointer;
	}
	
	
	
	public void addPointer() {
		
		if (gldisplay == null)
			throw new IllegalArgumentException("[DrawHelpers.addPointer] No display defined");
		gldisplay.addGadget(getPointer());
	}
	
	
	/**
	 * reset only pointer color and shape
	 */
	public void resetPointer() {
		pointerColor = LDrawColor.RED;
		pointerMode = PointerMode.DEFAULT;
		getPointer();
	}

	
	
	public void resetPointerMatrix() {
		pointerMatrix = new Matrix3D();
		getPointer();
	}
	
	
	public void setPointerMatrix(Matrix3D m) {
		pointerMatrix = m.getOnlyRotation();
		getPointer();
	}

	
	
	public void rotPointerX(float radians) {
		pointerMatrix = pointerMatrix.rotateX(radians);
		getPointer();
	}
	

	public void rotPointerY(float radians) {
		pointerMatrix = pointerMatrix.rotateY(radians);
		getPointer();
	}
	

	//////////////////////////////77
	//
	// geometric computations
	
	
	public float[] getTargetPoint(Point3D snapFar, Point3D snapNear) {
		return JSimpleGeom.planeXline(p1, p2, p3, snapFar, snapNear);
	}
	
	
	public Matrix3D getCurrentMatrix() {
		return pointerMatrix.transform(gridMatrix.getOnlyRotation());
	}
	
	
	////////////////////////////////
	//
	// static gadgets
	
	
	
	/////////////////////
	//
	//  Connection Points
	//
	/////////////////////

	
	
	
	public static Gadget3D getRawConnectionPoint(ConnectionPoint cp) {
		
		Gadget3D connection = Gadget3D.newStaticGadget(cp.getId(), new Matrix3D());
		// p1 point (magenta)
		connection.addElement(LDPrimitive.newLine(0x2ff00ff, 
				cp.getP1().x-6, cp.getP1().y, cp.getP1().z-6, 
				cp.getP1().x-6, cp.getP1().y, cp.getP1().z+6));
		connection.addElement(LDPrimitive.newLine(0x2ff00ff, 
				cp.getP1().x-6, cp.getP1().y, cp.getP1().z+6, 
				cp.getP1().x+6, cp.getP1().y, cp.getP1().z+6));
		connection.addElement(LDPrimitive.newLine(0x2ff00ff, 
				cp.getP1().x+6, cp.getP1().y, cp.getP1().z+6, 
				cp.getP1().x+6, cp.getP1().y, cp.getP1().z-6));
		connection.addElement(LDPrimitive.newLine(0x2ff00ff, 
				cp.getP1().x+6, cp.getP1().y, cp.getP1().z-6, 
				cp.getP1().x-6, cp.getP1().y, cp.getP1().z-6));
		// p2 point (cyan)
		connection.addElement(LDPrimitive.newLine(0x200ffff, 
				cp.getP2().x-6, cp.getP2().y, cp.getP2().z+6, 
				cp.getP2().x+6, cp.getP2().y, cp.getP2().z-6));
		connection.addElement(LDPrimitive.newLine(0x200ffff, 
				cp.getP2().x-6, cp.getP2().y, cp.getP2().z-6, 
				cp.getP2().x+6, cp.getP2().y, cp.getP2().z+6));
		// p1-p2 line (green)
		connection.addElement(LDPrimitive.newLine(0x280ff80, 
				cp.getP1().x, cp.getP1().y, cp.getP1().z, 
				cp.getP2().x, cp.getP2().y, cp.getP2().z));
		connection.render();
		return connection;
	}
	
	

	
	
	public static Gadget3D getConnectionPoint(ConnectionPoint cp) {
		
		Gadget3D connection = null;
		
		float len = cp.getP1().vector(cp.getP2()).modulo();
		if (len != 0) { 
			connection = Gadget3D.newStaticGadget(cp.getId(), 
					JSimpleGeom.alignMatrix(Point3D.ORIGIN, new Point3D(0,len,0), 
						cp.getP1(), cp.getP2()).moveTo(cp.getP1()));
		}
		else {
			connection = Gadget3D.newStaticGadget(cp.getId(), new Matrix3D().moveTo(cp.getP1()));
		}
		// p1 point (magenta)
		connection.addElement(LDPrimitive.newLine(0x2ff00ff, -6, 0, -6, -6, 0, 6));
		connection.addElement(LDPrimitive.newLine(0x2ff00ff, -6, 0, 6, 6, 0, 6));
		connection.addElement(LDPrimitive.newLine(0x2ff00ff, 6, 0, 6, 6, 0, -6));
		connection.addElement(LDPrimitive.newLine(0x2ff00ff, 6, 0, -6, -6, 0, -6));
		// p2 point (cyan)
		connection.addElement(LDPrimitive.newLine(0x200ffff, -6, len, +6, 6, len, -6));
		connection.addElement(LDPrimitive.newLine(0x200ffff, -6, len, -6, 6, len, 6));
		// p1-p2 line (green)
		connection.addElement(LDPrimitive.newLine(0x280ff80, 0, 0, 0, 0, len, 0));
		connection.render();
		return connection;
	}
	
	
	
	/////////////////////
	//
	//  Rotation gadgets
	//
	/////////////////////

	
	
	
	public Gadget3D getRotationWheel(ConnectionPoint cp) {
		
		
		float len = cp.getP1().vector(cp.getP2()).modulo();
		if (len != 0) {
			rotWheelMatrix = JSimpleGeom.alignMatrix(Point3D.ORIGIN, new Point3D(0,len,0), 
					cp.getP1(), cp.getP2()).moveTo(cp.getP1());
//					JSimpleGeom.alignMatrix(Point3D.ORIGIN, new Point3D(0,len,0), 
//						cp.getP1(), cp.getP2()).moveTo(cp.getP1()));
		}
		else {
			rotWheelMatrix = new Matrix3D().moveTo(cp.getP1());
			//connection = Gadget3D.newStaticGadget(ROTWHEEL, new Matrix3D().moveTo(cp.getP1()));
		}
		wheelp1 = new Point3D(0,0,0).transform(rotWheelMatrix);
		wheelp2 = new Point3D(1,0,0).transform(rotWheelMatrix);
		wheelp3 = new Point3D(0,0,-1).transform(rotWheelMatrix);
		wheelp4 = new Point3D(0,1,0).transform(rotWheelMatrix);
		Gadget3D connection = Gadget3D.newStaticGadget(ROTWHEEL, rotWheelMatrix); 
		connection.addElement(LDPrimitive.newLine(0x2e08080, -30, 0, 0, 30, 0, 0));
		connection.addElement(LDPrimitive.newLine(0x2e08080, 0, 0, -30, 0, 0, 30));
		connection.addElement(LDPrimitive.newLine(0x2e08080, -30, 0, 5, -30, 0, -5));
		connection.addElement(LDPrimitive.newLine(0x2e08080, 30, 0, 5, 30, 0, -5));
		connection.addElement(LDPrimitive.newLine(0x2e08080, -5, 0, -30, 5, 0, -30));
		connection.addElement(LDPrimitive.newLine(0x2e08080, 5, 0, 30, -5, 0, 30));
		connection.render();
		return connection;
	}
	
	
	
	public Matrix3D getRotMatrix(ConnectionPoint cp) {
		
		Matrix3D m = new Matrix3D(-cp.getP1().x, -cp.getP1().y, -cp.getP1().z)
			.transform(rotMatrix).moveTo(cp.getP1());
		return m;
	}
	
	
	
	public Gadget3D getRotationHandle(Point3D cursor, Point3D eye) {
		
		float[] dirpoint = JSimpleGeom.planeXline(wheelp1, wheelp2, wheelp3, cursor, eye);
		Gadget3D handle = Gadget3D.newStaticGadget(ROTHANDLE, new Matrix3D()); 
		float hx,hy,hz;
		Point3D d;
		if (dirpoint[0] == -1) {
			d = new Point3D(wheelp2);
			// no point, angle of view is parallel to rotation plane
			hx = wheelp2.x * 40;
			hy = wheelp2.y * 40;
			hz = wheelp2.z * 40;
		}
		else {
			d = wheelp1.vector(new Point3D(dirpoint[1], dirpoint[2], dirpoint[3])).normalize();
			hx = wheelp1.x + d.x * 40;
			hy = wheelp1.y + d.y * 40;
			hz = wheelp1.z + d.z * 40;
		}
		Point3D dp = new Point3D(hx, hy, hz);
		rotAngle = (float) Math.acos(JSimpleGeom.dotProdAngle(wheelp1, wheelp2, wheelp1, dp));
		Point3D cross = JSimpleGeom.crossProd(wheelp1, wheelp2, wheelp1, dp).translate(wheelp1);
		if (JSimpleGeom.dotPoints(wheelp1, wheelp4, wheelp1, cross) < 0) {
			rotAngle = -rotAngle;
		}
		rotMatrix = JSimpleGeom.alignMatrix(wheelp1,wheelp2,wheelp1, dp);
		handle.addElement(LDPrimitive.newLine(0x2ff8080, wheelp1.x, wheelp1.y, wheelp1.z, hx, hy, hz));
		handle.addElement(LDPrimitive.newLine(0x2ff8080, hx-5, hy, hz, hx+5, hy, hz));
		handle.addElement(LDPrimitive.newLine(0x2ff8080, hx, hy-5, hz, hx, hy+5, hz));
		handle.addElement(LDPrimitive.newLine(0x2ff8080, hx, hy, hz-5, hx, hy, hz+5));
		handle.render();
		return handle;
	}
	
	
	public float getRotAngle() {
		return rotAngle;
	}


	public void removeRotGadgets() {
		
		gldisplay.removeGadget(ROTWHEEL);
		gldisplay.removeGadget(ROTHANDLE);
	}
	
	
	

	
	/////////////////////
	//
	//  Flex parts gadgets
	//
	/////////////////////
	

	
	/**
	 * creates a gadget to highlight flex "lock" point with static ID
	 * @param cp connection point where to locate gadget
	 * @return a gadget
	 */
	public static Gadget3D getFlexPoint(ConnectionPoint cp) {
		

		float stiff = 40;
		Gadget3D flexpoint = Gadget3D.newStaticGadget(FLEXPOINT,
				JSimpleGeom.alignMatrix(Point3D.ORIGIN, new Point3D(0,stiff,0), 
					cp.getP1(), cp.getP2()).moveTo(cp.getP1()));
		// p1 point (magenta)
		flexpoint.addElement(LDPrimitive.newLine(0x200ff00, -6, 0, -6, -6, 0, 6));
		flexpoint.addElement(LDPrimitive.newLine(0x200ff00, -6, 0, 6, 6, 0, 6));
		flexpoint.addElement(LDPrimitive.newLine(0x200ff00, 6, 0, 6, 6, 0, -6));
		flexpoint.addElement(LDPrimitive.newLine(0x200ff00, 6, 0, -6, -6, 0, -6));
		// p2 point (cyan)
		flexpoint.addElement(LDPrimitive.newLine(0x200ff00, -6, stiff, +6, 6, stiff, -6));
		flexpoint.addElement(LDPrimitive.newLine(0x200ff00, -6, stiff, -6, 6, stiff, 6));
		// p1-p2 line (green)
		flexpoint.addElement(LDPrimitive.newLine(0x200ff00, 0, 0, 0, 0, stiff, 0));
		flexpoint.render();
		return flexpoint;

	}
	
	

	/**
	 * creates a gadget to highlight flex constraint point with static ID
	 * @param cp connection point where to locate gadget
	 * @return a gadget
	 */
	public static Gadget3D getConstraintPoint(ConnectionPoint cp) {
		

		float stiff = 40;
		Gadget3D flexpoint = Gadget3D.newStaticGadget(FLEXPOINT,
				JSimpleGeom.alignMatrix(Point3D.ORIGIN, new Point3D(0,stiff*2,0), 
					cp.getP1(), cp.getP2()).moveTo(cp.getP1()));
		// p1 point 
		flexpoint.addElement(LDPrimitive.newLine(LDrawColor.BRGREEN, -6, stiff, -6, -6, stiff, 6));
		flexpoint.addElement(LDPrimitive.newLine(LDrawColor.BRGREEN, -6, stiff, 6, 6, stiff, 6));
		flexpoint.addElement(LDPrimitive.newLine(LDrawColor.BRGREEN, 6, stiff, 6, 6, stiff, -6));
		flexpoint.addElement(LDPrimitive.newLine(LDrawColor.BRGREEN, 6, stiff, -6, -6, stiff, -6));
		// p2 point
		flexpoint.addElement(LDPrimitive.newLine(LDrawColor.BRGREEN, -6, -stiff+10, -6, 0, -stiff, 0));
		flexpoint.addElement(LDPrimitive.newLine(LDrawColor.BRGREEN, -6, -stiff+10, 6, 0, -stiff, 0));
		flexpoint.addElement(LDPrimitive.newLine(LDrawColor.BRGREEN, 6, -stiff+10, 6, 0, -stiff, 0));
		flexpoint.addElement(LDPrimitive.newLine(LDrawColor.BRGREEN, 6, -stiff+10, -6, 0, -stiff, 0));
		// p1-p2 line 
		flexpoint.addElement(LDPrimitive.newLine(LDrawColor.BRGREEN, 0, -stiff, 0, 0, stiff, 0));
		flexpoint.render();
		return flexpoint;

	}
	
	
	

//	/**
//	 * creates a gadget to display a starting or end point for a bezier curve
//	 * stiff is used to draw length of marker, short is flexible, long is rigid
//	 * @param cp connection point to place marker
//	 * @param stiff "stiffness" to indicate approx position for control point
//	 * @return
//	 */
//	public static Gadget3D getFlexEndPoint(ConnectionPoint cp, float stiff) {
//		
//		Gadget3D flexpoint = Gadget3D.newGadget(
//				JSimpleGeom.alignMatrix(Point3D.ORIGIN, new Point3D(0,stiff,0), 
//					cp.getP1(), cp.getP2()).moveTo(cp.getP1()));
//		// p1 point (magenta)
//		flexpoint.addElement(LDPrimitive.newLine(0x200ff00, -6, 0, -6, -6, 0, 6));
//		flexpoint.addElement(LDPrimitive.newLine(0x200ff00, -6, 0, 6, 6, 0, 6));
//		flexpoint.addElement(LDPrimitive.newLine(0x200ff00, 6, 0, 6, 6, 0, -6));
//		flexpoint.addElement(LDPrimitive.newLine(0x200ff00, 6, 0, -6, -6, 0, -6));
//		// p2 point (cyan)
//		flexpoint.addElement(LDPrimitive.newLine(0x200ff00, -6, stiff, +6, 6, stiff, -6));
//		flexpoint.addElement(LDPrimitive.newLine(0x200ff00, -6, stiff, -6, 6, stiff, 6));
//		// p1-p2 line (green)
//		flexpoint.addElement(LDPrimitive.newLine(0x200ff00, 0, 0, 0, 0, stiff, 0));
//		flexpoint.render();
//		return flexpoint;
//
//	}
	
	
	
	public static int getBezierResolution() {
		return bezierResolution;
	}


	public static void setBezierResolution(int bezierResolution) {
		DrawHelpers.bezierResolution = bezierResolution;
	}


	public static float[] getBezier(ConnectionPoint p1, ConnectionPoint p2, 
			List<ConnectionPoint> constraints, float rigid, int resolution) {
		
		ConnectionPoint s = p1;
		ConnectionPoint e;
		int numPoint = (constraints.size()+1)*resolution+1;
		float[] pp = new float[numPoint*3];
		int offset = 0;
		if (constraints.size() > 0) {
			for (int c=0;c<constraints.size(); c++) {
				e = constraints.get(c);
				// checks if constraints are too near
				float dist = s.getP1().vector(e.getP1()).modulo();
				float localRigid;
				if (dist < rigid * 2.5f) {
					localRigid = dist / 2.5f;
				}
				else {
					localRigid = rigid;
				}
				Point3D bp1 = s.getP1().vector(s.getP2()).normalize().scale(localRigid).translate(s.getP1());
				Point3D bp2 = e.getP1().vector(e.getP2()).normalize().scale(localRigid).translate(e.getP1());
				JSimpleGeom.generateBezier(s.getP1(), bp1, bp2, e.getP1(), resolution, pp, offset);
				s = ConnectionPoint.getDummy(new Point3D(0, 0, 0),e.getP2().vector(e.getP1())).fastMove(e.getP1());
				offset += resolution;
			}
		}
		e = p2;
		// checks if constraints are too near
		float dist = s.getP1().vector(e.getP1()).modulo();
		float localRigid;
		if (dist < rigid * 2.5f) {
			localRigid = dist / 2.5f;
		}
		else {
			localRigid = rigid;
		}
		Point3D bp1 = s.getP1().vector(s.getP2()).normalize().scale(localRigid).translate(s.getP1());
		Point3D bp2 = e.getP1().vector(e.getP2()).normalize().scale(localRigid).translate(e.getP1());
		JSimpleGeom.generateBezier(s.getP1(), bp1, bp2, e.getP1(), resolution, pp, offset);
		return pp;
	}
	
	

	
	public static Gadget3D getBezierGadget(ConnectionPoint p1, ConnectionPoint p2, 
			List<ConnectionPoint> constraints, float rigid, float maxLen) {
		
		Gadget3D bezier = Gadget3D.newStaticGadget(BEZIER,new Matrix3D());
		
		float len = 0;
		float[] pp = getBezier(p1, p2, constraints, rigid, bezierResolution);
		int color;
		if (maxLen > 0) {
			for (int i=0;i+3<pp.length; i+=3) {
				float x = pp[i+3] - pp[i];
				float y = pp[i+4] - pp[i+1];
				float z = pp[i+5] - pp[i+2];
				len += Math.sqrt(x*x+y*y+z*z);
			}
			color = len > maxLen?LDrawColor.RED:LDrawColor.GREEN;
		}
		else {
			color = LDrawColor.GREEN;
		}
		for (int i=0;i+3<pp.length; i+=3) {
			bezier.addElement(LDPrimitive.newLine(color, pp[i],pp[i+1],pp[i+2],pp[i+3],pp[i+4],pp[i+5]));
		}
		bezier.render();
		return bezier;
	}
	
	

	
	
//	public static Gadget3D getBezier(ConnectionPoint p1, ConnectionPoint p2, float rigid) {
//		
//		Gadget3D bezier = Gadget3D.newGadget(new Matrix3D());
////		Matrix3D m = JSimpleGeom.alignMatrix(Point3D.ORIGIN, new Point3D(0,stiff,0),
////				p1.getP1(),p1.getP2()).moveTo(p1.getP1());
////		Point3D bp2 = p1.getP2().transform(m);
////		m = JSimpleGeom.alignMatrix(Point3D.ORIGIN, new Point3D(0,stiff,0),
////				p2.getP1(),p2.getP2()).moveTo(p2.getP1());
////		Point3D bp3 = p2.getP2().transform(m);
//		Point3D bp1 = p1.getP1().vector(p1.getP2()).normalize().scale(rigid).translate(p1.getP1());
//		Point3D bp2 = p2.getP1().vector(p2.getP2()).normalize().scale(rigid).translate(p2.getP1());
//		//System.out.println("p1="+bp1+" p2="+bp2);
//		float step = 1f/50f;
//		float[] pp = new float[] { p1.getP1().x,p1.getP1().y,p1.getP1().z};
//		for (int i=1;i<=50; i++) {
//			float[] p = JSimpleGeom.pointOnCubicBezier(p1.getP1(), bp1, bp2, p2.getP1(), i*step);
//			//System.out.println(p[0]+" "+p[1]+" "+p[2]);
//			bezier.addElement(LDPrimitive.newLine(LDrawColor.RED, pp[0],pp[1],pp[2],p[0],p[1],p[2]));
//			pp = p;
//		}
//		bezier.render();
//		return bezier;
//	}
//	
//	
	public void removeBezierGadgets() {
		
		gldisplay.removeGadget(BEZIER);
	}
	
	
	
	/////////////////////
	//
	//  Bounding boxes and selection window
	//
	/////////////////////

	
	public static Gadget3D getBoundingBox(Point3D lb, Point3D rt, int level) {
		
		Gadget3D bbox = null;
		
		bbox = Gadget3D.newStaticGadget(BBOX, new Matrix3D());
		// p1 point (magenta)
		int color;
		switch (level) {
		case 0: 
			color = 0x20000ff;
			break;
		case 1:
			color = 0x2ff0000;
			break;
		case 2:
			color = 0x200ff00;
			break;
		case 3:
			color = 0x2c0ff00;
			break;
		default:
			color = 0x2e0e000;
		}
		bbox.addElement(LDPrimitive.newLine(color,lb.x,lb.y,lb.z, rt.x,lb.y,lb.z)); 
		bbox.addElement(LDPrimitive.newLine(color,rt.x,lb.y,lb.z, rt.x,rt.y,lb.z)); 
		bbox.addElement(LDPrimitive.newLine(color,rt.x,rt.y,lb.z, lb.x,rt.y,lb.z));
		bbox.addElement(LDPrimitive.newLine(color,lb.x,rt.y,lb.z, lb.x,lb.y,lb.z)); 

		bbox.addElement(LDPrimitive.newLine(color,lb.x,lb.y,rt.z, rt.x,lb.y,rt.z)); 
		bbox.addElement(LDPrimitive.newLine(color,rt.x,lb.y,rt.z, rt.x,rt.y,rt.z)); 
		bbox.addElement(LDPrimitive.newLine(color,rt.x,rt.y,rt.z, lb.x,rt.y,rt.z));
		bbox.addElement(LDPrimitive.newLine(color,lb.x,rt.y,rt.z, lb.x,lb.y,rt.z)); 

		bbox.addElement(LDPrimitive.newLine(color,lb.x,lb.y,lb.z, lb.x,lb.y,rt.z)); 
		bbox.addElement(LDPrimitive.newLine(color,rt.x,lb.y,lb.z, rt.x,lb.y,rt.z)); 
		bbox.addElement(LDPrimitive.newLine(color,rt.x,rt.y,lb.z, rt.x,rt.y,rt.z));
		bbox.addElement(LDPrimitive.newLine(color,lb.x,rt.y,lb.z, lb.x,rt.y,rt.z)); 

		bbox.render();
		return bbox;
	}
	

	/**
	 * selection window
	 * @param tl
	 * @param tr
	 * @param lr
	 * @param ll
	 * @return
	 */
	public static Gadget3D selectionWindow(float[] tl, float[] tr, float[] lr, float[] ll) {
	
		Gadget3D selWindow = null;
	
		selWindow = Gadget3D.newStaticGadget(SELWIN, new Matrix3D());
		// four lines, cyan
		selWindow.addElement(LDPrimitive.newLine(0x200ffff, 
				tl[0],tl[1],tl[2], 
				tr[0],tr[1],tr[2]));
		selWindow.addElement(LDPrimitive.newLine(0x200ffff, 
				tr[0],tr[1],tr[2], 
				lr[0],lr[1],lr[2]));
		selWindow.addElement(LDPrimitive.newLine(0x200ffff, 
				lr[0],lr[1],lr[2], 
				ll[0],ll[1],ll[2]));
		selWindow.addElement(LDPrimitive.newLine(0x200ffff, 
				ll[0],ll[1],ll[2], 
				tl[0],tl[1],tl[2]));
		selWindow.render();
		return selWindow;
	}

	


}
