package com.birowo.client.platform.render;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Draws the shapes the client shows in the world.
 *
 * <p>The helpers write into a buffer that the caller has taken from the render context; they do
 * not touch any state of their own, so the render state of the game is left exactly as it was
 * found.
 */
public final class ShapeRenderer {
	private ShapeRenderer() {
	}

	/**
	 * Draws the twelve edges of a box.
	 *
	 * @param consumer  the buffer the lines are written to
	 * @param entry     the transformation the lines are drawn with
	 * @param minX      the left edge of the box
	 * @param minY      the lower edge of the box
	 * @param minZ      the front edge of the box
	 * @param maxX      the right edge of the box
	 * @param maxY      the upper edge of the box
	 * @param maxZ      the back edge of the box
	 * @param argb      the colour of the lines
	 * @param lineWidth the width of the lines
	 */
	public static void drawBoxEdges(VertexConsumer consumer, MatrixStack.Entry entry, double minX, double minY,
			double minZ, double maxX, double maxY, double maxZ, int argb, float lineWidth) {
		// The four edges of the lower ring.
		segment(consumer, entry, minX, minY, minZ, maxX, minY, minZ, argb, lineWidth);
		segment(consumer, entry, maxX, minY, minZ, maxX, minY, maxZ, argb, lineWidth);
		segment(consumer, entry, maxX, minY, maxZ, minX, minY, maxZ, argb, lineWidth);
		segment(consumer, entry, minX, minY, maxZ, minX, minY, minZ, argb, lineWidth);

		// The four edges of the upper ring.
		segment(consumer, entry, minX, maxY, minZ, maxX, maxY, minZ, argb, lineWidth);
		segment(consumer, entry, maxX, maxY, minZ, maxX, maxY, maxZ, argb, lineWidth);
		segment(consumer, entry, maxX, maxY, maxZ, minX, maxY, maxZ, argb, lineWidth);
		segment(consumer, entry, minX, maxY, maxZ, minX, maxY, minZ, argb, lineWidth);

		// The four upright edges.
		segment(consumer, entry, minX, minY, minZ, minX, maxY, minZ, argb, lineWidth);
		segment(consumer, entry, maxX, minY, minZ, maxX, maxY, minZ, argb, lineWidth);
		segment(consumer, entry, maxX, minY, maxZ, maxX, maxY, maxZ, argb, lineWidth);
		segment(consumer, entry, minX, minY, maxZ, minX, maxY, maxZ, argb, lineWidth);
	}

	/**
	 * Draws a circle of straight lines in a horizontal plane.
	 *
	 * @param consumer  the buffer the lines are written to
	 * @param entry     the transformation the lines are drawn with
	 * @param centerX   the centre of the circle
	 * @param centerY   the height of the circle
	 * @param centerZ   the centre of the circle
	 * @param radius    the radius of the circle
	 * @param argb      the colour of the lines
	 * @param lineWidth the width of the lines
	 * @param segments  the number of straight lines the circle is made of
	 */
	public static void drawRing(VertexConsumer consumer, MatrixStack.Entry entry, double centerX, double centerY,
			double centerZ, double radius, int argb, float lineWidth, int segments) {
		double step = Math.PI * 2.0D / segments;

		for (int segment = 0; segment < segments; segment++) {
			double from = segment * step;
			double to = (segment + 1) * step;

			segment(consumer, entry,
					centerX + Math.cos(from) * radius, centerY, centerZ + Math.sin(from) * radius,
					centerX + Math.cos(to) * radius, centerY, centerZ + Math.sin(to) * radius,
					argb, lineWidth);
		}
	}

	/**
	 * Fills a box with six faces whose colour fades from the bottom to the top.
	 *
	 * @param consumer    the buffer the faces are written to
	 * @param entry       the transformation the faces are drawn with
	 * @param minX        the left edge of the box
	 * @param minY        the lower edge of the box
	 * @param minZ        the front edge of the box
	 * @param maxX        the right edge of the box
	 * @param maxY        the upper edge of the box
	 * @param maxZ        the back edge of the box
	 * @param bottomColor the colour of the lower vertices
	 * @param topColor    the colour of the upper vertices
	 */
	public static void drawBoxFill(VertexConsumer consumer, MatrixStack.Entry entry, double minX, double minY,
			double minZ, double maxX, double maxY, double maxZ, int bottomColor, int topColor) {
		face(consumer, entry, minX, minY, minZ, maxX, minY, minZ, maxX, maxY, minZ, minX, maxY, minZ,
				bottomColor, topColor);
		face(consumer, entry, maxX, minY, maxZ, minX, minY, maxZ, minX, maxY, maxZ, maxX, maxY, maxZ,
				bottomColor, topColor);
		face(consumer, entry, minX, minY, maxZ, minX, minY, minZ, minX, maxY, minZ, minX, maxY, maxZ,
				bottomColor, topColor);
		face(consumer, entry, maxX, minY, minZ, maxX, minY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ,
				bottomColor, topColor);
		face(consumer, entry, minX, maxY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, minX, maxY, maxZ,
				topColor, topColor);
		face(consumer, entry, minX, minY, maxZ, maxX, minY, maxZ, maxX, minY, minZ, minX, minY, minZ,
				bottomColor, bottomColor);
	}

	/**
	 * The light of the world, as a direction the shading is built from.
	 */
	private static final double LIGHT_X = 0.40D;

	private static final double LIGHT_Y = 0.82D;

	private static final double LIGHT_Z = 0.41D;

	/**
	 * Draws a cone that stands on a horizontal circle.
	 *
	 * <p>The cone is made of triangles that share one point above the circle. The colour of every
	 * point is shaded by the light of the world, which is a fixed direction, so that the cone
	 * looks like a solid body and not like a flat silhouette.
	 *
	 * @param consumer  the buffer the triangles are written to
	 * @param entry     the transformation the cone is drawn with
	 * @param centerX   the horizontal centre of the circle
	 * @param baseY     the height of the circle
	 * @param centerZ   the depth of the centre of the circle
	 * @param radius    the radius of the circle
	 * @param height    the height of the point of the cone above the circle
	 * @param argb      the colour of the cone
	 * @param rotation  the angle the cone is turned by, in radians
	 * @param segments  the number of triangles the side of the cone is made of
	 */
	public static void drawCone(VertexConsumer consumer, MatrixStack.Entry entry, double centerX, double baseY,
			double centerZ, double radius, double height, int argb, float rotation, int segments) {
		double step = Math.PI * 2.0D / segments;
		double apexY = baseY + height;

		// The normal of every side of the cone, which is what the shading is built from.
		double[] sideX = new double[segments];
		double[] sideY = new double[segments];
		double[] sideZ = new double[segments];

		for (int segment = 0; segment < segments; segment++) {
			double from = rotation + segment * step;
			double to = from + step;

			double ax = centerX + Math.cos(from) * radius;
			double az = centerZ + Math.sin(from) * radius;
			double bx = centerX + Math.cos(to) * radius;
			double bz = centerZ + Math.sin(to) * radius;

			// The normal of the triangle that is made of the point of the cone and the two ends
			// of the side.
			double ux = ax - centerX;
			double uy = baseY - apexY;
			double uz = az - centerZ;
			double vx = bx - centerX;
			double vy = uy;
			double vz = bz - centerZ;

			double nx = uy * vz - uz * vy;
			double ny = uz * vx - ux * vz;
			double nz = ux * vy - uy * vx;

			double length = Math.sqrt(nx * nx + ny * ny + nz * nz);

			if (length > 0.0D) {
				nx /= length;
				ny /= length;
				nz /= length;
			}

			sideX[segment] = nx;
			sideY[segment] = ny;
			sideZ[segment] = nz;
		}

		double apexNormalX = 0.0D;
		double apexNormalY = 0.0D;
		double apexNormalZ = 0.0D;

		for (int segment = 0; segment < segments; segment++) {
			apexNormalX += sideX[segment];
			apexNormalY += sideY[segment];
			apexNormalZ += sideZ[segment];
		}

		double apexLength = Math.sqrt(apexNormalX * apexNormalX + apexNormalY * apexNormalY
				+ apexNormalZ * apexNormalZ);

		if (apexLength > 0.0D) {
			apexNormalX /= apexLength;
			apexNormalY /= apexLength;
			apexNormalZ /= apexLength;
		}

		int apexColor = shade(argb, light(apexNormalX, apexNormalY, apexNormalZ));

		for (int segment = 0; segment < segments; segment++) {
			int next = (segment + 1) % segments;

			double from = rotation + segment * step;
			double to = from + step;

			double ax = centerX + Math.cos(from) * radius;
			double az = centerZ + Math.sin(from) * radius;
			double bx = centerX + Math.cos(to) * radius;
			double bz = centerZ + Math.sin(to) * radius;

			// The normal of a point of the circle is the average of the two sides that meet
			// there, which keeps the shading of the cone even.
			double vertexX = sideX[segment] + sideX[next];
			double vertexY = sideY[segment] + sideY[next];
			double vertexZ = sideZ[segment] + sideZ[next];

			double vertexLength = Math.sqrt(vertexX * vertexX + vertexY * vertexY + vertexZ * vertexZ);

			if (vertexLength > 0.0D) {
				vertexX /= vertexLength;
				vertexY /= vertexLength;
				vertexZ /= vertexLength;
			}

			int colorA = shade(argb, light(vertexX, vertexY, vertexZ));
			int colorB = shade(argb, light(sideX[next], sideY[next], sideZ[next]));

			consumer.vertex(entry, (float) centerX, (float) apexY, (float) centerZ).color(apexColor);
			consumer.vertex(entry, (float) ax, (float) baseY, (float) az).color(colorA);
			consumer.vertex(entry, (float) bx, (float) baseY, (float) bz).color(colorB);
		}

		// The bottom of the cone, so that it does not look hollow when it is seen from below.
		int bottomColor = shade(argb, light(0.0D, -1.0D, 0.0D));

		for (int segment = 0; segment < segments; segment++) {
			double from = rotation + segment * step;
			double to = from + step;

			consumer.vertex(entry, (float) centerX, (float) baseY, (float) centerZ).color(bottomColor);
			consumer.vertex(entry, (float) (centerX + Math.cos(to) * radius), (float) baseY,
					(float) (centerZ + Math.sin(to) * radius)).color(bottomColor);
			consumer.vertex(entry, (float) (centerX + Math.cos(from) * radius), (float) baseY,
					(float) (centerZ + Math.sin(from) * radius)).color(bottomColor);
		}
	}

	/**
	 * @param normalX the horizontal part of the normal of a point
	 * @param normalY the vertical part of the normal of a point
	 * @param normalZ the depth of the normal of a point
	 * @return how bright the point is, between zero and one
	 */
	private static float light(double normalX, double normalY, double normalZ) {
		double dot = normalX * LIGHT_X + normalY * LIGHT_Y + normalZ * LIGHT_Z;
		double brightness = 0.34D + 0.66D * Math.max(0.0D, dot);

		return (float) Math.min(1.0D, brightness);
	}

	/**
	 * @param argb   the colour that is shaded
	 * @param factor how much of the colour is kept, between zero and one
	 * @return the shaded colour, with the opacity of the original
	 */
	private static int shade(int argb, float factor) {
		int alpha = (argb >>> 24) & 0xFF;
		int red = Math.round(((argb >> 16) & 0xFF) * factor);
		int green = Math.round(((argb >> 8) & 0xFF) * factor);
		int blue = Math.round((argb & 0xFF) * factor);

		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}

	/**
	 * Writes one straight line into the buffer.
	 *
	 * @param consumer  the buffer the line is written to
	 * @param entry     the transformation the line is drawn with
	 * @param fromX     the horizontal start of the line
	 * @param fromY     the vertical start of the line
	 * @param fromZ     the depth of the start of the line
	 * @param toX       the horizontal end of the line
	 * @param toY       the vertical end of the line
	 * @param toZ       the depth of the end of the line
	 * @param argb      the colour of the line
	 * @param lineWidth the width of the line
	 */
	private static void segment(VertexConsumer consumer, MatrixStack.Entry entry, double fromX, double fromY,
			double fromZ, double toX, double toY, double toZ, int argb, float lineWidth) {
		double dx = toX - fromX;
		double dy = toY - fromY;
		double dz = toZ - fromZ;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);

		float normalX = length > 0.0D ? (float) (dx / length) : 0.0F;
		float normalY = length > 0.0D ? (float) (dy / length) : 0.0F;
		float normalZ = length > 0.0D ? (float) (dz / length) : 0.0F;

		consumer.vertex(entry, (float) fromX, (float) fromY, (float) fromZ)
				.color(argb)
				.normal(entry, normalX, normalY, normalZ)
				.lineWidth(lineWidth);
		consumer.vertex(entry, (float) toX, (float) toY, (float) toZ)
				.color(argb)
				.normal(entry, normalX, normalY, normalZ)
				.lineWidth(lineWidth);
	}

	/**
	 * Writes one face of a box into the buffer.
	 *
	 * @param consumer    the buffer the face is written to
	 * @param entry       the transformation the face is drawn with
	 * @param bottomLeftX the horizontal position of the lower left corner
	 * @param bottomLeftY the vertical position of the lower left corner
	 * @param bottomLeftZ the depth of the lower left corner
	 * @param bottomRightX the horizontal position of the lower right corner
	 * @param bottomRightY the vertical position of the lower right corner
	 * @param bottomRightZ the depth of the lower right corner
	 * @param topRightX   the horizontal position of the upper right corner
	 * @param topRightY   the vertical position of the upper right corner
	 * @param topRightZ   the depth of the upper right corner
	 * @param topLeftX    the horizontal position of the upper left corner
	 * @param topLeftY    the vertical position of the upper left corner
	 * @param topLeftZ    the depth of the upper left corner
	 * @param bottomColor the colour of the lower corners
	 * @param topColor    the colour of the upper corners
	 */
	private static void face(VertexConsumer consumer, MatrixStack.Entry entry, double bottomLeftX, double bottomLeftY,
			double bottomLeftZ, double bottomRightX, double bottomRightY, double bottomRightZ, double topRightX,
			double topRightY, double topRightZ, double topLeftX, double topLeftY, double topLeftZ, int bottomColor,
			int topColor) {
		consumer.vertex(entry, (float) bottomLeftX, (float) bottomLeftY, (float) bottomLeftZ).color(bottomColor);
		consumer.vertex(entry, (float) bottomRightX, (float) bottomRightY, (float) bottomRightZ).color(bottomColor);
		consumer.vertex(entry, (float) topRightX, (float) topRightY, (float) topRightZ).color(topColor);
		consumer.vertex(entry, (float) topLeftX, (float) topLeftY, (float) topLeftZ).color(topColor);
	}
}
