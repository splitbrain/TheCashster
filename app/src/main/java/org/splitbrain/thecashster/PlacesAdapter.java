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

import io.realm.Case;
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
     */
    void findNearbyPlaces(@Nullable Location location, String filter) {
        if (location == null) {
            Toast.makeText(mContext, "Sorry, no location available",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // load our own places here
        loadLocalPlaces(location, filter);
        addCustomPlace(location, filter);
        loadFoursquarePlaces(location, filter);
    }

    /**
     * Load matching nearby places from foursquare
     */
    private void loadFoursquarePlaces(final Location location, final String filter) {
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
    }

    /**
     * Find locally stored places nearby
     * <p>
     * Adds the result ordered by lastUsed date. Nearby places are found through a bounding box
     * based on the locations accuracy
     */
    private void loadLocalPlaces(Location location, String filter) {
        LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());
        int radius = round(location.getAccuracy() * 15);
        LatLngBounds bnd = toBounds(ll, radius);

        Realm realm = Realm.getDefaultInstance();
        RealmQuery<Place> query = realm.where(Place.class);
        query.between("lat", bnd.southwest.latitude, bnd.northeast.latitude);
        query.between("lon", bnd.southwest.longitude, bnd.northeast.longitude);
        if (filter.length() > 0) {
            query.contains("name", filter, Case.INSENSITIVE);
        }

        RealmResults<Place> results = query.findAllSorted("lastused", Sort.DESCENDING);
        for (Place result : results) {
            result.setLocal(true);
            result.setDistanceFrom(ll);
            add(result);
        }
    }

    /**
     * Adds a custom place
     */
    private void addCustomPlace(Location ll, String filter) {
        if(filter.length() == 0) return;

        Place custom = new Place();
        custom.setCategory("Custom");
        custom.setLat(ll.getLatitude());
        custom.setLon(ll.getLongitude());
        custom.setName(filter);
        custom.setInfo("Create new Place");
        add(custom);
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

        String info = item.getInfo();
        if (info.length() == 0) {
            info = item.getAddress()
                    .concat(" [")
                    .concat(item.getCategory())
                    .concat("]")
                    .concat(" ")
                    .concat(String.valueOf(item.getDistance()))
                    .concat("m");
        }

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

    /**
     * Remove all items and reset the selected item
     */
    @Override
    public void clear() {
        super.clear();
        mSelected = -1;
    }
}
