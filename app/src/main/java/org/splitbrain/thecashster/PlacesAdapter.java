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

import org.splitbrain.thecashster.Tasks.AsyncHandlerTask;
import org.splitbrain.thecashster.Tasks.FourSquareTask;
import org.splitbrain.thecashster.model.Place;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.realm.Case;
import io.realm.Realm;
import io.realm.RealmQuery;
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
            Toast.makeText(mContext, R.string.err_nolocation,
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
                calculateRadius(location),
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
        LatLngBounds bnd = toBounds(ll, calculateRadius(location));

        Realm realm = Realm.getDefaultInstance();
        RealmQuery<Place> query = realm.where(Place.class);
        query.between("lat", bnd.southwest.latitude, bnd.northeast.latitude);
        query.between("lon", bnd.southwest.longitude, bnd.northeast.longitude);
        if (filter.length() > 0) {
            query.contains("name", filter, Case.INSENSITIVE);
        }

        List<Place> results = realm.copyFromRealm(query.findAllSorted("lastused", Sort.DESCENDING));
        for (Place result : results) {
            result.setLocal(true);
            result.setDistanceFrom(ll);
            add(result);
        }

        realm.close();
    }

    /**
     * Calculate the radius based on the current accuracy
     */
    private int calculateRadius(Location location) {
        return round(location.getAccuracy() * 15);
    }

    /**
     * Adds a custom place
     */
    private void addCustomPlace(Location ll, String filter) {
        if (filter.length() == 0) return;

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
     */
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        View rowView = convertView;
        if (rowView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            assert inflater != null;
            rowView = inflater.inflate(R.layout.places_row, parent, false);

            ViewHolder viewHolder = new ViewHolder();
            viewHolder.first = rowView.findViewById(R.id.firstLine);
            viewHolder.second = rowView.findViewById(R.id.secondLine);
            viewHolder.radio = rowView.findViewById(R.id.radioSelect);
            viewHolder.star = rowView.findViewById(R.id.imageStar);

            rowView.setTag(viewHolder);
        }

        ViewHolder holder = (ViewHolder) rowView.getTag();
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

        holder.first.setText(item.getName());
        holder.second.setText(info);
        holder.radio.setChecked(mSelected == position);
        if (item.isLocal()) {
            holder.star.setVisibility(View.VISIBLE);
        } else {
            holder.star.setVisibility(View.GONE);
        }

        return rowView;
    }

    /**
     * Hold the views for a row item
     */
    static class ViewHolder {
        TextView first;
        TextView second;
        RadioButton radio;
        ImageView star;
    }

    // region Overrides

    @Override
    public void addAll(@NonNull Collection<? extends Place> collection) {
        for (Place x : collection) {
            add(x);
        }
    }

    @Override
    public void add(@Nullable Place object) {
        if (object == null) return;
        if (mItems.contains(object)) return;
        super.add(object);
    }

    @Override
    public void clear() {
        super.clear();
        mSelected = -1;
    }

    @Override
    public void remove(@Nullable Place object) {
        super.remove(object);
        mSelected = -1;
    }

    // endregion
}
