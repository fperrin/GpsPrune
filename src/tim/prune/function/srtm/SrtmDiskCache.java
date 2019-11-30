package tim.prune.function.srtm;

import java.io.File;

import tim.prune.config.Config;
import tim.prune.I18nManager;

public class SrtmDiskCache {

	private static boolean _cacheIsUsable = false;
	private static File _cacheDir = null;

	public static boolean ensureCacheIsUsable()
	{

		if (_cacheIsUsable)
		{
			return true;
		}
		// Check the cache is ok
		String diskCachePath = Config.getConfigString(Config.KEY_DISK_CACHE);
		if (diskCachePath == null)
		{
			return false;
		}
		File srtmDir = new File(diskCachePath, "srtm");
		if (!srtmDir.exists() && !srtmDir.mkdir()) {
			// can't create the srtm directory
			return false;
		}
		_cacheIsUsable = true;
		_cacheDir = srtmDir;
		return true;
	}

	public static File getCacheDir(String inSourceName)
	{
		if (_cacheDir == null)
		{
			ensureCacheIsUsable();
		}
		File cacheDir = new File(_cacheDir, inSourceName);
		if (!cacheDir.exists() && !cacheDir.mkdir()) {
			// can't create the srtm directory
			return null;
		}
		return cacheDir;
	}
}
