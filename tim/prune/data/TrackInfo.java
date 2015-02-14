package tim.prune.data;

import java.util.Iterator;
import java.util.Set;
import tim.prune.UpdateMessageBroker;

/**
 * Class to hold all track information, including data
 * and the selection information
 */
public class TrackInfo
{
	private Track _track = null;
	private Selection _selection = null;
	private FileInfo _fileInfo = null;
	private PhotoList _photoList = null;


	/**
	 * Constructor
	 * @param inTrack Track object
	 */
	public TrackInfo(Track inTrack)
	{
		_track = inTrack;
		_selection = new Selection(_track);
		_fileInfo = new FileInfo();
		_photoList = new PhotoList();
	}


	/**
	 * @return the Track object
	 */
	public Track getTrack()
	{
		return _track;
	}


	/**
	 * @return the Selection object
	 */
	public Selection getSelection()
	{
		return _selection;
	}


	/**
	 * @return the FileInfo object
	 */
	public FileInfo getFileInfo()
	{
		return _fileInfo;
	}

	/**
	 * @return the PhotoList object
	 */
	public PhotoList getPhotoList()
	{
		return _photoList;
	}

	/**
	 * Get the currently selected point, if any
	 * @return DataPoint if single point selected, otherwise null
	 */
	public DataPoint getCurrentPoint()
	{
		return _track.getPoint(_selection.getCurrentPointIndex());
	}

	/**
	 * Get the currently selected photo, if any
	 * @return Photo if selected, otherwise null
	 */
	public Photo getCurrentPhoto()
	{
		return _photoList.getPhoto(_selection.getCurrentPhotoIndex());
	}


	/**
	 * Add a Set of Photos
	 * @param inSet Set containing Photo objects
	 * @return array containing number of photos and number of points added
	 */
	public int[] addPhotos(Set<Photo> inSet)
	{
		// Firstly count number of points and photos to add
		int numPhotosToAdd = 0;
		int numPointsToAdd = 0;
		Iterator<Photo> iterator = null;
		if (inSet != null && !inSet.isEmpty())
		{
			iterator = inSet.iterator();
			while (iterator.hasNext())
			{
				try
				{
					Photo photo = iterator.next();
					if (photo != null && !_photoList.contains(photo))
					{
						numPhotosToAdd++;
						if (photo.getDataPoint() != null)
						{
							numPointsToAdd++;
						}
					}
				}
				catch (ClassCastException ce) {}
			}
		}
		// If there are any photos to add, add them
		if (numPhotosToAdd > 0)
		{
			DataPoint[] dataPoints = new DataPoint[numPointsToAdd];
			int pointNum = 0;
			boolean hasAltitude = false;
			// Add each Photo in turn
			iterator = inSet.iterator();
			while (iterator.hasNext())
			{
				try
				{
					Photo photo = iterator.next();
					if (photo != null && !_photoList.contains(photo))
					{
						// Add photo
						_photoList.addPhoto(photo);
						// Add point if there is one
						if (photo.getDataPoint() != null)
						{
							dataPoints[pointNum] = photo.getDataPoint();
							// Check if any points have altitudes
							hasAltitude |= (photo.getDataPoint().getAltitude() != null);
							pointNum++;
						}
					}
				}
				catch (ClassCastException ce) {}
			}
			if (numPointsToAdd > 0)
			{
				// add points to track
				_track.appendPoints(dataPoints);
				// modify track field list
				_track.getFieldList().extendList(Field.LATITUDE);
				_track.getFieldList().extendList(Field.LONGITUDE);
				if (hasAltitude) {_track.getFieldList().extendList(Field.ALTITUDE);}
			}
		}
		int[] result = {numPhotosToAdd, numPointsToAdd};
		return result;
	}


	/**
	 * Delete the currently selected range of points
	 * @return true if successful
	 */
	public boolean deleteRange()
	{
		int startSel = _selection.getStart();
		int endSel = _selection.getEnd();
		boolean answer = _track.deleteRange(startSel, endSel);
		// clear range selection
		_selection.modifyRangeDeleted();
		return answer;
	}


	/**
	 * Delete the currently selected point
	 * @return true if point deleted
	 */
	public boolean deletePoint()
	{
		if (_track.deletePoint(_selection.getCurrentPointIndex()))
		{
			_selection.modifyPointDeleted();
			UpdateMessageBroker.informSubscribers();
			return true;
		}
		return false;
	}


	/**
	 * Delete the currently selected photo and optionally its point too
	 * @param inPointToo true to also delete associated point
	 * @return true if delete successful
	 */
	public boolean deleteCurrentPhoto(boolean inPointToo)
	{
		// delete currently selected photo
		int photoIndex = _selection.getCurrentPhotoIndex();
		if (photoIndex >= 0)
		{
			Photo photo = _photoList.getPhoto(photoIndex);
			_photoList.deletePhoto(photoIndex);
			// has it got a point?
			if (photo.getDataPoint() != null)
			{
				if (inPointToo)
				{
					// delete point
					int pointIndex = _track.getPointIndex(photo.getDataPoint());
					_track.deletePoint(pointIndex);
				}
				else
				{
					// disconnect point from photo
					photo.getDataPoint().setPhoto(null);
					photo.setDataPoint(null);
				}
			}
			// update subscribers
			_selection.modifyPointDeleted();
			UpdateMessageBroker.informSubscribers();
		}
		return true;
	}


	/**
	 * Delete all the points which have been marked for deletion
	 * @return number of points deleted
	 */
	public int deleteMarkedPoints()
	{
		int numDeleted = _track.deleteMarkedPoints();
		if (numDeleted > 0) {
			_selection.clearAll();
			UpdateMessageBroker.informSubscribers();
		}
		return numDeleted;
	}


	/**
	 * Clone the selected range of data points
	 * @return shallow copy of DataPoint objects
	 */
	public DataPoint[] cloneSelectedRange()
	{
		return _track.cloneRange(_selection.getStart(), _selection.getEnd());
	}

	/**
	 * Merge the track segments within the given range
	 * @param inStart start index
	 * @param inEnd end index
	 * @return true if successful
	 */
	public boolean mergeTrackSegments(int inStart, int inEnd)
	{
		boolean firstTrackPoint = true;
		// Loop between start and end
		for (int i=inStart; i<=inEnd; i++) {
			DataPoint point = _track.getPoint(i);
			// Set all segments to false apart from first track point
			if (point != null && !point.isWaypoint()) {
				point.setSegmentStart(firstTrackPoint);
				firstTrackPoint = false;
			}
		}
		// Find following track point, if any
		DataPoint nextPoint = _track.getNextTrackPoint(inEnd+1);
		if (nextPoint != null) {nextPoint.setSegmentStart(true);}
		_selection.markInvalid();
		UpdateMessageBroker.informSubscribers();
		return true;
	}

	/**
	 * Interpolate extra points between two selected ones
	 * @param inNumPoints num points to insert
	 * @return true if successful
	 */
	public boolean interpolate(int inNumPoints)
	{
		boolean success = _track.interpolate(_selection.getStart(), inNumPoints);
		if (success) {
			_selection.selectRangeEnd(_selection.getEnd() + inNumPoints);
		}
		return success;
	}


	/**
	 * Average selected points to create a new one
	 * @return true if successful
	 */
	public boolean average()
	{
		boolean success = _track.average(_selection.getStart(), _selection.getEnd());
		if (success) {
			selectPoint(_selection.getEnd()+1);
		}
		return success;
	}


	/**
	 * Select the given DataPoint
	 * @param inPoint DataPoint object to select
	 */
	public void selectPoint(DataPoint inPoint)
	{
		selectPoint(_track.getPointIndex(inPoint));
	}

	/**
	 * Select the data point with the given index
	 * @param inPointIndex index of DataPoint to select, or -1 for none
	 */
	public void selectPoint(int inPointIndex)
	{
		if (_selection.getCurrentPointIndex() == inPointIndex || inPointIndex >= _track.getNumPoints()) {return;}
		// get the index of the current photo
		int photoIndex = _selection.getCurrentPhotoIndex();
		// Check if point has photo or not
		boolean pointHasPhoto = false;
		if (inPointIndex >= 0)
		{
			Photo pointPhoto = _track.getPoint(inPointIndex).getPhoto();
			pointHasPhoto = (pointPhoto != null);
			if (pointHasPhoto) {
				photoIndex = _photoList.getPhotoIndex(pointPhoto);
			}
		}
		// Might need to deselect photo
		if (!pointHasPhoto)
		{
			// selected point hasn't got a photo - deselect photo if necessary
			if (photoIndex < 0 || _photoList.getPhoto(photoIndex).isConnected()) {
				photoIndex = -1;
			}
		}
		// give to selection
		_selection.selectPhotoAndPoint(photoIndex, inPointIndex);
	}

	/**
	 * Select the given Photo and its point if any
	 * @param inPhotoIndex index of photo to select
	 */
	public void selectPhoto(int inPhotoIndex)
	{
		if (_selection.getCurrentPhotoIndex() == inPhotoIndex) {return;}
		// Photo is primary selection here, not as a result of a point selection
		// Therefore the photo selection takes priority, deselecting point if necessary
		// Find Photo object
		Photo photo = _photoList.getPhoto(inPhotoIndex);
		if (photo != null)
		{
			// Find point object and its index
			int pointIndex = _track.getPointIndex(photo.getDataPoint());
			// Check whether to deselect current point or not if photo not correlated
			if (pointIndex < 0)
			{
				int currPointIndex = _selection.getCurrentPointIndex();
				if (currPointIndex >= 0 && _track.getPoint(currPointIndex).getPhoto() == null)
				{
					pointIndex = currPointIndex; // Keep currently selected point
				}
			}
			// give to selection object
			_selection.selectPhotoAndPoint(inPhotoIndex, pointIndex);
		}
		else {
			// no photo, just reset selection
			DataPoint currPoint = getCurrentPoint();
			if (currPoint != null && currPoint.getPhoto() == null) {
				_selection.selectPhotoAndPoint(-1, _selection.getCurrentPointIndex()); // keep point
			}
			else {
				_selection.selectPhotoAndPoint(-1, -1); // deselect point too
			}
		}
	}
}
