package com.birowo.client.platform.render;

import java.util.Optional;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.LayeringTransform;
import net.minecraft.client.render.OutputTarget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.util.Identifier;

/**
 * The render layers the client draws its shapes with.
 *
 * <p>The vanilla line layer is depth tested, so an outline that is behind a wall is hidden by it.
 * There is no vanilla layer without a depth test, and a render layer can only be built with
 * {@code RenderLayer.of}, which is why the client widens the access to that method.
 *
 * <p>Every layer here is a copy of a vanilla layer with one thing changed: the depth test is
 * switched off, or the faces are not culled. The shaders, the vertex format and the blend
 * function are the ones vanilla uses, so nothing else has to change for the shapes to look the
 * way they normally do.
 */
public final class WorldLayers {
	private static final RenderLayer LINES_NO_DEPTH = lines(true);
	private static final RenderLayer FILLED_BOX = filledBox(false);
	private static final RenderLayer FILLED_BOX_NO_DEPTH = filledBox(true);

	private WorldLayers() {
	}

	/**
	 * @return the render layer for lines that are drawn through walls
	 */
	public static RenderLayer linesNoDepth() {
		return LINES_NO_DEPTH;
	}

	/**
	 * @return the render layer for a filled box that is hidden by the world
	 */
	public static RenderLayer filledBox() {
		return FILLED_BOX;
	}

	/**
	 * @return the render layer for a filled box that is drawn through walls
	 */
	public static RenderLayer filledBoxNoDepth() {
		return FILLED_BOX_NO_DEPTH;
	}

	/**
	 * Builds a copy of the vanilla line layer.
	 *
	 * @param noDepth whether the copy ignores the depth of the world
	 * @return the render layer
	 */
	private static RenderLayer lines(boolean noDepth) {
		RenderPipeline pipeline = RenderPipelines.LINES;

		RenderPipeline.Snippet snippet = new RenderPipeline.Snippet(
				Optional.of(pipeline.getVertexShader()),
				Optional.of(pipeline.getFragmentShader()),
				Optional.of(pipeline.getShaderDefines()),
				Optional.of(pipeline.getSamplers()),
				Optional.of(pipeline.getUniforms()),
				pipeline.getBlendFunction(),
				Optional.of(noDepth ? DepthTestFunction.NO_DEPTH_TEST : pipeline.getDepthTestFunction()),
				Optional.of(pipeline.getPolygonMode()),
				Optional.of(pipeline.isCull()),
				Optional.of(pipeline.isWriteColor()),
				Optional.of(pipeline.isWriteAlpha()),
				Optional.of(Boolean.FALSE),
				Optional.of(pipeline.getColorLogic()),
				Optional.of(pipeline.getVertexFormat()),
				Optional.of(pipeline.getVertexFormatMode()));

		RenderPipeline built = RenderPipeline.builder(snippet)
				.withLocation(Identifier.of("birowo", noDepth ? "lines_no_depth" : "lines"))
				.withDepthTestFunction(noDepth ? DepthTestFunction.NO_DEPTH_TEST : pipeline.getDepthTestFunction())
				.withDepthWrite(false)
				.build();

		RenderSetup setup = RenderSetup.builder(built)
				.layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
				.outputTarget(OutputTarget.ITEM_ENTITY_TARGET)
				.build();

		return RenderLayer.of("birowo_lines", setup);
	}

	/**
	 * Builds a copy of the vanilla filled box layer.
	 *
	 * <p>The faces of the box are not culled, so that the box is filled whichever way it is seen
	 * from.
	 *
	 * @param noDepth whether the copy ignores the depth of the world
	 * @return the render layer
	 */
	private static RenderLayer filledBox(boolean noDepth) {
		RenderPipeline pipeline = RenderPipelines.DEBUG_FILLED_BOX;

		RenderPipeline.Snippet snippet = new RenderPipeline.Snippet(
				Optional.of(pipeline.getVertexShader()),
				Optional.of(pipeline.getFragmentShader()),
				Optional.of(pipeline.getShaderDefines()),
				Optional.of(pipeline.getSamplers()),
				Optional.of(pipeline.getUniforms()),
				pipeline.getBlendFunction(),
				Optional.of(noDepth ? DepthTestFunction.NO_DEPTH_TEST : pipeline.getDepthTestFunction()),
				Optional.of(pipeline.getPolygonMode()),
				Optional.of(Boolean.FALSE),
				Optional.of(pipeline.isWriteColor()),
				Optional.of(pipeline.isWriteAlpha()),
				Optional.of(Boolean.FALSE),
				Optional.of(pipeline.getColorLogic()),
				Optional.of(pipeline.getVertexFormat()),
				Optional.of(pipeline.getVertexFormatMode()));

		RenderPipeline built = RenderPipeline.builder(snippet)
				.withLocation(Identifier.of("birowo", noDepth ? "filled_box_no_depth" : "filled_box"))
				.withDepthTestFunction(noDepth ? DepthTestFunction.NO_DEPTH_TEST : pipeline.getDepthTestFunction())
				.withCull(false)
				.withDepthWrite(false)
				.build();

		RenderSetup setup = RenderSetup.builder(built)
				.translucent()
				.layeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
				.build();

		return RenderLayer.of("birowo_filled_box", setup);
	}
}
