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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import bricksnspace.j3dgeom.JSimpleGeom;
import bricksnspace.j3dgeom.Matrix3D;
import bricksnspace.j3dgeom.Point3D;
import bricksnspace.ldrawlib.ConnectionPoint;
import bricksnspace.ldrawlib.ConnectionTypes;
import bricksnspace.ldrawlib.LDPrimitive;
import bricksnspace.ldrawlib.LDrawColor;
import bricksnspace.ldrawlib.LDrawCommand;
import bricksnspace.ldrawlib.LDrawPart;

/**
 * Handles connections between parts
 * 
 * @author Mario Pascucci
 *
 */
public class ConnectionHandler {
	
	// connections in model, mapped by type
	private Map<Integer,List<ConnectionPoint>> connectionsMap = 
			new HashMap<Integer,List<ConnectionPoint>>();
	
	// connections database indexed by connection global Id
	private Map<Integer,ConnectionPoint> connections = new HashMap<Integer,ConnectionPoint>();
	
	private Point3D lastConnPoint = new Point3D(0,0,0);
	private ConnectionPoint target = null;
	private Matrix3D connMatrix = null;
	private boolean locked;
	private PartQueryable model;

	// all connection with spatial index
	private ConnGrid spatialIndex = null;   // DB
	

	
	public ConnectionHandler(PartQueryable model) {
		
		for (ConnectionTypes ct:ConnectionTypes.listTypes()) {
			connectionsMap.put(ct.getId(), new ArrayList<ConnectionPoint>());
		}
		spatialIndex = new ConnGrid();
		this.model = model;
	}

	
	
	public Point3D getLastConn() {
		
		return lastConnPoint;
	}
	
	
	
	public ConnectionPoint getTarget() {
		return target;
	}

	
	public void resetTarget() {
		target = null;
	}
	
	
	
	public boolean isLocked() {
		return target != null;
	}
	
	
	public Matrix3D getAlignMatrix() {
		
		return connMatrix;
	}


	public void dumpConnections() {
		
		for (List<ConnectionPoint> l:connectionsMap.values()) {
			if (l.size() > 0) {
				System.out.println(l.get(0).getType());
				for (ConnectionPoint p: l) {
					System.out.println(p);
				}
			}
		}
	}

	
	

	public List<ConnectionPoint> getConnectionsByType(int ct) {
		
		return connectionsMap.get(ct);
	}
	
	
	
	/** 
	 * Add connection points from a primitive
	 * 
	 * @param rp a primitive
	 */
	public void addConnections(LDPrimitive rp) {

		if (rp.getType() != LDrawCommand.REFERENCE)
			return;
		ConnectionPoint[] listCp = rp.getConnPoints();//ConnectionPoint.getConnections(rp);
		for (ConnectionPoint cp: listCp) {
			addSingleConn(cp);
		}
	}
	
	
	/**
	 * Add all connections from a rendered model
	 * do NOT use for single part!
	 * @param m
	 */
	public void addAllConnections(LDrawPart m) {
		
		for (LDPrimitive p : m.getPrimitives()) {
			addConnections(p);
		}
	}
	
	
	/**
	 * add all connections for part
	 * do NOT use for models.
	 * @param ldrawid
	 */
	public void addPartConnections(String ldrawid) {
		
		LDPrimitive p = LDPrimitive.newGlobalPart(ldrawid, LDrawColor.CURRENT, new Matrix3D());
		addConnections(p);
	}
	
	
	
	public void delConnections(LDPrimitive rp) {
		
		for (ConnectionPoint cp : rp.getConnPoints()) {
			connections.remove(cp.getId());
			connectionsMap.get(cp.getType().getId()).remove(cp);
			spatialIndex.removePoint(cp);
		}
	}
	
	
	
	public void addSingleConn(ConnectionPoint cp) {
		
		// add to global connection lists
		connectionsMap.get(cp.getType().getId()).add(cp);
		spatialIndex.insertPoint(cp);
		// add to connId indexed database
		connections.put(cp.getId(), cp);
	}

	
	
	public void delSingleConn(ConnectionPoint cp) {
		
		connections.remove(cp.getId());
		connectionsMap.get(cp.getType().getId()).remove(cp);
		spatialIndex.removePoint(cp);
	}
	
	
	
	public ConnectionPoint getConnectionPointById(int id) {
		
		return connections.get(id);
	}
	
	
	
	public Collection<ConnectionPoint> getConnectionList() {
		
		return connections.values();
	}
	


	/**
	 * Returns connection point for part using cursor and eye coordinates
	 *  
	 * @param cp list of connection points taken from LDrawPart (not placed)
	 * @param cursor 3D position of cursor in world coordinates
	 * @param eye 3D position of eye in world coordinates
	 * @return a 3D point of connection
	 */
	public boolean getConnectionPoint(LDPrimitive part, Matrix3D pointerMatrix, Point3D cursor, Point3D eye) {
		
		boolean needsAlign = false;
		List<ConnectionPoint> candidates = null;
		ConnectionPoint targetConn = null;
		Point3D partOffset = null,offset=null;
		Matrix3D alignMatrix = null;
		
		//System.out.println("Start"); // DB
		locked = false;
		// for every part connection point
		float distance = 1e30f;
		for (ConnectionPoint cc : part.getConnPoints()) {
			//System.out.println(c.getType()+"-"+c.getP1());
			// trace a line to point of view to connection point
			// look for nearest connection of right type
			switch (cc.getType().getFamily()) {
			case VECTOR:
				ConnectionPoint c = cc.fastMove(cursor);
				candidates = getNearConn(c.getType().getOpposite(), c.getP1(), eye, 100);
				if (candidates.size() != 0) {
					// System.out.println(c.getType()+"-"+candidates); // DB
					// found connections, select nearest to eye
					for (ConnectionPoint nc : candidates) {
						float dist = nc.getP1().getDistSq(eye); 
						if (dist < distance) {
							// checks orientation
							if (JSimpleGeom.dotPoints(c.getP1(), c.getP2(), nc.getP1(), nc.getP2()) > 0) {
								// checks if cos(angle) is near to 1, i.e. connection points vectors 
								// 		are parallel and same orientation
								// 		at the moment no action is taken if angle is slightly different
								//		in next releases we try to add a "suggested angle" to align parts
								float cosphi = JSimpleGeom.dotProdAngle(c.getP1(), c.getP2(), nc.getP1(), nc.getP2());
								if (cosphi >= 0.70) {
									distance = dist;
									targetConn = nc;
									if (cosphi >= 0.999) {
										// part are aligned
										partOffset = c.getDelta();
										needsAlign = false;
									}
									else {
										// parts needs alignment
										needsAlign = true;
										alignMatrix = JSimpleGeom.alignMatrix(c.getP1(),c.getP2(),nc.getP1(),nc.getP2());
										partOffset = c.getDelta().transform(alignMatrix.getOnlyRotation());
									}
									locked = true;								
								}
							}
							//System.out.println(dist); // DB
						}
					}
				}
				break;
			case RAIL:
				c = cc.fastMove(cursor);
				candidates = getNearestRailConn(c.getType().getOpposite(),c, eye);
				// gets length of moving connection point
				float clen = c.getP1().getDistSq(c.getP2());
				// if exists, gets point on line p1-p2 of axle/bar nearest to line p1-eye from target
				if (candidates.size() > 0) {
					for (ConnectionPoint railcp: candidates) {
						float dist = JSimpleGeom.line2line(eye,c.getP1(), railcp.getP1(), railcp.getP2());
						if (dist < distance) {
							Point3D p = JSimpleGeom.nearestPointToSegments(railcp.getP1(), railcp.getP2(), 
									c.getP1(), eye,false);
							offset = railcp.getP1().vector(p).translate(c.getDelta());
							c = cc.fastMove(railcp.getP1().translate(offset));
							float dist1 = railcp.getP1().getDistSq(c.getP1());
							float dist2 = railcp.getP2().getDistSq(c.getP1());
							float dist3 = railcp.getP1().getDistSq(c.getP2());
							float dist4 = railcp.getP2().getDistSq(c.getP2());
							// use longer part size to check if connection is too far
							float len = railcp.getP1().getDistSq(railcp.getP2());
							//float lenMax = Math.max(len,clen);
							// if connection point is too far
							//if ((dist1 > clen && dist2 > clen) || (dist3 > clen && dist4 > clen) ) {
							if (clen < len) {
								if (Math.max(dist1, dist2) > len && Math.max(dist3, dist4) > len) {
								//if (dist1+dist2 > len+400) {
									//System.out.println(dist1+" - "+dist2+" - l= "+len); // DB
			//						locked = false;
									continue;
								}
							}
							else {
								if (Math.max(dist1, dist3) > clen && Math.max(dist2, dist4) > clen) {
									continue;
								}
							}
							float cosphi = JSimpleGeom.dotProdAngle(c.getP1(), c.getP2(), railcp.getP1(), railcp.getP2());
							distance = dist;
							targetConn = railcp;
							if (Math.abs(cosphi) >= 0.999) {
								// part are aligned
								partOffset = offset;
								needsAlign = false;
							}
							else {
								// parts needs alignment
								needsAlign = true;
								alignMatrix = JSimpleGeom.minAlignMatrix(c.getP1(),c.getP2(),railcp.getP1(),railcp.getP2());
								partOffset = offset.transform(alignMatrix.getOnlyRotation());
							}
							locked = true;
						}
//						partOffset = offset;
//						locked = true;
//						needsAlign = false;
					}
				}
				break;
			case POINT:
				// do not checks orientation->ball-socket can connect in any direction
				c = cc.fastMove(cursor);
				candidates = getNearConn(c.getType().getOpposite(), c.getP1(), eye, 100);
				if (candidates.size() != 0) {
					// System.out.println(c.getType()+"-"+candidates); // DB
					// found connections, select nearest to eye
					for (ConnectionPoint nc : candidates) {
						float dist = nc.getP1().getDistSq(eye); 
						if (dist < distance) {
							distance = dist;
							targetConn = nc;
							partOffset = c.getDelta();
							needsAlign = false;
							locked = true;
						}
					}
				}
				break;
			default:
				locked = false;
				needsAlign = false;
				break;
			}
		}
		// now we have coord of connection point,
		// but we need "displacement" relative to part origin, 
		// not to part connection point
		if (locked) {
			//System.out.println(targetConn); // DB
			//System.out.println(partOffset);
			target = targetConn;
			lastConnPoint = targetConn.getP1().translate(partOffset);
			if (needsAlign) {
				connMatrix = part.getTransformation().transform(alignMatrix);
				return true;
			}
			else {
				return false;
			}
		}
		else {
			lastConnPoint = cursor;
			target = null;
			// checks if part is already aligned to pointer matrix
			float angle = JSimpleGeom.dotProdAngle(
					new Point3D(0, 0, 0), new Point3D(pointerMatrix.transformNormal(0,1,0)), 
					new Point3D(0, 0, 0), new Point3D(part.getTransformation().transformNormal(0, 1, 0)));
			if (angle > 0.999) {
				return false;
				//return LDRenderedPart.getByGlobalId(part.getId()).fastMove(cursor);
			}
			else {
				connMatrix = pointerMatrix;
				return true;
				//part.setTransformation(pointerMatrix);
				//return LDRenderedPart.newRenderedPart(part).fastMove(cursor);
			}
		}
	}
	

	
	
	private List<ConnectionPoint> getNearestRailConn(int ct, ConnectionPoint connAtCursor, Point3D eye) {
		
		List<ConnectionPoint> cp = new ArrayList<ConnectionPoint>();
		//float partDistance = 1e30f;

		for (ConnectionPoint p:connectionsMap.get(ct)) {
			//System.out.println(ct+" - "+ freeConnections.get(ct).size());
			// if part is hidden, ignore connections
			if (model.isHidden(p.getPartId()))
				continue;
			float dist = JSimpleGeom.line2line(eye,connAtCursor.getP1(), p.getP1(), p.getP2());
			if (dist < 10) {
				//float connDist = Math.min(connAtCursor.getP1().getDistSq(p.getP1()),connAtCursor.getP1().getDistSq(p.getP2()));
				//if (connDist < partDistance) {
					//System.out.println("+"+dist);
//				System.out.println(JSimpleGeom.dotProdAngle(p.getP1(), p.getP2(), 
//						connAtCursor.getP1(), connAtCursor.getP2()));  // DB
				if (Math.abs(JSimpleGeom.dotProdAngle(p.getP1(), p.getP2(), 
						connAtCursor.getP1(), connAtCursor.getP2())) >= 0.9f) {  // ~25 degree
					cp.add(p);
				}
			}
		}
		return cp;
	}
	

	
	
	/**
	 * returns connection point p1 nearest to line eye-cursor 
	 * @param cursor world coordinates of cursor
	 * @param eye world coordinate of eye
	 * @return
	 */
	public ConnectionPoint getNearestConnection(Point3D cursor, Point3D eye) {

		List<ConnectionPoint> candidates = null;
		ConnectionPoint nearest = null;

		float distance = 1e30f;
		for (ConnectionTypes ct :ConnectionTypes.listTypes()) {
			candidates = getNearConn(ct.getId(), cursor, eye, 9);
			if (candidates.size() != 0) {
				// found connections, select nearest to eye
				for (ConnectionPoint nc : candidates) {
					float dist = nc.getP1().getDistSq(eye); 
					if (dist < distance) {
						distance = dist;
						nearest = nc;
					}
				}
			}
		}
		return nearest;
	}
	
	


	/**
	 * Finds nearest connection point in part to line specified by line as two xyz vertex
	 * 
	 * @param part LDPrimitive where to find connection
	 * @param l1 first point of a line as {l1x,l1y,l1z,l2x,l2y,l2z}
	 * @param l2 second point of line
	 * @return a single connection points or null
	 */
	public ConnectionPoint getPartNearConn(LDPrimitive part, Point3D l1, Point3D l2) {
		
		if (model.isHidden(part.getId())) 
			return null;
		ConnectionPoint cp = null;
		float distance = 1e30f;
		for (ConnectionPoint pt:part.getConnPoints()) {
			float d = JSimpleGeom.point2line(pt.getP1(), l1, l2);
			if (d < distance) {
				cp = pt;
				distance = d; 
			}
		}
		return cp;
	}


	
	/**
	 * Finds nearest connection point in part to line specified by line as two xyz vertex
	 * 
	 * @param part LDPrimitive where to find connection
	 * @param l1 first point of a line as {l1x,l1y,l1z,l2x,l2y,l2z}
	 * @param l2 second point of line
	 * @return a single connection points or null
	 */
	public ConnectionPoint getPartNearConnByType(int ct, LDPrimitive part, Point3D l1, Point3D l2) {
		
		if (model.isHidden(part.getId())) 
			return null;
		ConnectionPoint cp = null;
		float distance = 1e30f;
		for (ConnectionPoint pt:part.getConnPoints()) {
			float d = JSimpleGeom.point2line(pt.getP1(), l1, l2);
			if (pt.getType().getId() == ct && d < distance) {
				cp = pt;
				distance = d; 
			}
		}
		return cp;
	}


	
	/**
	 * returns all point nearest to line specified by line as two xyz vertex
	 * 
	 * @param ct connection type
	 * @param l1 first point of a line as {l1x,l1y,l1z,l2x,l2y,l2z}
	 * @param l2 second point of line
	 * @param distance is a square of distance limit
	 * @return a list of connection points
	 */
	
	private List<ConnectionPoint> getNearConn(int ct, Point3D target, Point3D eye, float distance) {
		
		ArrayList<ConnectionPoint> cp = new ArrayList<ConnectionPoint>();
		List<ConnectionPoint> bb = spatialIndex.selectByRay(ct, eye, target);
		for (ConnectionPoint p:bb) {
			//System.out.println(ct+" - "+ freeConnections.get(ct).size());
			// if part is hidden, ignore connections
			if (model.isHidden(p.getPartId()))
				continue;
			if (JSimpleGeom.point2line(p.getP1(), target, eye) < distance) {
				cp.add(p);
			}
		}
		return cp;
	}



}
