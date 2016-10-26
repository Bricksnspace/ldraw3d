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

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLContext;
import javax.media.opengl.GLDrawableFactory;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLException;
import javax.media.opengl.GLOffscreenAutoDrawable;
import javax.media.opengl.GLProfile;
import javax.media.opengl.awt.GLCanvas;
import javax.media.opengl.glu.GLU;

import bricksnspace.j3dgeom.JSimpleGeom;
import bricksnspace.j3dgeom.Matrix3D;
import bricksnspace.j3dgeom.Point3D;
import bricksnspace.ldrawlib.LDrawException;

import com.jogamp.common.nio.Buffers;


/**
 * @author Mario Pascucci
 *
 */
public class LDrawGLDisplay implements GLEventListener, MouseListener, MouseMotionListener, MouseWheelListener {

	static { GLProfile.initSingleton(); }
		
	private GLCanvas canvas;
	private GL2 currentGL2;
	
	private boolean wireframe = true;
	private boolean polygon = true;
	private boolean lighting = true;
	private boolean perspective = false;
	private volatile boolean bufferOk = false;
	private boolean selection = false; 
	private boolean autoRedraw = true;
	private float zoomFactor = 1.0f;
	private float offsetx = 0f;
	private float offsety = 0f;
	private float offsetz = 0f;
	private Matrix3D viewMatrix = new Matrix3D();
	private float[] projectionMatrix = new float[16];
	private int[] viewPort = new int[4];
	
	public static final int VERTEX = 0;
	public static final int VERTEX_COLOR = 1;
	public static final int LINE = 2;
	public static final int LINE_COLOR = 3;
	
	public static final int ROT_LDD = 1;
	public static final int ROT_STD = 0;
	
	private int rotMode = ROT_LDD;

	private Map<Integer,LDRenderedPart> model = new HashMap<Integer,LDRenderedPart>();
	private Map<Integer,Gadget3D> gadgets = new HashMap<Integer,Gadget3D>();
	private GLContext glcontext = null;
	private GLU glu = null;
	private LinkedList<HandlingListener> handlingListeners = new LinkedList<HandlingListener>();
	private long drawingTime;
	private int centerx;
	private int centery;
	private boolean mouseTracking = true;
	private boolean partHover = false;
	private static boolean antialias = false;
	private int partHoverId = 0;

	// FBO for OpenGL color selection/pick
	// framebuffer
	private int[] fbo = new int[1];
	// render buffer
	private int[] rbo = new int[2];
	private static final int COLOR_RB = 0;
	private static final int DEPTH_RB = 1;
	
	// selection by window
	int selcorner1x,  selcorner1y, selcorner2x, selcorner2y;
	private volatile Gadget3D selWindow = null;
	

	private enum QActions { ADD,DEL };
	
	private class QueueAction<T> {
		
		QActions action;
		T part;
		int id;
		
		private QueueAction(T p) {
			action = QActions.ADD;
			part = p;
			id = 0;
		}
		
		
		private QueueAction(int i) {
			action = QActions.DEL;
			part = null;
			id = i;
		}
		
	}
	
	
	// insert/remove queue operation
	BlockingDeque<QueueAction<Gadget3D>> gadgetAddQueue = 
			new LinkedBlockingDeque<QueueAction<Gadget3D>>();
	BlockingDeque<QueueAction<LDRenderedPart>> partAddQueue = 
			new LinkedBlockingDeque<QueueAction<LDRenderedPart>>();
//	BlockingDeque<Integer> gadgetRemoveQueue = new LinkedBlockingDeque<Integer>();
//	BlockingDeque<Integer> partRemoveQueue = new LinkedBlockingDeque<Integer>();

	
	
	
	public LDrawGLDisplay() throws LDrawException
	{
		try {
			GLProfile glp = GLProfile.getDefault();
			if (!glp.isGL2()) {
				throw new LDrawException(LDrawGLDisplay.class,"Your graphic card doesn't support requested OpenGL level.\nYour GL is:"+glp.getName());
			}
			GLCapabilities caps = new GLCapabilities(glp);
			if (isAntialias()) {
				caps.setSampleBuffers(true);
				caps.setNumSamples(8);
			}
			canvas = new GLCanvas(caps);
			canvas.setAutoSwapBufferMode(false);
			canvas.addGLEventListener(this);
		}
		catch (GLException e) {
			throw new LDrawException(LDrawGLDisplay.class, "OpenGL error", e);
		}
	}

	
	public Component getCanvas() {
		return canvas;
	}

	
	public void setWireframe(boolean wireframe) {

		this.wireframe = wireframe;
		if (autoRedraw)
			canvas.repaint();	
	}


	public void setPolygon(boolean polygon) {
		this.polygon = polygon;
		if (autoRedraw)
			canvas.repaint();	
	}


	public void setLighting(boolean lighting) {
		this.lighting = lighting;
		if (autoRedraw)
			canvas.repaint();	
	}


	public void setPerspective(boolean perspective) {
		this.perspective = perspective;
		if (autoRedraw)
			canvas.repaint();	
	}
	
	

	/**
	 * gets rotation mode, using ROT_LDD or ROT_STD constants
	 * ROT_LDD: rotation is like LDD, around model Y axis.
	 * ROT_STD: rotation is along full 3D axes. 
	 * @return rotation mode
	 */
	public int getRotMode() {
		return rotMode;
	}


	/**
	 * sets rotation mode, using ROT_LDD or ROT_STD constants
	 * ROT_LDD: rotation is like LDD, around model Y axis.
	 * ROT_STD: rotation is along full 3D axes. 
	 * @param rotMode rotation mode constant
	 */
	public void setRotMode(int rotMode) {
		this.rotMode = rotMode;
	}


	public void setZoomFactor(float zoom) {
		if (zoom <= 0.001) {
			throw new IllegalArgumentException("Zoom multiplier cannot be too low or negative");
		}
		zoomFactor *= zoom;
		if (autoRedraw)
			canvas.repaint();	
	}
	
	
	public void setZoom(float zoom) {
		
		zoomFactor = zoom;
		if (autoRedraw)
			canvas.repaint();	
	}
	
	
	public void resetZoom() {
		
		zoomFactor = 1f;
		if (autoRedraw)
			canvas.repaint();	
	}


	public void setOffsetx(float offsetx) {
		viewMatrix = viewMatrix.moveTo(-offsetx, 0, 0);
		if (autoRedraw)
			canvas.repaint();	
	}


	public void setOffsety(float offsety) {
		viewMatrix = viewMatrix.moveTo(0,-offsety, 0);
		if (autoRedraw)
			canvas.repaint();	
	}



	public void setOrigin(float x, float y, float z) {
		
		float f[] = viewMatrix.transformPoint(x, y, z);
		viewMatrix = viewMatrix.moveTo(-f[0], -f[1], -f[2]);
		if (autoRedraw)
			canvas.repaint();	
	}

	
	public boolean autoRedrawEnabled() {
		return autoRedraw;
	}


	public void enableAutoRedraw() {
		autoRedraw = true;
	}


	public void disableAutoRedraw() {
		autoRedraw = false;
	}

	
	public void enableHover() {
		partHover = true;
	}
	

	public void disableHover() {
		partHover = false;
	}
	

	public static boolean isAntialias() {
		return antialias;
	}


	public static void setAntialias(boolean enable) {
		LDrawGLDisplay.antialias = enable;
	}


	public void enableMouseTracking() {

		mouseTracking  = true;
		canvas.addMouseMotionListener(this);
		canvas.addMouseListener(this);
		canvas.addMouseWheelListener(this);
	}

	
	public void disableMouseTracking() {
		
		mouseTracking = false;
		canvas.removeMouseMotionListener(this);
		canvas.removeMouseListener(this);
		canvas.removeMouseWheelListener(this);
	}

	
	public boolean isMouseTracking() {
		
		return mouseTracking;
	}
	
	
	
	/**
	 * Rotate view around view X axis
	 * @param anglex angle in degree
	 */
	public void rotateX(float anglex) {
		viewMatrix = viewMatrix.rotateX((float)(anglex*Math.PI/180));
		if (autoRedraw)
			canvas.repaint();	
	}


	/**
	 * Rotate view around view Y axis
	 * @param angley angle in degree
	 */
	public void rotateY(float angley) {
		if (rotMode == ROT_LDD) {
			Point3D o = new Point3D(viewMatrix.transformPoint(0, 0, 0));
			Point3D y1 = new Point3D(viewMatrix.transformPoint(0, 1, 0));
			viewMatrix = viewMatrix.transform(JSimpleGeom.axisRotMatrix(o, y1, angley));
		}
		else {
			viewMatrix = viewMatrix.rotateY((float)(angley*Math.PI/180));
		}
		if (autoRedraw)
			canvas.repaint();	
	}


	public void resetView() {
		
		viewMatrix = new Matrix3D();	
		if (autoRedraw)
			canvas.repaint();	
	}
	
	


	/**
	 * Add or replace a rendered part from a model
	 * Updates GL context VA buffers
	 * @param p rendered part to add (or replace if ID is the same)
	 */
	public void addRenderedPart(LDRenderedPart p) {
		partAddQueue.add(new QueueAction<LDRenderedPart>(p));
		if (autoRedraw)
			canvas.repaint();
	}
	
	
	/**
	 * Remove a rendered part from a model
	 * Updates GL context VA buffers
	 * @param p part to remove
	 */
	public void delRenderedPart(int id) {
		
		//System.out.println("d:"+id);
		partAddQueue.add(new QueueAction<LDRenderedPart>(id));
		if (autoRedraw)
			canvas.repaint();
	}
	
	
	public synchronized void clearAllParts() {
		
		for (int i:model.keySet()) {
			partAddQueue.add(new QueueAction<LDRenderedPart>(i));
		}
	}
	

	
	public LDRenderedPart getPart(int id) {
		
		return model.get(id);
	}
	
	


	/////////////////////////////////
	// Gadgets
	/////////////////////////////////

	public synchronized void addGadget(Gadget3D g) {
		
		gadgetAddQueue.add(new QueueAction<Gadget3D>(g));
		if (autoRedraw)
			canvas.repaint();
		
	}
	
	
	public synchronized void removeGadget(Integer g) {
		gadgetAddQueue.add(new QueueAction<Gadget3D>(g));
		if (autoRedraw)
			canvas.repaint();
	}
	
	
	
	/**
	 * removes all gadgets where ID >= 0 
	 * excluding drawing helper gadgets (grid, axis, pointer...)
	 */
	public synchronized void clearGadgets() {

		for (Integer g : gadgets.keySet()) {
			if (g >= 0)
				gadgetAddQueue.add(new QueueAction<Gadget3D>(g));
		}
		// removes adding gadgets in queue not already added
		Iterator<QueueAction<Gadget3D>> c = gadgetAddQueue.iterator();
		while (c.hasNext()) {
			QueueAction<Gadget3D> a = c.next();
			if (a.action == QActions.ADD && a.part.getId() >= 0) {
				c.remove();
			}
		}
	}
	
	
	
	public Gadget3D getGadget(int id) {
		return gadgets.get(id);
	}
	
	
	public void hideGadget(Gadget3D g) {
		
		Gadget3D p = gadgets.get(g.getId());
		if (p != null) {
			p.hide();
			if (autoRedraw)
				canvas.repaint();
		}
	}
	
	

	public void showGadget(Gadget3D g) {
		
		Gadget3D p = gadgets.get(g.getId());
		if (p != null) {
			p.show();
			if (autoRedraw)
				canvas.repaint();
		}
	}
	
	
	
	public void selectGadget(int id) {
		gadgets.get(id).select();
	}
	
	
	
	public void unselectGadget(int id) {
		gadgets.get(id).unSelect();
	}
	


	/**
	 * checks available OpenGL profile
	 */
	public static String checkGL() {
		
		GLProfile glp = GLProfile.getDefault();
		return glp.getName();
	}
	
	
	//////////////////////////////////////
	// selection and selection listeners
	//////////////////////////////////////
	
	public boolean isSelectionEnabled() {
		return selection;
	}


	public void enableSelection(boolean selection) {
		this.selection = selection;
	}

	
	public void addPickListener(HandlingListener p) {
		
		if (p == null) 
			throw new NullPointerException("[LDrawGLDisplay] Cannot use a null SelectionListener");
		// avoid duplicate insert
		if (handlingListeners.contains(p))
			return;
		handlingListeners.add(p);
	}


	
	public void removePickListener(HandlingListener p) {
		
		handlingListeners.remove(p);
	}
	
	
	/**
	 * Add a gadget to GL context VA buffers
	 * 
	 * context must be already set before call
	 *  
	 * @param p gadget to add
	 */
	private void addGadgetVA(Gadget3D p, GL2 gl2) {
		
		int[] vboArrayNames = new int[2];

		// gets and save array buffer names
        if (p.getLineVertexCount() > 0) {
	        gl2.glGenBuffers( 2, vboArrayNames, 0 );
	        p.setLineName(vboArrayNames[VERTEX]);
	        p.setLineColorName(vboArrayNames[VERTEX_COLOR]);
	        gl2.glBindBuffer( GL2.GL_ARRAY_BUFFER, p.getLineName());
	        gl2.glBufferData( GL2.GL_ARRAY_BUFFER,
	                          p.getWireFrameVBO().length * Buffers.SIZEOF_FLOAT,
	                          null,
	                          GL2.GL_STATIC_DRAW );
	        ByteBuffer bytebuffer = gl2.glMapBuffer( GL2.GL_ARRAY_BUFFER, GL2.GL_WRITE_ONLY );
	        FloatBuffer vertexbuffer = bytebuffer.order( ByteOrder.nativeOrder() ).asFloatBuffer();
	        vertexbuffer.put(p.getWireFrameVBO());
	        gl2.glUnmapBuffer( GL2.GL_ARRAY_BUFFER );
	        // store line colors
	        gl2.glBindBuffer( GL2.GL_ARRAY_BUFFER, p.getLineColorName());
	        gl2.glBufferData( GL2.GL_ARRAY_BUFFER,
	                          p.getWireColorVa().length * Buffers.SIZEOF_BYTE,
	                          null,
	                          GL2.GL_STATIC_DRAW );
	        bytebuffer = gl2.glMapBuffer( GL2.GL_ARRAY_BUFFER, GL2.GL_WRITE_ONLY );
	        ByteBuffer lineColorBuffer = bytebuffer.order( ByteOrder.nativeOrder() );
	        lineColorBuffer.put(p.getWireColorVa());
	        gl2.glUnmapBuffer( GL2.GL_ARRAY_BUFFER );
        }
	}
	

	/**
	 * Remove VA buffers of a single gadget
	 * GL context must be already set before call
	 * @param p gadget to remove
	 */
	private void delGadgetVA(Gadget3D p, GL2 gl2) {
		
		if (p.getLineVertexCount() > 0) {
			gl2.glDeleteBuffers(2, new int[] {p.getLineName(),p.getLineColorName()},0);
		}
	}

	
	
	/**
	 * Add a rendered part to GL context VA buffers
	 * 
	 * context must be already set before call
	 *  
	 * @param p rendered part
	 */
	private void addRenderedPartVA(LDRenderedPart p, GL2 gl2) {
		
		int[] vboArrayNames = new int[2];

		// gets and save array buffer names
		if (p.getTriangleVertexCount() > 0) {
	        gl2.glGenBuffers( 2, vboArrayNames, 0 );
	        p.setTriangleName(vboArrayNames[VERTEX]);
	        p.setTriangleColorName(vboArrayNames[VERTEX_COLOR]);
	        // store vertex coords
	        gl2.glBindBuffer( GL2.GL_ARRAY_BUFFER, p.getTriangleName());
	        gl2.glBufferData( GL2.GL_ARRAY_BUFFER,
	                          p.getTrianglesVBO().length * Buffers.SIZEOF_FLOAT,
	                          null,
	                          GL2.GL_STATIC_DRAW );
	        ByteBuffer bytebuffer = gl2.glMapBuffer( GL2.GL_ARRAY_BUFFER, GL2.GL_WRITE_ONLY );
	        FloatBuffer vertexbuffer = bytebuffer.order( ByteOrder.nativeOrder() ).asFloatBuffer();
	        vertexbuffer.put(p.getTrianglesVBO());
	        gl2.glUnmapBuffer( GL2.GL_ARRAY_BUFFER );
	        // store vertex colors
	        gl2.glBindBuffer( GL2.GL_ARRAY_BUFFER, p.getTriangleColorName() );
	        gl2.glBufferData( GL2.GL_ARRAY_BUFFER,
	                          p.getTriangleColorVA().length * Buffers.SIZEOF_BYTE,
	                          null,
	                          GL2.GL_STATIC_DRAW );
	        bytebuffer = gl2.glMapBuffer( GL2.GL_ARRAY_BUFFER, GL2.GL_WRITE_ONLY );
	        ByteBuffer vertexColorBuffer = bytebuffer.order( ByteOrder.nativeOrder() );
	        vertexColorBuffer.put(p.getTriangleColorVA());
	        gl2.glUnmapBuffer( GL2.GL_ARRAY_BUFFER );
		}
        // store line coords
        if (p.getLineVertexCount() > 0) {
	        gl2.glGenBuffers( 2, vboArrayNames, 0 );
	        p.setLineName(vboArrayNames[VERTEX]);
	        p.setLineColorName(vboArrayNames[VERTEX_COLOR]);
	        gl2.glBindBuffer( GL2.GL_ARRAY_BUFFER, p.getLineName());
	        gl2.glBufferData( GL2.GL_ARRAY_BUFFER,
	                          p.getWireFrameVBO().length * Buffers.SIZEOF_FLOAT,
	                          null,
	                          GL2.GL_STATIC_DRAW );
	        ByteBuffer bytebuffer = gl2.glMapBuffer( GL2.GL_ARRAY_BUFFER, GL2.GL_WRITE_ONLY );
	        FloatBuffer vertexbuffer = bytebuffer.order( ByteOrder.nativeOrder() ).asFloatBuffer();
	        vertexbuffer.put(p.getWireFrameVBO());
	        gl2.glUnmapBuffer( GL2.GL_ARRAY_BUFFER );
	        // store line colors
	        gl2.glBindBuffer( GL2.GL_ARRAY_BUFFER, p.getLineColorName());
	        gl2.glBufferData( GL2.GL_ARRAY_BUFFER,
	                          p.getWireColorVa().length * Buffers.SIZEOF_BYTE,
	                          null,
	                          GL2.GL_STATIC_DRAW );
	        bytebuffer = gl2.glMapBuffer( GL2.GL_ARRAY_BUFFER, GL2.GL_WRITE_ONLY );
	        ByteBuffer lineColorBuffer = bytebuffer.order( ByteOrder.nativeOrder() );
	        lineColorBuffer.put(p.getWireColorVa());
	        gl2.glUnmapBuffer( GL2.GL_ARRAY_BUFFER );
        }
        if (p.getAuxLineVertexCount() > 0) {
	        gl2.glGenBuffers( 2, vboArrayNames, 0 );
	        p.setAuxLineName(vboArrayNames[VERTEX]);
	        p.setAuxLineColorName(vboArrayNames[VERTEX_COLOR]);
	        gl2.glBindBuffer( GL2.GL_ARRAY_BUFFER, p.getAuxLineName());
	        gl2.glBufferData( GL2.GL_ARRAY_BUFFER,
	                          p.getAuxWireFrameVBO().length * Buffers.SIZEOF_FLOAT,
	                          null,
	                          GL2.GL_STATIC_DRAW );
	        ByteBuffer bytebuffer = gl2.glMapBuffer( GL2.GL_ARRAY_BUFFER, GL2.GL_WRITE_ONLY );
	        FloatBuffer vertexbuffer = bytebuffer.order( ByteOrder.nativeOrder() ).asFloatBuffer();
	        vertexbuffer.put(p.getAuxWireFrameVBO());
	        gl2.glUnmapBuffer( GL2.GL_ARRAY_BUFFER );
	        // store line colors
	        gl2.glBindBuffer( GL2.GL_ARRAY_BUFFER, p.getAuxLineColorName());
	        gl2.glBufferData( GL2.GL_ARRAY_BUFFER,
	                          p.getAuxWireColorVa().length * Buffers.SIZEOF_BYTE,
	                          null,
	                          GL2.GL_STATIC_DRAW );
	        bytebuffer = gl2.glMapBuffer( GL2.GL_ARRAY_BUFFER, GL2.GL_WRITE_ONLY );
	        ByteBuffer lineColorBuffer = bytebuffer.order( ByteOrder.nativeOrder() );
	        lineColorBuffer.put(p.getAuxWireColorVa());
	        gl2.glUnmapBuffer( GL2.GL_ARRAY_BUFFER );
        }
        if (p.getBboxCount() > 0) {
	        gl2.glGenBuffers( 1, vboArrayNames, 0 );
	        p.setBboxName(vboArrayNames[VERTEX]);
	        gl2.glBindBuffer( GL2.GL_ARRAY_BUFFER, p.getBboxName());
	        gl2.glBufferData( GL2.GL_ARRAY_BUFFER,
	                          p.getBboxVBO().length * Buffers.SIZEOF_FLOAT,
	                          null,
	                          GL2.GL_STATIC_DRAW );
	        ByteBuffer bytebuffer = gl2.glMapBuffer( GL2.GL_ARRAY_BUFFER, GL2.GL_WRITE_ONLY );
	        FloatBuffer vertexbuffer = bytebuffer.order( ByteOrder.nativeOrder() ).asFloatBuffer();
	        vertexbuffer.put(p.getBboxVBO());
	        gl2.glUnmapBuffer( GL2.GL_ARRAY_BUFFER );
        	
        }

	}
	

	/**
	 * Remove VA buffers of a single rendered part
	 * GL context must be already set before call
	 * @param p rendered part to remove
	 */
	private void delRenderedPartVA(LDRenderedPart p, GL2 gl2) {
		
		if (p.getLineVertexCount() > 0) {
			gl2.glDeleteBuffers(2, new int[] {p.getLineName(),p.getLineColorName()},0);
		}
		if (p.getAuxLineVertexCount() > 0) {
			gl2.glDeleteBuffers(2, new int[] {p.getAuxLineName(),p.getAuxLineColorName()},0);
		}
		if (p.getTriangleVertexCount() > 0) {
			gl2.glDeleteBuffers(2, new int[] {p.getTriangleName(),p.getTriangleColorName()},0);
		}
		if (p.getBboxCount() > 0) {
			gl2.glDeleteBuffers(1, new int[] {p.getBboxName()},0);
		}
	}

	
	

	public long getDrawTimeMs() {
		return drawingTime;
	}
	
	
	
	
	private void renderScene(GL2 gl2, GLU glu, int width, int height) {

		gl2.glMatrixMode(GL2.GL_PROJECTION);
        gl2.glLoadIdentity();  // Reset The Projection Matrix
        //
        //GLU glu = new GLU();
        //int width = drawable.getSurfaceWidth();
        //int height = drawable.getSurfaceHeight() == 0 ? 1 : drawable.getSurfaceHeight();
        height = height == 0 ? 1 : height;
        if (perspective) {
        	glu.gluPerspective(40f, (float)width/height, 1f, 10000f);
        	glu.gluLookAt(0, 0, -800*zoomFactor, 0, 0, 0, 0, -1, 0);
        }
        else {
        	gl2.glOrthof(-width*zoomFactor/2,width*zoomFactor/2,-height*zoomFactor/2,height*zoomFactor/2, -2000, 2000);
        	glu.gluLookAt(0, 0, -500, 0, 0, 0, 0, -1, 0);
        }
        gl2.glViewport( 0, 0, width, height );
        
        gl2.glMatrixMode( GL2.GL_MODELVIEW );
        gl2.glLoadIdentity();
        gl2.glMultMatrixf(viewMatrix.getAsOpenGLMatrix(), 0);
        gl2.glTranslatef(-offsetx, -offsety, -offsetz);
        
        if (antialias) {
        	gl2.glEnable(GL2.GL_LINE_SMOOTH);
        	gl2.glHint(GL2.GL_LINE_SMOOTH_HINT, GL2.GL_FASTEST);
        }
        gl2.glClearColor(0.95f, 0.95f, 0.95f, 1f);    // This Will Clear The Background Color
        gl2.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT |
        		GL2.GL_ACCUM_BUFFER_BIT);    // Clear The Screen And The Depth Buffer
        gl2.glColorMaterial( GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT_AND_DIFFUSE );
        gl2.glDisable(GL2.GL_LIGHTING);
        
        if (bufferOk) {
            gl2.glEnableClientState( GL2.GL_VERTEX_ARRAY );
            
            // renderings
            
            // renders polygons
            if (polygon) {
                if (lighting)
                	gl2.glEnable(GL2.GL_LIGHTING);
	            gl2.glEnableClientState(GL2.GL_COLOR_ARRAY);
	            gl2.glEnableClientState(GL2.GL_NORMAL_ARRAY);
		        for (LDRenderedPart p : model.values()) {
		        	if (p.isHidden() || p.isDimmed()) {
		        		continue;
		        	}
		        	if (p.getTriangleVertexCount() > 0) {
			            gl2.glBindBuffer( GL2.GL_ARRAY_BUFFER, p.getTriangleName() );
			            gl2.glVertexPointer( 3, GL2.GL_FLOAT, 6 * Buffers.SIZEOF_FLOAT, 0 );
			            gl2.glNormalPointer(GL2.GL_FLOAT,6 * Buffers.SIZEOF_FLOAT ,3 * Buffers.SIZEOF_FLOAT);
			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getTriangleColorName());
			            gl2.glColorPointer( 4, GL2.GL_UNSIGNED_BYTE, 4 * Buffers.SIZEOF_BYTE, 0 );
			            gl2.glDrawArrays( GL2.GL_TRIANGLES, 0, p.getTriangleVertexCount() );
			            gl2.glBindBuffer( GL2.GL_ARRAY_BUFFER, 0 );
		        	}

		        }
	            gl2.glDisableClientState( GL2.GL_NORMAL_ARRAY );	
	            gl2.glDisable(GL2.GL_LIGHTING);
	            gl2.glDisableClientState( GL2.GL_COLOR_ARRAY );
	        }

            // renders edges
            if (wireframe) {
	            gl2.glEnableClientState( GL2.GL_COLOR_ARRAY );
	            gl2.glLineWidth(1f);
		        for (LDRenderedPart p : model.values()) {
		        	if (p.isHidden() || p.isDimmed() || p.isHighLighted()) {
		        		continue;
		        	}
		        	if (LDRenderedPart.isBoundingSelect() || !p.isSelected()) {
		        		if (p.getLineVertexCount() > 0) {
				            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getLineName());
				            gl2.glVertexPointer( 3, GL2.GL_FLOAT, 3 * Buffers.SIZEOF_FLOAT, 0 );
				            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getLineColorName());
				            gl2.glColorPointer( 4, GL2.GL_UNSIGNED_BYTE, 4 * Buffers.SIZEOF_BYTE, 0 );
				            gl2.glDrawArrays( GL2.GL_LINES, 0, p.getLineVertexCount() );
				            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
		        		}
			            if (!polygon && p.getAuxLineVertexCount() > 0) {
			            	// display aux lines only if polygons are hidden
				            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getAuxLineName());
				            gl2.glVertexPointer( 3, GL2.GL_FLOAT, 3 * Buffers.SIZEOF_FLOAT, 0 );
				            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getAuxLineColorName());
				            gl2.glColorPointer( 4, GL2.GL_UNSIGNED_BYTE, 4 * Buffers.SIZEOF_BYTE, 0 );
				            gl2.glDrawArrays( GL2.GL_LINES, 0, p.getAuxLineVertexCount() );
				            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
			            }
		            }
		        }
	            gl2.glDisableClientState( GL2.GL_COLOR_ARRAY );
            }
            
            // renders highlights
            gl2.glLineWidth(3f);
	        for (LDRenderedPart p : model.values()) {
	        	if (p.isHidden()) {
	        		continue;
	        	}
	            if (p.isHighLighted()) {
	            	gl2.glColor4f(0.95f, 0.4f, 0.4f,1f);
	            	// if is bounding is selected, use edges to highlight
		            if (LDRenderedPart.isBoundingSelect()) {
			            if (p.getLineVertexCount() > 0) {
				            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getLineName());
				            gl2.glVertexPointer( 3, GL2.GL_FLOAT, 3 * Buffers.SIZEOF_FLOAT, 0 );
				            gl2.glDrawArrays( GL2.GL_LINES, 0, p.getLineVertexCount() );
				            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
			            }
		            }	// bounding disabled, use bounding boxes to highlight
		            else {
			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getBboxName());
			            gl2.glVertexPointer( 3, GL2.GL_FLOAT, 3 * Buffers.SIZEOF_FLOAT, 0 );
			            gl2.glDrawArrays( GL2.GL_LINES, 0, p.getBboxCount() );
			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);		            	
		            }
	            }
//	        	if (p.getId() == partHoverId && partHover) {
//		            gl2.glColor4f(0.9f, 0.3f, 0.9f,1f);
//		            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getBboxName());
//		            gl2.glVertexPointer( 3, GL2.GL_FLOAT, 3 * Buffers.SIZEOF_FLOAT, 0 );
//		            gl2.glDrawArrays( GL2.GL_LINES, 0, p.getBboxCount() );
//		            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
//	        	}
	            if (p.isConnected()) {
		            gl2.glColor4f(1f, 0.7f, 0.6f,1f);
		            if (LDRenderedPart.isBoundingSelect()) {
		            	// selection is by bounding box
			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getBboxName());
			            gl2.glVertexPointer( 3, GL2.GL_FLOAT, 3 * Buffers.SIZEOF_FLOAT, 0 );
			            gl2.glDrawArrays( GL2.GL_LINES, 0, p.getBboxCount() );
			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
		            }
	            }
	            else if (p.isSelected()) {
		            gl2.glColor4f(0.6f, 1f, 0.5f,1f);
		            if (LDRenderedPart.isBoundingSelect()) {
		            	// selection is by bounding box
			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getBboxName());
			            gl2.glVertexPointer( 3, GL2.GL_FLOAT, 3 * Buffers.SIZEOF_FLOAT, 0 );
			            gl2.glDrawArrays( GL2.GL_LINES, 0, p.getBboxCount() );
			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
		            }
		            else {
		            	// selection is by edges and aux lines
			            if (p.getLineVertexCount() > 0) {
				            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getLineName());
				            gl2.glVertexPointer( 3, GL2.GL_FLOAT, 3 * Buffers.SIZEOF_FLOAT, 0 );
				            gl2.glDrawArrays( GL2.GL_LINES, 0, p.getLineVertexCount() );
				            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
			            }
			            if (p.getAuxLineVertexCount() > 0) {
				            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getAuxLineName());
				            gl2.glVertexPointer( 3, GL2.GL_FLOAT, 3 * Buffers.SIZEOF_FLOAT, 0 );
				            gl2.glDrawArrays( GL2.GL_LINES, 0, p.getAuxLineVertexCount() );
				            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
			            }
		            }
	        	}
//	            gl2.glEnableClientState( GL2.GL_COLOR_ARRAY );
//	            gl2.glLineWidth(1f);
//	        	if (wireframe && !p.isHighLighted() && !p.isDimmed() &&
//	        			(LDRenderedPart.isBoundingSelect() || !p.isSelected())) {
//	        		if (p.getLineVertexCount() > 0) {
//			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getLineName());
//			            gl2.glVertexPointer( 3, GL2.GL_FLOAT, 3 * Buffers.SIZEOF_FLOAT, 0 );
//			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getLineColorName());
//			            gl2.glColorPointer( 4, GL2.GL_UNSIGNED_BYTE, 4 * Buffers.SIZEOF_BYTE, 0 );
//			            gl2.glDrawArrays( GL2.GL_LINES, 0, p.getLineVertexCount() );
//			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
//	        		}
//		            if (!polygon && p.getAuxLineVertexCount() > 0) {
//		            	// display aux lines only if polygons are hidden
//			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getAuxLineName());
//			            gl2.glVertexPointer( 3, GL2.GL_FLOAT, 3 * Buffers.SIZEOF_FLOAT, 0 );
//			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getAuxLineColorName());
//			            gl2.glColorPointer( 4, GL2.GL_UNSIGNED_BYTE, 4 * Buffers.SIZEOF_BYTE, 0 );
//			            gl2.glDrawArrays( GL2.GL_LINES, 0, p.getAuxLineVertexCount() );
//			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
//		            }
//	            }
//	            gl2.glDisableClientState( GL2.GL_COLOR_ARRAY );
	        }

	        // rendering gadgets
            gl2.glEnableClientState( GL2.GL_COLOR_ARRAY );
            gl2.glLineWidth(1.5f);
	        for (Gadget3D p : gadgets.values()) {
	            // draw gadgets (axis, grids, pointers, ...)
	        	// gadgets are only lines without lighting and edges
	        	if (p.isHidden())
	        		continue;
	            if (p.getLineVertexCount() > 0) {
	            	if (p.isSelected()) {
	    	            gl2.glDisableClientState( GL2.GL_COLOR_ARRAY );
	    	            gl2.glColor4f(0.6f, 1f, 0.5f,1f);
			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getLineName());
			            gl2.glVertexPointer( 3, GL2.GL_FLOAT, 3 * Buffers.SIZEOF_FLOAT, 0 );
			            gl2.glDrawArrays( GL2.GL_LINES, 0, p.getLineVertexCount() );
			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
			            gl2.glEnableClientState( GL2.GL_COLOR_ARRAY );
	            	}
	            	else {
			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getLineName());
			            gl2.glVertexPointer( 3, GL2.GL_FLOAT, 3 * Buffers.SIZEOF_FLOAT, 0 );
			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, p.getLineColorName());
			            gl2.glColorPointer( 4, GL2.GL_UNSIGNED_BYTE, 4 * Buffers.SIZEOF_BYTE, 0 );
			            gl2.glDrawArrays( GL2.GL_LINES, 0, p.getLineVertexCount() );
			            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
	            	}
	            }
	        }
        }
        gl2.glDisableClientState( GL2.GL_COLOR_ARRAY );
        
        // hover part rendering
        LDRenderedPart h = model.get(partHoverId);
    	if (partHover && h != null) {
    		gl2.glLineWidth(3f);
            gl2.glColor4f(0.9f, 0.3f, 0.9f,1f);
            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, h.getBboxName());
            gl2.glVertexPointer( 3, GL2.GL_FLOAT, 3 * Buffers.SIZEOF_FLOAT, 0 );
            gl2.glDrawArrays( GL2.GL_LINES, 0, h.getBboxCount() );
            gl2.glBindBuffer(GL2.GL_ARRAY_BUFFER, 0);
    	}
    	
    	gl2.glLineWidth(1f);
        
    	// dimmed parts rendering
        // MUST be last to allow blending works with transparency
        if (lighting)
        	gl2.glEnable(GL2.GL_LIGHTING);
        gl2.glEnableClientState(GL2.GL_NORMAL_ARRAY);
        for (LDRenderedPart p : model.values()) {
        	if (p.isDimmed() && !p.isHidden()) {
    			gl2.glColor4f(0.9f, 0.9f, 0.9f, 0.2f);
	            gl2.glBindBuffer( GL2.GL_ARRAY_BUFFER, p.getTriangleName() );
	            gl2.glVertexPointer( 3, GL2.GL_FLOAT, 6 * Buffers.SIZEOF_FLOAT, 0 );
	            gl2.glNormalPointer(GL2.GL_FLOAT,6 * Buffers.SIZEOF_FLOAT ,3 * Buffers.SIZEOF_FLOAT);
	            gl2.glDrawArrays( GL2.GL_TRIANGLES, 0, p.getTriangleVertexCount() );
	            gl2.glBindBuffer( GL2.GL_ARRAY_BUFFER, 0 );
        	}
        }
        gl2.glDisableClientState( GL2.GL_NORMAL_ARRAY );	
        gl2.glDisable(GL2.GL_LIGHTING);	        		
        
        
        gl2.glDisableClientState( GL2.GL_VERTEX_ARRAY );

	}
	
	
	
	private void updateSceneObjects(GL2 gl2) {
		
        while (!gadgetAddQueue.isEmpty()) {
        	QueueAction<Gadget3D> a = gadgetAddQueue.poll();
        	if (a.action == QActions.ADD) {
	        	//Gadget3D rm = gadgets.get(r.getId());
	        	Gadget3D rm = gadgets.put(a.part.getId(),a.part);
	        	if (rm != null) {
	        		// remove old gadget
	        		delGadgetVA(rm, gl2);
	            	//System.out.println("Da:"+rm.getId());
	        		//gadgets.remove(rm.getId());
	        	}
            	//System.out.println("A:"+a.part.getId());
	        	addGadgetVA(a.part, gl2);
        	}
        	else {
            	Gadget3D rm = gadgets.get(a.id);
            	if (rm == null)
            		continue;
            	//System.out.println("D:"+a.id);
            	delGadgetVA(rm, gl2);
            	gadgets.remove(a.id);       		
        	}
        }
        while (!partAddQueue.isEmpty()) {
        	QueueAction<LDRenderedPart> a = partAddQueue.poll(); 
    		// add to GL context
        	if (a.action == QActions.ADD) {
	    		//System.out.println("a:"+a.part.getId()); //XX
	    		// add new part to model
	    		LDRenderedPart r = model.put(a.part.getId(),a.part);
	    		// if there was an old part with same id
	    		if (r != null) {
	    			// remove from GL context VA buffers
	    			delRenderedPartVA(r, gl2);
	    		}
	    		addRenderedPartVA(a.part, gl2);
        	}
        	else {
        		if (model.containsKey(a.id)) {
        			//System.out.println("r:"+a.id);  //XX
        			delRenderedPartVA(model.get(a.id), gl2);
        			model.remove(a.id);
        		}       		
        	}
        }

	}
	
	
	
	@Override
	public void display(GLAutoDrawable drawable) {
		
		long t0 = System.nanoTime();
		glcontext.makeCurrent();

        // first updates context with added and removed objects
//        while (!gadgetRemoveQueue.isEmpty()) {
//        	Integer r = gadgetRemoveQueue.poll(); 
//        	Gadget3D rm = gadgets.get(r);
//        	if (rm == null)
//        		continue;
//        	delGadgetVA(rm);
//        	gadgets.remove(r);
//        }
//        while (!partRemoveQueue.isEmpty()) {
//        	Integer r = partRemoveQueue.poll(); 
//    		if (model.containsKey(r)) {
//    			//System.out.println("r:"+r);
//    			delRenderedPartVA(model.get(r));
//    			model.remove(r);
//    		}
//        }
		updateSceneObjects(currentGL2);
//        while (!gadgetAddQueue.isEmpty()) {
//        	QueueAction<Gadget3D> a = gadgetAddQueue.poll();
//        	if (a.action == QActions.ADD) {
//	        	//Gadget3D rm = gadgets.get(r.getId());
//	        	Gadget3D rm = gadgets.put(a.part.getId(),a.part);
//	        	if (rm != null) {
//	        		// remove old gadget
//	        		delGadgetVA(rm);
//	            	//System.out.println("Da:"+rm.getId());
//	        		//gadgets.remove(rm.getId());
//	        	}
//            	//System.out.println("A:"+a.part.getId());
//	        	addGadgetVA(a.part);
//        	}
//        	else {
//            	Gadget3D rm = gadgets.get(a.id);
//            	if (rm == null)
//            		continue;
//            	//System.out.println("D:"+a.id);
//            	delGadgetVA(rm);
//            	gadgets.remove(a.id);       		
//        	}
//        }
//        while (!partAddQueue.isEmpty()) {
//        	QueueAction<LDRenderedPart> a = partAddQueue.poll(); 
//    		// add to GL context
//        	if (a.action == QActions.ADD) {
//	    		//System.out.println("a:"+r.getId());
//	    		// add new part to model
//	    		LDRenderedPart r = model.put(a.part.getId(),a.part);
//	    		// if there was an old part with same id
//	    		if (r != null) {
//	    			// remove from GL context VA buffers
//	    			delRenderedPartVA(r);
//	    		}
//	    		addRenderedPartVA(a.part);
//        	}
//        	else {
//        		if (model.containsKey(a.id)) {
//        			//System.out.println("r:"+r);
//        			delRenderedPartVA(model.get(a.id));
//        			model.remove(a.id);
//        		}       		
//        	}
//        }

        // now render
		renderScene(currentGL2, glu, drawable.getSurfaceWidth(), drawable.getSurfaceHeight());
        
		// display it
        //canvas.swapBuffers();
        
        // draw hidden scene for color-mode selection
        if (selection) {
        	// draw in back-buffer same model with color-mode selection
        	// do not draw gadgets and lines in this mode, so can't select
            if (bufferOk) {
            	// disable antialias and avoiding color color id  
            	currentGL2.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fbo[0]);
                currentGL2.glDisable(GL2.GL_MULTISAMPLE);
                currentGL2.glClearColor(0, 0, 0, 255);    // background is solid black
            	
            	currentGL2.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
	            currentGL2.glEnableClientState( GL2.GL_VERTEX_ARRAY );
    	        for (LDRenderedPart p : model.values()) {
    	        	currentGL2.glColor3ub(
    	        			(byte)((p.getId()&0xff0000)>>16), 
    	        			(byte) ((p.getId()&0xff00)>>8), 
    	        			(byte)(p.getId()&0xff));
    	        	if (p.getTriangleVertexCount() > 0 && !p.isHidden()) {
    		            currentGL2.glBindBuffer( GL2.GL_ARRAY_BUFFER, p.getTriangleName() );
    		            currentGL2.glVertexPointer( 3, GL2.GL_FLOAT, 6 * Buffers.SIZEOF_FLOAT, 0 );
    		            currentGL2.glDrawArrays( GL2.GL_TRIANGLES, 0, p.getTriangleVertexCount() );
    		            currentGL2.glBindBuffer( GL2.GL_ARRAY_BUFFER, 0 );
    	        	}
    	        }
	            currentGL2.glDisableClientState( GL2.GL_VERTEX_ARRAY );
	            currentGL2.glEnable(GL2.GL_MULTISAMPLE);
	            currentGL2.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
            }
        }
        canvas.swapBuffers();   // DB
        currentGL2.glGetFloatv(GL2.GL_PROJECTION_MATRIX, projectionMatrix, 0);
        currentGL2.glGetIntegerv(GL2.GL_VIEWPORT, viewPort, 0);
        glcontext.release();
        drawingTime = System.nanoTime()-t0;
        //System.out.println(drawingTime);    // DB
        int glerror = currentGL2.glGetError(); 
        if (glerror != 0)
        	System.out.println("[LDrawGLDisplay] " + Integer.toHexString(glerror));
	}
	

	
	@Override
	public void dispose(GLAutoDrawable drawable) { 
		
		bufferOk = false;
		disableMouseTracking();
		if (model.size() == 0)
			return;
		glcontext.makeCurrent();
		for (LDRenderedPart p : model.values()) {
			delRenderedPartVA(p, currentGL2);
		}
		glcontext.release();
		model.clear();
	}

	
	
	private void initScene(GL2 gl2) {
		
		gl2.glMatrixMode(GL2.GL_MODELVIEW);
        gl2.glLightfv(GL2.GL_LIGHT1, GL2.GL_POSITION, new float[] {5f,-9f,-10f,0f}, 0);
        gl2.glLightfv(GL2.GL_LIGHT1, GL2.GL_AMBIENT,new float[] { 0.2f, 0.2f, 0.2f, 1f },0);
        //gl2.glLightfv(GL2.GL_LIGHT1, GL2.GL_SPECULAR, new float[] {0.9f,0.9f,0.9f,1f}, 0);
        gl2.glLightfv(GL2.GL_LIGHT1, GL2.GL_DIFFUSE,new float[] { 0.8f, 0.8f, 0.8f, 1f },0);        

        gl2.glEnable(GL2.GL_COLOR_MATERIAL);
        gl2.glColorMaterial( GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT_AND_DIFFUSE );
        gl2.glDisable(GL2.GL_LIGHT0);
        gl2.glEnable(GL2.GL_LIGHT1);
        gl2.glEnable(GL2.GL_LIGHTING);
        //gl2.glEnable(GL2.GL_SMOOTH);
        //gl2.glEnable(GL2.GL_MULTISAMPLE);
        
        // useless with GL_COLOR_MATERIAL enabled
        //gl2.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SPECULAR, new float[] { 0.5f, 0.5f, 0.5f, 1.0f },0);
        //gl2.glMaterialf(GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, 80f);
        gl2.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        gl2.glEnable(GL2.GL_BLEND); 
        gl2.glLineWidth(1f);
        gl2.glClearDepth(1.0);                    // Enables Clearing Of The Depth Buffer
        gl2.glDepthFunc(GL2.GL_LESS);                // The Type Of Depth Test To Do
        gl2.glEnable(GL2.GL_DEPTH_TEST);  // Enables Depth Testing

        gl2.glMatrixMode(GL2.GL_PROJECTION);
        gl2.glLoadIdentity();  // Reset The Projection Matrix
        if (isSelectionEnabled()) {
        	// generates framebuffer object names
    		gl2.glGenFramebuffers(1, fbo, 0);
    		gl2.glGenRenderbuffers(2, rbo, 0);
            createSelectionFBO();
        }

	}
	
	
	
	@Override
	public void init(GLAutoDrawable drawable) {
		
		glcontext = drawable.getContext();
		glcontext.makeCurrent();
		
		currentGL2 = drawable.getGL().getGL2();
		initScene(currentGL2);
        int glerror = currentGL2.glGetError(); 
        if (glerror != 0)
        	System.out.println("[LDrawGLInit] " + Integer.toHexString(glerror));
        glu = new GLU(); 
        glcontext.release();
        bufferOk = true;
		enableMouseTracking();
 	}

	
	
	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width,
			int height) {

		if (isSelectionEnabled()) {
			// reshape FBO and render buffers
			// render buffers don't need to be deleted to reshape
			currentGL2.glBindRenderbuffer(GL2.GL_RENDERBUFFER, rbo[DEPTH_RB]);
			currentGL2.glRenderbufferStorage(GL2.GL_RENDERBUFFER, GL2.GL_DEPTH_COMPONENT, 
					canvas.getWidth(), canvas.getHeight());
				
			currentGL2.glBindRenderbuffer(GL2.GL_RENDERBUFFER, rbo[COLOR_RB]);
			currentGL2.glRenderbufferStorage(GL2.GL_RENDERBUFFER, GL2.GL_RGBA8, 
					canvas.getWidth(), canvas.getHeight());			
		}
		canvas.repaint(); 
	}

	
	
	
	/**
	 * Prepare a framebuffer object for color picking
	 * <p>
	 * from: 
	 * http://www.gamedev.net/topic/570321-glreadpixel-selection--driver-forced-anti-aliasing/
	 * http://www.gamedev.net/topic/570645-solved-fbo-with-glreadpixel/
	 * http://www.lighthouse3d.com/tutorials/opengl-short-tutorials/opengl_framebuffer_objects/
	 * 
	 */
	private void createSelectionFBO() {
		
		currentGL2.glBindRenderbuffer(GL2.GL_RENDERBUFFER, rbo[DEPTH_RB]);
		currentGL2.glRenderbufferStorage(GL2.GL_RENDERBUFFER, GL2.GL_DEPTH_COMPONENT, 
				canvas.getWidth(), canvas.getHeight());
			
		currentGL2.glBindRenderbuffer(GL2.GL_RENDERBUFFER, rbo[COLOR_RB]);
		currentGL2.glRenderbufferStorage(GL2.GL_RENDERBUFFER, GL2.GL_RGBA8, 
				canvas.getWidth(), canvas.getHeight());

		currentGL2.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fbo[0]);

		currentGL2.glFramebufferRenderbuffer(GL2.GL_FRAMEBUFFER, GL2.GL_COLOR_ATTACHMENT0, GL2.GL_RENDERBUFFER, rbo[COLOR_RB]);
		currentGL2.glFramebufferRenderbuffer(GL2.GL_FRAMEBUFFER, GL2.GL_DEPTH_ATTACHMENT, GL2.GL_RENDERBUFFER, rbo[DEPTH_RB]);
		
	}
	
	
	
	
	public void update() {
		
		canvas.repaint();
	}
	
	
	/*
	 * version without AWTGLReadBufferUtil that works on OSX Mavericks
	 */
	public BufferedImage getScreenShot() {
		
		glcontext.makeCurrent();
		int w = glcontext.getGLDrawable().getSurfaceWidth();
		int h = glcontext.getGLDrawable().getSurfaceHeight();
		renderScene(glcontext.getGL().getGL2(), glu, w, h);
		// with this setting on OSX Mavericks images are broken: use no alpha channel
//		BufferedImage image = new BufferedImage(w,h, BufferedImage.TYPE_INT_ARGB);
		BufferedImage image = new BufferedImage(w,h, BufferedImage.TYPE_3BYTE_BGR);
		DataBufferByte awfulBufferBack = (DataBufferByte) image.getRaster().getDataBuffer();
		Buffer b = ByteBuffer.wrap(awfulBufferBack.getData());
		glcontext.getGL().getGL2().glPixelStorei(GL2.GL_PACK_ALIGNMENT, 1);
		// broken images on OSX Mavericks if alpha channel is included
//		glcontext.getGL().getGL2().glReadPixels(0, 0, w, h, GL2.GL_BGRA, GL2.GL_UNSIGNED_INT_8_8_8_8_REV, b);
		glcontext.getGL().getGL2().glReadPixels(0, 0, w, h, GL2.GL_BGR, GL2.GL_UNSIGNED_BYTE, b);
		glcontext.release();
		canvas.repaint();
		AffineTransform tx = AffineTransform.getScaleInstance(1, -1);
	    tx.translate(0,-image.getHeight());
	    AffineTransformOp op = new AffineTransformOp(tx,
	        AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
	    image = op.filter(image, null);
		return image;
	}
	
	
	/**
	 * Generate an image from off-screen framebuffer
	 * 
	 * Use current view matrix, projection and zoom factor
	 * 
	 * @param sizex width in pixel
	 * @param sizey height in pixel
	 * @return static image
	 */
	public BufferedImage getStaticImage(int sizex, int sizey) {
		
		GLDrawableFactory fac = GLDrawableFactory.getFactory(GLProfile.getDefault());
		GLCapabilities glCap = new GLCapabilities(GLProfile.getDefault());
		// Without line below, there is an error in Windows.
		glCap.setDoubleBuffered(false);
//		glCap.setOnscreen(false);
//		glCap.setFBO(true);
		if (isAntialias()) {
//			glCap.setAlphaBits(4);
			glCap.setSampleBuffers(true);
			glCap.setNumSamples(8);
//			System.out.println("s:"+glCap.getNumSamples()+" SB:"+glCap.getSampleBuffers());
		}
		//makes a new buffer
		GLOffscreenAutoDrawable buf = fac.createOffscreenAutoDrawable(null, glCap, null, sizex, sizey);
		//required for drawing to the buffer
		GLContext context =  buf.createContext(null); 
		context.makeCurrent();
		GL2 localGl2 = context.getGL().getGL2();
		//System.out.println("disegno");
//		localGl2.glDrawBuffer(GL2.GL_BACK);
//		localGl2.glReadBuffer(GL2.GL_BACK);
		localGl2.glViewport(0, 0, sizex, sizey);
		//localGl2.glDrawBuffer(GL2.GL_FRONT_AND_BACK);
		initScene(localGl2);
        bufferOk = true;
		updateSceneObjects(localGl2);
		renderScene(localGl2, new GLU(),sizex, sizey);
		buf.swapBuffers();
		//System.out.println("catturo");
		// not working on Mavericks 10.9.x
		//AWTGLReadBufferUtil agb = new AWTGLReadBufferUtil(buf.getGLProfile(), true);
		//BufferedImage image = agb.readPixelsToBufferedImage(context.getGL(), true);
		BufferedImage image = new BufferedImage(sizex,sizey, BufferedImage.TYPE_3BYTE_BGR);
		DataBufferByte awfulBufferBack = (DataBufferByte) image.getRaster().getDataBuffer();
		Buffer b = ByteBuffer.wrap(awfulBufferBack.getData());
		localGl2.glPixelStorei(GL2.GL_PACK_ALIGNMENT, 1);
		// broken images on OSX Mavericks if alpha channel is included
//		glcontext.getGL().getGL2().glReadPixels(0, 0, w, h, GL2.GL_BGRA, GL2.GL_UNSIGNED_INT_8_8_8_8_REV, b);
		localGl2.glReadPixels(0, 0, sizex, sizey, GL2.GL_BGR, GL2.GL_UNSIGNED_BYTE, b);
		clearAllParts();
		context.release();

		//System.out.println(image.toString());
		context.destroy();
		buf.destroy();
		// flip vertical
		AffineTransform tx = AffineTransform.getScaleInstance(1, -1);
	    tx.translate(0,-image.getHeight());
	    AffineTransformOp op = new AffineTransformOp(tx,
	        AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
	    image = op.filter(image, null);
        return image;
	}



	
	
	
	
	/* 
	 * Doesn't work in OSX Mavericks. Probably a bug in AWT.
	 * Returns a totally transparent black image. 
	 */
//	public BufferedImage getScreenShot() {
//
//		glcontext.makeCurrent();
//		render(glcontext.getGLDrawable());
//		AWTGLReadBufferUtil agb = new AWTGLReadBufferUtil(glcontext.getGLDrawable().getGLProfile(), true);
//		BufferedImage image = agb.readPixelsToBufferedImage(glcontext.getGL(), true);
//		glcontext.release();
//		canvas.repaint();
//		return image;
//	}
//	
//	
	
	
	/*
	 * selection by window on bounding boxes 
	 */
	private void doSelectByWindow() {
		
        if (bufferOk) {
        	int h = canvas.getHeight();
        	// if user do an inverse window selection...
        	int xmin = Math.min(selcorner1x, selcorner2x);
        	int xmax = Math.max(selcorner1x, selcorner2x);
        	int ymin = Math.min(h-selcorner1y, h-selcorner2y);
        	int ymax = Math.max(h-selcorner1y, h-selcorner2y);
        	
			//GLU glu = new GLU();
			float[] pos = new float[3];
			float[] vm = viewMatrix.getAsOpenGLMatrix();
			float[] tl = new float[3];
			float[] tr = new float[3];
			float[] lr = new float[3];
			float[] ll = new float[3];
			glu.gluUnProject((float)xmin,(float) ymax, 0.01f, 
					vm, 0, 
					projectionMatrix, 0, viewPort, 0, 
					tl, 0);
			glu.gluUnProject((float)xmax,(float) ymax, 0.01f, 
					vm, 0, 
					projectionMatrix, 0, viewPort, 0, 
					tr, 0);
			glu.gluUnProject((float)xmax,(float) ymin, 0.01f, 
					vm, 0, 
					projectionMatrix, 0, viewPort, 0, 
					lr, 0);
			glu.gluUnProject((float)xmin,(float) ymin, 0.01f, 
					vm, 0, 
					projectionMatrix, 0, viewPort, 0, 
					ll, 0);

			//hotRemoveGadget(selWindow);
			selWindow = DrawHelpers.selectionWindow(tl, tr, lr, ll);
			addGadget(selWindow);

			for (LDRenderedPart p : model.values()) {
				if (p.isHidden())
					continue;
	        	float[] bb = p.getBboxVBO();
	        	boolean inside = true;
	        	for (int i=0;i<48;i+=6) {
	    			glu.gluProject(bb[i],bb[i+1],bb[i+2], 
	    					vm, 0, 
	    					projectionMatrix, 0, viewPort, 0, 
	    					pos, 0);
	    			//System.out.println("i:"+i+" x:"+pos[0]+" y:"+pos[1]+" ym:"+ymin+" yM:"+ymax);
	    			if (pos[0]<xmin || pos[0]>xmax ||
	    					pos[1]<ymin || pos[1]>ymax) {
	    				inside = false;
	    				break;
	    			}
	        	}
	        	if (inside) {
	    			for (HandlingListener hl:handlingListeners) {
	    				hl.picked(p.getId(), null,null, PickMode.ADD);
	    			}
	        	}
	        }
        }
	}
	
	
	
	/* 
	 * mouse listeners for part picking (non Javadoc)
	 * @see java.awt.event.MouseListener#mouseClicked(java.awt.event.MouseEvent)
	 */

	/**
	 * callback PartSelectionListeners to notify of part selection
	 * 
	 * listeners must implement PartSelectionListener interface
	 * Listener called with:
	 * int partId - global part id (0 if no part selected)
	 * PickMode - mode of picking (NONE, ADD, TOGGLE) 
	 */
	@Override
	public void mouseClicked(MouseEvent e) {
		
		// only button1 or button3 (left or right)
		int button = e.getButton();
		if ((button == MouseEvent.BUTTON1 || 
				button == MouseEvent.BUTTON3) && 
				bufferOk) {
			glcontext.makeCurrent();
			int clickedX = e.getX();
			int clickedY = canvas.getHeight()-e.getY();
			
			currentGL2.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fbo[0]);
			ByteBuffer b = ByteBuffer.allocateDirect(4);
			b.order(ByteOrder.nativeOrder());
			currentGL2.glReadPixels(clickedX, clickedY, 1, 1, GL2.GL_RGB, GL2.GL_UNSIGNED_BYTE, b);
			currentGL2.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
			int selectedId = (int) (b.get(0)&0xff)*65536+(int)(b.get(1)&0xff)*256+(int)(b.get(2)&0xff);
			glcontext.release();
			// test view to world coordinates
			//GLU glu = new GLU();
			float[] pos = new float[6];
			glu.gluUnProject((float)clickedX,(float) clickedY, 0, 
					viewMatrix.getAsOpenGLMatrix(), 0, 
					projectionMatrix, 0, viewPort, 0, 
					pos, 0);
			Point3D eyeNear = new Point3D(pos);
			//System.out.println("x="+pos[0]+" y="+pos[1]+" z="+pos[2]);
			glu.gluUnProject((float)clickedX,(float) clickedY, 1, 
					viewMatrix.getAsOpenGLMatrix(), 0, 
					projectionMatrix, 0, viewPort, 0, 
					pos, 0);
			Point3D eyeFar = new Point3D(pos);
			//System.out.println("x="+pos[3]+" y="+pos[4]+" z="+pos[5]);
			//float[] point = JSimpleGeom.planeXline(new float[] {1,1,0,-1,-1,0,-2,2,0}, pos);
			//System.out.println("t="+point[0]+" x="+point[1]+" y="+point[2]+" z="+point[3]);
			// select pick mode
			PickMode pm = PickMode.NONE;
			if (e.isControlDown()) {
				pm = PickMode.TOGGLE;
			}
			else if (e.isShiftDown()) {
				pm = PickMode.ADD;
			}
			if (button == MouseEvent.BUTTON3) {
				// special case: recenter to selected part
				pm = PickMode.CENTER_TO;
			}
			// calls listeners
			for (HandlingListener p:handlingListeners) {
				p.picked(selectedId, eyeNear,eyeFar, pm);
			}
		}
	}

	
	@Override
	public void mousePressed(MouseEvent e) { 
		// button 3 == rotation
		if ((e.getModifiersEx() & 
				(MouseEvent.BUTTON3_DOWN_MASK | MouseEvent.BUTTON2_DOWN_MASK | MouseEvent.BUTTON1_DOWN_MASK)) == 
						MouseEvent.BUTTON3_DOWN_MASK) {
			centerx = e.getX();
			centery = e.getY();
		}
		// button 1 == selection
		else if ((e.getModifiersEx() & 
				(MouseEvent.BUTTON3_DOWN_MASK | MouseEvent.BUTTON2_DOWN_MASK | MouseEvent.BUTTON1_DOWN_MASK)) == 
				MouseEvent.BUTTON1_DOWN_MASK) {
			selcorner1x = e.getX();
			selcorner1y = e.getY();
		}
	}

	@Override
	public void mouseReleased(MouseEvent e) { 

		if (selWindow != null) {
			removeGadget(DrawHelpers.SELWIN);
			selWindow = null;
			canvas.repaint();
		}
	}

	@Override
	public void mouseEntered(MouseEvent e) { 
		
	}

	@Override
	public void mouseExited(MouseEvent e) { 
		
	}


	@Override
	public void mouseDragged(MouseEvent e) {

		if ((e.getModifiersEx() & 
				(MouseEvent.BUTTON3_DOWN_MASK | MouseEvent.BUTTON2_DOWN_MASK | MouseEvent.BUTTON1_DOWN_MASK)) == 
						MouseEvent.BUTTON3_DOWN_MASK) {
			float factor = (float)Math.PI/(canvas.getWidth()>360?canvas.getWidth():360) ;
			int deltax = centerx - e.getX();
			int deltay = centery - e.getY();
			//System.out.println("x="+deltax + " y="+deltay); //DB
			centerx = e.getX();
			centery = e.getY();
			if (e.isShiftDown()) {
				// it is a pan
				viewMatrix = viewMatrix.moveTo(-deltax*zoomFactor, -deltay*zoomFactor, 0);
			}
			else {
				//it is a rotation
				if (rotMode == ROT_LDD) {
					viewMatrix = viewMatrix.rotateX(deltay*factor);
					Point3D o = new Point3D(viewMatrix.transformPoint(0, 0, 0));
					Point3D y1 = new Point3D(viewMatrix.transformPoint(0, 1, 0));
					viewMatrix = viewMatrix.transform(JSimpleGeom.axisRotMatrix(o, y1, (float) (deltax*factor*180/Math.PI)));
				}
				else {
					viewMatrix = viewMatrix.rotateY(-deltax*factor)
								   	.rotateX(deltay*factor);
				}
			}
			canvas.repaint();
		}
		else if ((e.getModifiersEx() & 
				(MouseEvent.BUTTON3_DOWN_MASK | MouseEvent.BUTTON2_DOWN_MASK | MouseEvent.BUTTON1_DOWN_MASK)) == 
				MouseEvent.BUTTON1_DOWN_MASK
				&& selection) {
			selcorner2x = e.getX();
			selcorner2y = e.getY();
			doSelectByWindow();
			canvas.repaint();
		}
	}


	@Override
	public void mouseMoved(MouseEvent e) { 

		int posX = e.getX();
		int posY = canvas.getHeight()-e.getY();
		//GLU glu = new GLU();
		float[] pos = new float[3];
		glu.gluUnProject((float)posX,(float) posY, 0, 
				viewMatrix.getAsOpenGLMatrix(), 0, 
				projectionMatrix, 0, viewPort, 0, 
				pos, 0);
		Point3D eyeNear = new Point3D(pos);
		//System.out.println("x="+pos[0]+" y="+pos[1]+" z="+pos[2]);
		glu.gluUnProject((float)posX,(float) posY, 1, 
				viewMatrix.getAsOpenGLMatrix(), 0, 
				projectionMatrix, 0, viewPort, 0, 
				pos, 0);
		Point3D eyeFar = new Point3D(pos);
//		glu.gluUnProject((float)canvas.getWidth()/2.0f,(float) canvas.getHeight()/2.0f, -10f, 
//				viewMatrix.getAsOpenGLMatrix(), 0, 
//				projectionMatrix, 0, viewPort, 0, 
//				pos, 0);
//		Point3D eye = new Point3D(pos);// Point3D(pos);
		
		// for part hover highlighting
//		if (glcontext != null) {
			glcontext.makeCurrent();
			currentGL2.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fbo[0]);
			ByteBuffer b = ByteBuffer.allocateDirect(4);
			b.order(ByteOrder.nativeOrder());
			currentGL2.glReadPixels(posX, posY, 1, 1, GL2.GL_RGB, GL2.GL_UNSIGNED_BYTE, b);
			partHoverId = (int) (b.get(0)&0xff)*65536+(int)(b.get(1)&0xff)*256+(int)(b.get(2)&0xff);
			currentGL2.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
			glcontext.release();
//		}
		//System.out.println("x="+pos[6]+" y="+pos[7]+" z="+pos[8]);
		// calls listeners
		for (HandlingListener p:handlingListeners) {
			p.moved(partHoverId,eyeNear,eyeFar);
		}
	}


	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		
		int r = e.getWheelRotation();
		if (r < 0) {
			zoomFactor *= 0.9f;
		}
		else {
			zoomFactor *= 1.1f;
		}
		canvas.repaint();
	}

}
