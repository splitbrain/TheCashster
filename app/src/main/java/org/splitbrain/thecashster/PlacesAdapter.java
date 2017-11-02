package org.splitbrain.thecashster;

import android.content.Context;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.maps.android.SphericalUtil;

import org.splitbrain.thecashster.model.Place;

import java.util.ArrayList;

import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;
import io.realm.Sort;

import static java.lang.Math.round;

/**
 * This adapter manages the list of available places
 * <p>
 * It's used in the EntryActivity. The adapter takes care of fetching the necessary data
 * to display on it's own when the findNearbyPlaces() method is called and thus can be
 * initialized with an empty list.
 *
 * @author Andreas Gohr andi@splitbrain.org
 */
public class PlacesAdapter extends ArrayAdapter<Place> {

    private Context mContext;
    private int mSelected = -1;
    private final ArrayList<Place> mItems;

    /**
     * Constructor
     */
    PlacesAdapter(@NonNull Context context, ArrayList<Place> items) {
        super(context, -1, items);
        mItems = items; // keep a reference to the items
        mContext = context;
    }

    /**
     * Marks the item at the given position as selected
     * <p>
     * We only allow one Item to be selected at a time. So this unselects any previously
     * selected item.
     *
     * @param position The position of the item to select
     */
    void selectItem(int position) {
        mSelected = position;
        notifyDataSetChanged();
    }


    /**
     * Return the currently selected Item
     */
    @Nullable
    Place getSelected() {
        try {
            return getItem(mSelected);
        } catch (java.lang.ArrayIndexOutOfBoundsException ignored) {
            mSelected = -1;
            return null;
        }
    }


    /**
     * Triggers updating the list of items based on the given location and filter
     *
     * @param location the user's current location as returned by Google Play Services
     * @param filter   the currently entered search string
     * @todo break down into smaller internal pieces
     * @todo replace google places API with foursquare or Places search
     */
    void findNearbyPlaces(@Nullable Location location, String filter) {
        if (location == null) {
            Toast.makeText(mContext, "Sorry, no location available",
                    Toast.LENGTH_SHORT).show();

            return;
        }

        // location.hasAccuracy() could define the bounding box size 250 vs 500m

        // load our own places here
        loadLocalPlaces(location);

        LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());
        FourSquareTask fst = new FourSquareTask(
                mContext.getResources().getString(R.string.FourSquareClientID),
                mContext.getResources().getString(R.string.FourSquareClientSecret),
                ll,
                filter
        );
        fst.setOnTaskCompleted(new AsyncHandlerTask.OnTaskCompleted() {
            @Override
            public void onTaskCompleted(AsyncHandlerTask task) {
                addAll(((FourSquareTask) task).getPlaces());
            }
        });
        fst.execute();


/*
        // fixme we probably want to replace this with the PlacesSearch API or the foursquare API
        PlaceDetectionClient mPlaceDetectionClient = Places.getPlaceDetectionClient(mContext, null);
        Task<PlaceLikelihoodBufferResponse> placeResult = mPlaceDetectionClient.getCurrentPlace(null);
        placeResult.addOnCompleteListener(new OnCompleteListener<PlaceLikelihoodBufferResponse>() {
            @Override
            public void onComplete(@NonNull Task<PlaceLikelihoodBufferResponse> task) {
                PlaceLikelihoodBufferResponse likelyPlaces = task.getResult();
                for (PlaceLikelihood placeLikelihood : likelyPlaces) {
                    Place place = new Place();
                    place.setName(placeLikelihood.getPlace().getName().toString());
                    place.setLatLng(placeLikelihood.getPlace().getLatLng());
                    place.setAddress(placeLikelihood.getPlace().getAddress().toString());
                    place.setFoursquare(placeLikelihood.getPlace().getId());

                    if (mItems.contains(place)) {
                        Log.d("me", "place already there");
                    } else {
                        Log.d("me", "place new");
                        add(place);
                    }
                }
                likelyPlaces.release();
            }
        });
        */
    }

    /**
     * Find locally stored places nearby
     * <p>
     * Adds the result ordered by lastUsed date. NEarby places are found through a bounding box
     * based on the locations accuracy
     *
     * @param location the current location
     */
    private void loadLocalPlaces(Location location) {
        LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());
        int radius = round(location.getAccuracy() * 15);
        LatLngBounds bnd = toBounds(ll, radius);

        Realm realm = Realm.getDefaultInstance();
        RealmQuery<Place> query = realm.where(Place.class);
        query.between("lat", bnd.southwest.latitude, bnd.northeast.latitude);
        query.between("lon", bnd.southwest.longitude, bnd.northeast.longitude);

        RealmResults<Place> results = query.findAllSorted("lastused", Sort.DESCENDING);
        for (Place result : results) {
            result.setLocal(true);
            add(result);
        }
    }


    /**
     * Creates a bounding box around the given location
     *
     * @param center         the current location
     * @param radiusInMeters the distance from that location to make the bounding box
     * @return the bounding box
     * @link https://stackoverflow.com/a/31029389/172068
     */
    @NonNull
    private LatLngBounds toBounds(LatLng center, double radiusInMeters) {
        double distanceFromCenterToCorner = radiusInMeters * Math.sqrt(2.0);
        LatLng southwestCorner =
                SphericalUtil.computeOffset(center, distanceFromCenterToCorner, 225.0);
        LatLng northeastCorner =
                SphericalUtil.computeOffset(center, distanceFromCenterToCorner, 45.0);
        return new LatLngBounds(southwestCorner, northeastCorner);
    }

    /**
     * Create the view for each row of places
     *
     * @todo implement viewholder pattern
     */
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) mContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rowView = inflater.inflate(R.layout.places_row, parent, false);

        Place item = getItem(position);
        assert item != null;

        String info = item.getAddress()
                .concat(" [")
                .concat(item.getCategory())
                .concat("]");

        TextView first = rowView.findViewById(R.id.firstLine);
        first.setText(item.getName());

        TextView second = rowView.findViewById(R.id.secondLine);
        second.setText(info);

        RadioButton radio = rowView.findViewById(R.id.radioSelect);
        radio.setChecked(mSelected == position);

        ImageView star = rowView.findViewById(R.id.imageStar);
        if (item.isLocal()) {
            star.setVisibility(View.VISIBLE);
        } else {
            star.setVisibility(View.GONE);
        }

        return rowView;
    }

    @Override
    public void clear() {
        super.clear();
        mSelected = -1;
    }
}
