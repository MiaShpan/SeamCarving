The function showSeams duplicates the input image, then goes through the image and set the color of every pixel which is a part of a seam to be 'seamColorRGB'
Here is the function: 

public BufferedImage showSeams(int seamColorRGB) {
	int currentCol;
	BufferedImage dup = duplicateWorkingImage();
	setForEachParameters(inWidth, inHeight);
	for(int i = 0; i < seams.length ; i++){
		for(int row = 0; row < seams[0].length; row++){
			currentCol = seams[i][row];
			dup.setRGB(currentCol, row, seamColorRGB);
		}
	}

	return dup;
}