/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.idyl.prophunt;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.geometry.SimplePolygon;
import net.runelite.api.model.Jarvis;

import java.util.ArrayList;
import java.util.List;

import static net.runelite.api.Perspective.*;

public class RLUtils {
    /**
     * The following code is from the Runelite API Perspective class
     */
    private static void modelToCanvasCpu(Client client, int end, int x3dCenter, int y3dCenter, int z3dCenter, int rotate, int[] x3d, int[] y3d, int[] z3d, int[] x2d, int[] y2d) {
        final int
                cameraPitch = client.getCameraPitch(),
                cameraYaw = client.getCameraYaw(),

                pitchSin = SINE[cameraPitch],
                pitchCos = COSINE[cameraPitch],
                yawSin = SINE[cameraYaw],
                yawCos = COSINE[cameraYaw],
                rotateSin = SINE[rotate],
                rotateCos = COSINE[rotate],

                cx = x3dCenter - client.getCameraX(),
                cy = y3dCenter - client.getCameraY(),
                cz = z3dCenter - client.getCameraZ(),

                viewportXMiddle = client.getViewportWidth() / 2,
                viewportYMiddle = client.getViewportHeight() / 2,
                viewportXOffset = client.getViewportXOffset(),
                viewportYOffset = client.getViewportYOffset(),

                zoom3d = client.getScale();

        for (int i = 0; i < end; i++) {
            int x = x3d[i];
            int y = y3d[i];
            int z = z3d[i];

            if (rotate != 0) {
                int x0 = x;
                x = x0 * rotateCos + y * rotateSin >> 16;
                y = y * rotateCos - x0 * rotateSin >> 16;
            }

            x += cx;
            y += cy;
            z += cz;

            final int
                    x1 = x * yawCos + y * yawSin >> 16,
                    y1 = y * yawCos - x * yawSin >> 16,
                    y2 = z * pitchCos - y1 * pitchSin >> 16,
                    z1 = y1 * pitchCos + z * pitchSin >> 16;

            int viewX, viewY;

            if (z1 < 50) {
                viewX = Integer.MIN_VALUE;
                viewY = Integer.MIN_VALUE;
            } else {
                viewX = (viewportXMiddle + x1 * zoom3d / z1) + viewportXOffset;
                viewY = (viewportYMiddle + y2 * zoom3d / z1) + viewportYOffset;
            }

            x2d[i] = viewX;
            y2d[i] = viewY;
        }
    }

    public static SimplePolygon calculateAABB(Client client, Model m, int jauOrient, int x, int y, int z, int padding) {
        // Get the AABB of the model
        AABB aabb = m.getAABB(jauOrient);

        int x1 = aabb.getCenterX();
        int y1 = aabb.getCenterZ();
        int z1 = aabb.getCenterY();

        int ex = aabb.getExtremeX();
        int ey = aabb.getExtremeZ();
        int ez = aabb.getExtremeY();

        // Calculate the AABB boundaries
        int x2 = x1 + ex;
        int y2 = y1 + ey;
        int z2 = z1 + ez;

        x1 -= ex;
        y1 -= ey;
        z1 -= ez;

        // Expand the bounding box by the padding value
        x1 -= padding;
        y1 -= padding;
        z1 -= padding;

        x2 += padding;
        y2 += padding;
        z2 += padding;

        // Create the 8 corner points of the expanded bounding box
        int[] xa = new int[]{
                x1, x2, x1, x2,
                x1, x2, x1, x2
        };
        int[] ya = new int[]{
                y1, y1, y2, y2,
                y1, y1, y2, y2
        };
        int[] za = new int[]{
                z1, z1, z1, z1,
                z2, z2, z2, z2
        };

        // Convert these 3D coordinates to 2D canvas coordinates
        int[] x2d = new int[8];
        int[] y2d = new int[8];

        modelToCanvasCpu(client, 8, x, y, z, 0, xa, ya, za, x2d, y2d);

        // Return the convex hull (the 2D projection of the expanded AABB)
        return Jarvis.convexHull(x2d, y2d);
    }
}

