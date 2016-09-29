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

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import bricksnspace.j3dgeom.Matrix3D;
import bricksnspace.j3dgeom.Point3D;
import bricksnspace.ldrawlib.LDPrimitive;
import bricksnspace.ldrawlib.LDrawColor;
import bricksnspace.ldrawlib.LDrawCommand;



/**
 * @author Mario Pascucci
 *
 */


/* 
 * a gadget, used by display to identify or mark connection points, display grid, axis, cursor  
 * with only lines.
 * VBOs:
 *  - lines vertex (no normals, floats)
 *  - color per vertex (bytes)
 *  
 */
public class Gadget3D {
	
	private float[] wireVBO = null;		// Vertex for  lines
	private byte[] wireColorVA = null;
	
	private List<LDPrimitive> elements;
	private Matrix3D matrix = new Matrix3D();
	
	private int id;
	private int lineName;
	private int lineColorName;
	private int lineVertexCount = 0;
	private boolean selected = false;
	private boolean hidden = false;

	private int lvertexIndex;
	private int lcolorIndex;
	
	private static int globalId = 0;

	
	private Gadget3D() {

		elements = new ArrayList<LDPrimitive>();
	}
	
	
	
	private Gadget3D(Gadget3D p) {
		
		elements = new ArrayList<LDPrimitive>();
		id = p.id;
		lineVertexCount = p.lineVertexCount;
		selected = p.isSelected();
		hidden = p.isHidden();
		wireVBO = p.wireVBO.clone();
		wireColorVA = p.wireColorVA.clone();
		matrix = p.matrix.getCopy();
	}
	
	
	public Gadget3D getCopy() {
		
		Gadget3D g = new Gadget3D();
		for (LDPrimitive lp:elements) {
			g.elements.add(lp.getClone());
		}
		g.id = id;
		g.lineVertexCount = lineVertexCount;
		g.selected = isSelected();
		g.hidden = isHidden();
		g.matrix = matrix.getCopy();		
		g.wireVBO = wireVBO.clone();
		g.wireColorVA = wireColorVA.clone();
		return g;
	}
	

    private static synchronized int getUniqueId() {
		
		return ++globalId;
    }

	
	public int getId() {
		return id;
	}
	
	
	@Override
	public String toString() {
		return "Gadget3D [id=" + id + ", "
				+ "wireVBO[" + wireVBO.length + "], "
				+ "wireColorVA[" + wireColorVA.length + "] ]";
	}


	
	public void addElement(LDPrimitive p) {
		
		// silently ignore all but lines 
		if (p.getType() != LDrawCommand.LINE)
			return;
		elements.add(p);
	}
	
	
	
	public Gadget3D fastMove(Point3D pos) {
		
		Gadget3D mp = new Gadget3D(this);
		if (lineVertexCount > 0) {
			mp.wireVBO = new float[wireVBO.length];
			for (int i=0;i<wireVBO.length;i+=3) {
				mp.wireVBO[i] = wireVBO[i] + pos.x;
				mp.wireVBO[i+1] = wireVBO[i+1] + pos.y;
				mp.wireVBO[i+2] = wireVBO[i+2] + pos.z;
			}
			mp.wireColorVA = wireColorVA.clone();
		}
		return mp;
	}
	
	
	
	private void addLineVertex(float[] point, Color c) {
		
		wireVBO[lvertexIndex++] = point[0];
		wireVBO[lvertexIndex++] = point[1];
		wireVBO[lvertexIndex++] = point[2];
		wireColorVA[lcolorIndex++] = (byte)c.getRed();
		wireColorVA[lcolorIndex++] = (byte)c.getGreen();
		wireColorVA[lcolorIndex++] = (byte)c.getBlue();
		wireColorVA[lcolorIndex++] = (byte)c.getAlpha();

	}


	
	public void render() {
		
		
		try {
			wireVBO = new float[elements.size()*2*3];
			wireColorVA = new byte[elements.size()*2*4];
		}
		catch (OutOfMemoryError ex) {
			wireVBO = null;
			wireColorVA = null;
			throw new OutOfMemoryError("Your model is too big to render."); 
		}
		lineVertexCount = elements.size() * 2;
		lvertexIndex = 0;
		lcolorIndex = 0;

		Color pc;

		for (LDPrimitive p : elements) {
			pc = LDrawColor.getById(p.getColorIndex()).getColor();
			for (int i=0;i<2;i++) {
				addLineVertex(
						matrix.transformPoint(p.getPointsFV()[i*3], p.getPointsFV()[i*3+1],p.getPointsFV()[i*3+2]),
						pc);
			}
		}
	}
	
	
	
	public static Gadget3D newStaticGadget(int id, Matrix3D m) {
		
		Gadget3D part = new Gadget3D();
		part.id = id;
		part.matrix = m;
		return part;
	}
	
	
	
	public static Gadget3D newGadget(Matrix3D m) {
		
		Gadget3D part = new Gadget3D();
		part.id = getUniqueId();
		part.matrix = m;
		return part;
	}
	
	
	
	public void select() {
		
		selected = true;
	}
	
	
	
	public void unSelect() {
		
		selected = false;
	}
	

	public boolean isSelected() {
		return selected;
	}
	
	
	
	public void hide() {
		
		hidden = true;
	}
	
	
	public void show() {
		
		hidden = false;
	}

	
	public boolean isHidden() {
		
		return hidden;
	}

	
	
	public float[] getWireFrameVBO() {
		return wireVBO;
	}
	
	
	public byte[] getWireColorVa() {
		return wireColorVA;
	}


	public int getLineVertexCount() {
		return lineVertexCount;
	}


	public int getLineName() {
		return lineName;
	}


	public void setLineName(int lineName) {
		this.lineName = lineName;
	}


	public int getLineColorName() {
		return lineColorName;
	}


	public void setLineColorName(int lineColorName) {
		this.lineColorName = lineColorName;
	}
	
	
}
