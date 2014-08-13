package com.mapzen.activity;

import com.mapzen.MapController;
import com.mapzen.MapzenApplication;
import com.mapzen.R;
import com.mapzen.android.lost.LocationClient;
import com.mapzen.core.DataUploadService;
import com.mapzen.core.SettingsFragment;
import com.mapzen.fragment.MapFragment;
import com.mapzen.route.RouteFragment;
import com.mapzen.route.RoutePreviewFragment;
import com.mapzen.search.AutoCompleteAdapter;
import com.mapzen.search.OnPoiClickListener;
import com.mapzen.search.PagerResultsFragment;
import com.mapzen.search.SavedSearch;
import com.mapzen.util.DatabaseHelper;
import com.mapzen.util.DebugDataSubmitter;
import com.mapzen.util.Logger;
import com.mapzen.util.MapzenGPSPromptDialogFragment;
import com.mapzen.util.MapzenProgressDialogFragment;

import com.bugsense.trace.BugSenseHandler;
import com.squareup.okhttp.OkHttpClient;

import org.oscim.android.MapActivity;
import org.oscim.layers.marker.MarkerItem;
import org.oscim.map.Map;
import org.scribe.model.Token;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.support.v4.app.FragmentManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ImageSpan;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.SearchView;

import java.io.File;
import java.util.Calendar;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import static android.preference.PreferenceManager.getDefaultSharedPreferences;
import static com.mapzen.MapController.getMapController;
import static com.mapzen.search.SavedSearch.getSavedSearch;

public class BaseActivity extends MapActivity {
    @Inject MapzenProgressDialogFragment progressDialogFragment;
    public static final String COM_MAPZEN_UPDATE_VIEW = "com.mapzen.updates.view";
    public static final String COM_MAPZEN_UPDATES_LOCATION = "com.mapzen.updates.location";
    public static final String
            DEBUG_DATA_ENDPOINT = "http://on-the-road.dev.mapzen.com/upload";
    protected DatabaseHelper dbHelper;
    protected DebugDataSubmitter debugDataSubmitter;
    @Inject LocationClient locationClient;
    private Menu activityMenu;
    private AutoCompleteAdapter autoCompleteAdapter;
    private MapzenApplication app;
    private MapFragment mapFragment;
    private MapzenGPSPromptDialogFragment gpsPromptDialogFragment;

    protected boolean enableActionbar = true;

    protected Executor debugDataExecutor = Executors.newSingleThreadExecutor();

    MenuItem searchMenuItem;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (MapzenApplication) getApplication();
        app.inject(this);
        BugSenseHandler.initAndStartSession(this, "ebfa8fd7");
        BugSenseHandler.addCrashExtraData("OEM", Build.MANUFACTURER);
        setContentView(R.layout.base);
        initMapFragment();
        gpsPromptDialogFragment = new MapzenGPSPromptDialogFragment();
        initMapController();
        initAlarm();
        initSavedSearches();
    }

    @Override
    public void onPause() {
        super.onPause();
        locationClient.disconnect();
        persistSavedSearches();
    }

    @Override
    protected void onResume() {
        super.onResume();
        locationClient.connect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        clearNotifications();
    }

    private void clearNotifications() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(
                NOTIFICATION_SERVICE);
        notificationManager.cancelAll();
    }

    public SQLiteDatabase getDb() {
        return app.getDb();
    }

    public boolean isInDebugMode() {
        SharedPreferences prefs = getDefaultSharedPreferences(this);
        return prefs.getBoolean(getString(R.string.settings_key_debug), false);
    }

    public void toggleDebugMode() {
        SharedPreferences prefs = getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(getString(R.string.settings_key_debug), !isInDebugMode());
        editor.commit();
        toggleMenus();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                final PreferenceFragment settingsFragment = SettingsFragment.newInstance(this);
                getFragmentManager().beginTransaction()
                        .add(R.id.settings, settingsFragment, SettingsFragment.TAG)
                        .addToBackStack(null)
                        .commit();
                return true;
            case R.id.phone_home:
                initDebugDataSubmitter();
                debugDataExecutor.execute(debugDataSubmitter);
                return true;
            case R.id.logout:
                SharedPreferences prefs = getSharedPreferences("OAUTH", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.remove("token");
                editor.remove("forced_login");
                editor.commit();
                startActivity(new Intent(this, InitialActivity.class));
                finish();
                return true;
            case R.id.upload_traces:
                uploadTraces();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void showProgressDialog() {
        if (!progressDialogFragment.isAdded()) {
            progressDialogFragment.show(getSupportFragmentManager(), "dialog");
        }
    }

    public void showGPSPromptDialog() {
        if (!gpsPromptDialogFragment.isAdded()) {
            gpsPromptDialogFragment.show(getSupportFragmentManager(), "gps_dialog");
        }
    }

    public void promptForGPSIfNotEnabled() {
        if (!locationClient.isGPSEnabled()) {
            showGPSPromptDialog();
        }
    }

    public void dismissProgressDialog() {
        progressDialogFragment.dismiss();
    }

    public MapzenProgressDialogFragment getProgressDialogFragment() {
        return progressDialogFragment;
    }

    private void initMapFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        mapFragment = (MapFragment) fragmentManager.findFragmentById(R.id.map_fragment);
        mapFragment.setAct(this);
        mapFragment.setOnPoiClickListener(new OnPoiClickListener() {
            @Override
            public void onPoiClick(int index, MarkerItem item) {
                final PagerResultsFragment pagerResultsFragment = getPagerResultsFragment();
                pagerResultsFragment.setCurrentItem(index);
            }
        });
    }

    private PagerResultsFragment getPagerResultsFragment() {
        return (PagerResultsFragment) getSupportFragmentManager()
                .findFragmentByTag(PagerResultsFragment.TAG);
    }

    public MapFragment getMapFragment() {
        return mapFragment;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        activityMenu = menu;
        MenuInflater inflater = getMenuInflater();
        int menuLayout = isInDebugMode() ? R.menu.debug_option_group : R.menu.options_menu;
        inflater.inflate(menuLayout, menu);
        SearchManager searchManager =
                (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        searchMenuItem = menu.findItem(R.id.search);
        searchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                hideOptionsMenu();
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                resetSearchView();
                final PagerResultsFragment pagerResultsFragment = getPagerResultsFragment();
                if (pagerResultsFragment != null && pagerResultsFragment.isAdded()) {
                    getSupportFragmentManager().beginTransaction().remove(pagerResultsFragment)
                            .commit();
                }
                showOptionsMenu();
                return true;
            }
        });

        final SearchView searchView = (SearchView) searchMenuItem.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        initSavedSearchAutoComplete(searchView);
        setupAdapter(searchView);
        searchView.setOnQueryTextListener(autoCompleteAdapter);

        if (!app.getCurrentSearchTerm().isEmpty()) {
            searchMenuItem.expandActionView();
            Logger.d("search: " + app.getCurrentSearchTerm());
            searchView.setQuery(app.getCurrentSearchTerm(), false);
            searchView.clearFocus();
        }

        Uri data = getIntent().getData();
        if (data != null) {
            Logger.d("data = " + data.toString());
            if (data.toString().contains("geo:")) {
                handleGeoIntent(searchView, data);
            } else if (data.toString().contains("maps.google.com")) {
                handleMapsIntent(searchView, data);
            }
        }
        return true;
    }

    public void hideOptionsMenu() {
        activityMenu.setGroupVisible(R.id.overflow_menu, false);
    }

    public void showOptionsMenu() {
        activityMenu.setGroupVisible(R.id.overflow_menu, true);
    }

    private void resetSearchView() {
        final SearchView searchView = (SearchView) searchMenuItem.getActionView();
        searchView.setQuery("", false);
        searchView.clearFocus();
        searchView.setIconified(true);
        app.setCurrentSearchTerm("");
        autoCompleteAdapter.resetCursor();
        autoCompleteAdapter.loadSavedSearches();
    }

    /**
     * Sets auto-complete threshold to 0. Enables drop-down even when text view is empty. Triggers
     * {@link AutoCompleteAdapter} when search view gets focus. Uses resource black magic to get a
     * reference to the {@link AutoCompleteTextView} inside the {@link SearchView}.
     */
    private void initSavedSearchAutoComplete(final SearchView searchView) {
        final AutoCompleteTextView autoCompleteTextView = getQueryAutoCompleteTextView(searchView);
        autoCompleteTextView.setThreshold(0);
        autoCompleteTextView.setTextAppearance(this, R.style.MapzenSearchText);
        searchView.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    searchMenuItem.expandActionView();
                    autoCompleteAdapter.loadSavedSearches();
                    searchView.setQuery("", false);
                }
            }
        });

        final ImageView close = (ImageView) searchView.findViewById(searchView.getContext()
                .getResources().getIdentifier("android:id/search_close_btn", null, null));
        close.setImageDrawable(getResources().getDrawable(R.drawable.ic_cancel));

        // Set custom search hint icon.
        final SpannableStringBuilder ssb =
                new SpannableStringBuilder(getString(R.string.search_hint_icon_spacer));
        final Drawable searchIcon = getResources().getDrawable(R.drawable.ic_search);
        int textSize = (int) (autoCompleteTextView.getTextSize() * 1.25);
        searchIcon.setBounds(0, 0, textSize, textSize);
        ssb.setSpan(new ImageSpan(searchIcon), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        autoCompleteTextView.setHint(ssb);
    }

    public AutoCompleteTextView getQueryAutoCompleteTextView(SearchView searchView) {
        return (AutoCompleteTextView) searchView.findViewById(searchView.getContext()
                .getResources().getIdentifier("android:id/search_src_text", null, null));
    }

    private void toggleMenus() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MenuInflater inflater = getMenuInflater();
                activityMenu.clear();
                inflater.inflate(R.menu.debug_option_group, activityMenu);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (settingsFragmentOnBack()) {
            return;
        }
        if (routingFragmentOnBack()) {
            return;
        }
        super.onBackPressed();
        clearNotifications();
    }

    private boolean settingsFragmentOnBack() {
        SettingsFragment settingsFragment = (SettingsFragment) getFragmentManager()
                .findFragmentByTag(SettingsFragment.TAG);
        if (settingsFragment != null && settingsFragment.isAdded()) {
            getFragmentManager().beginTransaction()
                    .detach(settingsFragment)
                    .commit();
            return true;
        }
        return false;
    }

    private boolean routingFragmentOnBack() {
        RouteFragment routeFragment = (RouteFragment)
                getSupportFragmentManager().findFragmentByTag(RouteFragment.TAG);
        if (routeFragment != null && routeFragment.isAdded()) {
            boolean shouldClose = routeFragment.onBackAction();
            if (shouldClose) {
                ((RoutePreviewFragment) getSupportFragmentManager()
                        .findFragmentByTag(RoutePreviewFragment.TAG)).showFragmentContents();
                clearNotifications();
                super.onBackPressed();
            }
            return true;
        }
        return false;
    }


    private void handleGeoIntent(SearchView searchView, Uri data) {
        if (data.toString().contains("q=")) {
            searchMenuItem.expandActionView();
            String queryString = Uri.decode(data.toString().split("q=")[1]);
            app.setCurrentSearchTerm(queryString);
            searchView.setQuery(queryString, true);
        }
    }

    private void handleMapsIntent(SearchView searchView, Uri data) {
        if (data.toString().contains("q=")) {
            searchMenuItem.expandActionView();
            String queryString = Uri.decode(data.toString().split("q=")[1].split("@")[0]);
            app.setCurrentSearchTerm(queryString);
            searchView.setQuery(queryString, true);
        }
    }

    public Map getMap() {
        return mMap;
    }

    public SearchView getSearchView() {
        if (searchMenuItem != null) {
            return (SearchView) searchMenuItem.getActionView();
        }

        return null;
    }

    public MenuItem getSearchMenu() {
        return searchMenuItem;
    }

    public void setupAdapter(SearchView searchView) {
        if (autoCompleteAdapter == null) {
            autoCompleteAdapter = new AutoCompleteAdapter(getActionBar().getThemedContext(),
                    this, app.getColumns(), getSupportFragmentManager());
            autoCompleteAdapter.setSearchView(searchView);
            autoCompleteAdapter.setMapFragment(mapFragment);
        }

        searchView.setSuggestionsAdapter(autoCompleteAdapter);
    }

    private void initMapController() {
        MapController mapController = getMapController();
        mapController.setActivity(this);
    }

    public void hideActionBar() {
        collapseSearchView();
        getActionBar().hide();
    }

    public void collapseSearchView() {
        MenuItem searchMenu = getSearchMenu();
        if (searchMenu != null) {
            searchMenu.collapseActionView();
        }
    }

    public void enableActionbar() {
        enableActionbar = true;
    }

    public void disableActionbar() {
        enableActionbar = false;
    }

    public void showActionBar() {
        if (!getActionBar().isShowing() && enableActionbar) {
            getActionBar().show();
        }
    }

    public boolean executeSearchOnMap(String query) {
        final SearchView searchView = (SearchView) searchMenuItem.getActionView();
        searchView.setSuggestionsAdapter(null);

        PagerResultsFragment pagerResultsFragment = PagerResultsFragment.newInstance(this);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.pager_results_container, pagerResultsFragment,
                        PagerResultsFragment.TAG)
                .commit();
        return pagerResultsFragment.executeSearchOnMap(getSearchView(), query);
    }

    public void initDebugDataSubmitter() {
        debugDataSubmitter = new DebugDataSubmitter(this);
        debugDataSubmitter.setClient(new OkHttpClient());
        debugDataSubmitter.setEndpoint(DEBUG_DATA_ENDPOINT);
        debugDataSubmitter.setFile(new File(getDb().getPath()));
    }

    public void setAccessToken(Token accessToken) {
        app.setAccessToken(accessToken);
    }

    public void updateView() {
        sendBroadcast(new Intent(COM_MAPZEN_UPDATE_VIEW));
    }

    public interface ViewUpdater {
        public void onViewUpdate();
    }

    private void initAlarm() {
        Calendar cal = Calendar.getInstance();
        Intent intent = new Intent(this, DataUploadService.class);
        PendingIntent pintent = PendingIntent.getService(this, 0, intent, 0);

        int hourInMillis = 60 * 60 * 1000;
        AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        alarm.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), hourInMillis, pintent);
    }

    private void uploadTraces() {
        Intent uploadIntent = new Intent(this, DataUploadService.class);
        startService(uploadIntent);
    }

    private void initSavedSearches() {
        SharedPreferences prefs = getDefaultSharedPreferences(this);
        getSavedSearch().deserialize(prefs.getString(SavedSearch.TAG, ""));
    }

    private void persistSavedSearches() {
        SharedPreferences prefs = getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(SavedSearch.TAG, getSavedSearch().serialize());
        editor.commit();
    }

    public void locateButtonAction(View view) {
        mapFragment.centerOnCurrentLocation();
    }

    public Menu getActivityMenu() {
        return activityMenu;
    }
}
