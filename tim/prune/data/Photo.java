package tim.prune.data;

import java.awt.Dimension;
import java.io.File;

import javax.swing.ImageIcon;

/**
 * Class to represent a photo and link to DataPoint
 */
public class Photo extends MediaFile
{
	/** Size of original image */
	private Dimension _size = null;
	/** rotation flag (clockwise from 0 to 3) */
	private int _rotation = 0;
	// TODO: Need to store caption for image?
	// thumbnail for image (from exif)
	private byte[] _exifThumbnail = null;

	/**
	 * Constructor
	 * @param inFile File object for photo
	 */
	public Photo(File inFile)
	{
		super(inFile, null);
	}

	/**
	 * Calculate the size of the image (slow)
	 */
	private void calculateSize()
	{
		ImageIcon icon = new ImageIcon(_file.getAbsolutePath());
		int width = icon.getIconWidth();
		int height = icon.getIconHeight();
		if (width > 0 && height > 0)
		{
			_size = new Dimension(width, height);
		}
	}

	/**
	 * @return size of image as Dimension object
	 */
	public Dimension getSize()
	{
		if (_size == null) {
			calculateSize();
		}
		return _size;
	}

	/**
	 * @return width of the image, if known
	 */
	public int getWidth()
	{
		if (_size == null)
		{
			calculateSize();
			if (_size == null) {return -1;}
		}
		return _size.width;
	}

	/**
	 * @return height of the image, if known
	 */
	public int getHeight()
	{
		if (_size == null)
		{
			calculateSize();
			if (_size == null) {return -1;}
		}
		return _size.height;
	}

	/**
	 * @return byte array of thumbnail data
	 */
	public byte[] getExifThumbnail()
	{
		return _exifThumbnail;
	}

	/**
	 * @param inBytes byte array from exif
	 */
	public void setExifThumbnail(byte[] inBytes)
	{
		_exifThumbnail = inBytes;
	}

	/**
	 * Delete the cached data when the Photo is no longer needed
	 */
	public void resetCachedData()
	{
		_size = null;
		// remove thumbnail too
	}

	/**
	 * @param inRotation initial rotation value (from exif)
	 */
	public void setRotation(int inRotation)
	{
		if (inRotation >= 0 && inRotation <= 3) {
			_rotation = inRotation;
		}
	}

	/**
	 * Rotate the image by 90 degrees
	 * @param inRight true to rotate right, false for left
	 */
	public void rotate(boolean inRight)
	{
		int dir = inRight?1:3;
		_rotation = (_rotation + dir) % 4;
	}

	/**
	 * @return rotation status
	 */
	public int getRotationDegrees()
	{
		return _rotation * 90;
	}
}
