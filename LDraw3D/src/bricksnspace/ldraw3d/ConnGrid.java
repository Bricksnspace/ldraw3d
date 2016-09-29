/*
	Copyright 2015 Mario Pascucci <mpascucci@gmail.com>
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



import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bricksnspace.j3dgeom.Point3D;
import bricksnspace.ldrawlib.ConnectionPoint;
import bricksnspace.ldrawlib.ConnectionTypes;


/**
 * Connections spatial index library using really simple algorithm 
 * <p/>
 * It uses a simple grid-like space partitioning.
 * It is a tree where X coordinate is main index, followed by Z coordinate and Y, 
 * in two nested tree: main tree contains supernodes, that contains a cluster of nodes  
 * 
 * spacing defines side size of nodes in LDU
 * tolerance defines overlapping between adjacent nodes in LDU
 * superspacing defines supernodes side size in nodes.
 * If superspacing=4 and spacing=160, every node is 160x160x160 LDU and every supernode is 
 * (160x4)x(160x4)x(160x4) LDU = 640x640x640 LDU
 * 
 * @author Mario Pascucci
 *
 */
public class ConnGrid {

	private static float tolerance = 10;
	/** this is the unitary cube size of a single "box" of points */
	private static float spacing = 160f; 
	private static int superSpacing = 4;
	private Map<Integer,Node> root = new HashMap<Integer, ConnGrid.Node>();
	private Map<Integer,SuperNode> superRoot = new HashMap<Integer, ConnGrid.SuperNode>();
	static int uid = 0;
	
	
	/**
	 * A cluster of nodes, grouped by coordinate
	 * @author Mario Pascucci
	 *
	 */
	private class SuperNode {
		
		private int id;
		private Map<Integer,SuperNode> subSuperNodes = null;
		private ConnGrid subGrid = null;
		/** bounding box corners coords */ 
		private Point3D rt, lb;

		
		
		public SuperNode(boolean isLeaf, int stepx, int stepy, int stepz) {
			
			if (isLeaf) {
				subGrid = new ConnGrid();
			}
			else {
				subSuperNodes = new HashMap<Integer, SuperNode>();
			}
			rt = new Point3D((stepx+1)*spacing*superSpacing+tolerance,
					(stepy+1)*spacing*superSpacing+tolerance,
					(stepz+1)*spacing*superSpacing+tolerance);
			lb = new Point3D(stepx*spacing*superSpacing-tolerance,
					stepy*spacing*superSpacing-tolerance,
					stepz*spacing*superSpacing-tolerance);
			id = ConnGrid.getUid();
		}
		
		
		
		@Override
		public String toString() {
			return String.format("SNode [id=%d rt=%s, lb=%s]", id,
					rt, lb);
		}



	}
	
	
	
	private class Node {
		
		private int id;
		private Map<Integer,Node> subNodes = null;
		private Map<Integer,List<ConnectionPoint>> points = null;
		/** bounding box corners coords */ 
		private Point3D rt, lb;
		private int maxPointsFill = 500, maxNodesFill = 50;

		
		/**
		 * Creates a node or a leaf with default min and max fill values
		 * @param isLeaf if true creates a leaf node
		 */
		public Node(boolean isLeaf, int stepx, int stepy, int stepz) {
			
			if (isLeaf) {
				points = new HashMap<Integer,List<ConnectionPoint>>();
				for (ConnectionTypes ct:ConnectionTypes.listTypes()) {
					points.put(ct.getId(), new ArrayList<ConnectionPoint>());
				}

			}
			else {
				subNodes = new HashMap<Integer, ConnGrid.Node>();
			}
			rt = new Point3D((stepx+1)*spacing+tolerance,
					(stepy+1)*spacing+tolerance,
					(stepz+1)*spacing+tolerance);
			lb = new Point3D(stepx*spacing-tolerance,
					stepy*spacing-tolerance,
					stepz*spacing-tolerance);
			id = ConnGrid.getUid();
		}
		
		
		
		@Override
		public String toString() {
			return String.format("Node [id=%d type=%s, childs=%d, rt=%s, lb=%s]", id,
					isLeaf()?"Leaf":"Node", isLeaf()?points.size():subNodes.size() , rt, lb);
		}


		/**
		 * Checks if a node or leaf is underfilled
		 * @return true if underfilled
		 */ 
		@SuppressWarnings("unused")
		public boolean isEmpty() {
			
			if (subNodes != null) {
				return subNodes.size() == 0;
			}
			else if (points != null) {
				return points.size() == 0;
			}
			return false;
		}
		
		
		/**
		 * Checks if a node is overflowing
		 * @return true if overflowing
		 */
		@SuppressWarnings("unused")
		public boolean isFull() {
			
			if (subNodes != null) {
				return subNodes.size() >= maxNodesFill;
			}
			else if (points != null) {
				return points.size() >= maxPointsFill;
			}
			return false;
		}
		
		
		public boolean isLeaf() {
			
			return points != null;
		}
		
		
		
		public void insertPoint(ConnectionPoint p) {
			
			if (points == null) 
				throw new IllegalStateException("[ConnTree.Node.insertPoint] Node is not a leaf!");
			points.get(p.getType().getId()).add(p);
		}
		
		/**
		 * Deletes a point from leaf node, recalc bounding box
		 * @param p
		 */
		public void deletePoint(ConnectionPoint p) {
			if (points == null) 
				throw new IllegalStateException("[ConnTree.Node.deletePoint] Node is not a leaf!");
			points.get(p.getType().getId()).remove(p);
		}
		
		
		
	}
	
	
	ConnGrid () {
		
	}

	
	static synchronized int getUid() {
		return ++uid;
	}
	
	
	@Override
	public String toString() {
		return String.format("ConnTree [root=%s]", root);
	}





	public static float getTolerance() {
		return tolerance;
	}


	public static void setTolerance(float tolerance) {
		if (tolerance < 0)
			throw new IllegalArgumentException("[ConnTree] Tolerance can't be negative");
		ConnGrid.tolerance = tolerance;
	}


	public void dumpStruct() {
		
		for (SuperNode sx : superRoot.values()) {
			System.out.println("X:"+sx);
			for (SuperNode sz : sx.subSuperNodes.values()) {
				System.out.println("Z:"+sz);
				for (SuperNode sy : sz.subSuperNodes.values()) {
					System.out.println("Y:"+sy);
					for (Node x : sy.subGrid.root.values()) {
						System.out.println("..X:"+x);
						for (Node z:x.subNodes.values()) {
							System.out.println("..Z:"+z);
							for (Node y: z.subNodes.values()) {
								System.out.println("..Y:"+y);
							}
						}
					}
				}
			}
		}
	}
	
	
	public List<Gadget3D> getBoundingBoxes() {
		
		ArrayList<Gadget3D> bb = new ArrayList<Gadget3D>();
		for (SuperNode sx : superRoot.values()) {
			for (SuperNode sz : sx.subSuperNodes.values()) {
				for (SuperNode sy : sz.subSuperNodes.values()) {
					bb.add(DrawHelpers.getBoundingBox(sy.lb, sy.rt, 0));
					for (Node x : sy.subGrid.root.values()) {
						for (Node z:x.subNodes.values()) {
							for (Node y: z.subNodes.values()) {
								bb.add(DrawHelpers.getBoundingBox(y.lb, y.rt, 1));
							}
						}
					}
				}
			}
		}
		return bb;
	}

	
	
	/**
	 * Find subNode containing point p in spacing-bounded cube
	 * @param p point to search
	 */
	public Node getNode(Point3D p) {
		
		// X subtree search
		int sstepx = (int) Math.floor(p.x / spacing / superSpacing);
		int sstepy = (int) Math.floor(p.y / spacing / superSpacing);
		int sstepz = (int) Math.floor(p.z / spacing / superSpacing);
		// search superNode
		SuperNode sx = superRoot.get(sstepx);
		if (sx == null) {
			// create new X supernode
			sx = new SuperNode(false, sstepx, sstepy, sstepz);
			superRoot.put(sstepx, sx);
		}
		SuperNode sz = sx.subSuperNodes.get(sstepz);
		if (sz == null) {
			// create new Z supernode
			sz = new SuperNode(false, sstepx, sstepy, sstepz);
			sx.subSuperNodes.put(sstepz, sz);
		}
		SuperNode sy = sz.subSuperNodes.get(sstepy);
		if (sy == null) {
			// create new Y superleafnode
			sy = new SuperNode(true, sstepx, sstepy, sstepz);
			sz.subSuperNodes.put(sstepy, sy);
		}
		// search Node
		int stepx = (int) Math.floor(p.x / spacing);
		int stepy = (int) Math.floor(p.y / spacing);
		int stepz = (int) Math.floor(p.z / spacing);
		Node nx = sy.subGrid.root.get(stepx);
		if (nx == null) {
			// needs a new X node
			nx = new Node(false,stepx,stepy,stepz);
			sy.subGrid.root.put(stepx, nx);
		}
		// Z subtree search
		Node nz = nx.subNodes.get(stepz);
		if (nz == null) {
			// needs a new Z node
			nz = new Node(false,stepx,stepy,stepz);
			nx.subNodes.put(stepz, nz);
		}
		// Y subtree search
		Node ny = nz.subNodes.get(stepy);
		if (ny == null) {
			// needs a new Y leafnode
			ny = new Node(true,stepx,stepy,stepz);
			nz.subNodes.put(stepy, ny);
		}
		return ny;
	}
	
	
	

	
	
	public void insertPoint(ConnectionPoint p) {
		
		Node n = getNode(p.getP1());
		n.insertPoint(p);
	}
	
	
	
	public void removePoint(ConnectionPoint p) {

		Node n = getNode(p.getP1());
		n.deletePoint(p);
	}
	
	
	/*
	 * from http://gamedev.stackexchange.com/a/18459
	 */
	public List<ConnectionPoint> selectByRay(int connType, Point3D eye, Point3D target) {
		
		List<ConnectionPoint> pt = new ArrayList<ConnectionPoint>();
		Point3D dir = eye.vector(target).normalize();
		// r.dir is unit direction vector of ray
		Point3D dirfrac = new Point3D(1.0f / dir.x, 1.0f / dir.y, 1.0f / dir.z);
		// lb is the corner of AABB with minimal coordinates - left bottom, rt is maximal corner
		// r.org is origin of ray
		for (SuperNode sx : superRoot.values()) {
			for (SuperNode sz:sx.subSuperNodes.values()) {
				for (SuperNode sy: sz.subSuperNodes.values()) {
					float st1 = (sy.lb.x - eye.x)*dirfrac.x;
					float st2 = (sy.rt.x - eye.x)*dirfrac.x;
					float st3 = (sy.lb.y - eye.y)*dirfrac.y;
					float st4 = (sy.rt.y - eye.y)*dirfrac.y;
					float st5 = (sy.lb.z - eye.z)*dirfrac.z;
					float st6 = (sy.rt.z - eye.z)*dirfrac.z;
			
					float stmin = Math.max(Math.max(Math.min(st1, st2), Math.min(st3, st4)), Math.min(st5, st6));
					float stmax = Math.min(Math.min(Math.max(st1, st2), Math.max(st3, st4)), Math.max(st5, st6));
			
					// if tmin > tmax, ray doesn't intersect AABB
					if (stmin > stmax) {
					    continue;
					}

					// if tmax < 0, ray (line) is intersecting AABB, but whole AABB is behing us
					if (stmax < 0) {
					    continue;
					}
		
					for (Node x : sy.subGrid.root.values()) {
						for (Node z:x.subNodes.values()) {
							for (Node y: z.subNodes.values()) {
								
								float t1 = (y.lb.x - eye.x)*dirfrac.x;
								float t2 = (y.rt.x - eye.x)*dirfrac.x;
								float t3 = (y.lb.y - eye.y)*dirfrac.y;
								float t4 = (y.rt.y - eye.y)*dirfrac.y;
								float t5 = (y.lb.z - eye.z)*dirfrac.z;
								float t6 = (y.rt.z - eye.z)*dirfrac.z;
						
								float tmin = Math.max(Math.max(Math.min(t1, t2), Math.min(t3, t4)), Math.min(t5, t6));
								float tmax = Math.min(Math.min(Math.max(t1, t2), Math.max(t3, t4)), Math.max(t5, t6));
						
								// if tmin > tmax, ray doesn't intersect AABB
								if (tmin > tmax) {
								    continue;
								}
			
								// if tmax < 0, ray (line) is intersecting AABB, but whole AABB is behing us
								if (tmax < 0) {
								    continue;
								}
						
								for (ConnectionPoint p:y.points.get(connType)) {
									pt.add(p);
								}
							}
						}
					}
				}
			}
		}
		return pt;
	}
	
	

}
