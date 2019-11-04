package edu.cg;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collections;

public class SeamsCarver extends ImageProcessor {
	// MARK: Fields
	private int numOfSeams;
	private ResizeOperation resizeOp;
	private BufferedImage greyImage;
	private int numberOfHandledSeams;
	private boolean[][] imageMask;
	private boolean[][] shiftedMask;
	private Min[][] m;
	private int[][] colIndices;
	private int[][] colIndicesIncrease;
	private long[][] pixelsEnergy;
	private int[][] seams;
	private int[][] rotatedSeams;
	private int[] currentSeam;

	public SeamsCarver(Logger logger, BufferedImage workingImage, int outWidth, RGBWeights rgbWeights,
			boolean[][] imageMask) {
		super((s) -> logger.log("Seam carving: " + s), workingImage, rgbWeights, outWidth, workingImage.getHeight());

		numOfSeams = Math.abs(outWidth - inWidth);
		this.imageMask = imageMask;
		if (inWidth < 2 | inHeight < 2)
			throw new RuntimeException("Can not apply seam carving: workingImage is too small");

		if (numOfSeams > inWidth / 2)
			throw new RuntimeException("Can not apply seam carving: too many seams...");

		// Setting resizeOp by with the appropriate method reference
		if (outWidth > inWidth)
			resizeOp = this::increaseImageWidth;
		else if (outWidth < inWidth)
			resizeOp = this::reduceImageWidth;
		else
			resizeOp = this::duplicateWorkingImage;

		numberOfHandledSeams = 0;
		m = new Min[this.inHeight][this.inWidth];
		pixelsEnergy = new long[this.inHeight][this.inWidth];
		seams = new int[numOfSeams][inHeight];
		rotatedSeams = new int[inHeight][numOfSeams];
		currentSeam = new int[inHeight];
		shiftedMask = new boolean[inHeight][outWidth];
		greyImage = greyscale();
		initColIndices();
		seamCarving();
		handleIncreaseImage();

		this.logger.log("preliminary calculations were ended.");
	}

	public BufferedImage resize() {
		this.logger.log("resizing image...");
		return resizeOp.resize();
	}

	private void initColIndices(){
		this.logger.log("init colIndices");
		colIndices = new int[inHeight][inWidth];
		initMatrixWithCols(false);
	}

	private void initMatrixWithCols(boolean increase){
		int[][] matrix = (increase) ? colIndicesIncrease : colIndices;

		for(int i = 0; i < colIndices.length; i++) {
			for(int j = 0; j<colIndices[0].length; j++) {
				matrix[i][j] = j;
			}
		}
	}

	private void handleIncreaseImage(){
		this.logger.log("handle increase image");
		if (outWidth > inWidth) {
			initColIndicesIncrease();
			initRotatedSeams();
			fillColIndices();
		}
	}

	private void initColIndicesIncrease(){
		colIndicesIncrease = new int[inHeight][outWidth];
		initMatrixWithCols(true);
	}

	private void seamCarving() {
		this.logger.log("starting seam carving");
		while(numberOfHandledSeams < numOfSeams) {
			initPixelEnergy();
			fillM();
			findSeam(findMinValueIndex());
			shiftColIndices();
			numberOfHandledSeams++;
		}
	}

	private void initPixelEnergy() {
		this.logger.log("init pixel energy");
		this.setForEachParameters(inWidth - numberOfHandledSeams, inHeight);
		this.forEach((y, x) -> {
			pixelsEnergy[y][x] = calcPixelEnergy(y,x);
		});
	}

	private void fillM(){
		this.logger.log("fill M");
		this.setForEachParameters(inWidth - numberOfHandledSeams, inHeight);
		this.forEach((y, x) -> {
			m[y][x] = calcMinCost(y,x);
		});
		this.logger.log("M was successfully filled");
	}

	private Min findMinValueIndex(){
		int row = m.length -1;
		long minValue = m[row][0].minCost;
		Min min = m[row][0];

		for(int currentCol = 1; currentCol < inWidth - numberOfHandledSeams; currentCol++) {
			if(m[row][currentCol].minCost < minValue) {
				minValue = m[row][currentCol].minCost;
				min = m[row][currentCol];
			}
		}

		return min;
	}

	private void findSeam(Min min){
		this.logger.log("finding seam number " + numberOfHandledSeams+1);
		Min currentMin = min;
		currentSeam[inHeight - 1] = min.index;
		seams[numberOfHandledSeams][inHeight - 1] = colIndices[inHeight - 1][min.index];

		for(int currentRow = inHeight - 1; currentRow > 0; currentRow--){
			currentMin = currentMin.parent;
			currentSeam[currentRow - 1] = currentMin.index;
			seams[numberOfHandledSeams][currentRow - 1] = colIndices[currentRow - 1][currentMin.index];
		}
		this.logger.log("seam number " + numberOfHandledSeams+1 + "was found");
	}

	private Min calcMinCost(int y, int x) {
		long topLeft, top, topRight, minValue;

		if (y == 0) {
			return new Min(pixelsEnergy[y][x], null, x);
		}

		topLeft = (isLeftCol(x)) ? Long.MAX_VALUE : m[y-1][x-1].minCost + calcCValue(y, x, "left");
		top = m[y-1][x].minCost + calcCValue(y, x, "up");
		topRight = (isRightCol(x)) ? Long.MAX_VALUE : m[y-1][x+1].minCost + calcCValue(y, x, "right");

		return getMinCostObj(top, topRight, topLeft, y, x);
	}

	private Min getMinCostObj(long topCost, long topRightCost, long topLeftCost, int y, int x){
		long minValue = Math.min(Math.min(topLeftCost, topCost), topRightCost);
		Min minObj;

		if(minValue == topRightCost) {
			minObj = new Min(pixelsEnergy[y][x] + topRightCost, m[y-1][x+1], x);
		} else if(minValue == topLeftCost) {
			minObj = new Min(pixelsEnergy[y][x] + topLeftCost, m[y-1][x-1], x);
		} else {
			minObj = new Min(pixelsEnergy[y][x] + topCost, m[y-1][x], x);
		}

		return minObj;
	}

	private long calcCValue(int y, int x, String pixel) {
		long cValue = 0;
		long topPixelValue = getGreyLevel(y-1, x);
		long rightPixelValue = (isRightCol(x)) ? 255L : getGreyLevel(y, x+1);
		long leftPixelValue = (isLeftCol(x)) ? 255L : getGreyLevel(y,x-1);
		long leftRightAbs = (isRightCol(x)) || (isLeftCol(x)) ? 255L :
				(long)Math.abs(getGreyLevel(y, x+1) - getGreyLevel(y,x-1));
		long topLeftAbs = (isRightCol(x)) || (isLeftCol(x)) ? 255L :
				(long)Math.abs(topPixelValue - getGreyLevel(y,x-1));
		long topRightAbs = (isRightCol(x)) || (isLeftCol(x)) ? 255L :
				(long)Math.abs(topPixelValue - getGreyLevel(y,x+1));

		switch (pixel) {
			case "left":
				cValue = leftRightAbs + topLeftAbs;
				break;
			case "up":
				cValue = leftRightAbs;
				break;
			case "right":
				cValue = leftRightAbs + topRightAbs;
				break;
		}

		return cValue;
	}

	private long getGreyLevel(int y, int x){
		return new Color (greyImage.getRGB(colIndices[y][x],y)).getRed();
	}

	private boolean isLeftCol(int x){
		return (x == 0);
	}

	private boolean isRightCol(int x){
		return (x == inWidth - numberOfHandledSeams - 1);
	}

	private long calcPixelEnergy(int y, int x) {
		long E1, E2, E3;
		long pixelEnergy = getGreyLevel(y,x);

		E1 = (x < inWidth - 1 - numberOfHandledSeams) ? Math.abs(pixelEnergy - getGreyLevel(y,x+1)) :
				Math.abs(pixelEnergy - getGreyLevel(y,x-1));
		E2 = (y < inHeight -1) ? Math.abs(pixelEnergy - getGreyLevel(y+1,x)) :
				Math.abs(pixelEnergy - getGreyLevel(y-1,x));
		E3 = imageMask[y][colIndices[y][x]] ? Integer.MAX_VALUE : 0;

		return E1+E2+E3;
	}

	private void shiftColIndices(){
		this.logger.log("starting shift cols left");
		for(int row = 0; row < inHeight; row++){
			shiftRowLeft(row);
		}
		this.logger.log("cols were successfully shifted to the left");
	}

	private void shiftRowLeft(int row){
		int currentCol = currentSeam[row];
		int numOfCols = inWidth - numberOfHandledSeams;
		for(int col = currentCol; col < numOfCols; col++){
			colIndices[row][col] = (col == (numOfCols -1)) ? (-1) : colIndices[row][col+1];
		}
	}

	private void shiftRowRight(int row, int seamCol){
		int currentCol = seamCol;
		int numOfCols = inWidth + numberOfHandledSeams -1;
		int saveNext;
		int saveCurrent = colIndicesIncrease[row][currentCol];
		for(int col = currentCol; col < numOfCols; col++){
			saveNext = colIndicesIncrease[row][col+1];
			colIndicesIncrease[row][col+1] = saveCurrent;
			saveCurrent = saveNext;
		}
	}

	private BufferedImage reduceImageWidth() {
		return createOutputImage(false);
	}

	private BufferedImage increaseImageWidth() {
		return createOutputImage(true);
	}

	private BufferedImage createOutputImage(boolean increase){
		this.logger.log("creating output image");
		int[][] indices = increase ? colIndicesIncrease : colIndices;
		BufferedImage output = newEmptyOutputSizedImage();

		setForEachParameters(outWidth, inHeight);
		this.forEach((y, x) -> {
			int red = new Color(workingImage.getRGB(indices[y][x],y)).getRed();
			int green = new Color(workingImage.getRGB(indices[y][x],y)).getGreen();
			int blue = new Color(workingImage.getRGB(indices[y][x],y)).getBlue();
			Color color = new Color(red, green, blue);
			output.setRGB(x, y, color.getRGB());
		});
		this.logger.log("output image was created");

		return output;
	}

	private void fillColIndices(){
		int numberOfHandledCols = 0;

		for(int row = 0; row < rotatedSeams.length; row++){
			for (int col = 0; col< rotatedSeams[0].length; col++){
				shiftRowRight(row, rotatedSeams[row][col]+numberOfHandledCols);
				numberOfHandledCols++;
			}
			numberOfHandledCols = 0;
		}
	}

	public BufferedImage showSeams(int seamColorRGB) {
		int currentCol;
		BufferedImage dup = duplicateWorkingImage();

		for(int i = 0; i < seams.length ; i++){
			for(int row = 0; row < seams[0].length; row++){
				currentCol = seams[i][row];
				dup.setRGB(currentCol, row, seamColorRGB);
			}
		}

		return dup;
	}

	private void initRotatedSeams(){
		for(int row = 0; row<seams.length; row++){
			for(int col= 0; col<seams[0].length; col++){
				rotatedSeams[col][row] = seams[row][col];
			}
		}
		for(int row = 0; row<rotatedSeams.length; row++){
			Arrays.sort(rotatedSeams[row]);
		}
	}

	public boolean[][] getMaskAfterSeamCarving() {
		if(inWidth < outWidth){
			getMaskAfterSeamCarvingIncrease();
		} else {
			getMaskAfterSeamCarvingReduce();
		}
		return shiftedMask;
	}

	private void getMaskAfterSeamCarvingIncrease(){
		for (int row = 0; row < shiftedMask.length; row++){
			for (int col = 0; col < shiftedMask[0].length; col++){
				shiftedMask[row][col] = imageMask[row][colIndicesIncrease[row][col]];
			}
		}
	}

	private void getMaskAfterSeamCarvingReduce(){
		for (int row = 0; row < shiftedMask.length; row++){
			for (int col = 0; col < shiftedMask[0].length; col++){
				shiftedMask[row][col] = imageMask[row][colIndices[row][col]];
			}
		}
	}

	// MARK: An inner interface for functional programming.
	@FunctionalInterface
	interface ResizeOperation {
		BufferedImage resize();
	}

	private class Min{
		long minCost;
		int index;
		Min parent;

		Min(long min, Min parent, int index) {
			this.minCost = min;
			this.parent = parent;
			this.index = index;
		}
	}
}
