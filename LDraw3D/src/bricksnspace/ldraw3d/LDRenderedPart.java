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
import java.io.IOException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import bricksnspace.j3dgeom.Matrix3D;
import bricksnspace.j3dgeom.Point3D;
import bricksnspace.ldrawlib.LDPrimitive;
import bricksnspace.ldrawlib.LDrawColor;
import bricksnspace.ldrawlib.LDrawCommand;
import bricksnspace.ldrawlib.LDrawPart;


/**
 * @author Mario Pascucci
 *
 */


/* 
 * a single LDraw part, identified from its part code, colored and placed in space 
 * with VBOs already full of triangles and lines.
 * VBOs:
 *  - triangles vertex with normals (floats)
 *  - color per vertex (bytes)
 *  - lines vertex (no normals, floats)
 *  - color per vertex (bytes)
 *  - aux lines vertex (no normals, floats)
 *  - color per vertex (bytes)
 *  Special VBO for bounding box
 *  - bb lines vertex (floats, no color, no normals)
 *  
 */
public class LDRenderedPart {
	
	private LDPrimitive pp;
	private float[] polyVBO = null; 	// Vertex Buffer Object for triangles
	private float[] wireVBO = null;		// Vertex for  lines
	private float[] auxWireVBO = null;	// aux lines
	private float[] bbox = null;		// bounding box
	private byte[] polyColorVA = null;
	private byte[] wireColorVA = null;
	private byte[] auxWireColorVA = null;
	
	
	private int triangleName;			// VBO names for OpenGL
	private int triangleColorName;
	private int lineName;
	private int lineColorName;
	private int auxLineName;
	private int auxLineColorName;
	private int bboxName;
	private int triangleVertexCount = 0;
	private int lineVertexCount = 0;
	private int auxLineVertexCount = 0;
	private int bboxCount = 0; 
	private boolean selected = false;
	private boolean connected = false;
	private boolean hidden = false;
	private boolean highLighted = false;
	private boolean dimmed = false;

	private int tvertexIndex;
	private int tColorIndex;
	private int lvertexIndex;
	private int lcolorIndex;
	private int avertexIndex;
	private int acolorIndex;
	private float xmax,xmin,ymax,ymin,zmax,zmin;
	private static boolean useBounding = false;
	private static boolean auxLines = true;
	
	
	private LDRenderedPart(LDPrimitive p) {
		
		pp = p;
		generatePartVBOs();
	}
	
	
	private LDRenderedPart(LDRenderedPart p) {
		
		pp = p.getPlacedPart().getClone();
		triangleVertexCount = p.getTriangleVertexCount();
		selected = p.isSelected();
		hidden = p.isHidden();
		triangleVertexCount = p.getTriangleVertexCount();
		lineVertexCount = p.getLineVertexCount();
		auxLineVertexCount = p.getAuxLineVertexCount();
		bboxCount = p.getBboxCount();
	}
	
	
	public int getId() {
		return pp.getId();
	}

	
	
	
	
	@Override
	public String toString() {
		return "LDRenderedPart [pp=" + pp + ", "
				+ "polyVBO[" + polyVBO.length + "], "
				+ "wireVBO[" + wireVBO.length + "], "
				+ "auxWireVBO[" + auxWireVBO.length + "], "
				+ "polyColorVA[" + polyColorVA.length + "], "
				+ "wireColorVA[" + wireColorVA.length + "], "
				+ "auxWireColorVA[" + auxWireColorVA.length + "] ]";
	}


	
	
	public LDRenderedPart fastMove(Point3D pos) {
		
		LDRenderedPart mp = new LDRenderedPart(this);
		if (triangleVertexCount > 0) {
			mp.polyVBO = new float[polyVBO.length];
			for (int i=0;i<polyVBO.length;i+=6) {
				mp.polyVBO[i] = polyVBO[i] + pos.x;
				mp.polyVBO[i+1] = polyVBO[i+1] + pos.y;
				mp.polyVBO[i+2] = polyVBO[i+2] + pos.z;
				mp.polyVBO[i+3] = polyVBO[i+3];
				mp.polyVBO[i+4] = polyVBO[i+4];
				mp.polyVBO[i+5] = polyVBO[i+5];
			}
			mp.polyColorVA = polyColorVA.clone();
		}
		if (lineVertexCount > 0) {
			mp.wireVBO = new float[wireVBO.length];
			for (int i=0;i<wireVBO.length;i+=3) {
				mp.wireVBO[i] = wireVBO[i] + pos.x;
				mp.wireVBO[i+1] = wireVBO[i+1] + pos.y;
				mp.wireVBO[i+2] = wireVBO[i+2] + pos.z;
			}
			mp.wireColorVA = wireColorVA.clone();
		}
		if (auxLineVertexCount > 0) {
			mp.auxWireVBO = new float[auxWireVBO.length];
			for (int i=0;i<auxWireVBO.length;i+=3) {
				mp.auxWireVBO[i] = auxWireVBO[i] + pos.x;
				mp.auxWireVBO[i+1] = auxWireVBO[i+1] + pos.y;
				mp.auxWireVBO[i+2] = auxWireVBO[i+2] + pos.z;
			}
			mp.auxWireColorVA = auxWireColorVA.clone();
		}
		// it is needed?
		if (bboxCount > 0) {
			mp.bbox = new float[24*3];
			for (int i=0;i<bbox.length;i+=3) {
				mp.bbox[i] = bbox[i] + pos.x;
				mp.bbox[i+1] = bbox[i+1] + pos.y;
				mp.bbox[i+2] = bbox[i+2] + pos.z;
			}
		}
//		connections.clear();
//		for (ConnectionPoint cp : mp.getPlacedPart().getConnections()) {
//			connections.add(cp.fastMove(pos));
//		}
		return mp;
	}
	
	
	
	private float[] calcNormal(float[] p, float[] p1, float[] p2) {

		float v1x,v1y,v1z,v2x,v2y,v2z,xn,yn,zn;

		v1x = p1[0] - p[0];
		v1y = p1[1] - p[1];
		v1z = p1[2] - p[2];
		v2x = p2[0] - p1[0];
		v2y = p2[1] - p1[1];
		v2z = p2[2] - p1[2];
		xn = v1y * v2z - v1z * v2y;
		yn = v1z * v2x - v1x * v2z;
		zn = v1x * v2y - v1y * v2x;
		return new float[] {xn,yn,zn};
	}

	
	
	private void addPolyVertex(float[] point, float[] normal, Color c) {
		polyVBO[tvertexIndex++] = point[0];
		polyVBO[tvertexIndex++] = point[1];
		polyVBO[tvertexIndex++] = point[2];
		polyVBO[tvertexIndex++] = normal[0];
		polyVBO[tvertexIndex++] = normal[1];
		polyVBO[tvertexIndex++] = normal[2];
		polyColorVA[tColorIndex++] = (byte)c.getRed();
		polyColorVA[tColorIndex++] = (byte)c.getGreen();
		polyColorVA[tColorIndex++] = (byte)c.getBlue();
		polyColorVA[tColorIndex++] = (byte)c.getAlpha();
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


	
	private void addAuxLineVertex(float[] point, Color c) {
		
		auxWireVBO[avertexIndex++] = point[0];
		auxWireVBO[avertexIndex++] = point[1];
		auxWireVBO[avertexIndex++] = point[2];
		auxWireColorVA[acolorIndex++] = (byte)c.getRed();
		auxWireColorVA[acolorIndex++] = (byte)c.getGreen();
		auxWireColorVA[acolorIndex++] = (byte)c.getBlue();
		auxWireColorVA[acolorIndex++] = (byte)c.getAlpha();

	}


	
	/**
	 * A really complex function that uses OpenGL Vertex Buffer Object
	 * specification to create arrays of float for a part vertex and
	 * arrays of byte for colors
	 *
	 * Triangles with attribute array:
	 *  - coordinate (x,y,z)
	 *  - normal (x,y,z)
	 *  for every vertex
	 *  Separate color attribute byte array:
	 *  - color (r,g,b,a)
	 *  for every vertex
	 *
	 * Lines with attribute array:
	 *  - coordinate (x,y,z)
	 *  for every vertex
	 *  Separate color attribute byte array:
	 *  - color (r,g,b,a)
	 *  for every vertex
	 * @throws IOException 
	 * 
	 */ 
	private void renderPart(Collection<LDPrimitive> pt, int color, Matrix3D m, boolean invert) {

		float[] nm = new float[3];
		float[] p1, p2, p3;
		Color pc;

		//System.out.println(pt);
		for (LDPrimitive prim : pt) {
			switch (prim.getType()) {
			case TRIANGLE:
			// triangle:
				//System.out.println("Triangle-"+invert);
				if (prim.getColorIndex() == LDrawColor.CURRENT) {
					// current color
					pc = LDrawColor.getById(color).getColor();
				}
				else if (prim.getColorIndex() == LDrawColor.EDGE) {
					// edge color
					pc = LDrawColor.getById(color).getEdge();
				}
				else {
					// specific color
					pc = LDrawColor.getById(prim.getColorIndex()).getColor();
				}
				p1 = m.transformPoint(prim.getPointsFV()[0], prim.getPointsFV()[1],prim.getPointsFV()[2]);
				p2 = m.transformPoint(prim.getPointsFV()[3], prim.getPointsFV()[4],prim.getPointsFV()[5]);
				p3 = m.transformPoint(prim.getPointsFV()[6], prim.getPointsFV()[7],prim.getPointsFV()[8]);
				// place every vertex with color and normal on array
				
				if (invert^prim.isInvert()) {
					nm = calcNormal(p3, p2, p1);
				}
				else {
					nm = calcNormal(p1, p2, p3);
				}
				addPolyVertex(p1,nm,pc);
				addPolyVertex(p2,nm,pc);
				addPolyVertex(p3,nm,pc);
				break;
			case QUAD:
			// quad, rendered as two adjacent triangles:
				//System.out.println("Quad-"+invert);
				if (prim.getColorIndex() == LDrawColor.CURRENT) {
					// current color
					pc = LDrawColor.getById(color).getColor();
				}
				else if (prim.getColorIndex() == LDrawColor.EDGE) {
					// edge color
					pc = LDrawColor.getById(color).getEdge();
				}
				else {
					// specific color
					pc = LDrawColor.getById(prim.getColorIndex()).getColor();
				}
				p1 = m.transformPoint(prim.getPointsFV()[0], prim.getPointsFV()[1],prim.getPointsFV()[2]);
				p2 = m.transformPoint(prim.getPointsFV()[3], prim.getPointsFV()[4],prim.getPointsFV()[5]);
				p3 = m.transformPoint(prim.getPointsFV()[6], prim.getPointsFV()[7],prim.getPointsFV()[8]);
				// place every vertex with color and normal on array
				if (invert^prim.isInvert()) {
					nm = calcNormal(p3, p2, p1);
				}
				else {
					nm = calcNormal(p1, p2, p3);
				}
				addPolyVertex(p1,nm,pc);
				addPolyVertex(p2,nm,pc);
				addPolyVertex(p3,nm,pc);
//				// place every vertex with color and normal on array
//				if (invert) {
//					nm = m.transformNormal(-prim.getNormalFV()[0], -prim.getNormalFV()[1],-prim.getNormalFV()[2]);
//				}
//				else {
//					nm = m.transformNormal(prim.getNormalFV()[0], prim.getNormalFV()[1],prim.getNormalFV()[2]);
//				}
//				for (int i=0;i<3;i++) {
//					addPolyVertex(
//							m.transformPoint(prim.getPointsFV()[i*3], prim.getPointsFV()[i*3+1],prim.getPointsFV()[i*3+2]),
//							nm, pc);
//				}
				// now vertex 0,2,3
				p1 = m.transformPoint(prim.getPointsFV()[0], prim.getPointsFV()[1],prim.getPointsFV()[2]);
				p2 = m.transformPoint(prim.getPointsFV()[6], prim.getPointsFV()[7],prim.getPointsFV()[8]);
				p3 = m.transformPoint(prim.getPointsFV()[9], prim.getPointsFV()[10],prim.getPointsFV()[11]);
				addPolyVertex(p1,nm,pc);
				addPolyVertex(p2,nm,pc);
				addPolyVertex(p3,nm,pc);
//				addPolyVertex(
//						m.transformPoint(prim.getPointsFV()[0*3], prim.getPointsFV()[0*3+1],prim.getPointsFV()[0*3+2]),
//						nm, pc);
//				addPolyVertex(
//						m.transformPoint(prim.getPointsFV()[2*3], prim.getPointsFV()[2*3+1],prim.getPointsFV()[2*3+2]),
//						nm, pc);
//				addPolyVertex(
//						m.transformPoint(prim.getPointsFV()[3*3], prim.getPointsFV()[3*3+1],prim.getPointsFV()[3*3+2]),
//						nm, pc);
//				
//
				break;
			case REFERENCE:
			// sub-part
				int localColor = 0;
				if (prim.getColorIndex() == LDrawColor.CURRENT) {
					// current color
					localColor = color;
				}
				else if (prim.getColorIndex() == LDrawColor.EDGE) {
					// edge color is illegal in sub-part!
					Logger.getGlobal().log(Level.WARNING,"[LDRenderedPart] Illegal EDGE color in sub-part:\n"+prim.toString());
					localColor = color;
				}
				else {
					// specific color
					localColor = prim.getColorIndex();
				}
				// get as VBO
				//System.out.println(p.getId()+" c:"+localColor+" inv:"+invert+ " isInvert:"+p.isInvert());
				if (prim.getTransformation().determinant() < 0) {
					renderPart(LDrawPart.getPart(prim.getLdrawId()).getPrimitives(), 
							localColor, prim.getTransformation().transform(m), prim.isInvert()^(!invert));
				}
				else {
					renderPart(LDrawPart.getPart(prim.getLdrawId()).getPrimitives(), 
							localColor, prim.getTransformation().transform(m), prim.isInvert()^invert);
				}
				break;
			case LINE:
			// it is a line, so place in a wireframe VBO
				if (prim.getColorIndex() == LDrawColor.CURRENT) {
					// current color
					pc = LDrawColor.getById(color).getColor();
				}
				else if (prim.getColorIndex() == LDrawColor.EDGE) {
					// edge color
					pc = LDrawColor.getById(color).getEdge();
				}
				else {
					// specific color
					pc = LDrawColor.getById(prim.getColorIndex()).getColor();
				}
				// place every vertex with color and normal on array
				for (int i=0;i<2;i++) {
					addLineVertex(
							m.transformPoint(prim.getPointsFV()[i*3], prim.getPointsFV()[i*3+1],prim.getPointsFV()[i*3+2]),
							pc);
				}
				break;
			case AUXLINE:
			// it is an aux line, so place in a auxWireframe VBO
				if (!isAuxLinesEnabled())
					break;
				if (prim.getColorIndex() == LDrawColor.CURRENT) {
					// current color
					pc = LDrawColor.getById(color).getColor();
				}
				else if (prim.getColorIndex() == LDrawColor.EDGE) {
					// edge color
					pc = LDrawColor.getById(color).getEdge();
				}
				else {
					// specific color
					pc = LDrawColor.getById(prim.getColorIndex()).getColor();
				}
				// place every vertex with color and normal on array
				for (int i=0;i<2;i++) {
					addAuxLineVertex(
							m.transformPoint(prim.getPointsFV()[i*3], prim.getPointsFV()[i*3+1],prim.getPointsFV()[i*3+2]),
							pc);
				}
				break;
			default:
				//System.out.println("[LDRenderedPart] Unknown primitive:\n"+p.toString());
				break;
			}
		}
	}

	
	/**
	 * A "preventive" function to count vertex needed for a rendered part 
	 * 
	 * @return an int[3] array with: triangle count, line count, aux line count
	 * @throws IOException 
	 */
	private int[] countVertex(Collection<LDPrimitive> lp) {

		int triangles = 0;
		int lines = 0;
		int auxlines = 0;
		
		for (LDPrimitive p : lp) {
			switch (p.getType()) {
			case TRIANGLE:
				triangles++;
				break;
			case QUAD:
				triangles += 2;
				break;
			case REFERENCE:
				// get as VBO
				int[] c;
				c = countVertex(LDrawPart.getPart(p.getLdrawId()).getPrimitives());
				triangles += c[0];
				lines += c[1];
				auxlines += c[2];
				break;
			case LINE:
				lines++;
				break;
			case AUXLINE:
				auxlines++;
				break;
			default:
				break;
			}
		}
		return new int[] {triangles,lines,auxlines};

	}
	
	
	/**
	 * generates six values for xmax,xmin,ymax,ymin,zmax,zmin
	 * for part
	 */
	private void boundingBox(Collection<LDPrimitive> pt, Matrix3D m) {
		
		float[] b = new float[3];
		
		for (LDPrimitive p : pt) {
			switch (p.getType()) {
			case TRIANGLE:
				for (int i=0;i<3;i++) {
					b = m.transformPoint(p.getPointsFV()[i*3], p.getPointsFV()[i*3+1],p.getPointsFV()[i*3+2]);
					if (xmax < b[0]) 
						xmax = b[0];
					if (xmin > b[0])
						xmin = b[0];
					if (ymax < b[1]) 
						ymax = b[1];
					if (ymin > b[1])
						ymin = b[1];
					if (zmax < b[2]) 
						zmax = b[2];
					if (zmin > b[2])
						zmin = b[2];
				}
				break;
			case QUAD:
				for (int i=0;i<4;i++) {
					b = m.transformPoint(p.getPointsFV()[i*3], p.getPointsFV()[i*3+1],p.getPointsFV()[i*3+2]);
					if (xmax < b[0]) 
						xmax = b[0];
					if (xmin > b[0])
						xmin = b[0];
					if (ymax < b[1]) 
						ymax = b[1];
					if (ymin > b[1])
						ymin = b[1];
					if (zmax < b[2]) 
						zmax = b[2];
					if (zmin > b[2])
						zmin = b[2];
				}
				break;
			case REFERENCE:
				boundingBox(LDrawPart.getPart(p.getLdrawId()).getPrimitives(), 
							p.getTransformation().transform(m));
				break;
			default:
				break;
	
			}
		}
	}
	

	
	private void generatePartVBOs() {
		
		// count vertex for triangles and lines		
		int[] c = countVertex(pp.getPrimitives());

		try {
			polyVBO = new float[c[0]*3*6];
			polyColorVA = new byte[c[0]*3*4];
			wireVBO = new float[c[1]*2*3];
			wireColorVA = new byte[c[1]*2*4];
			auxWireVBO = new float[c[2]*2*3];
			auxWireColorVA = new byte[c[2]*2*4];
		}
		catch (OutOfMemoryError ex) {
			polyVBO = null;
			polyColorVA = null;
			wireVBO = null;
			wireColorVA = null;
			auxWireVBO = null;
			auxWireColorVA = null;
			throw new OutOfMemoryError("Your model is too big to render."); 
		}
		tvertexIndex = 0;
		tColorIndex = 0;
		lvertexIndex = 0;
		lcolorIndex = 0;
		avertexIndex = 0;
		acolorIndex = 0;

		renderPart(pp.getPrimitives(),pp.getColorIndex(),/*pp.getTransformation()*/ new Matrix3D(),false);

		// normalize normals
		for (int i=3;i<c[0]*3*6;i+=6) {
			float x = polyVBO[i];
			float y = polyVBO[i+1];
			float z = polyVBO[i+2];
			float d = (float) Math.sqrt(x*x+y*y+z*z);
			if (d == 0) {
				polyVBO[i] = 0f;
				polyVBO[i+1] = 0f;
				polyVBO[i+2] = 1f;
			}
			else {
				polyVBO[i] = x/d;
				polyVBO[i+1] = y/d;
				polyVBO[i+2] = z/d;
			}
		}
		xmin = 1000000;
		xmax = -1000000;
		ymin = 1000000;
		ymax = -1000000;
		zmin = 1000000;
		zmax = -1000000;
		if (pp.getType() == LDrawCommand.REFERENCE) {
			boundingBox(LDrawPart.getPart(pp.getLdrawId()).getPrimitives(), new Matrix3D());
		}
		else {
			boundingBox(pp.getPrimitives(), new Matrix3D());
		}
		bbox = new float[24*3]; 	// 12 lines * 3 float each (24 * vertexXYZ)
		bbox[ 0] = xmin;
		bbox[ 1] = ymin;
		bbox[ 2] = zmin;
		bbox[ 3] = xmax;
		bbox[ 4] = ymin;
		bbox[ 5] = zmin;	// x1,y1,z1 -> x2,y1,z1
		bbox[ 6] = xmax;
		bbox[ 7] = ymin;
		bbox[ 8] = zmin;
		bbox[ 9] = xmax;
		bbox[10] = ymax;
		bbox[11] = zmin;	// x2,y1,z1 -> x2,y2,z1
		bbox[12] = xmax;
		bbox[13] = ymax;
		bbox[14] = zmin;
		bbox[15] = xmin;
		bbox[16] = ymax;
		bbox[17] = zmin;	// x2,y2,z1 -> x1,y2,z1
		bbox[18] = xmin;
		bbox[19] = ymax;
		bbox[20] = zmin;
		bbox[21] = xmin;
		bbox[22] = ymin;
		bbox[23] = zmin;	// x1,y2,z1 -> x1,y1,z1
		
		bbox[24] = xmin;
		bbox[25] = ymin;
		bbox[26] = zmax;
		bbox[27] = xmax;
		bbox[28] = ymin;
		bbox[29] = zmax;	// x1,y1,z2 -> x2,y1,z2
		bbox[30] = xmax;
		bbox[31] = ymin;
		bbox[32] = zmax;
		bbox[33] = xmax;
		bbox[34] = ymax;
		bbox[35] = zmax;	// x2,y1,z2 -> x2,y2,z2
		bbox[36] = xmax;
		bbox[37] = ymax;
		bbox[38] = zmax;
		bbox[39] = xmin;
		bbox[40] = ymax;
		bbox[41] = zmax;	// x2,y2,z2 -> x1,y2,z2
		bbox[42] = xmin;
		bbox[43] = ymax;
		bbox[44] = zmax;
		bbox[45] = xmin;
		bbox[46] = ymin;
		bbox[47] = zmax;	// x1,y2,z2 -> x1,y1,z2
		
		bbox[48] = xmin;
		bbox[49] = ymin;
		bbox[50] = zmin;
		bbox[51] = xmin;
		bbox[52] = ymin;
		bbox[53] = zmax;	// x1,y1,z1 -> x1,y1,z2
		bbox[54] = xmax;
		bbox[55] = ymin;
		bbox[56] = zmin;
		bbox[57] = xmax;
		bbox[58] = ymin;
		bbox[59] = zmax;	// x2,y1,z1 -> x2,y1,z2
		bbox[60] = xmax;
		bbox[61] = ymax;
		bbox[62] = zmin;
		bbox[63] = xmax;
		bbox[64] = ymax;
		bbox[65] = zmax;	// x2,y2,z1 -> x2,y2,z2
		bbox[66] = xmin;
		bbox[67] = ymax;
		bbox[68] = zmin;
		bbox[69] = xmin;
		bbox[70] = ymax;
		bbox[71] = zmax;	// x1,y2,z1 -> x1,y2,z2
		bboxCount = 24;
		float[] p = new float[3];
		if (pp.getType() == LDrawCommand.REFERENCE) {
			for (int i=0;i<24*3;i+=3) {
				p = pp.getTransformation().transformPoint(bbox[i],bbox[i+1],bbox[i+2]);
				bbox[i] = p[0];
				bbox[i+1] = p[1];
				bbox[i+2] = p[2];
			}
		}
		// generates connection points
//		connections.clear();
//		for (ConnectionPoint cp : pp.getConnections()) {
//			connections.add(cp.getClone(pp.getId()));
//			//connections.add(cp);
//		}
	}
	
	
	
	public static LDRenderedPart newRenderedPart(LDPrimitive p) {
		
		//System.out.println(p.getId());
		//System.out.println(p);
		LDRenderedPart part = new LDRenderedPart(p);
//		renderedParts.put(p.getId(),part);
		//System.out.println("Added "+p.getLdrawid()+"-"+part.getId());
		//part.polyVBO = part.getTrianglesVBO();
		//part.polyColorVA = part.getTriangleColorVA();
		//part.wireVBO = part.getWireFrameVBO();
		//part.wireColorVA = part.getWireColorVa();
		part.triangleVertexCount = part.polyVBO.length/6;  // 3*coords + 3*normal
		part.lineVertexCount = part.wireVBO.length/3;
		part.auxLineVertexCount = part.auxWireVBO.length/3;
		return part;
	}
	
	
	
//	public static LDRenderedPart getByGlobalId(int id) {
//		
//		return renderedParts.get(id);
//	}
//	
//	
//	public static void deleteFromCache(int id) {
		
//		renderedParts.remove(id);
//	}
	
	
	private LDPrimitive getPlacedPart() {
		
		return pp;
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

	
	
	public float[] getTrianglesVBO() {
		return polyVBO;
	}

	
	public float[] getWireFrameVBO() {
		return wireVBO;
	}
	
	
	public byte[] getTriangleColorVA() {
		return polyColorVA;
	}
	
	
	public byte[] getWireColorVa() {
		return wireColorVA;
	}


	public float[] getAuxWireFrameVBO() {
		return auxWireVBO;
	}
	
	
	public byte[] getAuxWireColorVa() {
		return auxWireColorVA;
	}


	public int getTriangleVertexCount() {
		return triangleVertexCount;
	}


	public int getLineVertexCount() {
		return lineVertexCount;
	}


	public int getAuxLineVertexCount() {
		return auxLineVertexCount;
	}


	public int getTriangleName() {
		return triangleName;
	}



	public void setTriangleName(int triangleName) {
		this.triangleName = triangleName;
	}


	public int getTriangleColorName() {
		return triangleColorName;
	}


	public void setTriangleColorName(int triangleColorName) {
		this.triangleColorName = triangleColorName;
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
	
	
	public int getAuxLineName() {
		return auxLineName;
	}


	public void setAuxLineName(int auxName) {
		this.auxLineName = auxName;
	}


	public int getAuxLineColorName() {
		return auxLineColorName;
	}


	public void setAuxLineColorName(int auxColorName) {
		this.auxLineColorName = auxColorName;
	}
	
	
	public int getBboxName() {
		return bboxName;
	}


	public void setBboxName(int bboxName) {
		this.bboxName = bboxName;
	}


	public float[] getBboxVBO() {
		return bbox;
	}


	public int getBboxCount() {
		return bboxCount;
	}

	
	public float getSizeX() {
		
		return xmax-xmin;
	}
	

	public float getSizeY() {
		
		return ymax-ymin;
	}
	
	
	public float getSizeZ() {
		
		return zmax-zmin;
	}
	
	
	public float getCenterX() {
		
		return (xmax+xmin)/2;
	}
	
	
	public float getCenterY() {
		
		return (ymax+ymin)/2;
	}
	
	
	public float getCenterZ() {
		
		return (zmax+zmin)/2;
	}
	

	public static void useBoundingSelect() {
		
		useBounding = true;
	}
	
	
	public static void useLineSelect() {
		
		useBounding = false;
	}
	
	
	public static boolean isBoundingSelect() {
		
		return useBounding;
	}
	
	
	public boolean isHighLighted() {
		return highLighted;
	}


	public void highLight() {
		highLighted = true;
	}


	public void highLightOff() {
		highLighted = false;
	}


	public boolean isDimmed() {
		return dimmed;
	}


	public void dimOn() {
		dimmed = true;
	}


	public void dimOff() {
		dimmed = false;
	}


	public boolean isConnected() {
		return connected;
	}


	public void connected() {
		this.connected = true;
	}
	
	
	public void unConnect() {
		connected = false;
	}


	public static void disableAuxLines() {
		
		auxLines  = false;
	}
	
	
	public static void enableAuxLines() {
		
		auxLines = true;
	}
	
	
	public static boolean isAuxLinesEnabled() {
		return auxLines;
	}
	
	
}
