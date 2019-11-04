package edu.cg;

import java.awt.Color;
import java.awt.image.BufferedImage;

public class ImageProcessor extends FunctioalForEachLoops {

	// MARK: fields
	public final Logger logger;
	public final BufferedImage workingImage;
	public final RGBWeights rgbWeights;
	public final int inWidth;
	public final int inHeight;
	public final int workingImageType;
	public final int outWidth;
	public final int outHeight;

	// MARK: constructors
	public ImageProcessor(Logger logger, BufferedImage workingImage, RGBWeights rgbWeights, int outWidth,
			int outHeight) {
		super(); // initializing for each loops...

		this.logger = logger;
		this.workingImage = workingImage;
		this.rgbWeights = rgbWeights;
		inWidth = workingImage.getWidth();
		inHeight = workingImage.getHeight();
		workingImageType = workingImage.getType();
		this.outWidth = outWidth;
		this.outHeight = outHeight;
		setForEachInputParameters();
	}

	public ImageProcessor(Logger logger, BufferedImage workingImage, RGBWeights rgbWeights) {
		this(logger, workingImage, rgbWeights, workingImage.getWidth(), workingImage.getHeight());
	}

	// MARK: change picture hue - example
	public BufferedImage changeHue() {
		logger.log("Preparing for hue changing...");

		int r = rgbWeights.redWeight;
		int g = rgbWeights.greenWeight;
		int b = rgbWeights.blueWeight;
		int max = rgbWeights.maxWeight;

		BufferedImage ans = newEmptyInputSizedImage();

		forEach((y, x) -> {
			Color c = new Color(workingImage.getRGB(x, y));
			int red = r * c.getRed() / max;
			int green = g * c.getGreen() / max;
			int blue = b * c.getBlue() / max;
			Color color = new Color(red, green, blue);
			ans.setRGB(x, y, color.getRGB());
		});

		logger.log("Changing hue done!");

		return ans;
	}

	public final void setForEachInputParameters() {
		setForEachParameters(inWidth, inHeight);
	}

	public final void setForEachOutputParameters() {
		setForEachParameters(outWidth, outHeight);
	}

	public final BufferedImage newEmptyInputSizedImage() {
		return newEmptyImage(inWidth, inHeight);
	}

	public final BufferedImage newEmptyOutputSizedImage() {
		return newEmptyImage(outWidth, outHeight);
	}

	public final BufferedImage newEmptyImage(int width, int height) {
		return new BufferedImage(width, height, workingImageType);
	}

	// A helper method that deep copies the current working image.
	public final BufferedImage duplicateWorkingImage() {
		BufferedImage output = newEmptyInputSizedImage();
		setForEachInputParameters();
		forEach((y, x) -> output.setRGB(x, y, workingImage.getRGB(x, y)));

		return output;
	}

	public BufferedImage greyscale() {
		logger.log("applies for greyscale...");

		int redWeight = rgbWeights.redWeight;
		int greenWeight = rgbWeights.greenWeight;
		int blueWeight = rgbWeights.blueWeight;

		BufferedImage ans = newEmptyInputSizedImage();

		forEach((y, x) -> {
			Color c = new Color(workingImage.getRGB(x, y));
			int red = redWeight * c.getRed();
			int green = greenWeight * c.getGreen();
			int blue = blueWeight * c.getBlue();
			int gray = (red + green + blue) / (redWeight + greenWeight + blueWeight);
			Color color = new Color(gray, gray, gray);
			ans.setRGB(x, y, color.getRGB());
		});

		logger.log("GrayScale done!");

		return ans;
	}

	public BufferedImage nearestNeighbor() {
		this.logger.log("applies nearest neighbor");

		BufferedImage ans = this.newEmptyOutputSizedImage();
		float ratioX = (float)this.inWidth / (float)this.outWidth;
		float ratioY = (float)this.inHeight / (float)this.outHeight;

		this.setForEachOutputParameters();
		this.forEach((y, x) -> {
			int nearestX = Math.round((float)x * ratioX);
			int nearestY = Math.round((float)y * ratioY);
			nearestX = (nearestX > this.inWidth -1) ? this.inWidth -1 : nearestX;
			nearestY = (nearestY > this.inHeight -1) ? this.inHeight -1 : nearestY;
			ans.setRGB(x, y, this.workingImage.getRGB(nearestX, nearestY));
		});

		logger.log("Nearest Neighbor done!");

		return ans;
	}
}
