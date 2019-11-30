package tim.prune.function.srtm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.HttpURLConnection;

import tim.prune.App;
import tim.prune.GpsPrune;
import tim.prune.config.Config;

/**
 * Create an account at: https://urs.earthdata.nasa.gov/users/new
 * Data policy: https://lpdaac.usgs.gov/data/data-citation-and-policies/
 *
 */

public class SrtmGl1Source extends SrtmSource {
	/** URL prefix for all tiles */
	private static final String URL_PREFIX = "https://e4ftl01.cr.usgs.gov/MEASURES/SRTMGL1.003/2000.02.11/";
	/** Auth URL */
	private static final String AUTH_URL = "urs.earthdata.nasa.gov";


	public SrtmGl1Source()
	{
		CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
	}

	public String getName()
	{
		return "SRTMGL1_v003";
	}

	protected String getSourceExtension()
	{
		return ".SRTMGL1.hgt.zip";
	}

	private URL buildUrl(SrtmTile inTile)
		throws SrtmSourceException
	{
		try {
			return new URL(URL_PREFIX + inTile.getTileName() + getSourceExtension());
		}
		catch (MalformedURLException e)
		{
			throw new SrtmSourceException(e.getMessage());
		}
	}

	public boolean isReadyToUse()
	{
		return getAuth() != null;
	}

	private String getAuth()
	{
		String authString = Config.getConfigString(Config.KEY_EARTHDATA_AUTH);
		if (authString != null)
		{
			return "Basic " + authString; 
		}
		else
		{
			return null;
		}
	}

	public boolean downloadTile(SrtmTile inTile)
		throws SrtmSourceException
	{
		return downloadTile(inTile, getAuth());
	}

	private boolean downloadTile(SrtmTile inTile, String auth)
		throws SrtmSourceException
	{
		URL tileUrl = buildUrl(inTile);
		File outputFile = getCacheFileName(inTile);
		System.out.println("Download: Need to download: " + tileUrl);
		try
		{
			HttpURLConnection conn = (HttpURLConnection) tileUrl.openConnection();
			long fileLength = 0L;

			// Define streams
			FileOutputStream outStream = null;
			InputStream inStream = null;

			// Documentation about HTTP interface at:
			// https://wiki.earthdata.nasa.gov/display/EL/How+To+Access+Data+With+Java
			int redirects = 0;

			while (redirects < 10) {
				redirects++;

				conn.setRequestProperty("User-Agent", "GpsPrune v" + GpsPrune.VERSION_NUMBER);
				conn.setInstanceFollowRedirects(false);
				conn.setUseCaches(false);
				if (conn.getURL().getHost().equals(AUTH_URL))
				{
					conn.setRequestProperty("Authorization", auth);
				}

				int status = conn.getResponseCode();
				if (status == 200)
				{
					// Found the tile, we're good
					inStream = conn.getInputStream();
					fileLength = conn.getContentLengthLong();
					break;
				}
				else if (status == 302)
				{
					// redirected to SSO server then back to original resource
					String newUrl = conn.getHeaderField("Location");
					conn = (HttpURLConnection) (new URL(newUrl)).openConnection();
				}
				else if (status == 404)
				{
					throw new SrtmSourceException("Tile " + inTile.getTileName() + " not found at " + conn.getURL());
				}
				else
				{
					throw new SrtmSourceException("Invalid response from server: " + status + conn.getResponseMessage());
				}
			}

			// _progress.setValue(t * 10 + 1);
			outStream = new FileOutputStream(outputFile);

			// Copy all the bytes to the file
			int c;
			long written = 0L;
			while ((c = inStream.read()) != -1)
			{
				outStream.write(c);
				written++;
				// _progress.setValue(t * 10 + 1 + (int) ((10 * written) / fileLength));
			}
			// Make sure streams are closed
			try {inStream.close();} catch (Exception e) {}
			try {outStream.close();} catch (Exception e) {}
			return true;
		}
		catch (IOException e)
		{
			throw new SrtmSourceException("Error while downloading tile " + inTile.getTileName() + ": "+e.getMessage());
		}
	}

	public boolean testAuth(String auth)
		throws SrtmSourceException
	{
		// The only thing special about this tile is that it's the smallest tile
		// It covers small islands in Malaysia
		SrtmTile testTile = new SrtmTile(7, 117);
		if (isCached(testTile))
		{
			getCacheFileName(testTile).delete();
		}
		return downloadTile(testTile, auth);
	}

	public int getRowSize(SrtmTile inTile)
	{
		return 3601;
	}
}
