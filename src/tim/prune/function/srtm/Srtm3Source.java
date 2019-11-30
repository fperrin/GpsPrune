package tim.prune.function.srtm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.HttpURLConnection;

import tim.prune.GpsPrune;
import tim.prune.I18nManager;

public class Srtm3Source extends SrtmSource {
	/** URL prefix for all tiles */
	private static final String URL_PREFIX = "https://dds.cr.usgs.gov/srtm/version2_1/SRTM3/";
	/** Directory names for each continent */
	private static final String[] CONTINENTS = {"", "Eurasia", "North_America", "Australia",
						    "Islands", "South_America", "Africa"};
	private byte[] _continents_lookup;


	public Srtm3Source()
	{
		_continents_lookup = populateContinents();
	}

	public String getNameKey()
	{
		return "function.downloadsrtm." + getName();
	}

	public String getName()
	{
		return "SRTM3_v21";
	}

	protected String getSourceExtension()
	{
		return ".hgt.zip";
	}

	/**
	 * Read the dat file and get the contents
	 * @return byte array containing file contents
	 */
	private static byte[] populateContinents()
	{
		InputStream in = null;
		try
		{
			// Need absolute path to dat file
			in = Srtm3Source.class.getResourceAsStream("/tim/prune/function/srtm/srtmtiles.dat");
			if (in != null)
			{
				byte[] buffer = new byte[in.available()];
				in.read(buffer);
				in.close();
				return buffer;
			}
		}
		catch (java.io.IOException e) {
			System.err.println("Exception trying to read srtmtiles.dat : " + e.getMessage());
		}
		finally
		{
			try {
				in.close();
			}
			catch (Exception e) {} // ignore
		}
		return null;
	}

	/**
	 * Get the Url for the given tile
	 * @param inTile Tile to get
	 * @return URL
	 */
	private URL buildUrl(SrtmTile inTile)
		throws SrtmSourceException
	{
		
		// Get byte from lookup array
		int idx = (inTile.getLatitude() + 59)*360 + (inTile.getLongitude() + 180);
		int dir;
		try
		{
			dir = _continents_lookup[idx];
		}
		catch (ArrayIndexOutOfBoundsException e)
		{
			throw new SrtmSourceException("Could not find continent for tile "+inTile.getTileName());
		}
		try
		{
			return new URL(URL_PREFIX + CONTINENTS[dir] + "/" + inTile.getTileName() + getSourceExtension());
		}
		catch (MalformedURLException e)
		{
			throw new SrtmSourceException("Could not build URL for tile "+inTile.getTileName());
		}
	}

	public boolean isReadyToUse()
	{
		return true;
	}

	public boolean downloadTile(SrtmTile inTile)
		throws SrtmSourceException
	{
		int redirects = 5;
		URL tileUrl = buildUrl(inTile);
		File outputFile = getCacheFileName(inTile);
		System.out.println("Download: Need to download: " + tileUrl);

		try
		{
			HttpURLConnection conn = (HttpURLConnection) tileUrl.openConnection();

			// Define streams
			FileOutputStream outStream = null;
			InputStream inStream = null;

			conn.setRequestProperty("User-Agent", "GpsPrune v" + GpsPrune.VERSION_NUMBER);

			int status = conn.getResponseCode();
			if (status == 200)
			{
				inStream = conn.getInputStream();
			}
			else if (status == 404)
			{
				throw new SrtmSourceException("Tile not found: "+conn.getURL());
			}
			else
			{
				throw new SrtmSourceException("Invalid response from server: " +status+conn.getContent());
			}

			outStream = new FileOutputStream(outputFile);

			int c;
			while ((c = inStream.read()) != -1)
			{
				outStream.write(c);
			}
			// Make sure streams are closed
			try {inStream.close();} catch (Exception e) {}
			try {outStream.close();} catch (Exception e) {}
			return true;
		}
		catch (IOException e)
		{
			throw new SrtmSourceException("Error while downloading tile "+inTile.getTileName()+": "+e.getMessage());
		}
	}

	public int getRowSize(SrtmTile inTile)
	{
		return 1201;
	}
}
