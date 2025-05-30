/*
 *      @(#)KBSplinePathInterpolator.java 1.11 01/07/31 16:42:25
 *
 * Copyright (c) 1996-2001 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistribution in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in
 *   the documentation and/or other materials provided with the
 *   distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES
 * SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THE SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN
 * OR ITS LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR
 * FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR
 * PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY OF
 * LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE,
 * EVEN IF SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that Software is not designed,licensed or intended
 * for use in the design, construction, operation or maintenance of
 * any nuclear facility.
 */


//package com.sun.j3d.utils.behaviors.interpolators;
package de.jose.jo3d.interpolators;

import com.sun.j3d.internal.J3dUtilsI18N;

import javax.media.j3d.*;

/**
 * KBSplinePathInterpolator behavior.  This class defines the base class for
 * all Kochanek-Bartels (also known as TCB or Tension-Continuity-Bias) 
 * Spline Path Interpolators.  
 *
 * @since Java3D 1.2
 */

public abstract class KBSplinePathInterpolator extends TransformInterpolator {
  
    private   int             keysLength;
    /**
     * An array of KBKeyFrame for interpolator
     */
    protected KBKeyFrame[]    keyFrames;

    /**
     * This value is the distance between knots 
     * value which can be used in further calculations by the subclass.
     */
    protected float currentU;

    /**
     * The lower knot
     */
    protected int lowerKnot;
    /**
     * The upper knot
     */
    protected int upperKnot;

    /**
     * Constructs a KBSplinePathInterpolator node with a null alpha value and
     * a null target of TransformGroup
     *
     * @since Java 3D 1.3
     */
    KBSplinePathInterpolator() {
    }
  

    /**
     * @deprecated As of Java 3D version 1.3, replaced by
     * <code>KBSplinePathInterpolator(Alpha, TransformGroup, TCBKeyFrame[]) </code>
     */
    public KBSplinePathInterpolator(Alpha alpha, KBKeyFrame keys[]) {
	this(alpha, null, keys);
    }

    /**
     * Constructs a new KBSplinePathInterpolator object that interpolates
     * between keyframes with specified alpha, target and an default
     * axisOfTranform set to identity.
     * It takes at least two key frames. The first key 
     * frame's knot must have a value of 0.0 and the last knot must have a
     * value of 1.0.  An intermediate key frame with index k must have a 
     * knot value strictly greater than the knot value of a key frame with 
     * index less than k. Once this constructor has all the valid key frames
     * it creates its own list of key fames that duplicates the first key frame
     * at the beginning of the list and the last key frame at the end of the
     * list. 
     * @param alpha the alpha object for this interpolator
     * @param target the TransformGroup node affected by this interpolator
     * @param keys an array of KBKeyFrame. Requires at least two key frames.
     *
     * @since Java 3D 1.3
     */
    public KBSplinePathInterpolator(Alpha alpha, TransformGroup target, KBKeyFrame keys[]) {
	super(alpha, target);
	processKeyFrames( keys );
    }
    
    /**
     * Constructs a new KBSplinePathInterpolator object that interpolates
     * between keyframes with specified alpha, target and axisOfTransform.
     * It takes at least two key frames. The first key 
     * frame's knot must have a value of 0.0 and the last knot must have a
     * value of 1.0.  An intermediate key frame with index k must have a 
     * knot value strictly greater than the knot value of a key frame with 
     * index less than k. Once this constructor has all the valid key frames
     * it creates its own list of key fames that duplicates the first key frame
     * at the beginning of the list and the last key frame at the end of the
     * list. 
     * @param alpha the alpha object for this interpolator
     * @param target the TransformGroup node affected by this interpolator
     * @param axisOfTransform the transform that defines the local coordinate
     * @param keys an array of KBKeyFrame. Requires at least two key frames
     *
     * @since Java 3D 1.3
     */
    public KBSplinePathInterpolator(Alpha alpha,
				    TransformGroup target,
				    Transform3D axisOfTransform,
				    KBKeyFrame keys[]) {
	super(alpha, target, axisOfTransform);
	processKeyFrames( keys );
    }

    /**
     * Process the new array of key frames
     */
    private void processKeyFrames( KBKeyFrame[] keys ) {

        // Make sure that we have at least two key frames
        keysLength = keys.length;
        if (keysLength < 2) {
	    throw new IllegalArgumentException(J3dUtilsI18N.getString("KBSplinePathInterpolator0"));

        }

        // Make sure that the first key frame's knot is equal to 0.0 
	if (keys[0].knot < -0.0001 || keys[0].knot > 0.0001) {
	    throw new IllegalArgumentException(J3dUtilsI18N.getString("KBSplinePathInterpolator1"));
	}
	
        // Make sure that the last key frames knot is equal to 1.0 
	if (keys[keysLength-1].knot -1.0 < -0.0001 || keys[keysLength-1].knot -1.0 > 0.0001) {
	    throw new IllegalArgumentException(J3dUtilsI18N.getString("KBSplinePathInterpolator2"));
	}

        // Make sure that all the knots are in sequence 
	for (int i = 0; i < keysLength; i++)  {
	    if (i>0 && keys[i].knot < keys[i-1].knot) {
		throw new IllegalArgumentException(J3dUtilsI18N.getString("KBSplinePathInterpolator3"));
	    }
	}

        // Make space for a leading and trailing key frame in addition to 
        // the keys passed in
        keyFrames = new KBKeyFrame[keysLength+2];
        keyFrames[0] = new KBKeyFrame();
        keyFrames[0] = keys[0];
        for (int i = 1; i < keysLength+1; i++) {
           keyFrames[i] = keys[i-1]; 
        }
        keyFrames[keysLength+1] = new KBKeyFrame();
        keyFrames[keysLength+1] = keys[keysLength-1]; 

        // Make key frame length reflect the 2 added key frames
        keysLength += 2;
    }
  
    /**
     * This method retrieves the length of the key frame array.
     * @return the number of key frames 
     */
    public int getArrayLength(){
	return keysLength-2;
    }
  
    /**
     * This method retrieves the key frame at the specified index.
     * @param index the index of the key frame requested
     * @return the key frame at the associated index
     */
    public KBKeyFrame getKeyFrame (int index) {

        // We add 1 to index because we have added a leading keyFrame
	return this.keyFrames[index+1];
    }

    /**
     * Set the key frame at the specified index to <code>keyFrame</code>
     * @param index Index of the key frame to change
     * @param keyFrame The new key frame
     * @since Java 3D 1.3
     */
    public void setKeyFrame( int index, KBKeyFrame keyFrame ) {
	this.keyFrames[index+1] = keyFrame;
    }

    /**
     * Set allthe key frames
     * @param keyFrame The new key frame
     * @since Java 3D 1.3
     */
    public void setKeyFrames( KBKeyFrame[] keyFrames ) {
	processKeyFrames( keyFrames );
    }

    /**
     * @deprecated As of Java 3D version 1.3, replaced by
     * <code>computePathInterpolation(float)</code>
     */
    protected void computePathInterpolation() {
	computePathInterpolation(this.getAlpha().value());
    }

    /**
     * This method computes the bounding knot indices and interpolation value
     * "CurrentU" given the current value of the knots[] array and the 
     * specified alpha value
     * @param alphaValue alpha value between 0.0 and 1.0
     * 
     * @since Java 3D 1.3
     */
    protected void computePathInterpolation( float alphaValue ) {

        // skip knots till we find the two we fall between  
        int i = 1;
	int len = keysLength - 2;
        while ((alphaValue > keyFrames[i].knot) && (i < len)) {
          i++;
        }
	
        if (i == 1) {
            currentU = 0f;
            lowerKnot = 1;
            upperKnot = 2;
        } else {
           currentU = (alphaValue - keyFrames[i-1].knot)/
                      (keyFrames[i].knot - keyFrames[i-1].knot);
           lowerKnot = i-1;
           upperKnot = i;
        }
    }

     /**
     * Copies all KBSplinePathInterpolator information from
     * <code>originalNode</code> into
     * the current node.  This method is called from the
     * <code>cloneNode</code> method which is, in turn, called by the
     * <code>cloneTree</code> method.<P> 
     *
     * @param originalNode the original node to duplicate.
     * @param forceDuplicate when set to <code>true</code>, causes the
     *  <code>duplicateOnCloneTree</code> flag to be ignored.  When
     *  <code>false</code>, the value of each node's
     *  <code>duplicateOnCloneTree</code> variable determines whether
     *  NodeComponent data is duplicated or copied.
     *
     * @exception RestrictedAccessException if this object is part of a live
     *  or compiled scenegraph.
     *
     * @see Node#duplicateNode
     * @see Node#cloneTree
     * @see NodeComponent#setDuplicateOnCloneTree
     */
    public void duplicateNode(Node originalNode, boolean forceDuplicate) {
        super.duplicateNode(originalNode, forceDuplicate);
        KBSplinePathInterpolator originalSpline = 
                                  (KBSplinePathInterpolator) originalNode;
        setAlpha(originalSpline.getAlpha());
        keysLength = originalSpline.keysLength;
        keyFrames = new KBKeyFrame[keysLength];
        System.arraycopy(originalSpline.keyFrames, 0,
                         keyFrames, 0, keysLength);
    }

}
