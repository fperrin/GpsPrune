package tim.prune.function.srtm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public abstract class SrtmSource {
	public abstract String getName();
	public abstract boolean isReadyToUse();
	public abstract boolean downloadTile(SrtmTile inTile)
		throws SrtmSourceException;
	public abstract int getRowSize(SrtmTile inTile);
	protected abstract String getSourceExtension();

	public int[] getTileHeights(SrtmTile inTile)
		throws SrtmSourceException
	{
		File cacheFileName = getCacheFileName(inTile);
		if (cacheFileName == null)
		{
			throw new SrtmSourceException("Tile "+inTile.getTileName()+" not in cache");
		}
		try
		{
			ZipInputStream inStream = new ZipInputStream(new FileInputStream(cacheFileName));
			ZipEntry entry = inStream.getNextEntry();
			int rowSize = getRowSize(inTile);
			int tileSize = rowSize * rowSize;
			if (entry.getSize() != 2 * tileSize)
			{
				throw new SrtmSourceException("Tile file "+cacheFileName+" does not have the expected size");
			}
			int[] heights = new int[tileSize];
			// Read entire file contents into one byte array
			for (int i = 0; i < heights.length; i++)
			{
				heights[i] = inStream.read() * 256 + inStream.read();
				if (heights[i] >= 32768) {heights[i] -= 65536;}
			}
			// Close stream
			inStream.close();
			return heights;
		}
		catch (IOException e)
		{
			throw new SrtmSourceException("Failure opening "+cacheFileName+" for reading:"+e.getMessage());
		}

	}

	protected File getCacheDir()
	{
		return SrtmDiskCache.getCacheDir(getName());
	}

	protected File getCacheFileName(SrtmTile inTile)
	{
		String fileName = inTile.getTileName() + getSourceExtension();
		return new File(getCacheDir(), fileName);
	}

	public boolean isCached(SrtmTile inTile)
	{
		return getCacheFileName(inTile).exists();
	}
}
